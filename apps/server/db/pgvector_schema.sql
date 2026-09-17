-- pgvector 向量表（PostgreSQL，与 MySQL 的 resume_craft 是两个库）
-- 用于把"AI 近似打分"升级为"真实向量余弦相似度"

-- 简历向量：主简历 + 各优化版本各存一条
CREATE TABLE IF NOT EXISTS resume_embedding (
    id           BIGSERIAL PRIMARY KEY,
    resume_id    BIGINT       NOT NULL,
    version_id   BIGINT       NOT NULL DEFAULT 0,   -- 0 = 主简历
    user_id      BIGINT       NOT NULL,
    content_hash VARCHAR(64)  NOT NULL,
    embedding    VECTOR(1024) NOT NULL,
    model        VARCHAR(64)  NOT NULL,
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_resume_version UNIQUE (resume_id, version_id)
);

-- 岗位向量
CREATE TABLE IF NOT EXISTS job_embedding (
    id           BIGSERIAL PRIMARY KEY,
    job_id       BIGINT       NOT NULL,
    content_hash VARCHAR(64)  NOT NULL,
    embedding    VECTOR(1024) NOT NULL,
    model        VARCHAR(64)  NOT NULL,
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_job UNIQUE (job_id)
);

-- HNSW 索引：适合小数据量、高召回场景，空表可直接建
CREATE INDEX IF NOT EXISTS idx_resume_vec
    ON resume_embedding USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_job_vec
    ON job_embedding USING hnsw (embedding vector_cosine_ops);

-- 查询辅助索引
CREATE INDEX IF NOT EXISTS idx_resume_user ON resume_embedding (user_id);
CREATE INDEX IF NOT EXISTS idx_resume_hash ON resume_embedding (content_hash);
CREATE INDEX IF NOT EXISTS idx_job_hash    ON job_embedding (content_hash);