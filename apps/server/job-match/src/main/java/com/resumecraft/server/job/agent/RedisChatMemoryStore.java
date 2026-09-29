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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 求职助手的会话记忆存储。
 *
 * <h3>为什么主存储用 Redis</h3>
 * 记忆要跨请求、跨实例共享（同一个用户换一台机器接着聊），还要有 TTL
 * 自动过期——记忆里可能含简历片段，24h 后自动消失算隐私最小化。
 *
 * <h3>为什么降级兜底不能返回「空记忆」（踩过的坑）</h3>
 * 最初 Redis 挂了/熔断打开时，{@code getMessages} 兜底返回 {@code List.of()}，
 * 看起来"无记忆继续对话"很温柔，实际会<b>直接打断整轮对话</b>：
 * LangChain4j 的 AiServices 每执行完一次工具调用，都会<b>重新从记忆里读一遍消息</b>
 * 来拼第二轮请求（记忆才是它的事实来源）。记忆读到空列表 → 发给模型的
 * {@code messages} 是空数组 → DeepSeek 兼容接口直接拒绝：
 * {@code messages cannot be null or empty}。
 *
 * <p>实测现象：Redis 停机 → 熔断打开（3 次失败即开，开 30s）→ 这段时间里
 * 每个"会调工具"的会话都在第二轮失败，且工具帧已经发出去过，用户看到的是
 * "搜完了但回答没了"；30s 后熔断半开自动恢复，故障自己消失——最难查的就是这种。
 *
 * <h3>解决：进程内镜像</h3>
 * 降级期间改用本进程的 LRU 镜像承接读写：同一个实例上的一轮对话能正常走完
 * （工具轮重建请求时读得到刚写进去的消息），Redis 恢复后自动回到共享存储。
 * 代价是降级期间的会话不再跨实例共享、重启即丢——对"对话能不能聊完"来说值得。
 *
 * <p>镜像按会话条数封顶（{@value #MIRROR_MAX_SESSIONS}），避免长期运行把它变成内存泄漏。
 */
@Slf4j
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {
    /** 会话记忆的滑动过期时间；记忆里可能含简历片段，24h 也算隐私最小化 */
    private static final Duration TTL = Duration.ofHours(24);

    /** 本地镜像最多保留多少个会话；超出后按最近最少使用淘汰 */
    private static final int MIRROR_MAX_SESSIONS = 200;

    /**
     * 降级镜像：只在本进程内可见，Redis 正常时它只是缓存副本。
     *
     * <p>用 accessOrder=true 的 LinkedHashMap 做 LRU：被读到的会话移到队尾，
     * 淘汰发生在队首。
     */
    private final Map<String, List<ChatMessage>> mirror = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<ChatMessage>> eldest) {
                    return size() > MIRROR_MAX_SESSIONS;
                }
            });

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @CircuitBreaker(name = "agentMemory", fallbackMethod = "getMessagesFallback")
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {

        String key = CacheKeys.agentMemory(String.valueOf(memoryId));
        String json = stringRedisTemplate.opsForValue().get(key);

        if (json == null || json.isBlank()) {
            // Redis 里没有：可能是真的没聊过，也可能是刚才降级期间只写进了镜像。
            // 对调用方来说两者都要能继续对话，所以优先给镜像。
            return mirrorOf(key);
        }

        try {
            List<ChatMessage> messages = ChatMessageDeserializer.messagesFromJson(json);
            mirror.put(key, List.copyOf(messages));
            return messages;
        } catch (Exception e) {
            log.warn("Agent 记忆反序列化失败，回退本地镜像: memoryId={}, err={}", memoryId, e.getMessage());
            return mirrorOf(key);
        }
    }

    @CircuitBreaker(name = "agentMemory", fallbackMethod = "updateMessagesFallback")
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = CacheKeys.agentMemory(String.valueOf(memoryId));
        mirror.put(key, List.copyOf(messages));
        stringRedisTemplate.opsForValue().set(key, ChatMessageSerializer.messagesToJson(messages), TTL);
    }

    @CircuitBreaker(name = "agentMemory", fallbackMethod = "deleteMessagesFallback")
    @Override
    public void deleteMessages(Object memoryId) {
        String key = CacheKeys.agentMemory(String.valueOf(memoryId));
        mirror.remove(key);
        stringRedisTemplate.delete(key);
    }

    /**
     * Redis 不可用或熔断已打开时的兜底：读本地镜像。
     *
     * <p>注意这里<b>不能</b>返回 {@code List.of()}——工具轮会拿它重建请求，
     * 空列表会让模型收到 {@code messages cannot be null or empty}（见类注释）。
     */
    private List<ChatMessage> getMessagesFallback(Object memoryId, Throwable t) {
        logDegrade("读取", memoryId, t);
        return mirrorOf(CacheKeys.agentMemory(String.valueOf(memoryId)));
    }

    /**
     * updateMessages 的兜底。
     *
     * <p>返回类型必须与目标方法一致（void），否则 Resilience4j 在解析 fallback 时
     * 找不到匹配方法，会直接抛异常——降级反而变成故障。
     */
    private void updateMessagesFallback(Object memoryId, List<ChatMessage> messages, Throwable t) {
        logDegrade("写入", memoryId, t);
        mirror.put(CacheKeys.agentMemory(String.valueOf(memoryId)), List.copyOf(messages));
    }

    /** deleteMessages 的兜底（同上，void 方法必须有 void fallback） */
    private void deleteMessagesFallback(Object memoryId, Throwable t) {
        logDegrade("删除", memoryId, t);
        mirror.remove(CacheKeys.agentMemory(String.valueOf(memoryId)));
    }

    /** 取镜像副本；返回可写列表，避免调用方改动影响到镜像本身 */
    private List<ChatMessage> mirrorOf(String key) {
        List<ChatMessage> cached = mirror.get(key);
        return cached == null ? List.of() : new ArrayList<>(cached);
    }

    private void logDegrade(String action, Object memoryId, Throwable t) {
        if (t instanceof CallNotPermittedException) {
            log.debug("Agent 记忆{}被熔断跳过，改用本地镜像: memoryId={}", action, memoryId);
        } else {
            log.warn("Agent 记忆{}失败，改用本地镜像继续: memoryId={}, err={}", action, memoryId, t.getMessage());
        }
    }
}
