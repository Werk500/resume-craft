package com.resumecraft.server.application;


import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.resumecraft.server"})
// annotationClass=Mapper.class：只注册带 @Mapper 注解的接口，避免把 Service 接口误当 Mapper
@MapperScan(basePackages = "com.resumecraft.server", annotationClass = Mapper.class)
@EnableDiscoveryClient          // 启用服务注册与发现，让服务能注册到 Nacos
@EnableFeignClients(basePackages = "com.resumecraft.server.common.feign")  // 启用 Feign 客户端，如果这个服务需要调用其他服务
public class ApplicationServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApplicationServerApplication.class, args);
    }
}
