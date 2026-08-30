package com.resumecraft.server.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resumecraft.server.job.service.JobService;
import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.job.domain.JobMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class JobServiceImpl implements JobService {

    @Resource
    private JobMapper jobMapper;

    /**
     * 创建职位
     */
    @Override
    public Job createJob(Job job) {
        jobMapper.insert(job);
        return job;
    }

    /**
     * 查询所有职位
     * @return
     */
    @Override
    public List<Job> findAll() {
        LambdaQueryWrapper<Job> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(Job::getId);
        return jobMapper.selectList(queryWrapper);
    }

    @Override
    public Job findById(Long id) {
        Job job = jobMapper.selectById(id);
        if (job == null) {
            throw new IllegalArgumentException("岗位不存在：id=" + id);  // GlobalExceptionHandler 自动转 400
        }
        return job;
    }
}
