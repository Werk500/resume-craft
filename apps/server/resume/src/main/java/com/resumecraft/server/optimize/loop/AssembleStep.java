package com.resumecraft.server.optimize.loop;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AssembleStep implements OptimizeStep{
    @Override
    public String name() {
        return "assemble";
    }

    @Override
    public boolean ai() {
        return false;
    }

    @Override
    public void execute(OptimizeContext ctx) throws Exception {
        log.info("优化闭环结束: 轮数={}, 基线={}, 最终={}, 提升={}, 待确认新增={}",
                ctx.getIterations().size(), ctx.getBaselineCoverage(), ctx.getBestCoverage(),
                Math.round((ctx.getBestCoverage() - ctx.getBaselineCoverage()) * 10) / 10.0,
                ctx.getPendingSkills().size());
        // 注意：AssembleStep 只负责"算/记"，真正的 result 对象在 pipeline 里基于 ctx 构造

    }
}
