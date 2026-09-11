package com.resumecraft.server.common.cache;

/**
 * 统一缓存 key 构造与命名空间。
 *
 * <p>本机 Redis 的 db0 可能与其他项目共用，所有 key 统一加应用前缀
 * {@link #PREFIX}，避免不同项目 key 冲突；历史无前缀 key 会随 TTL 自然过期。
 */
public final class CacheKeys {

    /** 应用级命名空间前缀 */
    public static final String PREFIX = "resume-craft:";

    private CacheKeys() {
    }

    // ---------------- 诊断 ----------------

    public static String diagnose(Long resumeId) {
        return PREFIX + "diagnose:" + resumeId;
    }

    public static String diagnoseNull(Long resumeId) {
        return PREFIX + "diagnose:null:" + resumeId;
    }

    public static String diagnoseLock(Long resumeId) {
        return PREFIX + "diagnose:lock:" + resumeId;
    }

    // ---------------- 优化 ----------------

    public static String optimize(Long resumeId, String targetJob) {
        String jobKey = (targetJob == null || targetJob.trim().isEmpty()) ? "general" : targetJob.trim();
        return PREFIX + "optimize:" + resumeId + ":" + jobKey;
    }

    public static String targetedOptimize(Long resumeId, Long jobId) {
        return PREFIX + "targeted-optimize:" + resumeId + ":" + jobId;
    }

    // ---------------- 匹配 ----------------

    public static String match(Long resumeId, Long jobId) {
        return match(resumeId, jobId, null);
    }

    public static String match(Long resumeId, Long jobId, Long versionId) {
        if (versionId == null) {
            return PREFIX + "match:" + resumeId + ":" + jobId;
        }
        return PREFIX + "match:" + resumeId + ":" + jobId + ":v" + versionId;
    }

    // ---------------- JD 分析 ----------------

    public static String analyze(Long jobId) {
        return PREFIX + "analyze:" + jobId;
    }

    // ---------------- 按简历维度失效（供 keys(pattern) 使用） ----------------

    public static String optimizePattern(Long resumeId) {
        return PREFIX + "optimize:" + resumeId + ":*";
    }

    public static String targetedOptimizePattern(Long resumeId) {
        return PREFIX + "targeted-optimize:" + resumeId + ":*";
    }

    public static String matchPattern(Long resumeId) {
        return PREFIX + "match:" + resumeId + ":*";
    }
}
