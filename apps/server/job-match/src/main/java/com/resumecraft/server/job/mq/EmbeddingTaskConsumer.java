package com.resumecraft.server.job.mq;

import com.resumecraft.server.common.feign.ResumeClient;
import com.resumecraft.server.mq.EmbeddingTaskMessage;
import com.resumecraft.server.mq.KafkaTopicConfig;
import com.resumecraft.server.job.vector.VectorStore;
import feign.FeignException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * embedding 任务消费者：把 Kafka 消息转成 pgvector 里的向量。
 *
 * <h3>为什么这里只有一行业务调用</h3>
 * {@link VectorStore#getOrCreateResumeVector} 已经封装了完整的
 * "查 content_hash → 命中则跳过 → 未命中则调 embedding → 落库" 流程。
 * 消费者只负责"消费消息 + 成功后 ACK"，业务逻辑不重复实现。
 *
 * <h3>幂等</h3>
 * Kafka 是 at-least-once，重复消费是常态。幂等由两层保证：
 * VectorStore 内的 contentHash 短路（不重复调 API）+ 表上的
 * uk_resume_version 唯一约束（重复写只覆盖，不产生重复行）。
 */
@Slf4j
@Component
public class EmbeddingTaskConsumer {

    @Resource
    private VectorStore vectorStore;
    @Resource
    private ResumeClient resumeClient;

    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_EMBEDDING_TASK,
            containerFactory = "embeddingListenerContainerFactory"
    )
    public void onEmbeddingTask(EmbeddingTaskMessage message, Acknowledgment ack) {

        MDC.put("traceId", "kafka-" + System.currentTimeMillis());

        if (message == null || message.getResumeId() == null || message.getText() == null) {
            // 脏消息没有重试价值，直接 ACK 丢弃，避免白白占用重试次数后进死信
            log.warn("收到无效的 embedding 任务消息，已丢弃: {}", message);
            ack.acknowledge();
            return;
        }

        if(message.getVersionId() == null){
            try {
                resumeClient.getResume(message.getResumeId());
            }catch (FeignException e){
                // 注意是 < 500：4xx 才是永久性错误（简历已删除 / REVIEW 待核对），
                // 500 属于服务端临时故障，应该抛出去走重试
                if (e.status() >= 400 && e.status() < 500) {
                    // 4xx = 永久性错误，重试无意义，直接 ACK 丢弃：
                    //   - 简历已删除（避免写回孤儿向量）
                    //   - 简历处于 REVIEW 状态（正文待人工核对，不应生成向量）
                    log.info("简历不可用，跳过向量生成: resumeId={}, status={}",
                            message.getResumeId(), e.status());
                    ack.acknowledge();
                    return;
                }

                // 5xx 等服务端错误视为临时故障，抛出去走重试
                log.warn("校验简历存在性失败，将重试: resumeId={}, status={}, err={}",
                        message.getResumeId(), e.status(), e.getMessage());
                throw e;
            }
        }

        try {
            log.info("开始处理 embedding 任务: resumeId={}, versionId={}, userId={}",
                    message.getResumeId(), message.getVersionId(), message.getUserId());

            float[] vector = vectorStore.getOrCreateResumeVector(
                    message.getResumeId(),
                    message.getVersionId(),
                    message.getUserId(),
                    message.getText());

            if (vector == null) {
                // 返回 null 有两种可能：
                //   1) 向量功能整体关闭 / 未配 embedding Key  → 永久性，不该重试
                //   2) embedding API 暂时失败              → 临时性，重试可能成功
                // 这里按"不重试"处理：匹配路径有同步兜底，任务丢失不会影响功能正确性。
                // 若将来想让临时失败也重试，可在此处抛异常走 DefaultErrorHandler 的重试。
                log.warn("embedding 任务未生成向量（功能未启用或 API 失败），已跳过: resumeId={}, versionId={}",
                        message.getResumeId(), message.getVersionId());
                ack.acknowledge();
                return;
            }

            log.info("embedding 任务处理完成: resumeId={}, versionId={}, dims={}",
                    message.getResumeId(), message.getVersionId(), vector.length);
            // 业务成功后手动提交 offset；抛异常时不提交，交由错误处理器重试
            ack.acknowledge();

            // 写入后复检：关闭"消费前校验通过 → 写入向量"之间的竞态窗口。
            // 场景：消费者校验时简历还在，随后用户删除简历（级联清理跑完时向量尚不存在），
            // 消费者才把向量写进去 → 产生孤儿向量。这里发现简历已删就立即回滚。
            if (message.getVersionId() == null && !isResumeAlive(message.getResumeId())) {
                int rolled = vectorStore.deleteResumeVectors(message.getResumeId());
                log.info("写入后发现简历已删除，回滚向量: resumeId={}, deleted={}",
                        message.getResumeId(), rolled);
            }

            log.info("embedding 任务处理完成: resumeId={}, versionId={}, dims={}",
                    message.getResumeId(), message.getVersionId(), vector.length);
            ack.acknowledge();


        } catch (Exception e) {
            // 不 ACK，交由 DefaultErrorHandler 重试，重试耗尽后转入死信 topic
            log.error("embedding 任务处理失败，将进入重试: resumeId={}, versionId={}, err={}",
                    message.getResumeId(), message.getVersionId(), e.getMessage(), e);
            throw e;
        } finally {
            MDC.remove("traceId");
        }
    }

    /**
     * 判断简历是否仍然存在。
     *
     * <p>用于写入后复检：简历已删除时返回 false，调用方据此回滚刚写入的向量。
     * 这里刻意不再抛异常——复检是"尽力而为"的补偿动作，
     * 失败时不应触发消息重试（重试会重新生成向量，得不偿失）。
     *
     * @return true=简历存在；false=已删除或状态不可用
     */
    private boolean isResumeAlive(Long resumeId) {
        try {
            resumeClient.getResume(resumeId);
            return true;
        } catch (FeignException e) {
            // 4xx 表示简历不存在或处于 REVIEW 状态，都视为不可用
            return false;
        } catch (Exception e) {
            // 网络抖动等服务端问题：无法确认，保守地认为"还活着"，
            // 避免因为一次网络抖动就误删刚生成的有效向量
            log.warn("复检简历状态失败，保守跳过回滚: resumeId={}, err={}", resumeId, e.getMessage());
            return true;
        }
    }
}
