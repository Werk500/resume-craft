package com.resumecraft.server.resume.mq;

import com.resumecraft.server.mq.EmbeddingTaskMessage;
import com.resumecraft.server.mq.KafkaTopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Component
public class EmbeddingTaskProducer {

    private final KafkaTemplate<String, EmbeddingTaskMessage> kafkaTemplate;


    public EmbeddingTaskProducer(KafkaTemplate<String, EmbeddingTaskMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 组装并发送 embedding 生成任务。
     *
     * <p>hash 在这里计算：消费端靠它做幂等短路，内容没变就直接跳过，
     * 连 embedding API 都不调用。之所以由生产者算，是因为只有它知道
     * "这次写入的正文是什么"。
     *
     * @param versionId 优化版本 ID；传 null 表示主简历
     */
    public void publish(Long resumeId,Long versionId,Long userId,String text) {

        if (resumeId == null || text == null || text.isBlank()) {
            log.warn("跳过 embedding 任务：resumeId 为空或正文为空, resumeId={}", resumeId);
            return;
        }

        EmbeddingTaskMessage message = EmbeddingTaskMessage.builder()
                .resumeId(resumeId)
                .versionId(versionId)
                .userId(userId)
                .contentHash(sha256(text))
                .text(text).build();

        sendEmbeddingTask(message);

    }

    /**
     * 发送 embedding 生成任务。
     *
     * <p>关键设计：<b>发送失败只记日志，绝不抛异常。</b>
     * 向量是可重新生成的派生数据，主流程（保存简历）不应因为消息队列故障而失败。
     * 缺失的向量会在匹配时由同步兜底路径补上。
     */
    public void sendEmbeddingTask(EmbeddingTaskMessage message) {

        if (message == null || message.getResumeId() == null) {
            log.warn("跳过 embedding 任务：消息为空或缺少 resumeId");
            return;
        }

        //用 resumeId 作为分区 key：同一份简历的消息落到同一分区，保证顺序
        String key = String.valueOf(message.getResumeId());

        // 两段式异常处理是必须的，缺一不可：
        //
        //   1) try/catch 捕获 send() 的【同步】异常：Kafka 不可达时，
        //      send() 会阻塞到 max.block.ms 后直接抛 KafkaException（元数据拉取失败），
        //      这个异常不会进入 whenComplete，若不捕获会冒泡成 500 打断主流程。
        //   2) whenComplete 处理【异步】失败：消息已交给客户端，但 broker 拒绝或超时。
        //
        // 只写 whenComplete 是常见误区——Kafka 完全不可用时正是走同步路径。
        try {
            kafkaTemplate.send(KafkaTopicConfig.TOPIC_EMBEDDING_TASK, key, message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("embedding 任务发送失败（不影响主流程，匹配时会同步兜底）: resumeId={}, versionId={}, err={}",
                                    message.getResumeId(), message.getVersionId(), ex.getMessage());
                            return;
                        }

                        // 记录 partition + offset，便于日后精确定位这条消息
                        var meta = result.getRecordMetadata();
                        log.info("embedding 任务已投递: topic={}, partition={}, offset={}, resumeId={}, versionId={}",
                                meta.topic(), meta.partition(), meta.offset(),
                                message.getResumeId(), message.getVersionId());
                    });
        } catch (Exception e) {
            log.warn("embedding 任务发送失败（同步异常，不影响主流程，匹配时会同步兜底）: "
                            + "resumeId={}, versionId={}, err={}",
                    message.getResumeId(), message.getVersionId(), e.getMessage());
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }


}
