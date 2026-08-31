package com.resumecraft.server.optimize.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.optimize.service.OptimizeService;
import com.resumecraft.server.resume.domain.Resume;
import com.resumecraft.server.resume.domain.ResumeVersion;
import com.resumecraft.server.resume.domain.ResumeVersionMapper;
import com.resumecraft.server.resume.service.ResumeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

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
    private ThreadPoolTaskExecutor aiExecutor;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ObjectMapper objectMapper;

    private static final String NULL_VALUE = "NULL";
    private static final long NULL_EXPIRE_MINUTES = 5;


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
     * 构建缓存 key
     */
    private String buildCacheKey(Long resumeId, String targetJob) {
        String jobKey = (targetJob == null || targetJob.trim().isEmpty()) ? "general" : targetJob.trim();
        return "optimize:" + resumeId + ":" + jobKey;
    }
}
