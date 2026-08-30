package com.resumecraft.server.ai.impl;

import com.resumecraft.server.ai.AiService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * Spring AI 真实现：走 OpenAI 兼容协议（DeepSeek/通义/Kimi/GLM 均可）。
 * app.ai.mock=false 时激活。
 * ChatClient 单例由 AiConfig 提供，此处直接注入。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.ai.mock", havingValue = "false")
public class SpringAiServiceImpl implements AiService {

    @Resource
    private ChatClient chatClient;

    public SpringAiServiceImpl() {
        log.info("===== 已启用 [Spring AI] 真实大模型 =====");
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {


        CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
            chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content()
        );

        try {
            return future.get(30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("AI调用被中断", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("AI调用失败", e.getCause());
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("AI调用超时, systemPrompt={}, userPrompt={}",
                    systemPrompt.substring(0, Math.min(50, systemPrompt.length())),
                    userPrompt.substring(0, Math.min(50, userPrompt.length())));
            throw new RuntimeException("AI服务响应超时，请稍后重试", e);
        }


    }
}
