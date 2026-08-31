package com.resumecraft.server.common.ThreadPoolConfig;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig {

    /**
     * AI调用专用线程池
     */
    @Bean(name = "aiExecutor")
    public ThreadPoolTaskExecutor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：4
        executor.setCorePoolSize(4);

        // 最大线程数：8
        executor.setMaxPoolSize(8);

        // 队列容量：100
        executor.setQueueCapacity(100);

        // 线程名称前缀（方便日志追踪）
        executor.setThreadNamePrefix("ai-exec-");

        // 拒绝策略：CallerRunsPolicy（池满时由调用线程执行）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 初始化线程池
        executor.initialize();

        return executor;
    }

    }
