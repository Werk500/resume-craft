package com.resumecraft.server.optimize.loop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.common.feign.dto.ScoreResponse;
import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.optimize.dto.*;
import com.resumecraft.server.optimize.guard.FabricationGuard;
import com.resumecraft.server.optimize.guard.OverstatementChecker;
import com.resumecraft.server.resume.gateway.JobMatchServiceGateway;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
    private AiService aiService;
    @Resource
    private JobMatchServiceGateway jobMatchServiceGateway;
    @Resource
    private FabricationGuard fabricationGuard;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private ThreadPoolTaskExecutor aiExecutor;
    @Resource
    private OverstatementChecker overstatementChecker;

    public OptimizeLoopResult run(String baseText, Job job, int maxRounds, double minGain) {
        ScoreResponse base = jobMatchServiceGateway.score(baseText, job.getId());

        if (base == null) {
            log.warn("内部打分不可用，优化闭环降级为单次改写: jobId={}", job.getId());
            String once = aiService.chat(PromptTemplates.TARGETED_REWRITE_SYSTEM,
                    PromptTemplates.targetedRewriteUser(baseText, List.of(), job));
            return OptimizeLoopResult.degraded(unwrapResume(once, baseText));
        }

        double baseline = nz(base.getKeywordCoverage());
        String best = baseText;// 目前最优的简历文本，初始 = 原文
        String current = baseText;// 下一轮改写的输入，初始 = 原文
        double bestCov = baseline;// 目前最优覆盖率，初始 = 基线

        List<String> missing = base.getMissingKeywords() == null
                ? List.of() : base.getMissingKeywords();// 当前缺的词

        List<OptimizeIteration> trace = new ArrayList<>();// 每轮打分记录

        for (int round = 1; round <= maxRounds; round++) {

            String candidate = aiService.chat(PromptTemplates.TARGETED_REWRITE_SYSTEM,
                    PromptTemplates.targetedRewriteUser(current, missing, job));

            ScoreResponse s = jobMatchServiceGateway.score(candidate, job.getId());
            if (s == null) {
                break;
            }

            double cov = nz(s.getKeywordCoverage());
            boolean kept = cov > bestCov;
            // 保留一位小数：double 相减会出 38.900000000000006 这种毛刺，会原样进日志和响应 JSON
            double gain = Math.round((cov - bestCov) * 10) / 10.0;

            log.info("优化闭环 第{}轮: 上一版覆盖率={}, 本轮={} (增益={}), 保留={}, 还缺 {} 个词",
                    round, bestCov, cov, gain, kept,
                    s.getMissingKeywords() == null ? 0 : s.getMissingKeywords().size());

            trace.add(OptimizeIteration.builder()
                    .round(round)
                    .keywordCoverage(cov)
                    .gain(gain)
                    .missingKeywords(s.getMissingKeywords())
                    .addedKeywords(needDiff(missing, s.getMissingKeywords()))
                    .kept(kept)
                    .build());

            if (kept) {
                best = candidate;
                bestCov = cov;
                current = candidate;
            }

            if (bestCov - baseline >= minGain) {
                break;
            }
            if (!kept) {
                break;
            }
            missing = s.getMissingKeywords();
        }

        // 循环结束后（保持"只审被采纳的版本"这个条件）
        List<AddedSkill> pendingSkills = new ArrayList<>();
        List<Overstatement> pendingClaims = new ArrayList<>();

        if(bestCov > baseline && !best.equals(baseText)){
            String finalBest = best;
            CompletableFuture<List<AddedSkill>> skillsFuture = CompletableFuture.supplyAsync(
                    () -> auditAddedSkills(baseText, finalBest), aiExecutor);// 内部已做 guard 校验
            CompletableFuture<List<Overstatement>> claimsFuture = CompletableFuture.supplyAsync(
                    () -> auditOverstatements(baseText, finalBest), aiExecutor);

            pendingSkills = joinSafely(skillsFuture, "技能依据审计");
            pendingClaims = joinSafely(claimsFuture, "夸大审计");
        }

        log.info("优化闭环结束: 轮数={}, 基线={}, 最终={}, 提升={}, 待确认新增={}",
                trace.size(), baseline, bestCov, Math.round((bestCov - baseline) * 10) / 10.0, pendingSkills.size());

        return OptimizeLoopResult.builder()
                .bestContent(best)// 历史最优文本
                .baselineCoverage(baseline)// 基线覆盖率
                .finalCoverage(bestCov)// 最终最优覆盖率
                .iterations(trace)// 完整迭代轨迹
                .pendingSkills(pendingSkills)
                .pendingClaims(pendingClaims)
                .build();
    }

    /** 审计 B：读出来 → OverstatementChecker 核实 */
    private List<Overstatement> auditOverstatements(String original, String rewritten) {
        try {
            String raw = aiService.chat(PromptTemplates.AUDIT_OVERSTATEMENT_SYSTEM,
                    PromptTemplates.auditOverstatementUser(original, rewritten));
            OverstatementAuditResult result =
                    objectMapper.readValue(extractJson(raw), OverstatementAuditResult.class);
            List<Overstatement> claims = result.getOverstatements() == null ? List.of() : result.getOverstatements();
            return overstatementChecker.check(rewritten, claims);
        } catch (Exception e) {
            log.warn("夸大审计调用失败，跳过: {}", e.getMessage());
            return List.of();
        }

    }


    /** 审计调用：让模型列出改写稿里新增的技能及依据；解析失败就当"没发现"，不阻塞主流程 */
    private List<AddedSkill> auditAddedSkills(String originalText, String rewrittenText) {
        try {
            String raw = aiService.chat(PromptTemplates.AUDIT_ADDED_SKILLS_SYSTEM,
                    PromptTemplates.auditAddedSkillsUser(originalText, rewrittenText));
            String json = extractJson(raw);
            RewriteResult result = objectMapper.readValue(json, RewriteResult.class);
            List<AddedSkill> claimed = result.getAddedSkills() == null ? List.of() : result.getAddedSkills();
            return fabricationGuard.verify(originalText, claimed);
        } catch (Exception e) {
            log.warn("事实核查调用失败，跳过待确认列表: {}", e.getMessage());
            return List.of();
        }


    }

    /** 模型有时会包 markdown 围栏或前后废话，这里剥掉并截出最外层大括号 */
    private String extractJson(String raw) {
        if (raw == null) return "{}";
        String json = raw.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```", "").trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        return (start >= 0 && end > start) ? json.substring(start, end + 1) : json;
    }

    /**
     * 解析改写节点的结构化输出。
     *
     * <p>模型偶尔带 markdown 围栏或前后废话，所以先剥围栏再截取最外层大括号；
     * 解析失败就按"纯文本简历"处理，保证闭环不会因为一次格式问题整轮挂掉。
     */
    private RewriteResult parseRewrite(String raw, String fallbackText) {
        if (raw == null || raw.isBlank()) {
            return RewriteResult.builder().optimizedResume(fallbackText).build();
        }

        String json = raw.replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```", "")
                .trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }

        try {
            RewriteResult result = objectMapper.readValue(json, RewriteResult.class);
            if (result.getOptimizedResume() == null || result.getOptimizedResume().isBlank()) {
                log.warn("改写结果缺少 optimizedResume，回退为上一版正文");
                return RewriteResult.builder().optimizedResume(fallbackText).build();
            }
            return result;
        } catch (Exception e) {
            log.warn("改写结果 JSON 解析失败，按纯文本正文处理: {}", e.getMessage());
            return RewriteResult.builder()
                    .optimizedResume(json.isBlank() ? fallbackText : json)
                    .build();
        }
    }

    /** 降级路径用：尽量从结构化输出里取出简历正文，取不到就用原文 */
    private String unwrapResume(String raw, String fallbackText) {
        return parseRewrite(raw, fallbackText).getOptimizedResume();
    }

    private double nz(Double v) {
        return v == null ? 0.0 : v;
    }

    /**
     * 本轮新覆盖的词 = 上一轮缺的 - 这一轮还缺的（集合差，保序）。
     * 上一轮缺的词在这一轮不再缺，就说明被补上了。
     */
    private static List<String> needDiff(List<String> prevMissing, List<String> currMissing) {
        if (prevMissing == null || prevMissing.isEmpty()) return List.of();
        Set<String> curr = currMissing == null ? Set.of() : new HashSet<>(currMissing);
        List<String> added = new ArrayList<>();
        for (String kw : prevMissing) {
            if (kw != null && !curr.contains(kw)) {
                added.add(kw);
            }
        }
        return added;
    }

    /** 并行任务取结果：超时/异常都只记日志并返回空列表，绝不拖垮主流程 */
    private <T> List<T> joinSafely(CompletableFuture<List<T>> future, String name) {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("{}失败（不影响优化结果）: {}", name, e.getMessage());
            return List.of();
        }
    }
}
