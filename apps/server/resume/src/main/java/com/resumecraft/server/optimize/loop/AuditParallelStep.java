package com.resumecraft.server.optimize.loop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.job.domain.JobMapper;
import com.resumecraft.server.optimize.dto.AddedSkill;
import com.resumecraft.server.optimize.dto.Overstatement;
import com.resumecraft.server.optimize.dto.OverstatementAuditResult;
import com.resumecraft.server.optimize.dto.RewriteResult;
import com.resumecraft.server.optimize.guard.FabricationGuard;
import com.resumecraft.server.optimize.guard.OverstatementChecker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class AuditParallelStep implements OptimizeStep{ // 两条审计并行

    @Resource
    private AiService aiService;
    @Resource
    private ThreadPoolTaskExecutor aiExecutor;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private OverstatementChecker overstatementChecker;
    @Resource
    private FabricationGuard fabricationGuard;

    @Override
    public String name() {
        return "audit-parallel";
    }

    @Override
    public boolean ai() {
        return true;
    }

    @Override
    public boolean optional() {
        return true;// 失败只降级，不影响结果
    }

    @Override
    public void execute(OptimizeContext ctx) throws Exception {
        // 只审"被采纳且优于原文"的版本 —— 与原逻辑完全一致
        if (ctx.getBestCoverage() <= ctx.getBaselineCoverage()
                || ctx.getBest().equals(ctx.getBaseText())) {
            return;
        }

        String original = ctx.getBaseText();
        String finalBest = ctx.getBest();

        CompletableFuture<List<AddedSkill>> skillsFuture = CompletableFuture.supplyAsync(
                () -> auditAddedSkills(original, finalBest), aiExecutor);
        CompletableFuture<List<Overstatement>> claimsFuture = CompletableFuture.supplyAsync(
                () -> auditOverstatements(original, finalBest), aiExecutor);

        ctx.setPendingSkills(joinSafely(skillsFuture, "技能依据审计"));
        ctx.setPendingClaims(joinSafely(claimsFuture, "夸大审计"));

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
