package com.resumecraft.server.optimize.loop;

import com.resumecraft.server.optimize.dto.NextStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NextStepAdviceStep implements  OptimizeStep{
    /** 覆盖率达标线：>= 视为"可以投递" */
    @Value("${app.optimize.readiness-threshold:60.0}")
    private double readinessThreshold;

    /** 弱匹配线：< 视为"建议换岗位" */
    @Value("${app.optimize.weak-threshold:40.0}")
    private double weakThreshold;

    @Override public String name() { return "next-step-advice"; }
    @Override public boolean ai() { return false; }     // 纯代码节点

    @Override
    public void execute(OptimizeContext ctx){
        int pending = ctx.getPendingSkills().size() + ctx.getPendingClaims().size();

        if (ctx.isDegraded()) {
            ctx.setNextStep(NextStep.DEGRADED);
            ctx.setAdvice("打分服务暂时不可用，本次只做了一次改写；服务恢复后重新优化，才能评估效果。");
            return;
        }

        double coverage = ctx.getBestCoverage();
        if (coverage >= readinessThreshold && pending == 0) {
            ctx.setNextStep(NextStep.READY);
            ctx.setAdvice("这份简历对这个岗位已经比较到位了（关键词覆盖率 " + fmt(coverage) + "%），可以投递。");
        } else if (coverage >= readinessThreshold) {
            ctx.setNextStep(NextStep.NEEDS_CONFIRM);
            ctx.setAdvice("先处置这 " + pending + " 项 AI 改动再投递——面试官会顺着简历问下去。");
        } else if (coverage >= weakThreshold) {
            ctx.setNextStep(NextStep.NEEDS_KEYWORDS);
            ctx.setAdvice("还差几个关键词（当前覆盖率 " + fmt(coverage) + "%），建议补上最相关的 2~3 个再投。");
        } else {
            ctx.setNextStep(NextStep.NOT_MATCHED);
            ctx.setAdvice("当前覆盖率只有 " + fmt(coverage) + "%，这个岗位和你的背景差距较大，建议换方向或换岗位，别硬投。");
        }

        log.info("下一步建议: step={}, coverage={}, pending={}", ctx.getNextStep(), fmt(coverage), pending);

    }

    private static String fmt(double v) { return String.valueOf(Math.round(v * 10) / 10.0); }

}
