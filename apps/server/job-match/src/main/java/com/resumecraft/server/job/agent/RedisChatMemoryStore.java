package com.resumecraft.server.job.agent;

import com.resumecraft.server.common.cache.CacheKeys;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {
    /** 会话记忆的滑动过期时间；记忆里可能含简历片段，24h 也算隐私最小化 */
    private static final Duration TTL = Duration.ofHours(24);

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @CircuitBreaker(name = "agentMemory", fallbackMethod = "getMessagesFallback")
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {

            String json = stringRedisTemplate.opsForValue()
                    .get(CacheKeys.agentMemory(String.valueOf(memoryId)));

            if(json == null || json.isBlank()) {
                return List.of();
            }

        try{
            return ChatMessageDeserializer.messagesFromJson(json);
        } catch (Exception e) {
            log.warn("Agent 记忆读取失败，按空记忆继续: memoryId={}, err={}", memoryId, e.getMessage());
            return List.of();
        }
    }

    @CircuitBreaker(name = "agentMemory", fallbackMethod = "updateMessagesFallback")
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            stringRedisTemplate.opsForValue().set(
                    CacheKeys.agentMemory(String.valueOf(memoryId)),
                    ChatMessageSerializer.messagesToJson(messages),
                    TTL);


    }

    @CircuitBreaker(name = "agentMemory", fallbackMethod = "deleteMessagesFallback")
    @Override
    public void deleteMessages(Object memoryId) {
            stringRedisTemplate.delete(CacheKeys.agentMemory(String.valueOf(memoryId)));
    }

    /** Redis 不可用或熔断已打开时的兜底：按空记忆继续对话 */
    private List<ChatMessage> getMessagesFallback(Object memoryId, Throwable t) {
        logDegrade("读取", memoryId, t);
        return List.of();
    }

    /**
     * updateMessages 的兜底。
     *
     * <p>返回类型必须与目标方法一致（void），否则 Resilience4j 在解析 fallback 时
     * 找不到匹配方法，会直接抛异常——降级反而变成故障。
     */
    private void updateMessagesFallback(Object memoryId, List<ChatMessage> messages, Throwable t) {
        logDegrade("写入", memoryId, t);
    }

    /** deleteMessages 的兜底（同上，void 方法必须有 void fallback） */
    private void deleteMessagesFallback(Object memoryId, Throwable t) {
        logDegrade("删除", memoryId, t);
    }

    private void logDegrade(String action, Object memoryId, Throwable t) {
        if (t instanceof CallNotPermittedException) {
            log.debug("Agent 记忆{}被熔断跳过: memoryId={}", action, memoryId);
        } else {
            log.warn("Agent 记忆{}失败，降级继续: memoryId={}, err={}", action, memoryId, t.getMessage());
        }
    }
}
