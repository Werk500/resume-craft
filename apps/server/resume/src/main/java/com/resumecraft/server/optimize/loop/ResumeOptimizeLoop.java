package com.resumecraft.server.optimize.loop;

import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.optimize.dto.*;
import com.resumecraft.server.optimize.guard.FabricationGuard;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * 简历优化闭环：基线打分 -> 改写 -> 复算 -> 保留最优，直到达标或轮数用尽。
 *
 * <p>为什么要闭环而不是一个大 prompt 让模型一次改完：
 * 1) 可验证——改完由规则引擎复算覆盖率，"改好没改好"不靠模型自述；
 * 2) 不退化——只有分数真的涨了才采纳，否则回滚并提前退出
 *    （实测模型会把覆盖率从 0.82 改到 0.72，多跑几轮不等于越来越好）；
 * 3) 防造假——改写节点返回结构化结果（新增了哪些技能 + 依据），
 *    由 {@link FabricationGuard} 回原文核对，找不到依据的进待确认列表。
 */
@Component
@Slf4j
public class ResumeOptimizeLoop {

    @Resource
    private OptimizePipeline pipeline;

    public OptimizeLoopResult run(String baseText, Job job, int maxRounds, double minGain) {
        return pipeline.run(baseText, job, maxRounds, minGain);
    }
}
