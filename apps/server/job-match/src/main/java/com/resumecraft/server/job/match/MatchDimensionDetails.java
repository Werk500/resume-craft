package com.resumecraft.server.job.match;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 匹配三个维度的明细数据（响应用，不入库）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchDimensionDetails {

    /** 关键词维度：用于前端展示覆盖度 */
    private Keyword keyword;

    /** 语义维度：当前无 embedding key，AI 近似打分 */
    private Semantic semantic;

    /** 硬性条件维度：学历/年限判定明细 */
    private HardRequirement hardRequirement;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Keyword {
        /** JD 核心关键词总数 */
        private Integer total;
        /** 简历命中的数量 */
        private Integer hit;
        /** 未命中关键词（与 MatchResult.missingKeywords 一致，方便前端少一层遍历） */
        private List<String> missingKeywords;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Semantic {
        /** 0~100 */
        private Double score;
        /** 一句话说明为什么给这个分 */
        private String reason;
        /** AI_APPROX=无 embedding 时 AI 近似；EMBEDDING=有向量；RULE_FALLBACK=AI 失败降级 */
        private String mode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HardRequirement {
        /** JD 中解析出的要求，例如 "本科及以上"、"3年以上" */
        private String educationRequirement;
        /** 简历是否满足学历 */
        private Boolean educationMet;
        /** 例如 "3年以上 Java 开发经验" */
        private String yearRequirement;
        /** 简历是否满足年限 */
        private Boolean yearMet;
        /** 不满足项的明细，空列表=全部满足 */
        private List<String> failedItems;
    }
}