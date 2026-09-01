package com.resumecraft.server.job.controller;


import com.resumecraft.server.common.ApiResponse;
import com.resumecraft.server.job.domain.JdAnalysis;
import com.resumecraft.server.job.service.JobService;
import com.resumecraft.server.job.domain.Job;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/job")
public class JobController {
    @Resource
    private JobService jobService;


    /**
     * 新建职位
     * @param job
     * @return
     */
    @PostMapping
    public ApiResponse<Job> createJob(@RequestBody Job job) {
        Job createdJob = jobService.createJob(job);
        return ApiResponse.ok(createdJob);
    }

    /**
     * 获取职位列表
     * @return
     */
    @GetMapping
    public ApiResponse<List<Job>> findAll() {
        List<Job> jobs = jobService.findAll();
        return ApiResponse.ok(jobs);
    }

    /**
     * 根据ID获取职位详情
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public ApiResponse<Job> findById(@PathVariable Long id){
        return ApiResponse.ok(jobService.findById(id));
    }


    /**
     * AI分析岗位
     * @param id
     * @return
     */
    @GetMapping("/{id}/analyze")
    public ApiResponse<JdAnalysis> analyseJob(@PathVariable Long id){
        return ApiResponse.ok(jobService.analyzeJob(id));
    }
}
