package com.resumecraft.server.job.match;

/**
 * 匹配上下文：语义评分走向量检索时需要的标识信息。
 *
 * <p>为什么单独抽一个对象而不是往 execute 上继续加参数：
 * 后续接 RabbitMQ 异步生成嵌入、以及给匹配结果补更多来源标识时，
 * 上下文都会继续膨胀，用对象承接比参数列表更好维护。
 *
 * @param resumeId  简历 ID
 * @param versionId 优化版本 ID；null 表示主简历
 * @param userId    简历归属用户，写入 resume_embedding 时需要
 */
public record MatchContext(Long resumeId, Long versionId, Long userId) {

    /** 主简历语义：不携带版本与用户信息 */
    public static MatchContext mainResume(Long resumeId) {
        return new MatchContext(resumeId, null, null);
    }
}
