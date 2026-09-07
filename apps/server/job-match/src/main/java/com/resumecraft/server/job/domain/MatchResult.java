package com.resumecraft.server.job.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.resumecraft.server.job.match.KeywordHit;
import com.resumecraft.server.job.match.MatchDimensionDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

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

    /** 归属用户（数据隔离） */
    private Long userId;

    private Long resumeId;

    private Long jobId;

    private Double overallScore;

    private Double keywordCoverage;

    private Double semanticSimilarity;

    private Double hardRequirementScore;

    private String matchExplanation;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private Boolean hardRequirementPassed;
    @TableField(exist = false)
    private List<KeywordHit> keywordHits;
    @TableField(exist = false)
    private List<String> missingKeywords;
    @TableField(exist = false)
    private MatchDimensionDetails dimensionDetails;
}