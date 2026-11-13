package com.resumecraft.server.diagnose.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.common.cache.CacheKeys;
import com.resumecraft.server.common.DiagnosisResponse;
import com.resumecraft.server.common.exception.ServiceUnavailableException;
import com.resumecraft.server.diagnose.domain.Diagnosis;
import com.resumecraft.server.diagnose.domain.DiagnosisMapper;
import com.resumecraft.server.diagnose.dto.DiagnosisResult;
import com.resumecraft.server.diagnose.service.DiagnosisService;
import com.resumecraft.server.resume.domain.Resume;
import com.resumecraft.server.resume.service.ResumeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DiagnosisServiceImpl implements DiagnosisService {

    @Resource
    private AiService aiService;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private DiagnosisMapper diagnosisMapper;
    @Lazy
    @Resource
    private ResumeService resumeService;

    private static final long LOCK_EXPIRE_SECONDS = 240;
    private static final long LOCK_WAIT_TIMEOUT_MILLIS = 65_000;
    private static final long LOCK_INITIAL_RETRY_MILLIS = 200;
    private static final long LOCK_MAX_RETRY_MILLIS = 1_000;

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT =
            createReleaseLockScript();

    /**
     * 诊断简历
     * @param resumeId
     * @return
     */
    @Override
    public DiagnosisResponse diagnose(Long resumeId,boolean refresh) {

        //参数校验
        validateResumeId(resumeId);

        //查询简历
        Resume resume = loadDiagnosableResume(resumeId);

        LockHandle lock = acquireLockOrReadCache(resumeId, refresh);

        if (lock.cachedJson() != null) {
            //反序列化
            return deserializeCachedResponse(lock.cachedJson(), resumeId);
        }

        String lockKey = CacheKeys.diagnoseLock(resumeId);



        try{
            if(refresh){
                deleteCachedResponse(resumeId);
            }else{
                //双重检查
                String cachedJson = readCachedJson(resumeId);
                if (cachedJson != null) {
                    return deserializeCachedResponse(cachedJson, resumeId);
                }
            }

            String rawJson = aiService.chat(
                    PromptTemplates.DIAGNOSIS_SYSTEM,
                    PromptTemplates.diagnosisUser(resume.getRawText())
            );

            DiagnosisResult result = parseAndValidateResult(rawJson);

            return saveAndBuildResponse(resume, result);
        }finally {
            releaseLock(lockKey, lock.lockToken());
        }

    }

    /**
     * 诊断简历-流式
     * @param resumeId
     * @return
     */
    @Override
    public Flux<String> diagnoseStream(Long resumeId,boolean refresh){




        //参数校验
        validateResumeId(resumeId);

        Resume resume = loadDiagnosableResume(resumeId); // 权限校验
        LockHandle lock = acquireLockOrReadCache(resumeId, refresh);

        if (lock.cachedJson() != null) {
            return Flux.just(lock.cachedJson()); // 直接返回缓存，不走向下逻辑
        }

        String lockKey = CacheKeys.diagnoseLock(resumeId);
        String lockToken = lock.lockToken();


        if (refresh) {
            deleteCachedResponse(resumeId);
        } else {
            String cachedJson = readCachedJson(resumeId);
            if (cachedJson != null) {
                releaseLock(lockKey, lockToken); //释放锁
                return Flux.just(cachedJson);
            }
        }

        StringBuilder buffer = new StringBuilder();

        try{
            //Flux.defer 保证里面的 aiService.chatStream() 是等到真正有人订阅（subscribe）这个 Flux 时才会执行。
            return Flux.defer(()-> aiService.chatStream(
                    PromptTemplates.DIAGNOSIS_SYSTEM,
                    PromptTemplates.diagnosisUser(resume.getRawText())))
                    //大模型每吐出一个小片段（一个 token 或一句话），就把它追加到 buffer 里。
                    .doOnNext(buffer::append)
                    .doOnComplete(()->{
                        try {
                            DiagnosisResult result = parseAndValidateResult(buffer.toString());
                            saveAndBuildResponse(resume, result);

                            log.info(
                                    "流式诊断完成, resumeId={}, totalScore={}",
                                    resumeId,
                                    result.getTotalScore()
                            );
                        } catch (Exception e) {
                            log.error(
                                    "处理流式诊断结果失败, resumeId={}",
                                    resumeId,
                                    e
                            );
                        }
                    })
                    .onErrorResume(error -> {
                        log.error("流式诊断失败, resumeId={}", resumeId, error);
                        return Flux.just(
                                "{\"error\":\"诊断失败，请稍后重试\"}"
                        );
                    })
                    //释放锁
                    .doFinally(signal -> releaseLock(lockKey, lockToken));
        }catch (RuntimeException e) {
            releaseLock(lockKey, lockToken);
            throw e;
        }
    }

    /**
     * 把大模型计算出的结果写入数据库
     * @param resumeId
     * @param userId
     * @param total
     * @param comp
     * @param expr
     * @param match
     * @param suggestions
     */
    private void saveDiagnosis(Long resumeId,Long userId, double total, double comp, double expr, double match, List<String> suggestions) {
        Diagnosis diagnosis = Diagnosis.builder()
                .resumeId(resumeId)
                .userId(userId)
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
     * 统一解析和校验
     * @param rawJson
     * @return
     */
    private DiagnosisResult parseAndValidateResult(String rawJson) {
        String json = extractJson(rawJson);

        DiagnosisResult result;
        try {
            result = objectMapper.readValue(json, DiagnosisResult.class);
        } catch (Exception e) {
            log.warn("解析 AI 诊断 JSON 失败: {}", e.getMessage());
            throw new ServiceUnavailableException(
                    "AI 诊断结果格式异常，请稍后重试",
                    e
            );
        }

        if (result == null) {
            throw new ServiceUnavailableException(
                    "AI 没有返回有效的诊断结果"
            );
        }

        result.setTotalScore(requireScore(
                "totalScore",
                result.getTotalScore()
        ));
        result.setCompletenessScore(requireScore(
                "completenessScore",
                result.getCompletenessScore()
        ));
        result.setExpressionScore(requireScore(
                "expressionScore",
                result.getExpressionScore()
        ));
        result.setMatchScore(requireScore(
                "matchScore",
                result.getMatchScore()
        ));
        result.setSuggestions(
                normalizeSuggestions(result.getSuggestions())
        );

        return result;
    }

    /**
     * 校验和修正分数
     * @param fieldName
     * @param score
     * @return
     */
    private double requireScore(String fieldName, Double score) {
        if (score == null || !Double.isFinite(score)) {
            throw new ServiceUnavailableException(
                    "AI 返回的诊断结果缺少有效字段：" + fieldName
            );
        }

        if (score < 0 || score > 100) {
            log.warn(
                    "AI 返回分数超出范围, field={}, score={}",
                    fieldName,
                    score
            );
        }

        return Math.max(0.0, Math.min(100.0, score));
    }

    /**
     * 清洗建议列表
     * @param suggestions
     * @return
     */
    private List<String> normalizeSuggestions(List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return List.of();
        }

        return suggestions.stream()
                .filter(Objects::nonNull)                     // 1. 过滤掉 null
                .map(String::trim)                            // 2. 去掉首尾空格
                .filter(suggestion -> !suggestion.isBlank())  // 3. 过滤掉全是空格的空串
                .distinct()                                   // 4. 去重（大模型容易重复生成）
                .limit(5)                                     // 5. 截断，最多只保留 5 条
                .toList();                                    // 6. 转为不可变 List
    }

    /**
     * 提取JSOn
     * @param rawJson
     * @return
     */
    private String extractJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new ServiceUnavailableException(
                    "AI 诊断结果为空，请稍后重试"
            );
        }

        String cleaned = rawJson.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        cleaned = cleaned.trim();

        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');

        if (start < 0 || end <= start) {
            throw new ServiceUnavailableException(
                    "AI 诊断结果格式异常，请稍后重试"
            );
        }

        return cleaned.substring(start, end + 1);
    }

    /**
     * 参数校验
     */
    private void validateResumeId(Long resumeId) {
        if (resumeId == null || resumeId <= 0) {
            throw new IllegalArgumentException("无效的简历ID: " + resumeId);
        }
    }

    private DiagnosisResponse saveAndBuildResponse(Resume resume,
                                                   DiagnosisResult result) {
        List<String> suggestions = result.getSuggestions();

        saveDiagnosis(
                resume.getId(),
                resume.getUserId(),
                result.getTotalScore(),
                result.getCompletenessScore(),
                result.getExpressionScore(),
                result.getMatchScore(),
                suggestions
        );

        DiagnosisResponse response = new DiagnosisResponse(
                resume.getId(),
                round(result.getTotalScore()),
                colorized(
                        result.getCompletenessScore(),
                        "信息完整度"
                ),
                colorized(
                        result.getExpressionScore(),
                        "表达质量"
                ),
                colorized(
                        result.getMatchScore(),
                        "岗位匹配度"
                ),
                suggestions
        );

        cacheResponse(resume.getId(), response);
        return response;
    }

    /**
     * 写入缓存
     * @param resumeId
     * @param response
     */
    private void cacheResponse(Long resumeId,
                               DiagnosisResponse response) {
        try {
            //随机过期时间
            int expireMinutes =
                    ThreadLocalRandom.current().nextInt(30, 61);

            stringRedisTemplate.opsForValue().set(
                    CacheKeys.diagnose(resumeId),
                    objectMapper.writeValueAsString(response),// 序列化为 JSON 字符串
                    expireMinutes,
                    TimeUnit.MINUTES
            );

            log.info(
                    "诊断结果缓存成功, resumeId={}, expireMinutes={}",
                    resumeId,
                    expireMinutes
            );
        } catch (Exception e) {
            //静默降级
            log.warn("写入诊断缓存失败, resumeId={}", resumeId, e);
        }
    }

    /**
     *
     * 获取锁或读取缓存
     * @param resumeId
     * @param refresh
     * @return
     */
    private LockHandle acquireLockOrReadCache(Long resumeId,boolean refresh) {
        String lockKey = CacheKeys.diagnoseLock(resumeId);
        String lockToken = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + LOCK_WAIT_TIMEOUT_MILLIS;

        boolean waitedForAnotherRequest = false;
        while(System.currentTimeMillis() < deadline) {
            if(!refresh||waitedForAnotherRequest) {
                String cachedJson = readCachedJson(resumeId);
                if (cachedJson != null) {
                    return new LockHandle(null,cachedJson);
                }
            }

            LockState state = tryAcquireLock(lockKey, lockToken);

            if (state == LockState.ACQUIRED) {
                return new LockHandle(lockToken, null);// 拿到锁，返回 token，去干活
            }
            if (state == LockState.BYPASSED) {
                return new LockHandle(null, null); // 降级：Redis 挂了，直接放行
            }

            waitedForAnotherRequest = true;
            waitWhileLocked(lockKey, resumeId, refresh, deadline);

            if (refresh) {
                String cachedJson = readCachedJson(resumeId);
                if (cachedJson != null) {
                    return new LockHandle(null, cachedJson);
                }
            }


        }
        throw new ServiceUnavailableException(
                "诊断任务正在处理中，请稍后重试"
        );
    }

    /**
     * 等待其他请求完成
     * @param lockKey
     * @param resumeId
     * @param refresh
     * @param deadline
     */
    private void waitWhileLocked(String lockKey, Long resumeId, boolean refresh, long deadline) {
        long retryInterval = LOCK_INITIAL_RETRY_MILLIS;

        while (System.currentTimeMillis() < deadline
                && isLockHeld(lockKey)) {

            if (!refresh) {
                String cachedJson = readCachedJson(resumeId);
                if (cachedJson != null) {
                    return;
                }
            }

            sleepForLock(retryInterval);
            retryInterval = Math.min(
                    LOCK_MAX_RETRY_MILLIS,
                    retryInterval + LOCK_INITIAL_RETRY_MILLIS
            );
        }
    }

    /**
     * 获取锁
     * @param lockKey
     * @param lockToken
     * @return
     */
    private LockState tryAcquireLock(String lockKey, String lockToken) {
        try {
            Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(lockKey,
                    lockToken,
                    LOCK_EXPIRE_SECONDS,
                    TimeUnit.SECONDS);
            return Boolean.TRUE.equals(success)
                    ? LockState.ACQUIRED
                    : LockState.BUSY;
        }catch (Exception e) {
            log.warn(
                    "获取诊断锁失败，降级为无锁执行, lockKey={}",
                    lockKey,
                    e
            );
            return LockState.BYPASSED;
        }
    }

    /**
     * 查询锁的状态
     * @param lockKey
     * @return
     */
    private boolean isLockHeld(String lockKey) {
        try {
            return Boolean.TRUE.equals(
                    stringRedisTemplate.hasKey(lockKey)
            );
        } catch (Exception e) {
            log.warn("查询诊断锁状态失败, lockKey={}", lockKey, e);
            return false;
        }
    }

    /**
     * 安全释放锁
     */
    private void releaseLock(String lockKey, String lockToken) {
        if (lockKey == null || lockToken == null) {
            return;
        }

        try {
            stringRedisTemplate.execute(
                    RELEASE_LOCK_SCRIPT,
                    List.of(lockKey),
                    lockToken
            );
        } catch (Exception e) {
            log.warn("释放诊断锁失败, lockKey={}", lockKey, e);
        }
    }

    /**
     * 读取缓存
     */
    private String readCachedJson(Long resumeId){
        String key = CacheKeys.diagnose(resumeId);

        String cachedJson;
        try {
            cachedJson = stringRedisTemplate.opsForValue().get(key);
        }catch (Exception e) {
            log.warn("读取诊断缓存失败, resumeId={}", resumeId, e);
            return null;
        }

        if (cachedJson == null) {
            return null;
        }

        try {
            objectMapper.readValue(cachedJson, DiagnosisResponse.class);
            return cachedJson;
        } catch (Exception e) {
            log.warn(
                    "诊断缓存格式损坏，删除后重新生成, resumeId={}",
                    resumeId,
                    e
            );
            deleteCachedResponse(resumeId);
            return null;
        }
    }

    /**
     * 删除缓存
     * @param resumeId
     */
    private void deleteCachedResponse(Long resumeId) {
        try {
            stringRedisTemplate.delete(CacheKeys.diagnose(resumeId));
        } catch (Exception e) {
            log.warn("删除诊断缓存失败, resumeId={}", resumeId, e);
        }
    }

    /**
     * 反序列化缓存
     * @param cachedJson
     * @param resumeId
     * @return
     */
    private DiagnosisResponse deserializeCachedResponse(String cachedJson,
                                                        Long resumeId) {
        try {
            log.info("命中诊断缓存, resumeId={}", resumeId);
            return objectMapper.readValue(
                    cachedJson,
                    DiagnosisResponse.class
            );
        } catch (Exception e) {
            deleteCachedResponse(resumeId);
            throw new ServiceUnavailableException(
                    "诊断缓存读取失败，请稍后重试",
                    e
            );
        }
    }

    /**
     * 等待时处理线程中断
     * @param millis
     */
    private void sleepForLock(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException(
                    "诊断等待被中断，请稍后重试",
                    e
            );
        }
    }




