package com.resumecraft.server.optimize.loop;

import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.optimize.dto.OptimizeLoopResult;
import com.resumecraft.server.optimize.dto.StepRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class OptimizePipeline {
    private final List<OptimizeStep> steps;

    public OptimizePipeline(BaselineScoreStep a, RewriteIterateStep b,
                            AuditParallelStep c, NextStepAdviceStep d, AssembleStep e) {
        this.steps = List.of(a, b, c, d,e);        // 顺序一眼可见
    }

    public OptimizeLoopResult run(String baseText, Job job, int maxRounds, double minGain){
        OptimizeContext ctx = new OptimizeContext(baseText, job, maxRounds, minGain);

        for (OptimizeStep step : steps) {
            long t0 = System.currentTimeMillis();
            try {
                step.execute(ctx);
                ctx.getSteps().add(record(step, step.outcome(ctx), t0, null));
            } catch (Exception e) {
                boolean degraded = step.optional();
                ctx.getSteps().add(record(step, degraded ? "DEGRADED" : "FAILED", t0, e.getMessage()));
                log.warn("节点 {} 失败（optional={}）: {}", step.name(), degraded, e.getMessage());
                if (!degraded) throw new RuntimeException("优化流程在节点 " + step.name() + " 失败", e);
            }
        }

        return OptimizeLoopResult.builder()
                .bestContent(ctx.getBest())
                .baselineCoverage(ctx.isDegraded() ? null : ctx.getBaselineCoverage())
                .finalCoverage(ctx.isDegraded() ? null : ctx.getBestCoverage())
                .iterations(ctx.getIterations())
                .pendingSkills(ctx.getPendingSkills())
                .pendingClaims(ctx.getPendingClaims())
                .steps(ctx.getSteps())
                .degraded(ctx.isDegraded())
                .nextStep(ctx.getNextStep())
                .advice(ctx.getAdvice())
                .build();
    }

    private StepRecord record(OptimizeStep step, String status, long t0, String detail) {
        return StepRecord.builder()
                .name(step.name())
                .ai(step.ai())
                .durationMs(System.currentTimeMillis() - t0)
                .status(status)
                .detail(detail)
                .build();
    }
}
