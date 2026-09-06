package com.resumecraft.server.job.service;

import com.resumecraft.server.job.domain.JdAnalysis;
import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.job.dto.JobPageResult;

import java.util.List;

public interface JobService {
    Job createJob(Job job);

    List<Job> findAll();

    Job findById(Long id);

    JdAnalysis analyzeJob(Long jobId);

    /**
     * 分页搜索职位
     * @param company
     * @param keyword
     * @param page
     * @param size
     * @return
     */
    JobPageResult search(String company, String keyword, int page, int size);
}
