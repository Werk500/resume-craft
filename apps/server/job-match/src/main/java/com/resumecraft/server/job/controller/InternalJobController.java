package com.resumecraft.server.job.controller;


import com.resumecraft.server.common.dto.JobBriefDTO;
import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.job.domain.JobMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/internal/job")
public class InternalJobController {

    @Resource
    private JobMapper jobMapper;

    @GetMapping("/{id}")
    public JobBriefDTO getJob(@PathVariable Long id) {
        Job job = jobMapper.selectById(id);
        if (job == null) {
            throw new IllegalArgumentException("岗位不存在: " + id);
        }

        JobBriefDTO dto = new JobBriefDTO();
        dto.setId(job.getId());
        dto.setCompany(job.getCompany());
        dto.setTitle(job.getTitle());
        dto.setLocation(job.getLocation());
        return dto;
    }
}
