package com.resumecraft.server.job.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.job.domain.Job;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JD 关键词 Provider 的缓存行为。
 *
 * <p>这层是「评分可复现」的前提：同一个岗位只要内容不变，就必须永远返回同一份关键词。
 * 同时它挂着两条降级路径（Redis 不可用、AI 抽取失败），都不能影响主流程。
 */
@ExtendWith(MockitoExtension.class)
class JobKeywordProviderTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AiService aiService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private JobKeywordProvider provider;

    private Job job() {
        return Job.builder()
                .id(9L)
                .company("字节跳动")
                .title("后端开发工程师")
                .description("负责电商业务后台研发")
                .requirements("熟悉 Java、Spring Boot")
                .build();
    }

    @Test
    @DisplayName("缓存命中：直接返回缓存，不再调 AI（这是分数可复现的关键）")
    void shouldReturnCachedKeywordsWithoutCallingAi() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("[\"Java\",\"Redis\"]");

        List<String> keywords = provider.keywords(job());

        assertThat(keywords).containsExactly("Java", "Redis");
        verify(aiService, never()).chat(anyString(), anyString());
    }

    @Test
    @DisplayName("缓存未命中：调 AI 抽取并按 30 天 TTL 写回")
    void shouldExtractAndCacheOnMiss() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(aiService.chat(eq(PromptTemplates.EXTRACT_KEYWORDS_SYSTEM), anyString()))
                .thenReturn("{\"keywords\":[\"Java\",\"MySQL\"]}");

        List<String> keywords = provider.keywords(job());

        assertThat(keywords).containsExactly("Java", "MySQL");
        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("Redis 不可用：跳过缓存直接走 AI，不抛异常")
    void shouldFallbackToAiWhenRedisUnavailable() {
        when(stringRedisTemplate.opsForValue())
                .thenThrow(new RedisConnectionFailureException("redis down"));
        when(aiService.chat(eq(PromptTemplates.EXTRACT_KEYWORDS_SYSTEM), anyString()))
                .thenReturn("{\"keywords\":[\"Java\"]}");

        assertThat(provider.keywords(job())).containsExactly("Java");
    }

    @Test
    @DisplayName("AI 返回非法 JSON：走规则兜底，仍能产出关键词")
    void shouldFallbackToRuleKeywordsWhenAiReturnsJunk() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(aiService.chat(eq(PromptTemplates.EXTRACT_KEYWORDS_SYSTEM), anyString()))
                .thenReturn("这不是 JSON");

        List<String> keywords = provider.keywords(job());

        // 规则库里命中 java / spring / spring boot / mysql 等，至少不为空
        assertThat(keywords).isNotEmpty();
        assertThat(keywords).contains("java");
    }
}
