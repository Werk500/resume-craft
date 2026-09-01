package com.resumecraft.server.diagnose.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 诊断报告实体。
 */
@Data
@TableName("diagnosis")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Diagnosis {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属用户（数据隔离） */
    private Long userId;

    private Long resumeId;

    private Double totalScore;

    private Double completenessScore;

    private Double expressionScore;

    private Double matchScore;

    private String suggestions;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}