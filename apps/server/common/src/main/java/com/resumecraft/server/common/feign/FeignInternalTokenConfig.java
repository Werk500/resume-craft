package com.resumecraft.server.common.feign;


import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 内部服务调用自动添加 Token 配置
 *
 * 作用：当微服务 A 通过 Feign 调用微服务 B 的 /internal/** 接口时，
 * 自动在请求头中携带 X-Internal-Token，无需手动添加。
 */
@Configuration
public class FeignInternalTokenConfig {

    /**
     * 从配置文件读取内部 token
     * 配置项：app.internal-token
     */
    @Value("${app.internal-token:}")
    private String internalToken;

    /**
     * 请求头名称（必须和 InternalTokenFilter 中定义的一致）
     */
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    /**
     * traceId 请求头（必须和 TraceIdFilter / TraceIdGlobalFilter 保持一致）
     */
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * MDC 中 traceId 的 key（必须和 TraceIdFilter.TRACE_ID 一致）
     */
    private static final String TRACE_ID_MDC_KEY = "traceId";


    /**
     * 创建 Feign 请求拦截器
     *
     * 每次 Feign 发起 HTTP 请求时，都会经过这个拦截器，
     * 自动在请求头中添加 X-Internal-Token
     */
    @Bean
    public RequestInterceptor internalTokenRequestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                // 如果配置了 token，则添加到请求头
                if (internalToken != null && !internalToken.isEmpty()) {
                    template.header(INTERNAL_TOKEN_HEADER, internalToken);
                }

                //traceId:从MDC取，透传给下游服务
                String traceId = MDC.get(TRACE_ID_MDC_KEY);
                if (traceId != null && !traceId.isEmpty()) {
                    template.header(TRACE_ID_HEADER, traceId);
                }
            }
        };

    }
}
