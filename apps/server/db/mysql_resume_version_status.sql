-- ============================================================================
-- 优化版本状态机（Human-in-the-loop）
--
-- 背景：AI 优化简历时会产出两类"需要用户拍板"的东西——
--   1) pendingSkills：改写稿里新增、但原文找不到依据的技能（FabricationGuard 判定）
--   2) pendingClaims：把经历说得比原文更重（OverstatementChecker 判定）
-- 在加入状态机之前，用户点不点确认都不影响结果：版本照样能导出、能投递。
-- 加入之后：带待确认项的版本必须确认过才能导出/投递。
--
-- 状态约定：
--   有待确认项 -> DRAFT；确认完成 -> CONFIRMED；
--   没有待确认项 -> 创建时直接 CONFIRMED。
-- 兼容：历史数据若 status 为空且 pending_json 为空，仍按“无需确认”处理。
--
-- 执行方式：mysql -uroot -p resume_craft < mysql_resume_version_status.sql
-- ============================================================================

ALTER TABLE resume_version
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT=待用户确认 / CONFIRMED=已确认（可导出、可投递）',
    ADD COLUMN confirmed_at DATETIME NULL
        COMMENT '用户确认时间',
    ADD COLUMN pending_json TEXT NULL
        COMMENT '版本生成时的待确认项快照(JSON: {skills:[], claims:[]})，确认接口据此校验"每一项都处置过"';
