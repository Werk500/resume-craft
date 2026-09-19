package com.resumecraft.server.mq;


import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic 声明。
 *
 * <p>应用启动时由 KafkaAdmin 自动创建这些 topic（不存在才建，已存在则跳过）。
 * 注意：<b>本类只声明 topic，不影响服务启动</b>——Kafka 不可用时 KafkaAdmin 只打警告，
 * 不会让 Spring 容器启动失败，这样消息队列故障就不会阻断整个服务。
 *
 * <p>{@link NewTopic} 来自 {@code org.apache.kafka.clients.admin}，
 * 由 spring-kafka 传递引入的 kafka-clients 提供。
 */
@Configuration
public class KafkaTopicConfig {

    /** 主 topic：简历 embedding 生成任务 */
    public static final String TOPIC_EMBEDDING_TASK = "embedding-task";

    /** 死信 topic：消费失败且重试耗尽后转入 */
    public static final String TOPIC_EMBEDDING_TASK_DLQ = "embedding-task-dlq";

    /**
     * 主 topic。
     *
     * <p>分区数设为 3：embedding 生成是 IO 密集型（等外部 API），
     * 多分区才能让同一个消费组内的多个消费者实例并行处理。
     * 单分区会被 Kafka 的顺序性约束退化成串行消费。
     */
    @Bean
    public NewTopic embeddingTaskTopic() {
        return TopicBuilder.name(TOPIC_EMBEDDING_TASK)
                .partitions(3)
                .replicas(1)   // 单节点开发环境，副本数只能是 1
                .build();
    }

    /**
     * 死信 topic：3 次重试仍失败的消息落到这里，避免毒消息反复阻塞消费。
     * 只需 1 个分区——人工排查用的低频通道，不需要并行。
     */
    @Bean
    public NewTopic embeddingTaskDlqTopic() {
        return TopicBuilder.name(TOPIC_EMBEDDING_TASK_DLQ)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
