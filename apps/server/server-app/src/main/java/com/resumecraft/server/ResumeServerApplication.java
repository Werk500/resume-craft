package com.resumecraft.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI简历设计与优化软件 - 后端服务入口（模块化单体起步）。
 * 领域包（resume/diagnose/optimize/job/auth）即未来微服务的拆分边界。
 */
@SpringBootApplication
public class ResumeServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResumeServerApplication.class, args);
    }
}