package com.resumecraft.server.diagnose.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.common.DiagnosisResponse;
import com.resumecraft.server.diagnose.domain.Diagnosis;
import com.resumecraft.server.diagnose.domain.DiagnosisMapper;
import com.resumecraft.server.diagnose.dto.DiagnosisResult;
import com.resumecraft.server.diagnose.service.DiagnosisService;
import com.resumecraft.server.resume.domain.Resume;
import com.resumecraft.server.resume.domain.ResumeMapper;
import com.resumecraft.server.resume.service.ResumeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DiagnosisServiceImpl implements DiagnosisService {

    @Resource
    private AiService aiService;
    @Resource
    private ResumeService resumeService;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private DiagnosisMapper diagnosisMapper;
    @Resource
    private ResumeMapper resumeMapper;

    private static final String CACHE_KEY_PREFIX = "diagnose:";
    private static final String CACHE_NULL_PREFIX = "diagnose:null:";
    private static final int NULL_CACHE_EXPIRE_MINUTES = 5;
    private static final int LOCK_EXPIRE_SECONDS = 60;        // 分布式锁过期时间：60秒
    private static final String CACHE_LOCK_PREFIX = "diagnose:lock:";

    private final Random random = new Random();

    /**
     * 诊断简历
     * @param resumeId
     * @return
     */
    public DiagnosisResponse diagnose(Long resumeId) {

        //参数校验
        validateResumeId(resumeId);

        String key = CACHE_KEY_PREFIX + resumeId;

        //从redis中获取缓存数据
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                // 缓存命中，将JSON字符串反序列化为DiagnosisResponse对象并直接返回
                return objectMapper.readValue(cached, DiagnosisResponse.class);
            }
        } catch (Exception e) {
            log.warn("缓存读取失败, resumeId={}", resumeId, e);
        }

//        //防止重复诊断
//        String lockKey = CACHE_LOCK_PREFIX + resumeId;
//        if(!tryAcquireLock(lockKey)) {
//            log.debug("获取锁失败，等待已有诊断结果, resumeId={}", resumeId);
//            return waitForDiagnosisResult(resumeId, key);
//        }

//        // 检查是否在缓存空值
//        try {
//            if(isNullCached(resumeId)) {
//                throw new IllegalArgumentException("简历不存在");
//            }
//        } catch (IllegalArgumentException e) {
//            throw e;  // 重新抛出业务异常
//        } catch (Exception e) {
//            log.warn("空值缓存检查失败, resumeId={}", resumeId, e);
//        }

        log.debug("开始诊断简历, resumeId={}", resumeId);

        //查询简历
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            //只有在确认数据库没有数据时，才写入空值缓存
            cacheNullValue(resumeId);//防止缓存穿透
            throw new IllegalArgumentException("简历不存在: " + resumeId);
        }

        // 数据存在，删除可能存在的空值缓存（数据已恢复）
        deleteNullCache(resumeId);

        String text = resume.getRawText();
        if (text == null || text.isEmpty()) {
            throw new IllegalStateException("简历内容为空，无法诊断");
        }

        // 拼 prompt，调 AI，拿回 JSON 字符串
        String json = aiService.chat(PromptTemplates.DIAGNOSIS_SYSTEM,
                PromptTemplates.diagnosisUser(text));

        // JSON 转 DTO
        DiagnosisResult r;
        try {
            r = objectMapper.readValue(json, DiagnosisResult.class);
        } catch (Exception e) {
            throw new RuntimeException("AI 返回解析失败", e);
        }

        //处理建议列表空值
        List<String> suggestions = r.getSuggestions();
        if (suggestions == null || suggestions.isEmpty()) {
            suggestions = Collections.emptyList();
        }

        // 将诊断结果保存到数据库
        saveDiagnosis(resumeId,r.getTotalScore(),r.getCompletenessScore(),
                r.getExpressionScore(), r.getMatchScore(), suggestions);

         // 组装最终返回给前端的诊断报告
        DiagnosisResponse response = new DiagnosisResponse(resumeId,
                round(r.getTotalScore()),                          // 总分（四舍五入）
                colorized(r.getCompletenessScore(), "信息完整度"), // 完整度（带颜色标识）
                colorized(r.getExpressionScore(), "表达质量"),     // 表达质量（带颜色标识）
                colorized(r.getMatchScore(), "岗位匹配度"),        // 匹配度（带颜色标识）
                suggestions);                               // 改进建议列表

        try {
            int expireTime = 30 + random.nextInt(31);
            stringRedisTemplate.opsForValue().set
                    (key,objectMapper.writeValueAsString(response),expireTime, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("缓存写入失败, resumeId={}", resumeId, e);
        }
        // 返回诊断结果
        return response;

    }


    private void saveDiagnosis(Long resumeId, double total, double comp, double expr, double match, List<String> suggestions) {
        Diagnosis diagnosis = Diagnosis.builder()
                .resumeId(resumeId)
                .totalScore(total)
                .completenessScore(comp)
                .expressionScore(expr)
                .matchScore(match)
                .suggestions(String.join("\n", suggestions))
                .build();

        diagnosisMapper.insert(diagnosis);
    }

    private DiagnosisResponse.DimensionScore colorized(double score, String label) {
        String color;
        if (score >= 70) color = "green";
        else if (score >= 40) color = "yellow";
        else color = "red";
        return new DiagnosisResponse.DimensionScore(score, color, label);
    }

    private double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    /**
     * 参数校验
     */
    private void validateResumeId(Long resumeId) {
        if (resumeId == null || resumeId <= 0) {
            throw new IllegalArgumentException("无效的简历ID: " + resumeId);
        }
    }

    /**
     * 缓存空值（防止缓存穿透）
     */
    private void cacheNullValue(Long resumeId) {
        try {
            String key = CACHE_NULL_PREFIX + resumeId;
            stringRedisTemplate.opsForValue().set(key, "null",
                    NULL_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("空值缓存写入失败, resumeId={}", resumeId, e);
        }
    }

    /**
     * 删除空值缓存
     * @param resumeId
     */
    private void deleteNullCache(Long resumeId) {
        String key = CACHE_NULL_PREFIX + resumeId;  // diagnose:null:123
        stringRedisTemplate.delete(key);  // 只删除 diagnose:null:123
    }

    /**
     * 尝试获取分布式锁
     */
    private boolean tryAcquireLock(String lockKey) {
        try {
            Boolean success = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            log.warn("获取分布式锁失败, lockKey={}", lockKey, e);
            return true; // 降级：获取锁失败允许执行
        }
    }

    /**
     * 释放分布式锁
     * @param lockKey
     */
    private void releaseLock(String lockKey) {
        try {
            stringRedisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.warn("释放锁失败, lockKey={}", lockKey, e);
        }
    }
}