//    /**
//     * 处理诊断完成（和阻塞版逻辑完全一致）
//     *
//     * @param resumeId 简历ID
//     * @param fullJson 完整的 JSON 字符串
//     */
//    private void handleDiagnosisComplete(Long resumeId, Long userId,String fullJson) {
//        try {
//            // 1. 解析 JSON 为诊断结果
//            DiagnosisResult result = objectMapper.readValue(fullJson, DiagnosisResult.class);
//            if (result == null) {
//                log.error("诊断结果解析为null, resumeId={}", resumeId);
//                return;
//            }
//            // 2. 处理建议列表（确保不为null）
//            List<String> suggestions = result.getSuggestions();
//            if (suggestions == null || suggestions.isEmpty()) {
//                suggestions = Collections.emptyList();
//            }
//
//            // 3. 保存诊断结果到数据库
//            saveDiagnosis(
//                    resumeId,
//                    userId,
//                    result.getTotalScore(),
//                    result.getCompletenessScore(),
//                    result.getExpressionScore(),
//                    result.getMatchScore(),
//                    suggestions
//            );
//
//            // 4. 组装诊断报告（用于缓存）
//            DiagnosisResponse response = new DiagnosisResponse(
//                    resumeId,
//                    round(result.getTotalScore()),
//                    colorized(result.getCompletenessScore(), "信息完整度"),
//                    colorized(result.getExpressionScore(), "表达质量"),
//                    colorized(result.getMatchScore(), "岗位匹配度"),
//                    suggestions
//            );
//
//            // 5. 缓存诊断结果到 Redis
//            String key = CACHE_KEY_PREFIX + resumeId;
//            try {
//                String json = objectMapper.writeValueAsString(response);
//                int expireTime = 30 + random.nextInt(31);
//                stringRedisTemplate.opsForValue().set(
//                        key,
//                        json,
//                        expireTime,
//                        TimeUnit.MINUTES
//                );
//                log.info("诊断结果缓存成功, resumeId={}, 过期时间={}分钟", resumeId, expireTime);
//            } catch (Exception e) {
//                log.warn("缓存写入失败, resumeId={}", resumeId, e);
//            }
//
//            log.info("流式诊断处理完成, resumeId={}, 总分={}", resumeId, result.getTotalScore());
//
//        } catch (Exception e) {
//            log.error("处理流式诊断结果失败, resumeId={}", resumeId, e);
//            throw new RuntimeException("处理诊断结果失败", e);
//        }
//    }

    private Resume loadDiagnosableResume(Long resumeId) {
        Resume resume = resumeService.findById(resumeId);

        if ("REVIEW".equals(resume.getOcrStatus())) {
            throw new IllegalArgumentException(
                    "图片文字识别置信度较低，请先人工核对解析内容"
            );
        }

        String text = resume.getRawText();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("简历内容为空，无法诊断");
        }

        return resume;
    }

    private static DefaultRedisScript<Long> createReleaseLockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(
                "scripts/diagnose-lock-release.lua"
        ));
        script.setResultType(Long.class);
        return script;
    }

    private record LockHandle(String lockToken, String cachedJson) {
    }

    private enum LockState {
        ACQUIRED,
        BUSY,
        BYPASSED
    }
}
