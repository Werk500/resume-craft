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
public class RewriteIterateStep implements OptimizeStep{

    @Resource
    private JobMatchServiceGateway gateway;
    @Resource
    private AiService aiService;

    @Override
    public String name() {
        return "rewrite-iterate";
    }

    @Override
    public boolean ai() {
        return true;
    }

    @Override
    public void execute(OptimizeContext ctx) throws Exception {

        if (ctx.isDegraded()) {
            String once = aiService.chat(PromptTemplates.TARGETED_REWRITE_SYSTEM,
                    PromptTemplates.targetedRewriteUser(ctx.getBaseText(), List.of(), ctx.getJob()));
            ctx.setBest(once);
            return;                            // 不做循环、不做复算
        }

        Job job = ctx.getJob();
        double baseline = ctx.getBaselineCoverage();

        String current = ctx.getBest();// 下一轮改写的输入，初始 = 原文
        List<String> missing = ctx.getMissing();//当前缺的词

        for (int round = 1; round <= ctx.getMaxRounds(); round++) {

            String candidate = aiService.chat(PromptTemplates.TARGETED_REWRITE_SYSTEM,
                    PromptTemplates.targetedRewriteUser(current, missing, job));

            ScoreResponse s = gateway.score(candidate, job.getId());
            if (s == null) {
                break;
            }

            double cov = nz(s.getKeywordCoverage());
            boolean kept = cov > ctx.getBestCoverage();
            // 保留一位小数：double 相减会出 38.900000000000006 这种毛刺，会原样进日志和响应 JSON
            double gain = Math.round((cov - ctx.getBestCoverage()) * 10) / 10.0;

            log.info("优化闭环 第{}轮: 上一版覆盖率={}, 本轮={} (增益={}), 保留={}, 还缺 {} 个词",
                    round, ctx.getBestCoverage(), cov, gain, kept,
                    s.getMissingKeywords() == null ? 0 : s.getMissingKeywords().size());

            ctx.getIterations().add(OptimizeIteration.builder()
                    .round(round)
                    .keywordCoverage(cov)
                    .gain(gain)
                    .missingKeywords(s.getMissingKeywords())
                    .addedKeywords(needDiff(missing, s.getMissingKeywords()))
                    .kept(kept)
                    .build());

            if (kept) {
                ctx.setBest(candidate);
                ctx.setBestCoverage(cov);
                current = candidate;
            }

            if (ctx.getBestCoverage() - baseline >= ctx.getMinGain()) {
                break;                            // 达标
            }
            if (!kept) {
                break;                            // 退化，止损
            }
            missing = s.getMissingKeywords();
            ctx.setMissing(missing);              // ← 关键：每轮更新黑板上的缺词
        }

        // for 结束后 best / bestCoverage / iterations 已在黑板里，无需额外回写
    }

    private double nz(Double v) {
        return v == null ? 0.0 : v;
    }

    /** 本轮新覆盖的词 = 上一轮缺的 - 这一轮还缺的（集合差，保序） */
    private static List<String> needDiff(List<String> prevMissing, List<String> currMissing) {
        if (prevMissing == null || prevMissing.isEmpty()) return List.of();
        Set<String> curr = currMissing == null ? Set.of() : new HashSet<>(currMissing);
        List<String> added = new ArrayList<>();
        for (String kw : prevMissing) {
            if (kw != null && !curr.contains(kw)) added.add(kw);
        }
        return added;
    }
}
