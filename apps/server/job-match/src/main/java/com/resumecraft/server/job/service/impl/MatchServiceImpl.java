package com.resumecraft.server.job.service.impl;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.common.ApiResponse;
import com.resumecraft.server.common.dto.ResumeBriefDTO;
import com.resumecraft.server.common.feign.ResumeClient;
import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.job.domain.JobMapper;
import com.resumecraft.server.job.domain.MatchResult;
import com.resumecraft.server.job.domain.MatchResultMapper;
import com.resumecraft.server.job.service.MatchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class MatchServiceImpl implements MatchService {


    @Resource
    private ResumeClient resumeClient;
    @Resource
    private JobMapper jobMapper;
    @Resource
    private MatchResultMapper matchResultMapper;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private AiService aiService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String NULL_VALUE = "NULL";
    private static final long NULL_EXPIRE_MINUTES = 5;

    /**
     * 人岗匹配分析
     * @param resumeId
     * @param jobId
     * @return
     */
    @Override
    public ApiResponse<MatchResult> match(Long resumeId, Long jobId) {

        //1.检查缓存（Redis 异常时降级跳过缓存，不阻塞主流程）
        String cacheKey = buildCacheKey(resumeId, jobId);
        String cachedValue = null;
        try {
            cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("缓存读取失败，跳过缓存, cacheKey={}", cacheKey, e);
        }
        //检查空值缓存
        if(NULL_VALUE.equals(cachedValue)) {
            log.warn("命中空值缓存, resumeId={}, jobId={}", resumeId, jobId);
            throw new RuntimeException("简历或岗位不存在");
        }

        //检查正常缓存
        if (cachedValue != null) {
            try {
                MatchResult cachedResult = objectMapper.readValue(cachedValue, MatchResult.class);
                log.info("命中缓存: resumeId={}, jobId={}, resultId={}",
                        resumeId, jobId, cachedResult.getId());
                return ApiResponse.ok(cachedResult);
            } catch (Exception e) {
                log.warn("缓存反序列化失败，将重新生成: {}", e.getMessage());
                stringRedisTemplate.delete(cacheKey);
            }
        }

        //2. 查询简历和岗位
        ResumeBriefDTO resume;
        Job job;
        try {
            resume = resumeClient.getResume(resumeId);
            if (resume == null) {
                // 简历不存在，缓存空值
                stringRedisTemplate.opsForValue().set(
                        cacheKey,
                        NULL_VALUE,
                        NULL_EXPIRE_MINUTES,
                        TimeUnit.MINUTES
                );
                log.warn("简历不存在, 已缓存空值, resumeId={}", resumeId);
                throw new RuntimeException("简历不存在，resumeId: " + resumeId);
            }

            job = jobMapper.selectById(jobId);
            if (job == null) {
                // 岗位不存在，缓存空值
                stringRedisTemplate.opsForValue().set(
                        cacheKey,
                        NULL_VALUE,
                        NULL_EXPIRE_MINUTES,
                        TimeUnit.MINUTES
                );
                log.warn("岗位不存在, 已缓存空值, jobId={}", jobId);
                throw new RuntimeException("岗位不存在，jobId: " + jobId);
            }
        } catch (RuntimeException e) {
            // 业务异常（简历或岗位不存在），已经缓存了空值，直接抛出
            throw e;
        } catch (Exception e) {
            // 系统异常（数据库连接失败等），不缓存空值
            log.error("查询数据失败, resumeId={}, jobId={}", resumeId, jobId, e);
            throw new RuntimeException("系统异常，请稍后重试");
        }

        log.info("开始人岗匹配分析，resumeId: {}, jobId: {}", resumeId, jobId);

        //3.拼prompt
        String resumeText = buildResumeText(resume);
        String userPrompt = PromptTemplates.matchUser(
                resumeText,
                job.getTitle(),
                job.getDescription(),
                job.getRequirements()
        );

        //4.调用AI
        String systemPrompt = PromptTemplates.MATCH_SYSTEM;
        String aiResponse = aiService.chat(systemPrompt, userPrompt);
        log.info("AI 响应: {}", aiResponse);

        //5.解析JSON
        MatchResult matchResult = parseMatchResult(aiResponse);
        matchResult.setJobId(jobId);
        matchResult.setResumeId(resumeId);
        matchResult.setUserId(resume.getUserId());

        //6.存match_result
        matchResultMapper.insert(matchResult);
        log.info("人岗匹配结果已保存，id: {}", matchResult.getId());

        // 7. 写入缓存（有效期 1小时 + 随机偏移防雪崩）
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

        // 8. 返回
        return ApiResponse.ok(matchResult);
    }

    /**
     * 构建缓存 key
     */
    private String buildCacheKey(Long resumeId, Long jobId) {
        return "match:" + resumeId + ":" + jobId;
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

    /**
     * 解析 AI 返回的 JSON
     */
    private MatchResult parseMatchResult(String aiResponse) {
        try {
            String jsonStr = aiResponse
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();

            JsonNode json = objectMapper.readTree(jsonStr);

            return MatchResult.builder()

                    .overallScore(getDouble(json, "overallScore"))
                    .keywordCoverage(getDouble(json, "keywordCoverage"))
                    .semanticSimilarity(getDouble(json, "semanticSimilarity"))
                    .hardRequirementScore(getDouble(json, "hardRequirementScore"))
                    .matchExplanation(getString(json, "matchExplanation"))
                    .build();
        } catch (Exception e) {
            log.error("解析 AI 响应 JSON 失败，原始响应: {}", aiResponse, e);
            throw new RuntimeException("AI 响应格式异常，无法解析匹配结果", e);
        }
    }

    //安全地从 JSON 对象中获取 Double 类型的值。
    private Double getDouble(JsonNode json, String key) {
        return json.has(key)//检查 JSON 中是否存在该字段
                && !json.get(key).isNull() ? json.get(key).asDouble() : null;//检查该字段的值是否为 null,如果字段存在且不为 null → 调用 asDouble() 返回数值
    }

    //安全地从 JSON 对象中获取 String 类型的值。
    private String getString(JsonNode json, String key) {
        return json.has(key) && !json.get(key).isNull() ? json.get(key).asText() : null;
    }
}
