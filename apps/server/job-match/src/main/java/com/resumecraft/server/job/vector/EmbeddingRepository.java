package com.resumecraft.server.job.vector;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * pgvector 读写仓储（走独立的 PostgreSQL 数据源）。
 *
 * <h3>两个关键约定</h3>
 * 1. <b>向量入参一律传字符串并显式 {@code ::vector} 强转</b>：
 *    PostgreSQL 驱动不认识 vector 类型，无法直接绑定 float[]。
 * 2. <b>出参用 {@code embedding::text} 转字符串再自行解析</b>：
 *    驱动同样不会把 vector 映射成 Java 数组，拿到的原始形态是 "[0.1,0.2,...]" 文本。
 */
@Repository
public class EmbeddingRepository {

    private final JdbcTemplate vectorJdbcTemplate;

    /**
     * 必须用显式构造器：项目里没有 lombok.config，
     * {@code @RequiredArgsConstructor} 不会把字段上的 {@code @Qualifier} 复制到构造器参数，
     * 而容器里存在多个 JdbcTemplate 候选，会造成注入歧义。
     */
    public EmbeddingRepository(@Qualifier("vectorJdbcTemplate") JdbcTemplate vectorJdbcTemplate) {
        this.vectorJdbcTemplate = vectorJdbcTemplate;
    }

    // ----------------------------------------------------------- 简历向量

    public String selectResumeVector(Long resumeId, Long versionId) {
        return vectorJdbcTemplate.query(
                "SELECT embedding::text FROM resume_embedding WHERE resume_id = ? AND version_id = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                resumeId, versionId);
    }

    public String selectResumeHash(Long resumeId, Long versionId) {
        return vectorJdbcTemplate.query(
                "SELECT content_hash FROM resume_embedding WHERE resume_id = ? AND version_id = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                resumeId, versionId);
    }

    /**
     * 幂等写入：同一 (resume_id, version_id) 已存在则覆盖。
     * 依赖表上的唯一约束 uk_resume_version，重复消息/重复调用都不会产生脏数据。
     */
    public int upsertResumeVector(Long resumeId, Long versionId, Long userId,
                                  String hash, String vector, String model) {
        return vectorJdbcTemplate.update("""
                INSERT INTO resume_embedding
                    (resume_id, version_id, user_id, content_hash, embedding, model)
                VALUES (?, ?, ?, ?, ?::vector, ?)
                ON CONFLICT (resume_id, version_id) DO UPDATE
                   SET content_hash = EXCLUDED.content_hash,
                       embedding    = EXCLUDED.embedding,
                       model        = EXCLUDED.model,
                       update_time  = CURRENT_TIMESTAMP
                """, resumeId, versionId, userId, hash, vector, model);
    }

    /**
     * 删除某份简历的全部向量（主简历 + 所有优化版本）。
     * 简历删除时调用，避免向量表留下孤儿数据。
     *
     * @return 实际删除行数
     */
    public int deleteResumeVectors(Long resumeId) {
        return vectorJdbcTemplate.update(
                "DELETE FROM resume_embedding WHERE resume_id = ?", resumeId);
    }

    // ----------------------------------------------------------- 岗位向量

    public String selectJobVector(Long jobId) {
        return vectorJdbcTemplate.query(
                "SELECT embedding::text FROM job_embedding WHERE job_id = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                jobId);
    }

    public String selectJobHash(Long jobId) {
        return vectorJdbcTemplate.query(
                "SELECT content_hash FROM job_embedding WHERE job_id = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                jobId);
    }

    public int upsertJobVector(Long jobId, String hash, String vector, String model) {
        return vectorJdbcTemplate.update("""
                INSERT INTO job_embedding (job_id, content_hash, embedding, model)
                VALUES (?, ?, ?::vector, ?)
                ON CONFLICT (job_id) DO UPDATE
                   SET content_hash = EXCLUDED.content_hash,
                       embedding    = EXCLUDED.embedding,
                       model        = EXCLUDED.model,
                       update_time  = CURRENT_TIMESTAMP
                """, jobId, hash, vector, model);
    }

    /** 自检用：确认向量库连通 */
    public Integer ping() {
        return vectorJdbcTemplate.queryForObject("SELECT 1", Integer.class);
    }
}
