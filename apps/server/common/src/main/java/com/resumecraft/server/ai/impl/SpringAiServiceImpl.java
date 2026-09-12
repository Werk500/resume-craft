package com.resumecraft.server.ai.impl;

import com.resumecraft.server.ai.AiService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.function.Supplier;


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

    @Value("${app.ai.timeout.chat-seconds:60}")
    private long chatTimeoutSeconds;
    @Value("${app.ai.timeout.stream-seconds:60}")
    private long streamTimeoutSeconds;
    @Value("${app.ai.timeout.ocr-seconds:120}")
    private long ocrTimeoutSeconds;
    @Value("${app.ai.retry.max-attempts:2}")
    private int maxAttempts;

    public SpringAiServiceImpl() {
        log.info("===== 已启用 [Spring AI] 真实大模型 =====");
    }

    /**
     * 阻塞式调用：返回完整响应文本
     * 使用 CompletableFuture + 超时控制，适合非流式场景
     */
    @Override
    public String chat(String systemPrompt, String userPrompt) {

        return executeWithRetry(
                "chat",
                systemPrompt,
                userPrompt,
                () -> CompletableFuture.supplyAsync(() ->
                                chatClient.prompt()
                                        .system(systemPrompt)
                                        .user(userPrompt)
                                        .call()
                                        .content(),
                        aiExecutor),
                chatTimeoutSeconds
        );
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
                    .timeout(Duration.ofSeconds(streamTimeoutSeconds))  // 总超时控制
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

        return executeWithRetry(
                "ocr",
                ocrBlockSystem,
                "<image bytes, length=" + imageBytes.length + ">",
                () -> CompletableFuture.supplyAsync(() ->
                                chatClient.prompt()
                                        .system(ocrBlockSystem)
                                        .user(u -> u.text("请识别这张简历图片中的文字，并按系统提示返回结构化 JSON")
                                                .media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(imageBytes)))
                                        .call()
                                        .content(),
                        aiExecutor),
                ocrTimeoutSeconds
        );
    }

    // ============================================================
    // 通用重试执行器
    //
    // 对 ExecutionException / TimeoutException 做最多 maxAttempts 次尝试；
    // 每次重试之间 Thread.sleep(retryBaseBackoffMs * attempt)。
    // 全部失败再抛业务 RuntimeException。
    // ============================================================
    private String executeWithRetry(String scene,
                                    String systemPrompt,
                                    String userPrompt,
                                    Supplier<CompletableFuture<String>> task,
                                    long timeoutSeconds){
        RuntimeException lastFailure = null;

        for (int attempt = 0; attempt <= maxAttempts; attempt++) {
            try {
                return task.get().get(timeoutSeconds, TimeUnit.SECONDS);

            } catch (InterruptedException e) {
                // 中断：立刻停止，不重试（保留中断标志）
                Thread.currentThread().interrupt();
                throw new RuntimeException("AI调用被中断", e);

            } catch (ExecutionException e) {
                lastFailure = new RuntimeException("AI调用失败: " + rootMessage(e), e.getCause());
                log.warn("[{}] 第 {}/{} 次调用失败（ExecutionException）：{}",
                        scene, attempt, maxAttempts, rootMessage(e));

            } catch (TimeoutException e) {
                lastFailure = new RuntimeException("AI服务响应超时，请稍后重试", e);
                log.warn("[{}] 第 {}/{} 次调用超时（{} 秒）",
                        scene, attempt, maxAttempts, timeoutSeconds);
            }

            if (attempt == maxAttempts) {
                break;
            }


            long sleepMs = 300L * attempt;
            log.info("[{}] {} ms 后进行第 {} 次重试", scene, sleepMs, attempt + 1);
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("重试等待被中断", ie);
            }
        }

        log.error("[{}] 已重试 {} 次，全部失败，systemPrompt={}, userPrompt={}",
                scene, maxAttempts, truncateLog(systemPrompt), truncateLog(userPrompt));
        throw lastFailure != null
                ? lastFailure
                : new RuntimeException("AI调用失败，未知原因");
    }


    /** 取异常的根因 message，便于日志 */
    private String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() != null ? cur.getMessage() : cur.getClass().getSimpleName();
    }

    /**
     * 截断日志字符串，避免日志过长
     */
    private String truncateLog(String text) {
        if (text == null) return "null";
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}
