package com.resumecraft.server.ai.impl;

import com.resumecraft.server.ai.AiService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
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
    @Resource
    private ThreadPoolTaskExecutor aiExecutor;

    public SpringAiServiceImpl() {
        log.info("===== 已启用 [Spring AI] 真实大模型 =====");
    }

    /**
     * 阻塞式调用：返回完整响应文本
     * 使用 CompletableFuture + 超时控制，适合非流式场景
     */
    @Override
    public String chat(String systemPrompt, String userPrompt) {

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
            chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content(),aiExecutor
        );

        try {
            return future.get(60, TimeUnit.SECONDS);
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

    /**
     * 流式调用：返回 Flux<String> token 序列
     * 直接使用 chatClient.stream()，不做 CompletableFuture 包装
     * 非阻塞，由 Reactor 调度
     */
    @Override
    public Flux<String> chatStream(String systemPrompt, String userPrompt) {
        log.info("开始流式AI调用, systemPrompt={}, userPrompt={}",
                truncateLog(systemPrompt), truncateLog(userPrompt));

        try {
            // Spring AI ChatClient 的 stream() 方法返回 Flux<ChatResponse>
            // 从 ChatResponse 中提取 content 并合并为 Flux<String>
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .stream()           // 流式调用
                    .content()          // 直接返回 Flux<String>
                    .doOnNext(chunk -> log.debug("收到流式片段: {}", chunk))
                    .doOnComplete(() -> log.info("流式AI调用完成"))
                    .doOnError(error -> log.error("流式AI调用失败", error))
                    .timeout(Duration.ofSeconds(60))  // 总超时控制
                    .onErrorResume(error -> {
                        log.error("流式AI调用出错", error);
                        return Flux.just("[错误] AI服务响应异常，请稍后重试");
                    });
        } catch (Exception e) {
            log.error("启动流式AI调用失败", e);
            return Flux.just("[错误] " + e.getMessage());
        }
    }

    @Override
    public String ocrRecognize(byte[] imageBytes, String ocrBlockSystem) {

        if (imageBytes == null || ocrBlockSystem == null) {
            throw new IllegalArgumentException("图片内容为空");
        }

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                chatClient.prompt()
                        .system(ocrBlockSystem)
                        .user(u -> u.text("请识别这张简历图片中的文字，并按系统提示返回结构化 JSON")
                                .media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(imageBytes)))
                        .call()
                        .content(), aiExecutor);

        try {
            return future.get(120,TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("OCR调用被中断", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("OCR调用失败: " + e.getCause().getMessage(), e.getCause());
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("OCR识别超时，请更换更清晰的图片重试", e);
        }
    }

    /**
     * 截断日志字符串，避免日志过长
     */
    private String truncateLog(String text) {
        if (text == null) return "null";
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}
