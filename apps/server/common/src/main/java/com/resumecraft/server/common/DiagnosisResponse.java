package com.resumecraft.server.common;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 诊断报告统一返回结构。
 */
@Setter
@Getter
public class DiagnosisResponse {

    private Long resumeId;
    private double totalScore;
    private DimensionScore completeness;
    private DimensionScore expression;
    private DimensionScore matchScore;
    private List<String> suggestions;

    public DiagnosisResponse(Long resumeId, double totalScore,
                             DimensionScore completeness,
                             DimensionScore expression,
                             DimensionScore matchScore,
                             List<String> suggestions) {
        this.resumeId = resumeId;
        this.totalScore = totalScore;
        this.completeness = completeness;
        this.expression = expression;
        this.matchScore = matchScore;
        this.suggestions = suggestions;
    }

    /** 单维度得分及状态（red/yellow/green）。 */
    public static class DimensionScore {
        private final double score;     // 0~100
        private final String color;     // red | yellow | green
        private final String label;     // 中文标签

        public DimensionScore(double score, String color, String label) {
            this.score = Math.round(score * 10) / 10.0;
            this.color = color;
            this.label = label;
        }

        public double getScore() { return score; }
        public String getColor() { return color; }
        public String getLabel() { return label; }
    }
}
