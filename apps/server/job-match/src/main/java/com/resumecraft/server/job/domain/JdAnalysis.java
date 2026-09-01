package com.resumecraft.server.job.domain;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JdAnalysis {
    private List<String> hardRequirements;   // 硬性要求：学历/年限/必备技能（"本科及以上"、"3年+ Java"）
    private List<String> bonusPoints;        // 加分项：行业经验/项目类型/工具熟练度（"有高并发经验优先"）
    private List<String> hiddenRequirements; // 隐性要求：AI 推断的软素质（"抗压能力"、"自驱力"）
    private List<String> skills;             // 技能清单：JD 里出现的所有技能（画雷达图/匹配用）
    private List<RadarDimension> radar;      // 雷达图维度：5 个维度 AI 打分 0~100
    private String summary;                  // 岗位画像总结（2~3 句）

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RadarDimension {
        private String name;   // 维度名
        private Integer score; // 0~100
    }
}




