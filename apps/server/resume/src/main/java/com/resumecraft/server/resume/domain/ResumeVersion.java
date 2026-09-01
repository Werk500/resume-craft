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
}