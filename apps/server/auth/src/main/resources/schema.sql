-- ============================================================
-- AI简历设计与优化软件 - 建表脚本
-- 兼容 H2 (MODE=MySQL)，可平滑迁移到 MySQL（语法一致）
-- 由 Spring Boot 自动执行（内嵌库默认执行 classpath:schema.sql）
-- ============================================================

-- 简历解析记录（数据归属：用户）
CREATE TABLE IF NOT EXISTS resume (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL COMMENT '归属用户，关联 sys_user.id',
    file_name    VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_path    VARCHAR(500) NOT NULL COMMENT '存储路径',
    file_type    VARCHAR(20)  NOT NULL COMMENT 'pdf/docx/png...',
    parsed_name  VARCHAR(100) COMMENT '解析出的姓名',
    parsed_email VARCHAR(100) COMMENT '解析出的邮箱',
    parsed_phone VARCHAR(50)  COMMENT '解析出的电话',
    raw_text     TEXT         COMMENT '解析出的全文',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_resume_user (user_id)
);

-- 简历优化版本（数据归属：用户）
CREATE TABLE IF NOT EXISTS resume_version (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT       NOT NULL COMMENT '归属用户，关联 sys_user.id',
    resume_id         BIGINT       NOT NULL COMMENT '关联 resume.id',
    version_name      VARCHAR(200) NOT NULL COMMENT '版本名称',
    target_job        VARCHAR(200) COMMENT '目标岗位',
    optimized_content TEXT         COMMENT '优化后内容',
    match_score       DOUBLE       COMMENT '匹配度得分',
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_version_user (user_id)
);

-- AI 诊断报告（数据归属：用户）
CREATE TABLE IF NOT EXISTS diagnosis (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT NOT NULL COMMENT '归属用户，关联 sys_user.id',
    resume_id          BIGINT NOT NULL COMMENT '关联 resume.id',
    total_score        DOUBLE COMMENT '综合分',
    completeness_score DOUBLE COMMENT '信息完整度',
    expression_score   DOUBLE COMMENT '表达质量',
    match_score        DOUBLE COMMENT '岗位匹配度',
    suggestions        TEXT   COMMENT '改进建议',
    create_time        DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_diagnosis_user (user_id)
);

-- 岗位信息（共享数据，不归属用户）
CREATE TABLE IF NOT EXISTS job (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company      VARCHAR(100) NOT NULL COMMENT '公司',
    title        VARCHAR(200) NOT NULL COMMENT '岗位名称',
    department   VARCHAR(100) COMMENT '部门',
    location     VARCHAR(100) COMMENT '地点',
    salary_range VARCHAR(100) COMMENT '薪资范围',
    description  TEXT         COMMENT '岗位描述/JD',
    requirements TEXT         COMMENT '任职要求',
    source_url   VARCHAR(500) COMMENT '来源链接',
    UNIQUE KEY uk_source_url (source_url),
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 匹配度结果（数据归属：用户）
CREATE TABLE IF NOT EXISTS match_result (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                BIGINT NOT NULL COMMENT '归属用户，关联 sys_user.id',
    resume_id              BIGINT NOT NULL COMMENT '关联 resume.id',
    job_id                 BIGINT NOT NULL COMMENT '关联 job.id',
    overall_score          DOUBLE COMMENT '综合匹配度 0~100',
    keyword_coverage       DOUBLE COMMENT '关键词覆盖度',
    semantic_similarity    DOUBLE COMMENT '语义相似度',
    hard_requirement_score DOUBLE COMMENT '硬性条件达标分',
    match_explanation      TEXT   COMMENT '匹配解释（可解释归因）',
    create_time            DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_match_user (user_id)
);

-- 投递记录（数据归属：用户）
CREATE TABLE IF NOT EXISTS application_record (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT      NOT NULL COMMENT '归属用户，关联 sys_user.id',
    resume_version_id BIGINT      NOT NULL COMMENT '关联 resume_version.id',
    job_id            BIGINT      NOT NULL COMMENT '关联 job.id',
    applied_at        DATE        NOT NULL COMMENT '投递日期',
    channel           VARCHAR(50) COMMENT '投递渠道',
    status            VARCHAR(30) NOT NULL COMMENT 'pending/interviewing/rejected/no_response/accepted',
    notes             VARCHAR(500) COMMENT '备注',
    create_time       DATETIME    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_application_user (user_id)
);

-- 用户账号（M5）
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL COMMENT '登录名',
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文，绝不存明文',
    nickname    VARCHAR(50)  COMMENT '昵称',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);
