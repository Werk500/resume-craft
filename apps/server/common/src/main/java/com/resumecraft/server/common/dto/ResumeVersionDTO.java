package com.resumecraft.server.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 简历优化版本 DTO（跨服务契约，Step 3）。
 * resume-service 对外暴露，application 等服务通过 Feign 获取版本内容。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeVersionDTO {

    private Long id;

    private Long resumeId;

    private Long userId;

    private String versionName;

    private String targetJob;

    /** 优化后的 Markdown 全文 */
    private String optimizedContent;

    private Double matchScore;

    private LocalDateTime createTime;
}
