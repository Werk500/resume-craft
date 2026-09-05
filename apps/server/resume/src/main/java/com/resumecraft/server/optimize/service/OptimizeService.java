package com.resumecraft.server.optimize.service;

import com.resumecraft.server.optimize.dto.TargetedOptimizeResponse;
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


    /**
     * 定向优化简历（针对目标岗位）
     * @param resumeId
     * @param jobId
     * @return
     */
    TargetedOptimizeResponse targetedOptimize(Long resumeId, Long jobId);

    /** 单段精修：原文段 + 方向 → 改写后文本 */
    String rewrite(String original, String focus);

    /** 保存自定义内容为新版本（精修成果持久化） */
    ResumeVersion saveContent(Long resumeId, String content, String versionName);
    /** 导出版本：支持 docx 和 pdf */
    byte[] exportVersion(Long versionId, String format);  // format: "docx" | "pdf"
}
