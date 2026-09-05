package com.resumecraft.server.optimize.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.common.security.AuthContext;
import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.job.domain.JobMapper;
import com.resumecraft.server.optimize.dto.TargetedOptimizeResponse;
import com.resumecraft.server.optimize.service.OptimizeService;
import com.resumecraft.server.optimize.util.MarkdownConverter;
import com.resumecraft.server.optimize.util.PdfGenerator;
import com.resumecraft.server.resume.domain.Resume;
import com.resumecraft.server.resume.domain.ResumeMapper;
import com.resumecraft.server.resume.domain.ResumeVersion;
import com.resumecraft.server.resume.domain.ResumeVersionMapper;
import com.resumecraft.server.resume.service.ResumeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    private ThreadPoolTaskExecutor aiExecutor;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ObjectMapper objectMapper;

    private static final String NULL_VALUE = "NULL";
    private static final long NULL_EXPIRE_MINUTES = 5;
    private static final String CACHE_KEY_PREFIX = "targeted-optimize:";


    @Override
    public ResumeVersion optimize(Long resumeId, String targetJob) {

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

        //检查是否是空值缓存
        if(NULL_VALUE.equals(cachedValue)) {
            log.warn("命中空值缓存, resumeId={}, targetJob={}", resumeId, targetJob);
            throw new RuntimeException("简历不存在");
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
                stringRedisTemplate.delete(cacheKey);
            }
        }
        //查询数据库验证简历是否存在
        Resume resume;
        try {
            resume = resumeService.findById(resumeId);
        }
        catch (IllegalArgumentException e) {
            // 业务场景：简历不存在 → 写空值缓存（防穿透）→ 重抛原异常
            stringRedisTemplate.opsForValue().set(cacheKey, NULL_VALUE, NULL_EXPIRE_MINUTES, TimeUnit.MINUTES);
            throw e;                          // 保留"简历不存在"给前端
        } catch (Exception e) {
            // 数据库异常也可能导致查不到，不缓存空值
            log.error("查询简历失败, resumeId={}", resumeId, e);
            throw new RuntimeException("系统异常，请稍后重试");
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
        resumeVersionMapper.insert(version);
        log.info("优化版本已保存: id={}, resumeId={}", version.getId(), resumeId);
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
     * 用户请求 (resumeId, jobId)
     *     ↓
     * 1. 检查 Redis 缓存
     *     ↓ (未命中)
     * 2. 查询 Resume + Job
     *     ↓ (存在)
     * 3. 拼 Prompt → 调用 AI
     *     ↓
     * 4. 解析 AI 返回 JSON
     *     ├─ optimizedResume (Markdown)
     *     ├─ gaps (缺失技能列表)
     *     └─ changes (改动说明)
     *     ↓
     * 5. 存 resume_version
     *     ├─ userId = resume.getUserId()  ← 继承归属
     *     ├─ targetJob = job.getTitle()
     *     └─ optimizedContent = optimizedResume
     *     ↓
     * 6. 组装 TargetedOptimizeResponse
     *     ├─ versionId (新生成的版本ID)
     *     ├─ optimizedContent
     *     ├─ gaps
     *     └─ changes
     *     ↓
     * 7. 写入 Redis 缓存 (1小时 + 随机偏移)
     *     ↓
     * 8. 返回响应
     */
    @Override
    public TargetedOptimizeResponse targetedOptimize(Long resumeId, Long jobId) {

        log.info("开始定向优化: resumeId={}, jobId={}", resumeId, jobId);

        // 1. 构建缓存 key
        String cacheKey = CACHE_KEY_PREFIX + resumeId + ":" + jobId;

        // 2. 检查缓存（Redis 异常时降级跳过缓存）
        String cachedValue = null;
        try {
            cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("缓存读取失败，跳过缓存, cacheKey={}", cacheKey, e);
        }

        // 3. 检查空值缓存
        if (NULL_VALUE.equals(cachedValue)) {
            log.warn("命中空值缓存, resumeId={}, jobId={}", resumeId, jobId);
            throw new RuntimeException("简历或岗位不存在");
        }

        // 4. 检查正常缓存
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

        // 5. 查询简历和岗位
        Resume resume;
        Job job;
        try {
            resume = resumeMapper.selectById(resumeId);
            if (resume == null) {
                // 简历不存在，缓存空值
                try {
                    stringRedisTemplate.opsForValue().set(
                            cacheKey,
                            NULL_VALUE,
                            NULL_EXPIRE_MINUTES,
                            TimeUnit.MINUTES
                    );
                } catch (Exception e) {
                    log.warn("空值缓存写入失败: {}", e.getMessage());
                }
                throw new IllegalArgumentException("简历不存在: resumeId=" + resumeId);
            }

            job = jobMapper.selectById(jobId);
            if (job == null) {
                // 岗位不存在，缓存空值
                try {
                    stringRedisTemplate.opsForValue().set(
                            cacheKey,
                            NULL_VALUE,
                            NULL_EXPIRE_MINUTES,
                            TimeUnit.MINUTES
                    );
                } catch (Exception e) {
                    log.warn("空值缓存写入失败: {}", e.getMessage());
                }
                throw new IllegalArgumentException("岗位不存在: jobId=" + jobId);
            }
        } catch (IllegalArgumentException e) {
            throw e; // 业务异常直接抛出
        } catch (Exception e) {
            // 系统异常，不缓存空值
            log.error("查询数据失败, resumeId={}, jobId={}", resumeId, jobId, e);
            throw new RuntimeException("系统异常，请稍后重试");
        }

        //6.调用AI定向优化
        String userPrompt = PromptTemplates.targetedOptimizeUser(
                resume.getRawText(),
                job.getTitle(),
                job.getDescription(),
                job.getRequirements()
        );

        String aiResponse = aiService.chat(PromptTemplates.TARGETED_OPTIMIZE_SYSTEM, userPrompt);

        log.info("定向优化 AI 响应: {}", aiResponse);

        //7.解析 AI 返回的 JSON
        TargetedOptimizeResponse result = parseTargetedOptimizeResponse(aiResponse);

        String optimizedResume = result.getOptimizedContent();
        List<String> gaps = result.getGaps();
        List<String> changes = result.getChanges();

        log.info("定向优化解析成功: resumeId={}, 优化后内容长度={}, gaps数={}, changes数={}",
                resumeId,
                optimizedResume != null ? optimizedResume.length() : 0,
                gaps != null ? gaps.size() : 0,
                changes != null ? changes.size() : 0);

        //8.存 resume_version（继承简历归属）
        ResumeVersion version = ResumeVersion.builder()
                .resumeId(resumeId)
                .userId(resume.getUserId())
                .versionName("定向优化-" + job.getTitle())
                .targetJob(job.getTitle())
                .optimizedContent(optimizedResume)
                .build();
        resumeVersionMapper.insert(version);

        log.info("定向优化版本已保存: versionId={}, resumeId={}, userId={}, targetJob={}",
                version.getId(), resumeId, resume.getUserId(), job.getTitle());

        //9.组装响应
        TargetedOptimizeResponse response = TargetedOptimizeResponse.builder()
                .versionId(version.getId())
                .optimizedContent(optimizedResume)
                .gaps(gaps)
                .changes(changes)
                .build();

        //10. 写入缓存（有效期 1小时 + 随机偏移防雪崩）
        try {
            String resultJson = objectMapper.writeValueAsString(response);

            long baseSeconds = TimeUnit.HOURS.toSeconds(1);
            long randomOffset = ThreadLocalRandom.current().nextLong(0, 600); // 0-600秒
            long expireSeconds = baseSeconds + randomOffset;

            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    resultJson,
                    expireSeconds,
                    TimeUnit.SECONDS
            );
            log.info("定向优化结果已缓存: resumeId={}, jobId={}, versionId={}, 过期时间={}秒",
                    resumeId, jobId, version.getId(), expireSeconds);
        } catch (Exception e) {
            log.warn("缓存写入失败: {}", e.getMessage());
            // 缓存失败不影响主流程
        }

        return response;

    }


    /**
     * 构建缓存 key
     */
    private String buildCacheKey(Long resumeId, String targetJob) {
        String jobKey = (targetJob == null || targetJob.trim().isEmpty()) ? "general" : targetJob.trim();
        return "optimize:" + resumeId + ":" + jobKey;
    }

    /**
     * 解析定向优化结果
     */
    private TargetedOptimizeResponse parseTargetedOptimizeResponse(String aiResponse) {

        try {
            // 清理 Markdown 代码块标记
            String jsonStr = aiResponse
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();
            return objectMapper.readValue(jsonStr, TargetedOptimizeResponse.class);
        }catch (Exception e) {
            log.error("解析定向优化结果失败，原始响应: {}", aiResponse, e);
            throw new RuntimeException("AI 响应格式异常", e);
        }
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

        //插入数据库
        resumeVersionMapper.insert(version);
        log.info("保存精修版本成功，resumeId: {}, versionId: {}, versionName: {}",
                resumeId, version.getId(), version.getVersionName());

        return version;

    }

    @Override
    public byte[] exportVersion(Long versionId, String format) {
        log.info("导出版本, versionId: {}, format: {}", versionId, format);

        //1.查询版本 + 越权校验
        Long userId = AuthContext.getUserId();
        ResumeVersion version = resumeVersionMapper.selectById(versionId);

        if (version == null) {
            throw new IllegalArgumentException("版本不存在");
        }

        if (!userId.equals(version.getUserId())) {
            log.warn("越权导出, versionId: {}, userId: {}", versionId, userId);
            throw new IllegalArgumentException("无权导出该版本");
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
}
