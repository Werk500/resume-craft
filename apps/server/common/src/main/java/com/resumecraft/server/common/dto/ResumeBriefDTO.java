package com.resumecraft.server.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 简历摘要 DTO（跨服务契约，Step 3）。
 * resume-service 对外暴露，job-match 等服务通过 Feign 获取简历信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeBriefDTO {

    private Long id;

    private Long userId;

    private String fileName;

    private String fileType;

    private String parsedName;

    private String parsedEmail;

    private String parsedPhone;

    /** 简历解析全文（匹配/诊断用） */
    private String rawText;
}
