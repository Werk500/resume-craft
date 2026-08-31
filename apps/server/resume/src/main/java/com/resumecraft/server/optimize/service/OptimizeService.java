package com.resumecraft.server.optimize.service;

import com.resumecraft.server.resume.domain.ResumeVersion;

/**
 * 一键优化服务。
 */
public interface OptimizeService {

    /**
     * AI 优化简历。
     *
     * @param resumeId  简历 id
     * @param targetJob 目标岗位（可为 null/空 = 通用优化）
     * @return 保存后的优化版本
     */
    ResumeVersion optimize(Long resumeId, String targetJob);
}
