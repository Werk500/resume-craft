package com.resumecraft.server.optimize.service.impl;

import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.optimize.service.OptimizeService;
import com.resumecraft.server.resume.domain.Resume;
import com.resumecraft.server.resume.domain.ResumeVersion;
import com.resumecraft.server.resume.domain.ResumeVersionMapper;
import com.resumecraft.server.resume.service.ResumeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 一键优化实现：查简历 → 拼 prompt → AI 改写 → 存 resume_version。
 */
@Slf4j
@Service
public class OptimizeServiceImpl implements OptimizeService {

    @Resource
    private ResumeService resumeService;

    @Resource
    private AiService aiService;

    @Resource
    private ResumeVersionMapper resumeVersionMapper;

    @Override
    public ResumeVersion optimize(Long resumeId, String targetJob) {
        Resume resume = resumeService.findById(resumeId);
        log.info("开始 AI 优化: resumeId={}, targetJob={}", resumeId, targetJob);

        // 1. AI 改写（返回 Markdown 文本）
        String optimized = aiService.chat(
                PromptTemplates.OPTIMIZE_SYSTEM,
                PromptTemplates.optimizeUser(resume.getRawText(), targetJob));

        // 2. 存为优化版本（createTime 由 MetaObjectHandler 填充）
        ResumeVersion version = ResumeVersion.builder()
                .resumeId(resumeId)
                .versionName("AI优化-v1")
                .targetJob(targetJob)
                .optimizedContent(optimized)
                .build();
        resumeVersionMapper.insert(version);

        log.info("优化版本已保存: id={}, resumeId={}", version.getId(), resumeId);
        return version;
    }
}
