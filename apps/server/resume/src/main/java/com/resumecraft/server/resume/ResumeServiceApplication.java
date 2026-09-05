package com.resumecraft.server.resume;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 简历服务启动类（Step 3 独立服务）。
 * 聚合：上传解析 / AI 诊断 / 一键优化 + 内部接口（供 Feign）。
 */
@SpringBootApplication(scanBasePackages = {"com.resumecraft.server"})
// annotationClass=Mapper.class：只注册带 @Mapper 注解的接口，避免把 Service 接口误当 Mapper
@MapperScan(basePackages = "com.resumecraft.server", annotationClass = Mapper.class)
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.resumecraft.server.common.feign")
public class ResumeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResumeServiceApplication.class, args);
    }
}
