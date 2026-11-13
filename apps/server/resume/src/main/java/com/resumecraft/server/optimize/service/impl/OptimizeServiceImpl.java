package com.resumecraft.server.optimize.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.common.cache.CacheKeys;
import com.resumecraft.server.common.security.AuthContext;
import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.job.domain.JobMapper;
import com.resumecraft.server.mq.AfterCommitExecutor;
import com.resumecraft.server.optimize.cache.OptimizeCacheEvictor;
import com.resumecraft.server.optimize.dto.*;
import com.resumecraft.server.optimize.loop.ResumeOptimizeLoop;
import com.resumecraft.server.optimize.service.OptimizeService;
import com.resumecraft.server.optimize.util.MarkdownConverter;
import com.resumecraft.server.optimize.util.PdfGenerator;
import com.resumecraft.server.resume.domain.Resume;
import com.resumecraft.server.resume.domain.ResumeMapper;
import com.resumecraft.server.resume.domain.ResumeVersion;
import com.resumecraft.server.resume.domain.ResumeVersionMapper;
import com.resumecraft.server.resume.mq.EmbeddingTaskProducer;
import com.resumecraft.server.resume.service.ResumeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * 一键优化实现：查简历 → 拼 prompt → AI 改写 → 存 resume_version。
 */
@Slf4j
@Service
public class OptimizeServiceImpl implements OptimizeService {

    @Resource
    private ResumeService resumeService;

    @Resource
    private AiService aiService;

    @Resource
    private ResumeVersionMapper resumeVersionMapper;
    @Resource
    private ResumeMapper resumeMapper;
    @Resource
    private JobMapper jobMapper;
    @Resource
    private PdfGenerator pdfGenerator;
    @Resource
    private AfterCommitExecutor afterCommitExecutor;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private EmbeddingTaskProducer producer;
    @Resource
    private ResumeOptimizeLoop resumeOptimizeLoop;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private OptimizeCacheEvictor  optimizeCacheEvictor;

    /** 优化闭环最多迭代几轮 */
    @Value("${app.optimize.max-rounds:3}")
    private int maxRounds;

    /** 覆盖率提升多少个百分点算达标（提前结束，省 token） */
    @Value("${app.optimize.min-gain:5.0}")
    private double minGain;


    /**
     * AI 优化简历。
     *
     * @param resumeId  简历 id
     * @param targetJob 目标岗位（可为 null/空 = 通用优化）
     * @return 保存后的优化版本
     */
    @Override
    public ResumeVersion optimize(Long resumeId, String targetJob) {

        Resume resume = loadOptimizableResume(resumeId);

        log.info("开始 AI 优化: resumeId={}, targetJob={}", resumeId, targetJob);

        //1.构建缓存key
        String cacheKey = buildCacheKey(resumeId, targetJob);

        //先从缓存获取值（Redis 异常时降级跳过缓存，不阻塞主流程）
        String cachedValue = null;
        try {
            cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("缓存读取失败，跳过缓存, cacheKey={}", cacheKey, e);
        }

        //2.尝试从缓存中获取
        if (cachedValue != null) {
            try {
                ResumeVersion cachedVersion = objectMapper.readValue(cachedValue, ResumeVersion.class);
                log.info("命中缓存: resumeId={}, targetJob={}, versionId={}",
                        resumeId, targetJob, cachedVersion.getId());
                return cachedVersion;
            } catch (Exception e) {
                log.warn("缓存反序列化失败，将重新生成: {}", e.getMessage());
                // 缓存数据损坏，删除并继续执行
                try {
                    stringRedisTemplate.delete(cacheKey);
                } catch (Exception ex) {
                    log.warn("删除损坏缓存失败: {}", ex.getMessage());
                }
            }
        }

        //  3.AI 改写（返回 Markdown 文本）
        String optimized = aiService.chat(PromptTemplates.OPTIMIZE_SYSTEM,
                PromptTemplates.optimizeUser(resume.getRawText(), targetJob));

        // 4. 存为优化版本（createTime 由 MetaObjectHandler 填充）
        ResumeVersion version = ResumeVersion.builder()
                .resumeId(resumeId)
                .userId(resume.getUserId())
                .versionName("AI优化-v1")
                .targetJob(targetJob)
                .optimizedContent(optimized)
                .build();

        saveVersionAndPublish(version);

        log.info("优化版本已保存: id={}, resumeId={}",
                version.getId(), resumeId);
        // 5. 写入缓存（有效期 1 小时）
        try {
            //将 Java 对象序列化为 JSON 字符串，以便存入 Redis。
            String versionJson = objectMapper.writeValueAsString(version);

            long baseSeconds = TimeUnit.HOURS.toSeconds(1);
            long randomOffset = ThreadLocalRandom.current().nextLong(0, 600); // 0-600秒 = 0-10分钟
            long expireSeconds = baseSeconds + randomOffset;

            stringRedisTemplate.opsForValue().set(cacheKey, versionJson, expireSeconds, TimeUnit.SECONDS);
            log.info("优化结果已缓存: resumeId={}, targetJob={}, versionId={}",
                    resumeId, targetJob, version.getId());
        } catch (Exception e) {
            log.warn("缓存写入失败: {}", e.getMessage());
            // 缓存失败不影响主流程
        }

        return version;
    }

