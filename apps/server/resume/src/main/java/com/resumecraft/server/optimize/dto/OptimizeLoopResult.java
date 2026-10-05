package com.resumecraft.server.optimize.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 简历优化闭环的执行结果。
 *
 * <h3>为什么返回的是"历史最优版本"而不是"最后一版"</h3>
 * 模型每一轮改写都可能改差（实测出现过覆盖率从 0.82 掉到 0.72 的情况），
 * 所以闭环必须记录每轮成绩并保留最好的那一版，否则"多跑几轮"反而把简历改坏。
 * 这也是 {@code iterations} 存在的意义：前端能画出"第1轮 16.7% → 第2轮 22.5%"的轨迹，
 * 出问题时也能一眼看出是哪一轮涨的、哪一轮被回滚了。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizeLoopResult {

    /** 最终采纳的简历正文（历史最优版本，不一定是最后一轮产出的那一版） */
    private String bestContent;

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

    /**
     * 待用户确认的新增技能：模型写进了简历，但代码在原文里找不到依据。
     *
     * <p>不做自动删除（可能误删），而是交给用户判断——"AI 提建议、代码核实、用户确认"。
     */
    @Builder.Default
    private List<AddedSkill> pendingSkills = new ArrayList<>();

    @Builder.Default
    private List<Overstatement> pendingClaims = new ArrayList<>();
    @Builder.Default
    private List<StepRecord> steps = new ArrayList<>();

    /**
     * 是否降级执行：打分服务不可用（熔断/超时）时，闭环退化为"只改写一次"。
     * 此时 bestContent 是单次改写的结果，覆盖率字段为 null。
     */
    @Builder.Default
    private boolean degraded = false;

    /** 相比基线提升的百分点；没有基线（降级）时返回 0 */
    public double gain() {
        if (baselineCoverage == null || finalCoverage == null) {
            return 0d;
        }
        return finalCoverage - baselineCoverage;
    }

    /** 是否真的改好了：非降级、且覆盖率确实高于基线 */
    public boolean improved() {
        return !degraded
                && baselineCoverage != null
                && finalCoverage != null
                && finalCoverage > baselineCoverage;
    }

    /** 实际跑了几轮（降级时为 0） */
    public int rounds() {
        return iterations == null ? 0 : iterations.size();
    }

    /** 降级路径的构造：只跑一次改写，没有分数信息 */
    public static OptimizeLoopResult degraded(String content) {
        return OptimizeLoopResult.builder()
                .bestContent(content)
                .degraded(true)
                .build();
    }
}
