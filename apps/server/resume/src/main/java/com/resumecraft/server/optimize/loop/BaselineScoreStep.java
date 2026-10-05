package com.resumecraft.server.optimize.loop;

import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.common.feign.dto.ScoreResponse;
import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.optimize.dto.OptimizeIteration;
import com.resumecraft.server.resume.gateway.JobMatchServiceGateway;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class BaselineScoreStep implements OptimizeStep{
    @Resource
    private JobMatchServiceGateway gateway;


    @Override
    public String name() {
        return "baseline-score";
    }

    @Override
    public boolean ai() {
        return false;
    }

    /** 打分服务不可用时本节点标记降级后直接返回，状态应为 DEGRADED 而不是 OK */
    @Override
    public String outcome(OptimizeContext ctx) {
        return ctx.isDegraded() ? "DEGRADED" : "OK";
    }

    @Override
    public void execute(OptimizeContext ctx){

        ScoreResponse base = gateway.score(ctx.getBaseText(), ctx.getJob().getId());

        if (base == null){
            ctx.setDegraded(true);
            log.warn("内部打分不可用，优化闭环降级为单次改写: jobId={}", ctx.getJob().getId());
            return;
        }
        // base == null → 抛异常，由 pipeline 走降级分支（等价于现在的 degraded 路径）
        ctx.setBaselineCoverage(nz(base.getKeywordCoverage()));
        ctx.setBestCoverage(ctx.getBaselineCoverage());
        ctx.setBest(ctx.getBaseText());
        ctx.setMissing(base.getMissingKeywords() == null ? List.of() : base.getMissingKeywords());
    }

    private double nz(Double v) {
        return v == null ? 0.0 : v;
    }


}
