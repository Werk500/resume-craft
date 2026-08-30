package com.resumecraft.server.job.service;

import com.resumecraft.server.job.domain.Job;

import java.util.List;

public interface JobService {
    Job createJob(Job job);

    List<Job> findAll();

    Job findById(Long id);
}
