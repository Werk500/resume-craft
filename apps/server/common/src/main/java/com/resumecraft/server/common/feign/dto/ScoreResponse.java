package com.resumecraft.server.common.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ScoreResponse {

    private Double keywordCoverage;        // 关键词覆盖（闭环的信号）
    private List<String> missingKeywords;  // 缺哪些词 → 直接喂给下一轮改写
    private Double hardRequirementScore;
    private Boolean hardRequirementPassed;
    private Double semanticSimilarity;     // includeSemantic=false 时为 null
    private Double overallScore;           // 同上
}