    /**
     * 定向优化简历
     * @param resumeId
     * @param jobId
     * @return
    1.前置校验：校验简历是否可优化，校验岗位 ID 合法性。

    2.缓存穿透保护：查 Redis 缓存，命中直接反序列化返回。若缓存损坏则主动删除并回源。

    3.闭环优化：查询目标岗位 JD，调用 resumeOptimizeLoop.run()，基于覆盖率进行多轮 AI 迭代，产出优化后的简历内容、缺失词和修改轨迹。

    4.组装“待确认项”（Pending）：提取 AI 在优化过程中“瞎编”或“夸大”的内容（Skills / Claims），生成 JSON 交由用户后续确认。

    5.版本落库：生成一个全新的简历版本（状态为 DRAFT），记录版本名、覆盖率、待确认项等。

    6.异步事件驱动：通过 afterCommitExecutor 在数据库事务提交后，向消息队列（如 RocketMQ/Kafka）发送事件，触发后续流程（如向量化入库、异步通知等）。

    7.缓存与响应：组装包含“覆盖率、Gaps、Changes、优化建议”的富响应，写入随机 TTL 的 Redis 缓存后返回。
     */
    @Override
    public TargetedOptimizeResponse targetedOptimize(Long resumeId, Long jobId) {

        Resume resume = loadOptimizableResume(resumeId);

        if (jobId == null || jobId <= 0) {
            throw new IllegalArgumentException("无效的岗位ID: " + jobId);
        }

        log.info("开始定向优化: resumeId={}, jobId={}", resumeId, jobId);

        // 1. 构建缓存 key
        String cacheKey = CacheKeys.targetedOptimize(resumeId, jobId);

        // 2. 检查缓存（Redis 异常时降级跳过缓存）
        String cachedValue = null;
        try {
            cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("缓存读取失败，跳过缓存, cacheKey={}", cacheKey, e);
        }

        // 3. 检查正常缓存
        if (cachedValue != null) {
            try {
                TargetedOptimizeResponse cachedResult = objectMapper.readValue(
                        cachedValue,
                        TargetedOptimizeResponse.class
                );
                log.info("命中缓存: resumeId={}, jobId={}, versionId={}",
                        resumeId, jobId, cachedResult.getVersionId());
                return cachedResult;
            } catch (Exception e) {
                log.warn("缓存反序列化失败，将重新生成: {}", e.getMessage());
                try {
                    stringRedisTemplate.delete(cacheKey);
                } catch (Exception ex) {
                    log.warn("删除损坏缓存失败: {}", ex.getMessage());
                }
            }
        }

        // 4. 查询岗位
        Job job;
        try {
            job = jobMapper.selectById(jobId);
            if (job == null) {
                throw new IllegalArgumentException("岗位不存在: jobId=" + jobId);
            }
        } catch (IllegalArgumentException e) {
            throw e; // 业务异常直接抛出
        } catch (Exception e) {
            // 系统异常，不缓存空值
            log.error("查询数据失败, resumeId={}, jobId={}", resumeId, jobId, e);
            throw new RuntimeException("系统异常，请稍后重试");
        }

        OptimizeLoopResult loop = resumeOptimizeLoop.run(resume.getRawText(), job, maxRounds, minGain);
        String optimizedResume = loop.getBestContent();

        if (optimizedResume == null || optimizedResume.isBlank()) {
            throw new RuntimeException("AI 未返回有效的简历内容");
        }
        // 每轮的打分轨迹：覆盖率、增益、当轮缺失词、当轮新补词、是否被采纳
        List<OptimizeIteration> iterations = loop.getIterations();

        // gaps = 最终版简历里仍缺、但岗位要的词 —— 取最后一轮打分的 missingKeywords（最接近最终状态）
        // 兜底：一轮都没跑（首轮打分失败 / maxRounds=0）或字段为 null 时，返回不可变空列表
        List<String> gaps = iterations.isEmpty() || iterations.get(iterations.size() - 1).getMissingKeywords() == null
                ? List.of()
                : iterations.get(iterations.size() - 1).getMissingKeywords();

        // changes = 各轮“新补上的关键词”汇总，全部来自规则引擎的集合差计算，而非模型自述
        //   flatMap：把每轮的 List 摊平成一个 List；某轮为 null 时用 empty() 跳过，避免 NPE
        //   distinct：同一个词可能多轮反复补上又丢掉，只保留一次，防止列表虚高
        //   map：包装成可读的变更说明，供展示 / 落库
        //   toList：返回不可变列表（Java 16+）
        List<String> changes = iterations.stream()
                .flatMap(it -> it.getAddedKeywords() == null
                        ? Stream.<String>empty()
                        : it.getAddedKeywords().stream())
                .distinct()
                .map(kw -> "补充关键词：" + kw)
                .toList();

        log.info("优化闭环完成: resumeId={}, 轮数={}, 覆盖率 {} → {}, 内容长度={}, 未命中={}, 新覆盖={}",
                resumeId, loop.rounds(), loop.getBaselineCoverage(), loop.getFinalCoverage(),
                optimizedResume.length(), gaps.size(), changes.size());

        //8.存 resume_version（继承简历归属）
        // 组装 pending 快照：两项都空 → null（表示本来就不需要确认）
        PendingSnapshot snapshot = new PendingSnapshot(loop.getPendingSkills(), loop.getPendingClaims());
        String pendingJson = null;
        if(!snapshot.isEmpty()) {
            try {
                pendingJson = objectMapper.writeValueAsString(snapshot);
            } catch (Exception e) {
                // 序列化失败不能阻塞主流程，但要留痕：宁可写成 [] 让用户多点一次确认，也别写 null 放行
                log.error("pending_json 序列化失败，降级为空快照: versionId 未生成", e);
                pendingJson = "{\"skills\":[],\"claims\":[]}";
            }
        }

        String scoreTag = loop.getBaselineCoverage() == null
                ? ""
                : String.format("(覆盖率%.1f→%.1f)", loop.getBaselineCoverage(), loop.getFinalCoverage());

        ResumeVersion version = ResumeVersion.builder()
                .resumeId(resumeId)
                .userId(resume.getUserId())
                .versionName("定向优化-" + job.getTitle() + scoreTag)
                .targetJob(job.getTitle())
                .optimizedContent(optimizedResume)
                .matchScore(loop.getFinalCoverage())   // 表里的 match_score 正好存闭环终值
                .status("DRAFT")
                .pendingJson(pendingJson)
                .build();
        saveVersionAndPublish(version);

        log.info("定向优化版本已保存: versionId={}, resumeId={}, userId={}, targetJob={}",
                version.getId(), resumeId, resume.getUserId(), job.getTitle());
        //9.组装响应
        TargetedOptimizeResponse response = TargetedOptimizeResponse.builder()
                .versionId(version.getId())
                .optimizedContent(optimizedResume)
                .gaps(gaps)
                .changes(changes)
                .baselineCoverage(loop.getBaselineCoverage())
                .finalCoverage(loop.getFinalCoverage())
                .iterations(iterations)
                .pendingSkills(loop.getPendingSkills())
                .pendingClaims(loop.getPendingClaims())
                .steps(loop.getSteps())
                .degraded(loop.isDegraded())
                .nextStep(loop.getNextStep())
                .advice(loop.getAdvice())
                .build();

        //写redis
        cacheTargetedResult(
                cacheKey,
                resumeId,
                jobId,
                response
        );


        return response;

    }

