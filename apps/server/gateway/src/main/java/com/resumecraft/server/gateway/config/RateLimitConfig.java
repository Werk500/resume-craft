package com.resumecraft.server.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Configuration
public class RateLimitConfig {
    //按客户端IP限流
    @Bean
    public KeyResolver ipKeyResolver() {
        //Mono.just(x) 表示"一个立即返回 x 的异步流"。
        return exchange -> Mono.just(
                Objects.requireNonNull(exchange.getRequest()//拿请求
                        .getRemoteAddress()//拿远端地址
                        .getAddress()//拿IP地址
                        .getHostAddress()
                ));
    }
}
