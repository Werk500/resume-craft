package com.resumecraft.server.job.service.impl;


import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
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
    @Resource
    private TransactionTemplate transactionTemplate;

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

            // 检查正常缓存
            if (cachedValue != null) {
                try {
                    MatchResult cachedResult = objectMapper.readValue(cachedValue, MatchResult.class);
                    log.info("命中缓存: resumeId={}, jobId={}, versionId={}, resultId={}",
                            resumeId, jobId, versionId, cachedResult.getId());
                    return ApiResponse.ok(cachedResult);
                } catch (Exception e) {
                    log.warn("缓存反序列化失败，将重新生成: {}", e.getMessage());
                    try {
                        stringRedisTemplate.delete(cacheKey);
                    } catch (Exception ex) {
                        log.warn(
                                "删除损坏匹配缓存失败: cacheKey={}, err={}",
                                cacheKey,
                                ex.getMessage()
                        );
                    }
                }

            }
        } else {
            log.info("强制刷新模式，跳过缓存: resumeId={}, jobId={}, versionId={}", resumeId, jobId, versionId);
        }

        // 传入上下文：语义评分可据此走向量检索（versionId=null 表示主简历）
        MatchResult matchResult = matchEngine.execute(resumeText, job,
                new MatchContext(resumeId, versionId, resumeUserId));
        matchResult.setJobId(jobId);
        matchResult.setResumeId(resumeId);
        matchResult.setUserId(resumeUserId);

        //6.入库
        saveMatchResult(matchResult);

        //7.写入缓存
        cacheMatchResult(cacheKey, matchResult);

        return ApiResponse.ok(matchResult);

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

    private void saveMatchResult(MatchResult matchResult) {
        transactionTemplate.executeWithoutResult(status -> {
            matchResultMapper.insert(matchResult);

            if (matchResult.getId() == null) {
                throw new IllegalStateException(
                        "匹配结果保存失败：未生成主键"
                );
            }
        });
    }

    /**
     * 缓存写入
     * @param cacheKey
     * @param result
     */
    private void cacheMatchResult(
            String cacheKey,
            MatchResult result
    ) {
        try {
            String json = objectMapper.writeValueAsString(result);

            long baseSeconds = TimeUnit.HOURS.toSeconds(1);
            long randomOffset =
                    ThreadLocalRandom.current().nextLong(0, 600);

            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    json,
                    baseSeconds + randomOffset,
                    TimeUnit.SECONDS
            );

            log.info(
                    "匹配结果已缓存: cacheKey={}, resultId={}",
                    cacheKey,
                    result.getId()
            );
        } catch (Exception e) {
            log.warn(
                    "匹配缓存写入失败: cacheKey={}, err={}",
                    cacheKey,
                    e.getMessage()
            );
        }
    }


}
