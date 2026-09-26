package com.resumecraft.server.job.service.impl;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.common.ApiResponse;
import com.resumecraft.server.common.cache.CacheKeys;
import com.resumecraft.server.common.dto.ResumeBriefDTO;
import com.resumecraft.server.common.dto.ResumeVersionDTO;
import com.resumecraft.server.common.exception.ServiceUnavailableException;
import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.job.domain.JobMapper;
import com.resumecraft.server.job.domain.MatchResult;
import com.resumecraft.server.job.domain.MatchResultMapper;
import com.resumecraft.server.job.gateway.ResumeServiceGateway;
import com.resumecraft.server.job.match.MatchEngine;
import com.resumecraft.server.job.match.MatchContext;
import com.resumecraft.server.job.service.MatchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class MatchServiceImpl implements MatchService {

    @Resource
    private ResumeServiceGateway resumeServiceGateway;
    @Resource
    private JobMapper jobMapper;
    @Resource
    private MatchResultMapper matchResultMapper;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private MatchEngine matchEngine;

    private static final String NULL_VALUE = "NULL";
    private static final long NULL_EXPIRE_MINUTES = 5;

    /**
     * 人岗匹配分析
     * @param resumeId
     * @param jobId
     * @return
     */
    @Override
    public ApiResponse<MatchResult> match(Long currentUserId, Long resumeId, Long jobId) {

        return match(currentUserId,resumeId, jobId,null,false);
    }

    // ==================== 新增重载方法（支持版本 + 强制刷新） ====================

    /**
     * 人岗匹配分析（支持版本选择 + 强制刷新）
     * @param resumeId
     * @param jobId
     * @param versionId    版本ID（可选，传了=匹配某个优化版本，不传=匹配简历原文）
     * @param forceRefresh 是否强制刷新（true=跳过缓存）
     * @return
     */

    @Override
    public ApiResponse<MatchResult> match(Long currentUserId, Long resumeId, Long jobId, Long versionId, boolean forceRefresh) {

        //1.岗位校验
        Job job = jobMapper.selectById(jobId);

        if (job == null) {
            throw new RuntimeException("岗位不存在，jobId: " + jobId);
        }

        // 2. 解析匹配文本：优先版本内容，否则简历原文
        String resumeText;
        Long resumeUserId;
        if (versionId != null) {

            ResumeVersionDTO version = resumeServiceGateway.getVersion(versionId);
            if (version == null) {
                // 网关返回 null 表示熔断降级（resume 服务不可用），而非"版本不存在"。
                // 版本确实不存在时，resume 服务会返回 4xx，网关翻译成 IllegalArgumentException 抛出。
                throw new ServiceUnavailableException("简历服务暂时不可用，请稍后重试");
            }
            if (!resumeId.equals(version.getResumeId())) {
                throw new RuntimeException("版本不属于该简历");
            }
            resumeText = version.getOptimizedContent();
            resumeUserId = version.getUserId();
        }
        else {
            ResumeBriefDTO brief = resumeServiceGateway.getResume(resumeId);
            if (brief == null) {
                // 同上：null = 熔断降级（服务不可用），映射为 503 而非 400
                throw new ServiceUnavailableException("简历服务暂时不可用，请稍后重试");
            }
            resumeText =buildResumeText(brief);
            resumeUserId = brief.getUserId();
        }

        if (!Objects.equals(resumeUserId, currentUserId)) {
            // 与 resume 服务保持一致：不区分「不存在」和「无权访问」，
            // 避免通过返回信息探测资源是否存在
            throw new IllegalArgumentException("简历不存在：id=" + resumeId);
        }

        // 3. 构建缓存 key（包含版本信息，区分不同版本的匹配结果）
        String cacheKey = buildCacheKey(resumeId, jobId, versionId);

        // 4. 检查缓存（如果 forceRefresh = true，跳过缓存）
        if (!forceRefresh) {
            String cachedValue = null;
            try {
                cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
            } catch (Exception e) {
                log.warn("缓存读取失败，跳过缓存, cacheKey={}", cacheKey, e);
            }

            // 检查空值缓存
            if (NULL_VALUE.equals(cachedValue)) {
                log.warn("命中空值缓存, resumeId={}, jobId={}, versionId={}", resumeId, jobId, versionId);
                throw new RuntimeException("简历或岗位不存在");
            }
            // 检查正常缓存
            if (cachedValue != null) {
                try {
                    MatchResult cachedResult = objectMapper.readValue(cachedValue, MatchResult.class);
                    log.info("命中缓存: resumeId={}, jobId={}, versionId={}, resultId={}",
                            resumeId, jobId, versionId, cachedResult.getId());
                    return ApiResponse.ok(cachedResult);
                } catch (Exception e) {
                    log.warn("缓存反序列化失败，将重新生成: {}", e.getMessage());
                    stringRedisTemplate.delete(cacheKey);
                }
            }
        } else {
            log.info("强制刷新模式，跳过缓存: resumeId={}, jobId={}, versionId={}", resumeId, jobId, versionId);
        }
//        //5.本轮先保留 AI 打分（P2-2 才替换成 MatchEngine）
//        String userPrompt = PromptTemplates.matchUser(
//                resumeText, job.getTitle(), job.getDescription(), job.getRequirements());
//
//        MatchResult matchResult = parseMatchResult(aiService.chat(PromptTemplates.MATCH_SYSTEM, userPrompt));
//        matchResult.setJobId(jobId);
//        matchResult.setResumeId(resumeId);
//        matchResult.setUserId(resumeUserId);

        // 传入上下文：语义评分可据此走向量检索（versionId=null 表示主简历）
        MatchResult matchResult = matchEngine.execute(resumeText, job,
                new MatchContext(resumeId, versionId, resumeUserId));
        matchResult.setJobId(jobId);
        matchResult.setResumeId(resumeId);
        matchResult.setUserId(resumeUserId);

        //6.入库
        matchResultMapper.insert(matchResult);

        //7.写入缓存
        try {
            String resultJson = objectMapper.writeValueAsString(matchResult);

            // 基础1小时 + 随机0~10分钟，防止缓存雪崩
            long baseSeconds = TimeUnit.HOURS.toSeconds(1);
            long randomOffset = ThreadLocalRandom.current().nextLong(0, 600);
            long expireSeconds = baseSeconds + randomOffset;

            stringRedisTemplate.opsForValue().set(cacheKey, resultJson, expireSeconds, TimeUnit.SECONDS);

            log.info("匹配结果已缓存: resumeId={}, jobId={}, resultId={}, 过期时间={}秒",
                    resumeId, jobId, matchResult.getId(), expireSeconds);
        } catch (Exception e) {
            log.warn("缓存写入失败: {}", e.getMessage());
            // 缓存失败不影响主流程
        }

        return ApiResponse.ok(matchResult);

        }

    /**
     * 构建缓存 key
     */
    private String buildCacheKey(Long resumeId, Long jobId) {
        return CacheKeys.match(resumeId, jobId);
    }
    /**
     * 构建缓存 key（包含版本信息）
     */
    private String buildCacheKey(Long resumeId, Long jobId, Long versionId) {
        return CacheKeys.match(resumeId, jobId, versionId);
    }

    /**
     * 构建简历文本（根据你的 Resume 实体字段调整）
     */
    private String buildResumeText(ResumeBriefDTO resume) {
        return "姓名：" + (resume.getParsedName() == null ? "未提供" : resume.getParsedName()) + "\n" +
                "邮箱：" + (resume.getParsedEmail() == null ? "未提供" : resume.getParsedEmail()) + "\n" +
                "电话：" + (resume.getParsedPhone() == null ? "未提供" : resume.getParsedPhone()) + "\n" +
                "简历原文：\n" + (resume.getRawText() == null ? "无" : resume.getRawText());

    }

}
