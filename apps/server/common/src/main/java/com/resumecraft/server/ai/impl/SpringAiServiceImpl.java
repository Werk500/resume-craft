package com.resumecraft.server.ai.impl;

import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.common.metrics.AiCallType;
import com.resumecraft.server.common.metrics.AiObservability;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
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
    @Resource
    private AiObservability aiObservability;

    @Value("${app.ai.timeout.chat-seconds:60}")
    private long chatTimeoutSeconds;
    @Value("${app.ai.timeout.stream-seconds:60}")
    private long streamTimeoutSeconds;
    @Value("${app.ai.timeout.ocr-seconds:120}")
    private long ocrTimeoutSeconds;
    @Value("${app.ai.retry.max-attempts:2}")
    private int maxAttempts;
    @Value("${spring.ai.openai.chat.options.model:unknown}")
    private String modelName;

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
                AiCallType.CHAT,
                systemPrompt,
                userPrompt,
                () -> CompletableFuture.supplyAsync(() ->
                        {
                            ChatResponse response = chatClient.prompt()
                                    .system(systemPrompt)
                                    .user(userPrompt)
                                    .call()
                                    .chatResponse();
                            if (response.getMetadata() != null && response.getMetadata().getUsage() != null ) {
                                Usage usage = response.getMetadata().getUsage();
                                aiObservability.recordToken(
                                        AiCallType.CHAT,modelName,
                                        usage.getPromptTokens(), usage.getCompletionTokens()
                                );
                            }
                            return response.getResult().getOutput().getText();
                        },
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
            // 用 chatResponse() 而非 content()：前者保留每个 chunk 的 metadata，
            // 从而能读到 token 用量（流式场景的用量通常在最后一个 chunk 上）。
            //
            // 计时说明：Flux 是惰性的，方法返回时流尚未开始执行，
            // 因此用 doOnSubscribe 起表、doOnComplete / doOnError 收官。
            // 用数组持有可变状态，因为 lambda 只能捕获 effectively final 的变量。
//            doOnSubscribe  → 订阅时开始计时
//            doOnNext       → 每个 chunk 记 Token
//            map            → ChatResponse 转 String
//            doOnNext       → 打日志
//            timeout        → 超时控制
//            doOnComplete   → 完成记 success
//            doOnError      → 失败记 timeout/failure
//            onErrorResume  → 错误降级为文本

            final long[] startNanos = new long[1];

            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .stream()           // 流式调用
                    .chatResponse()
                    .doOnSubscribe(sub -> startNanos[0] = System.nanoTime())
                    .doOnNext(response -> {
                        // 每个 chunk 都尝试读取：流式响应只在最后一个 chunk 带 usage
                        if (response.getMetadata() != null && response.getMetadata().getUsage() != null ) {
                            Usage usage = response.getMetadata().getUsage();
                            aiObservability.recordToken(
                                    AiCallType.CHAT_STREAM,modelName,
                                    usage.getPromptTokens(), usage.getCompletionTokens()
                            );
                        }
                    })
                    .mapNotNull(response -> response.getResult().getOutput().getText())
                    .doOnNext(chunk -> log.debug("收到流式片段: {}", chunk))
                    .timeout(Duration.ofSeconds(streamTimeoutSeconds))  // 总超时控制
                    // doOnComplete 与 doOnError 是互斥的终止信号，各记录一次
                    // ⚠️ 不要用 doOnTerminate：它在 error 之后也会触发，会把 failure 覆盖成 success
                    .doOnComplete(() -> {
                        aiObservability.recordDuration(AiCallType.CHAT_STREAM,"success",
                                Duration.ofNanos(System.nanoTime() - startNanos[0]));
                        log.info("流式AI调用完成");
                    })
                    .doOnError(error -> {
                        // Reactor 的 timeout 操作符抛出 java.util.concurrent.TimeoutException
                        String outcome = (error instanceof java.util.concurrent.TimeoutException)
                                ? "timeout" : "failure";
                        aiObservability.recordDuration(AiCallType.CHAT_STREAM, outcome,
                                Duration.ofNanos(System.nanoTime() - startNanos[0]));
                        log.error("流式AI调用失败: {}", error.getMessage());
                    })
                    .onErrorResume(error -> {
                        log.error("流式AI调用出错，已降级为错误提示");
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
                AiCallType.OCR,
                ocrBlockSystem,
                "<image bytes, length=" + imageBytes.length + ">",
                () -> CompletableFuture.supplyAsync(() ->
                        {
                            ChatResponse response = chatClient.prompt()
                                    .system(ocrBlockSystem)
                                    .user(u -> u.text("请识别这张简历图片中的文字，并按系统提示返回结构化 JSON")
                                            .media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(imageBytes)))
                                    .call()
                                    .chatResponse();
                            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                                Usage usage = response.getMetadata().getUsage();
                                aiObservability.recordToken(
                                        AiCallType.OCR, modelName,
                                        usage.getPromptTokens(), usage.getCompletionTokens());
                            }

                            return response.getResult().getOutput().getText();
                        },
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
    private String executeWithRetry(AiCallType callType,
                                    String systemPrompt,
                                    String userPrompt,
                                    Supplier<CompletableFuture<String>> task,
                                    long timeoutSeconds){
        RuntimeException lastFailure = null;
        long start = System.nanoTime();

        for (int attempt = 0; attempt <= maxAttempts; attempt++) {
            try {

                String result = task.get().get(timeoutSeconds, TimeUnit.SECONDS);
                aiObservability.recordDuration(callType,"success",
                        Duration.ofNanos(System.nanoTime() - start));
                return result;
            } catch (InterruptedException e) {
                // 中断：立刻停止，不重试（保留中断标志）
                Thread.currentThread().interrupt();
                // 中断也要计入指标：否则指标总量会小于实际发起量
                aiObservability.recordDuration(callType, "interrupted",
                        Duration.ofNanos(System.nanoTime() - start));
                throw new RuntimeException("AI调用被中断", e);

            } catch (ExecutionException e) {
                lastFailure = new RuntimeException("AI调用失败: " + rootMessage(e), e.getCause());
                log.warn("[{}] 第 {}/{} 次调用失败（ExecutionException）：{}",
                        callType.tag(), attempt, maxAttempts, rootMessage(e));

            } catch (TimeoutException e) {
                lastFailure = new RuntimeException("AI服务响应超时，请稍后重试", e);
                log.warn("[{}] 第 {}/{} 次调用超时（{} 秒）",
                        callType.tag(), attempt, maxAttempts, timeoutSeconds);
            }

            if (attempt == maxAttempts) {
                break;
            }

            // 重试计数：attempt 从 0 开始，指标里从 1 开始更符合直觉
            aiObservability.recordRetry(callType, attempt + 1);

            long sleepMs = 300L * attempt;
            log.info("[{}] {} ms 后进行第 {} 次重试", callType.tag(), sleepMs, attempt + 1);
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("重试等待被中断", ie);
            }
        }

        String outcome = (lastFailure != null && lastFailure.getCause() instanceof TimeoutException) ? "timeout" : "failure";

        aiObservability.recordDuration(callType,outcome,Duration.ofNanos(System.nanoTime() - start));
        log.error("[{}] 已重试 {} 次，全部失败，systemPrompt={}, userPrompt={}",
                callType.tag(), maxAttempts, truncateLog(systemPrompt), truncateLog(userPrompt));
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
