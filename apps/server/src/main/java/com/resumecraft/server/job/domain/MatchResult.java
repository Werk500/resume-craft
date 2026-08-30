package com.resumecraft.server.job.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 简历-岗位匹配度结果实体。
 */
@Data
@TableName("match_result")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resumeId;

    private Long jobId;

    private Double overallScore;

    private Double keywordCoverage;

    private Double semanticSimilarity;

    private Double hardRequirementScore;

    private String matchExplanation;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}