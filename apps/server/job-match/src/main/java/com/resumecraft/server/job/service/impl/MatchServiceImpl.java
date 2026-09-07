package com.resumecraft.server.job.service.impl;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.common.ApiResponse;
import com.resumecraft.server.common.dto.ResumeBriefDTO;
import com.resumecraft.server.common.dto.ResumeVersionDTO;
import com.resumecraft.server.common.feign.ResumeClient;
import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.job.domain.JobMapper;
import com.resumecraft.server.job.domain.MatchResult;
import com.resumecraft.server.job.domain.MatchResultMapper;
import com.resumecraft.server.job.match.MatchEngine;
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
    public ApiResponse<MatchResult> match(Long resumeId, Long jobId) {

        return match(resumeId, jobId,null,false);
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
    public ApiResponse<MatchResult> match(Long resumeId, Long jobId, Long versionId, boolean forceRefresh) {

        //1.岗位校验
        Job job = jobMapper.selectById(jobId);

        if (job == null) {
            throw new RuntimeException("岗位不存在，jobId: " + jobId);
        }

        // 2. 解析匹配文本：优先版本内容，否则简历原文
        String resumeText;
        Long resumeUserId;
        if (versionId != null) {
            ResumeVersionDTO version = resumeClient.getVersion(versionId);
            if (version == null) {
                throw new RuntimeException("版本不存在，versionId: " + versionId);
            }
            if (!resumeId.equals(version.getResumeId())) {
                throw new RuntimeException("版本不属于该简历");
            }
            resumeText = version.getOptimizedContent();
            resumeUserId = version.getUserId();
        }
        else {
            ResumeBriefDTO brief = resumeClient.getResume(resumeId);
            if (brief == null) {
                throw new RuntimeException("简历不存在，resumeId: " + resumeId);
            }
            resumeText =buildResumeText(brief);
            resumeUserId = brief.getUserId();
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

        MatchResult matchResult = matchEngine.execute(resumeText, job);
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
        return "match:" + resumeId + ":" + jobId;
    }
    /**
     * 构建缓存 key（包含版本信息）
     */
    private String buildCacheKey(Long resumeId, Long jobId, Long versionId) {
        if (versionId != null) {
            return "match:" + resumeId + ":" + jobId + ":v" + versionId;
        }
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


//    //安全地从 JSON 对象中获取 Double 类型的值。
//    private Double getDouble(JsonNode json, String key) {
//        return json.has(key)//检查 JSON 中是否存在该字段
//                && !json.get(key).isNull() ? json.get(key).asDouble() : null;//检查该字段的值是否为 null,如果字段存在且不为 null → 调用 asDouble() 返回数值
//    }
//
//    //安全地从 JSON 对象中获取 String 类型的值。
//    private String getString(JsonNode json, String key) {
//        return json.has(key) && !json.get(key).isNull() ? json.get(key).asText() : null;
//    }
}
