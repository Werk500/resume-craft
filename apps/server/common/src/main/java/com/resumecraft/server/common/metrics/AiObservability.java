package com.resumecraft.server.common.metrics;


import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * AI 调用可观测性入口。
 *
 * <h3>为什么集中到一个 Bean 而不是各处直接调 MeterRegistry</h3>
 * 指标名一旦分散在多个类里，很容易出现拼写不一致（ai.tokens / ai.token），
 * 导致 Prometheus 面板上出现两套指标。集中定义可以保证命名与标签的一致性，
 * 也便于后续统一调整（例如加公共标签）。
 *
 * <h3>标签基数控制</h3>
 * 所有标签值都来自有限枚举（调用类型、结果、模型名），不会无限增长。
 * 刻意不把 prompt 内容、用户 ID 等作为标签——那会造成指标基数爆炸。
 */

@Slf4j
@Component
public class AiObservability {

    @Resource
    private MeterRegistry registry;

    /** 记录一次 AI 调用耗时（同时隐含调用次数与 outcome） */
    public void recordDuration(AiCallType type,String outcome, Duration duration) {
        Timer.builder("ai.duration")
                .description("AI call latency")
                .tag("type", type.tag())
                .tag("outcome",outcome)
                .publishPercentiles(0.5,0.95,0.99)
                .register(registry)
                .record(duration);
    }

    /** 记录 token 消耗；参数为 null 时跳过（部分模型或流式响应不上报用量） */
    public void recordToken(AiCallType type,String model,Integer promptTokens,Integer completionTokens){
        if(promptTokens != null){
            counter("ai.tokens", "AI token usage", type, model, "prompt").increment(promptTokens);
        }
        if (completionTokens != null) {
            counter("ai.tokens", "AI token usage", type, model, "completion").increment(completionTokens);
        }
        if (completionTokens != null || promptTokens != null) {
            int total = (promptTokens == null ? 0 : promptTokens)
                    + (completionTokens == null ? 0 : completionTokens);
            counter("ai.tokens", "AI token usage", type, model, "total").increment(total);
        }
    }


    /** 记录一次重试（attempt 为第几次重试，从 1 开始） */
    public void recordRetry(AiCallType type, int attempt) {
        Counter.builder("ai.retries")
                .description("AI call retry count")
                .tag("type", type.tag())
                .tag("attempt", String.valueOf(attempt))
                .register(registry)
                .increment();
    }

    /** 记录一次降级（from=退化的来源，to=退化后的兜底方式） */
    public void recordDegradation(String from, String to) {
        Counter.builder("ai.degradations")
                .description("AI capability degradation count")
                .tag("from", from)
                .tag("to", to)
                .register(registry)
                .increment();
    }

    /** 记录 embedding 向量缓存命中情况 */
    public void recordEmbeddingCache(boolean hit) {
        Counter.builder("ai.cache.requests")
                .description("Embedding vector cache hit/miss")
                .tag("cache", "vector_embedding")
                .tag("outcome", hit ? "hit" : "miss")
                .register(registry)
                .increment();
    }


    private Counter counter(String name, String desc, AiCallType type, String model, String kind) {

        return Counter.builder(name)
                .description(desc)
                .tag("type", type.tag())
                .tag("model",model == null ? "unknown" :model)
                .tag("kind",kind)
                .register(registry);
    }
}
