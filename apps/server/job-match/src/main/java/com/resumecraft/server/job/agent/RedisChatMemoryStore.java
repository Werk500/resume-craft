package com.resumecraft.server.job.agent;

import com.resumecraft.server.common.cache.CacheKeys;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {
    /** 会话记忆的滑动过期时间；记忆里可能含简历片段，24h 也算隐私最小化 */
    private static final Duration TTL = Duration.ofHours(24);

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        try {
            String json = stringRedisTemplate.opsForValue()
                    .get(CacheKeys.agentMemory(String.valueOf(memoryId)));

            if(json == null || json.isBlank()) {
                return List.of();
            }

            return ChatMessageDeserializer.messagesFromJson(json);
        } catch (Exception e) {
            log.warn("Agent 记忆读取失败，按空记忆继续: memoryId={}, err={}", memoryId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {

        try {
            stringRedisTemplate.opsForValue().set(
                    CacheKeys.agentMemory(String.valueOf(memoryId)),
                    ChatMessageSerializer.messagesToJson(messages),
                    TTL);
        } catch (Exception e) {
            log.warn("Agent 记忆写入失败: memoryId={}, err={}", memoryId, e.getMessage());
        }

    }

    @Override
    public void deleteMessages(Object memoryId) {
        try {
            stringRedisTemplate.delete(CacheKeys.agentMemory(String.valueOf(memoryId)));
        } catch (Exception e) {
            log.warn("Agent 记忆删除失败: memoryId={}, err={}", memoryId, e.getMessage());
        }
    }
}
