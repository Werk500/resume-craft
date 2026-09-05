package com.resumecraft.server.controller;


import com.resumecraft.server.common.dto.ResumeBriefDTO;
import com.resumecraft.server.common.dto.ResumeVersionDTO;
import com.resumecraft.server.resume.domain.Resume;
import com.resumecraft.server.resume.domain.ResumeMapper;
import com.resumecraft.server.resume.domain.ResumeVersion;
import com.resumecraft.server.resume.domain.ResumeVersionMapper;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalResumeController {

    @Resource
    private ResumeMapper resumeMapper;             // 内部接口直接查库，绕过 findById 的归属校验（Feign 无用户上下文）
    @Resource
    private ResumeVersionMapper resumeVersionMapper;

    @GetMapping("/resume/{id}")
    public ResumeBriefDTO getResume(@PathVariable Long id){
        Resume resume = resumeMapper.selectById(id);
        if (resume == null) {
            throw new IllegalArgumentException("简历不存在: " + id);
        }

        ResumeBriefDTO resumeBriefDTO = new ResumeBriefDTO();
        resumeBriefDTO.setId(resume.getId());
        resumeBriefDTO.setUserId(resume.getUserId());
        resumeBriefDTO.setFileName(resume.getFileName());
        resumeBriefDTO.setFileType(resume.getFileType());
        resumeBriefDTO.setParsedName(resume.getParsedName());
        resumeBriefDTO.setParsedEmail(resume.getParsedEmail());
        resumeBriefDTO.setParsedPhone(resume.getParsedPhone());
        resumeBriefDTO.setRawText(resume.getRawText());

        return resumeBriefDTO;
    }

    @GetMapping("/version/{id}")
    public ResumeVersionDTO getVersion(@PathVariable Long id){
        ResumeVersion resumeVersion = resumeVersionMapper.selectById(id);
        if (resumeVersion == null){
            throw new RuntimeException("版本不存在");
        }

        ResumeVersionDTO versionDTO = ResumeVersionDTO.builder()
                .id(resumeVersion.getId())
                .resumeId(resumeVersion.getResumeId())
                .userId(resumeVersion.getUserId())
                .versionName(resumeVersion.getVersionName())
                .targetJob(resumeVersion.getTargetJob())
                .optimizedContent(resumeVersion.getOptimizedContent())
                .matchScore(resumeVersion.getMatchScore())
                .createTime(resumeVersion.getCreateTime())
                .build();

        return versionDTO;
    }
}
