package com.resumecraft.server.ai;

import com.resumecraft.server.ai.impl.DashScopeEmbeddingServiceImpl;
import com.resumecraft.server.ai.impl.NoopEmbeddingServiceImpl;
import com.resumecraft.server.common.metrics.AiObservability;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量化能力装配。
 *
 * 注意：这里只注册业务侧的 EmbeddingService，不涉及 Spring AI 的 EmbeddingModel。
 * job-match 的 spring.ai.model.embedding=none 已关闭框架自动装配，两者互不干扰。
 */
@Configuration
public class EmbeddingConfig {

    @Bean
    @ConditionalOnMissingBean(EmbeddingService.class)
    public EmbeddingService embeddingService(
            @Value("${app.embedding.enabled:true}") boolean enabled,
            @Value("${app.embedding.api-key:}") String apiKey,
            @Value("${app.embedding.base-url:https://dashscope.aliyuncs.com/compatible-mode}") String baseUrl,
            @Value("${app.embedding.model:text-embedding-v3}") String model,
            @Value("${app.embedding.dimensions:1024}") int dimensions,
            @Value("${app.embedding.timeout-ms:5000}") int timeoutMs,AiObservability aiObservability) {

        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return new NoopEmbeddingServiceImpl();
        }
        return new DashScopeEmbeddingServiceImpl(apiKey, baseUrl, model, dimensions, timeoutMs,aiObservability);
    }
}
