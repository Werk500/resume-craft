package com.resumecraft.server.optimize.loop;

import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.optimize.dto.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OptimizeContext {

    // 输入（构造后不变）
    private final String baseText;     // 原始简历，也是防造假校验基准
    private final Job job;
    private final int maxRounds;
    private final double minGain;

    // 运行状态
    private double baselineCoverage;
    private double bestCoverage;
    private String best;               // 历史最优正文
    private List<String> missing;      // 当前还缺的词
    private List<OptimizeIteration> iterations = new ArrayList<>();
    private List<AddedSkill> pendingSkills = new ArrayList<>();
    private List<Overstatement> pendingClaims = new ArrayList<>();
    private List<StepRecord> steps = new ArrayList<>();
    private boolean degraded = false;      // 打分服务不可用 → 退化为"只改写一次"
    private NextStep nextStep;
    private String advice;

    public OptimizeContext(String baseText, Job job, int maxRounds, double minGain) {
        this.baseText = baseText;
        this.job = job;
        this.maxRounds = maxRounds;
        this.minGain = minGain;
        this.best = baseText;          // 初始最优 = 原文
        this.missing = List.of();
        this.best = baseText;
        this.missing = List.of();
    }
}