    private void cacheTargetedResult(String cacheKey, Long resumeId, Long jobId, TargetedOptimizeResponse response) {
        try{
            String resultJson = objectMapper.writeValueAsString(response);
            long baseSeconds = TimeUnit.HOURS.toSeconds(1);
            long randomOffset =
                    ThreadLocalRandom.current().nextLong(0, 600);
            long expireSeconds = baseSeconds + randomOffset;

            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    resultJson,
                    expireSeconds,
                    TimeUnit.SECONDS
            );

            log.info(
                    "定向优化结果已缓存: resumeId={}, jobId={}, versionId={}, expireSeconds={}",
                    resumeId,
                    jobId,
                    response.getVersionId(),
                    expireSeconds
            );
        }catch (Exception e) {
            // 版本已经提交成功，缓存失败不能把接口改成失败。
            log.warn(
                    "写入定向优化缓存失败: resumeId={}, jobId={}, err={}",
                    resumeId,
                    jobId,
                    e.getMessage()
            );
        }
    }


    /**
     * 构建缓存 key
     */
    private String buildCacheKey(Long resumeId, String targetJob) {
        return CacheKeys.optimize(resumeId, targetJob);
    }

    @Override
    public String rewrite(String original, String focus) {
        //1.focus校验
        if (!isValidFocus(focus)) {
            throw new IllegalArgumentException("无效的focus参数，必须是 DATA/METHOD/IMPACT");
        }

        //2.调用AI
        String userPrompt = PromptTemplates.rewriteUser(original, focus);
        String result = aiService.chat(PromptTemplates.REWRITE_SYSTEM, userPrompt);

        //3. 清理：去掉```围栏、trim
        result = cleanMarkdown(result);

        if (result == null||result.isEmpty()) {
            throw new RuntimeException("AI未返回内容");
        }

        return result;
    }

    @Override
    @Transactional
    public ResumeVersion saveContent(Long resumeId, String content, String versionName) {
        Resume resume = resumeMapper.selectById(resumeId);
        //1.校验简历是否存在
        if (resume == null) {
            throw new IllegalArgumentException("简历不存在，resumeId: " + resumeId);
        }

        //手动校验userId
        Long userId = AuthContext.getUserId();
        if (!resume.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该简历");
        }

        //2.组装ResumeVersion
        ResumeVersion version = new ResumeVersion();
        version.setResumeId(resumeId);
        version.setUserId(resume.getUserId());
        version.setOptimizedContent(content);

        //如果未指定版本名，自动生成
        if (versionName == null ) {
            String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            version.setVersionName("精修=" + timeStamp);
        }else {
            version.setVersionName(versionName);
        }

        return saveVersionAndPublish(version);

    }

    @Override
    public byte[] exportVersion(Long versionId, String format) {


        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("导出格式不能为空");
        }

        log.info("导出版本, versionId: {}, format: {}", versionId, format);

        //1.查询版本 + 越权校验
        ResumeVersion version = findOwnedVersion(versionId);

        if (version.getPendingJson() != null && !"CONFIRMED".equals(version.getStatus())) {
            throw new IllegalArgumentException("该版本还有 AI 改动未确认，请先在版本页确认后再导出");
        }

        //2.获取内容
        String content = version.getOptimizedContent();
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("版本内容为空");
        }

        //3.format 分支
        switch (format.toLowerCase()) {
            case "docx":
                log.info("导出 DOCX, versionId: {}", versionId);
                return MarkdownConverter.toDocx(content);
            case "pdf":
                log.info("导出 PDF, versionId: {}", versionId);
                String html = MarkdownConverter.toHtml(content);
                return pdfGenerator.htmlToPdf(html);

            default:
                throw new IllegalArgumentException("不支持的格式: " + format + "，仅支持 docx 或 pdf");
        }

    }

    @Override
    @Transactional
    public ResumeVersion confirm(Long versionId, ConfirmRequest req) {
        log.info("确认版本: versionId={}, decisions={}", versionId,
                req == null || req.getDecisions() == null ? 0 : req.getDecisions().size());

        // 1. 查版本 + 越权校验
        Long userId = AuthContext.getUserId();
        ResumeVersion version = findOwnedVersion(versionId);

        if (!userId.equals(version.getUserId())) {
            log.warn("越权确认, versionId={}, userId={}", versionId, userId);
            throw new IllegalArgumentException("无权操作该版本");
        }

        // 2. 已是 CONFIRMED → 幂等返回
        if ("CONFIRMED".equals(version.getStatus())) {
            log.info("版本已确认，幂等返回: versionId={}", versionId);
            return version;
        }

        // 3. pendingJson 为空 → 没有待确认项，直接置 CONFIRMED
        if (version.getPendingJson() == null || version.getPendingJson().isBlank()) {
            version.setStatus("CONFIRMED");
            version.setConfirmedAt(LocalDateTime.now());
            resumeVersionMapper.updateById(version);
            log.info("无待确认项，直接确认: versionId={}", versionId);

            Long resumeId = version.getResumeId();

            afterCommitExecutor.execute(() ->
                    optimizeCacheEvictor.evictByResumeId(resumeId)
            );
            return version;
        }

        // 4. 解析快照，构造待处理集合
        PendingSnapshot snapshot;
        try {
            snapshot = objectMapper.readValue(version.getPendingJson(), PendingSnapshot.class);
        } catch (Exception e) {
            log.error("pending_json 解析失败: versionId={}", versionId, e);
            throw new RuntimeException("版本待确认项数据损坏，请联系管理员");
        }

        Set<String> pending = buildPendingKeys(snapshot);

        // 5. 校验 action + 逐条移出
        List<ConfirmDecision> decisions = req==null||req.getDecisions() == null?
                List.of():req.getDecisions();

        for (ConfirmDecision d : decisions) {
            if (d == null || d.getAction() == null) {
                throw new IllegalArgumentException("处置项缺少 action");
            }
            if (!"ACCEPT".equals(d.getAction()) && !"REMOVE".equals(d.getAction())) {
                throw new IllegalArgumentException("非法 action: " + d.getAction() + "，只能是 ACCEPT / REMOVE");
            }

            String key = d.getType() + ":" + d.getTarget();
            if (key.isBlank()) {
                throw new IllegalArgumentException("处置项缺少 type ");
            }
            pending.remove(key);   // 多余或重复的 key 直接忽略，不报错
        }

        // 剩余非空 → 400
        if (!pending.isEmpty()) {
            List<String> preview = pending.stream().limit(3).toList();
            throw new IllegalArgumentException(
                    "还有 " + pending.size() + " 项未处置：" + String.join("、", preview));
        }

        // 6. content 非空且与原内容不同 → 覆盖
        boolean contentChanged = false;
        if(req.getContent() != null && !req.getContent().isBlank()
                && !req.getContent().equals(version.getOptimizedContent())) {
            version.setOptimizedContent(req.getContent());
            contentChanged = true;
        }

        // 7. 置 CONFIRMED
        version.setStatus("CONFIRMED");
        version.setConfirmedAt(LocalDateTime.now());
        resumeVersionMapper.updateById(version);

        long finalResumeId = version.getResumeId();
        Long finalVersionId = version.getId();
        Long finalUserId = version.getUserId();
        boolean changed = contentChanged;
        String finalContent = version.getOptimizedContent();

        afterCommitExecutor.execute(() -> {
            if (changed) {
                producer.publish(
                        finalResumeId,
                        finalVersionId,
                        finalUserId,
                        finalContent
                );
            }

            optimizeCacheEvictor.evictByResumeId(finalResumeId);
        });

        log.info("版本确认成功: versionId={}, contentChanged={}", versionId, contentChanged);
        return version;

    }

    /** 把快照摊平成 "SKILL:xxx" / "CLAIM:yyy" 的 key 集合，key 的构造规则必须和前端约定一致 */
    private Set<String> buildPendingKeys(PendingSnapshot snapshot) {
        Set<String> keys = new LinkedHashSet<>();
        if (snapshot.getSkills() != null) {
            for (AddedSkill s : snapshot.getSkills()) {
                keys.add("SKILL:" + s.getSkill());
            }
        }
        if (snapshot.getClaims() != null) {
            for (Overstatement c : snapshot.getClaims()) {
                keys.add("CLAIM:" + c.getClaim());
            }
        }
        return keys;
    }

    /**
     * 校验focus是否有效
     */
    private boolean isValidFocus(String focus) {
        return PromptTemplates.FOCUS_DATA.equals(focus) ||
                PromptTemplates.FOCUS_METHOD.equals(focus) ||
                PromptTemplates.FOCUS_IMPACT.equals(focus);
    }

    /**
     * 清理Markdown围栏
     */
    private String cleanMarkdown(String text) {
        if (text == null) return null;
        // 移除 ``` 和 ```xxx 等围栏
        return text.replaceAll("(?s)```\\w*\\s*", "")
                .replaceAll("```", "")
                .trim();
    }

    private Resume loadOptimizableResume(Long resumeId) {
        Resume resume = resumeService.findById(resumeId);

        if ("REVIEW".equals(resume.getOcrStatus())) {
            throw new IllegalArgumentException(
                    "图片文字识别置信度较低，请先人工核对解析内容"
            );
        }

        String text = resume.getRawText();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("简历内容为空，无法优化");
        }

        return resume;
    }

    /**
     * 在短事务中保存优化版本，并在事务提交后发送 embedding 任务。
     *
     * AI 调用必须发生在调用本方法之前，避免长期占用数据库连接。
     */
    private ResumeVersion saveVersionAndPublish(ResumeVersion version) {
        if (version == null) {
            throw new IllegalArgumentException("优化版本不能为空");
        }

        transactionTemplate.executeWithoutResult(status -> {
            resumeVersionMapper.insert(version);

            if(version.getId()==null){
                throw new IllegalStateException("优化版本保存失败：未生成版本ID");
            }

            Long resumeId = version.getResumeId();
            Long versionId = version.getId();
            Long userId = version.getUserId();
            String content = version.getOptimizedContent();

            // 当前存在活动事务，AfterCommitExecutor 会注册 afterCommit 回调。
            afterCommitExecutor.execute(() -> producer.publish(
                    resumeId,
                    versionId,
                    userId,
                    content
            ));
        });

        return version;
    }


    private String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private ResumeVersion findOwnedVersion(Long versionId) {
        Long currentUserId = AuthContext.getUserId();
        ResumeVersion version = resumeVersionMapper.selectById(versionId);

        if (version == null || !currentUserId.equals(version.getUserId())) {
            throw new IllegalArgumentException("版本不存在");
        }

        return version;
    }

}
