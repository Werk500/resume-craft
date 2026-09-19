package com.resumecraft.server.job.mq;

import com.resumecraft.server.mq.EmbeddingTaskMessage;
import com.resumecraft.server.mq.KafkaTopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * embedding 任务消费者配置。
 *
 * <h3>三个关键决策</h3>
 * 1. <b>手动 ACK</b>：消息只有在向量真正写入 pgvector 之后才提交 offset，
 *    避免"业务失败但 offset 已提交"导致的任务静默丢失。
 * 2. <b>并发度 = 分区数</b>：topic 有 3 个分区，容器并发设 3 才能吃满并行度。
 *    设大了会空转，设小了消费不完。
 * 3. <b>失败进死信</b>：重试 3 次仍失败的消息转入死信 topic，
 *    避免毒消息阻塞 partition（Kafka 单分区内严格有序，一条消息卡住后面全排队）。
 */

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    /** 固定的消费者组名：组内多个实例会瓜分分区 */
    public static final String GROUP_ID = "job-match-embedding-group";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /** 容器并发度：与 topic 分区数一致 */
    @Value("${app.kafka.consumer.concurrency:3}")
    private int concurrency;


    /**
     * 消费者工厂：定义"怎么连 Kafka、怎么把字节还原成对象"。
     * 泛型 <String, EmbeddingTaskMessage> = key 是字符串，value 是任务消息对象。
     */
    @Bean
    public ConsumerFactory<String, EmbeddingTaskMessage> embeddingConsumerFactory() {
        HashMap<String, Object> props = new HashMap<>();

        // ---- 连接与分组 ----
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);

        // ---- 反序列化：字节 -> 对象 ----
        // key 按字符串解析；value 交给下面的 JsonDeserializer 处理
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // 新消费者组第一次启动、或 offset 失效时，从最早的消息开始读，避免漏掉历史任务
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // 关闭自动提交 offset —— 配合下面的 MANUAL ACK，由业务代码显式记账
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // 每次拉取少量消息：一条消息要调一次外部 embedding API，
        // 拉太多会让单次 poll 处理时间过长，超过 max.poll.interval.ms 会触发 rebalance
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);

        // ---- 自定义 JsonDeserializer ----
        // 第二个参数 false 表示：不使用类型头来决定反序列化目标类
        JsonDeserializer<EmbeddingTaskMessage> deserializer =
                new JsonDeserializer<>(EmbeddingTaskMessage.class, false);

        // 安全白名单：只允许反序列化成这个包下的类，防止恶意消息构造任意对象
        deserializer.addTrustedPackages("com.resumecraft.server.mq");

        // 不依赖生产端的类型头，固定按本地类反序列化——生产/消费端类路径不同时更稳
        deserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);

    }

    /**
     * 监听容器工厂：把消费者工厂 + 并发 + ACK 模式 + 错误处理组装到一起。
     * @KafkaListener 注解默认会找名为 "kafkaListenerContainerFactory" 的 Bean，
     * 这里方法名不同，所以使用方需要通过 containerFactory 属性显式指定。
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EmbeddingTaskMessage> embeddingListenerContainerFactory
    ( ConsumerFactory<String, EmbeddingTaskMessage> embeddingConsumerFactory,
      KafkaTemplate<String, Object> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, EmbeddingTaskMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(embeddingConsumerFactory);

        // 并发消费者数量 = 分区数，吃满并行度
        factory.setConcurrency(concurrency);

        // 手动 ACK：由消费者在业务成功后显式调用 acknowledgment.acknowledge()
        // 业务没成功就不记账，消息还能被重新消费，避免静默丢失
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // ---- 错误处理：重试 + 死信 ----
        // 重试 3 次（间隔 2 秒），仍失败转入死信 topic
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                // 显式指定死信 topic，不依赖默认的 "<topic>-dlt" 命名规则，
                // 保证和 KafkaTopicConfig 里声明的 topic 名一致；
                // 第二个参数 -1 表示分区由 Kafka 自行决定
                (record, ex) -> new TopicPartition(KafkaTopicConfig.TOPIC_EMBEDDING_TASK_DLQ, -1));

        // FixedBackOff(间隔毫秒, 最大重试次数)：每 2 秒重试一次，最多重试 3 次
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3L));

        // 每次重试失败都打一条 warn 日志，记录第几次尝试、位置和错误原因，方便排查
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("embedding 任务消费失败，第 {} 次尝试: topic={}, partition={}, offset={}, err={}",
                        deliveryAttempt, record.topic(), record.partition(), record.offset(), ex.getMessage()));

        // 把错误处理器挂到容器工厂上，监听器生效时会自动使用
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
