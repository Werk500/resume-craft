package com.resumecraft.server.job.match;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.common.cache.CacheKeys;
import com.resumecraft.server.job.domain.Job;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class JobKeywordProvider {

    /** JD 不常改，TTL 给长一点；内容变了 hash 会变，旧 key 自然过期，不需要手动清理 */
    private static final Duration TTL = Duration.ofDays(30);
    private static final int MAX_KEYWORDS = 30;

    private static final List<String> COMMON_RULE_KEYWORDS = List.of(
            "java", "python", "go", "rust", "c++", "javascript",
            "typescript", "spring", "spring boot", "mybatis",
            "hibernate", "mysql", "postgresql", "redis", "mongodb",
            "elasticsearch", "docker", "kubernetes", "k8s", "jenkins",
            "git", "linux", "微服务", "分布式", "高并发", "消息队列",
            "kafka", "rabbitmq", "vue", "react", "angular", "html",
            "css", "前端", "后端", "数据分析", "机器学习", "ai",
            "人工智能", "算法"
    );

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource private AiService aiService;
    @Resource private ObjectMapper objectMapper;


    public List<String> keywords(Job job){
        String text = buildJobText(job);
        String key = CacheKeys.jdKeywords(job.getId(), sha256(text));

        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if(cached != null){
                List<String> cachedKeywords = objectMapper.readValue(
                        cached,
                        new TypeReference<List<String>>() {}
                );
                List<String> normalized = normalizeKeywords(cachedKeywords);
                if (!normalized.isEmpty()) {
                    return normalized;
                }
            }
        } catch (Exception e) {
            log.warn("JD 关键词缓存读取失败，跳过缓存: {}", e.getMessage());
        }

        List<String> keywords = normalizeKeywords(extractByAi(job));
        if (keywords.isEmpty()) {
            keywords = extractKeywordsByRule(job);
        }

        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(keywords), TTL);
        } catch (Exception e) {
            log.warn("JD 关键词缓存写入失败: {}", e.getMessage());   // 缓存挂了不影响主流程
        }
        return keywords;

    }


    /**
     * AI 从 JD 抽取核心关键词，失败时规则兜底
     */
    private List<String> extractByAi(Job job) {
        try {
            String userPrompt = PromptTemplates.extractKeywords(
                    job.getTitle(),
                    job.getDescription(),
                    job.getRequirements()
            );
            String response = aiService.chat(PromptTemplates.EXTRACT_KEYWORDS_SYSTEM, userPrompt);

            // 解析 JSON
            String jsonStr = response
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();

            JsonNode json = objectMapper.readTree(jsonStr);
            JsonNode keywordsNode = json.get("keywords");

            if (keywordsNode != null && keywordsNode.isArray()) {
                List<String> candidates = new ArrayList<>();
                for (JsonNode node : keywordsNode) {
                    String keyword = node.asText().trim();
                    if (!keyword.isEmpty()) {
                        candidates.add(keyword);
                    }
                }
                List<String> keywords = normalizeKeywords(candidates);
                if (!keywords.isEmpty()) {
                    return keywords;
                }
            }

            log.warn("AI 关键词抽取返回空，使用规则兜底");
            return extractKeywordsByRule(job);

        } catch (Exception e) {
            log.warn("AI 关键词抽取失败，使用规则兜底: {}", e.getMessage());
            return extractKeywordsByRule(job);
        }
    }

    /**
     * 规则兜底：从职位要求和描述中提取关键词
     */
    private List<String> extractKeywordsByRule(Job job) {
        String normalizedText = MatchTextNormalizer.normalize(
                buildJobText(job)
        );

        LinkedHashSet<String> keywords = new LinkedHashSet<>();

        for (String keyword : COMMON_RULE_KEYWORDS) {
            String normalizedKeyword =
                    MatchTextNormalizer.normalizeKeyword(keyword);

            if (MatchTextNormalizer.containsKeyword(
                    normalizedText,
                    normalizedKeyword
            )) {
                keywords.add(normalizedKeyword);
            }
        }

        // 更具体的技能优先，避免同义层级重复放大覆盖率
        if (keywords.contains("spring boot")) {
            keywords.remove("spring");
        }
        if (keywords.contains("kubernetes")) {
            keywords.remove("k8s");
        }

        if (keywords.isEmpty()) {
            String title = MatchTextNormalizer.normalizeKeyword(
                    Objects.requireNonNullElse(job.getTitle(), "")
            );

            if (!title.isBlank()) {
                return List.of(title);
            }
        }

        return keywords.stream()
                .limit(MAX_KEYWORDS)
                .toList();
    }

    private List<String> normalizeKeywords(List<String> rawKeywords) {
        return MatchTextNormalizer.normalizeAndDedupe(rawKeywords)
                .stream()
                .limit(MAX_KEYWORDS)
                .map(MatchTextNormalizer.KeywordTerm::display)
                .toList();
    }

    private String buildJobText(Job job) {
        return String.join(
                "\n",
                Objects.requireNonNullElse(job.getTitle(), ""),
                Objects.requireNonNullElse(job.getDescription(), ""),
                Objects.requireNonNullElse(job.getRequirements(), "")
        );
    }

    /**
     * 将任意一段文字，算成一个固定长度的"指纹"字符串。
     * @param text
     * @return
     */
    private String sha256(String text) {

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // SHA-256 一定存在，走到这里说明环境异常
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
