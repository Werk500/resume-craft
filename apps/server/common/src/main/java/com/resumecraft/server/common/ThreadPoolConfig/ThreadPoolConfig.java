package com.resumecraft.server.common.ThreadPoolConfig;


import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
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

    /**
     * OCR专用线程池
     * @return
     */
    @Bean(name = "ocrExecutor")
    public ThreadPoolTaskExecutor ocrExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 同时最多处理 3 页，避免一次上传占满所有 AI 调用能力
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("ocr-exec-");

        // OCR 是用户正在等待的请求：队列满时快速失败，不继续堆积任务
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // 把 traceId 等 MDC 日志上下文复制到工作线程
        executor.setTaskDecorator(task -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    task.run();
                } finally {
                    MDC.clear();
                }
            };
        });

        executor.initialize();
        return executor;
    }

    }
