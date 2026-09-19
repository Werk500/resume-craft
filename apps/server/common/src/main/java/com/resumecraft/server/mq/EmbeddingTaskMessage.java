package com.resumecraft.server.mq;

import lombok.*;

import java.io.Serializable;

/**
 * 简历 embedding 生成任务消息体。
 * 只传标识 + 文本，consumer 侧不需要回查 resume 服务。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingTaskMessage implements Serializable {
    private Long resumeId;
    private Long versionId;
    private Long userId;
    private String contentHash;   // 用于幂等：同一 hash 不重复生成
    private String text;          // 待 embedding 的正文
}
