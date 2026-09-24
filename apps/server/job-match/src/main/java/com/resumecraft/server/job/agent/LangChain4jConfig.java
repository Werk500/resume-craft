package com.resumecraft.server.job.agent;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.time.Duration;

/**
 * LangChain4j 模型配置。
 *
 * <h3>为什么单独配一个模型 Bean，不复用 Spring AI 的 ChatClient</h3>
 * 两者是独立的框架抽象：AiServices 需要 LangChain4j 自己的 ChatLanguageModel，
 * 无法直接使用 Spring AI 的 ChatClient。好在都是 OpenAI 兼容协议，
 * 可以指向同一个服务商（DeepSeek），配置项复用同一批环境变量。
 *
 * <h3>为什么不用 langchain4j-spring-boot-starter</h3>
 * 该 starter 截至 1.20.0 仍是 beta 版本。核心库已 GA，
 * 手工装配一个 Bean 反而更可控，也不会引入额外的自动配置行为。
 *
 * <h3>两个 Bean 的分工</h3>
 * {@code agentChatModel} 供同步接口（/api/v1/agent/chat）使用，可整轮重试；
 * {@code agentStreamingChatModel} 供 SSE 流式接口使用，逐个 token 推给前端。
 */
@Slf4j
@Configuration
public class LangChain4jConfig {

    @Bean
    public ChatModel agentChatModel(
            @Value("${AI_API_KEY:}") String apiKey,
            @Value("${AI_BASE_URL:https://api.deepseek.com}") String baseUrl,
            @Value("${AI_MODEL:deepseek-chat}") String modelName,
            @Value("${app.agent.timeout-seconds:60}") long timeoutSeconds,
            @Value("${app.agent.max-retries:1}") int maxRetries){
        log.info("===== 已启用 [LangChain4j] Agent 调度，model={} =====", modelName);

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)
                .temperature(0.2)
                .logRequests(false)// 请求体含简历内容，不记日志
                .logResponses(false)
                .build();
    }

    /**
     * 流式模型。
     *
     * <p>注意这里<b>没有</b> {@code .maxRetries(...)}：{@code OpenAiStreamingChatModel}
     * 的 Builder 不提供该方法（只有非流式的 {@code OpenAiChatModel} 才有）。
     * 这是刻意的——流式响应一旦已经吐出一部分 token，重试就会把内容重复推给前端，
     * 所以「重试」要么放在吐出第一个 token 之前，要么交给用户重新发起。
     * 断流风险改由网关/HTTP 层兜底，不在这里配置。
     */
    @Bean
    public StreamingChatModel agentStreamingChatModel(
            @Value("${AI_API_KEY:}") String apiKey,
            @Value("${AI_BASE_URL:https://api.deepseek.com}") String baseUrl,
            @Value("${AI_MODEL:deepseek-chat}") String modelName,
            @Value("${app.agent.timeout-seconds:60}") long timeoutSeconds) {

        log.info("===== 已启用 [LangChain4j] Agent 流式输出（SSE），model={} =====", modelName);

        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .temperature(0.2)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

}
