package com.resumecraft.server.optimize.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetedOptimizeResponse {
    private Long versionId;             // 保存的优化版本 id（resume_version 表）
    /** AI 返回的 key 是 optimizedResume，@JsonAlias 兼容两种命名 */
    @JsonAlias("optimizedResume")
    private String optimizedContent;    // 优化后的 Markdown 全文（前端直接展示）
    private List<String> gaps;          // 缺口提示：缺失技能/经验
    private List<String> changes;       // 改动说明：每处改了什么、为什么

    /** 优化前基线覆盖率（0-100）；降级路径下为 null */
    private Double baselineCoverage;

    /** 采纳版本的覆盖率（0-100）；降级路径下为 null */
    private Double finalCoverage;

    /**
     * 逐轮轨迹。
     *
     * <p>注意 {@code @Builder.Default}：Lombok 的 @Builder 会忽略字段初始值，
     * 不加这个注解，builder 构造出来的是 null 而不是空集合，遍历时会 NPE。
     */
    @Builder.Default
    private List<OptimizeIteration> iterations = new ArrayList<>();

    /** 待用户确认的新增技能（模型写了、但原文找不到依据，可能是编造） */
    @Builder.Default
    private List<AddedSkill> pendingSkills = new ArrayList<>();

    @Builder.Default
    private List<Overstatement> pendingClaims = new ArrayList<>();

    @Builder.Default
    private List<StepRecord> steps = new ArrayList<>();
    @Builder.Default
    private Boolean degraded = false;

}
