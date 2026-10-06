package com.resumecraft.server.resume.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 简历优化版本实体。
 */
@Data
@TableName("resume_version")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属用户（数据隔离） */
    private Long userId;

    private Long resumeId;

    private String versionName;

    private String targetJob;

    private String optimizedContent;

    private Double matchScore;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** DRAFT=待确认 / CONFIRMED=已确认（可导出、可投递） */
    private String status;
    private LocalDateTime confirmedAt;
    /** 待确认项快照 JSON，确认接口用它做完整性校验 */
    private String pendingJson;
}