package com.resumecraft.server.job.match;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class JobKeywordProvider {

    /** JD 不常改，TTL 给长一点；内容变了 hash 会变，旧 key 自然过期，不需要手动清理 */
    private static final Duration TTL = Duration.ofDays(30);

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource private AiService aiService;
    @Resource private ObjectMapper objectMapper;

    public List<String> keywords(Job job){
        String text = job.getTitle() + "\n" + job.getDescription() + "\n" + job.getRequirements();
        String key = CacheKeys.jdKeywords(job.getId(), sha256(text));

        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if(cached != null){
                return objectMapper.readValue(cached, new TypeReference<List<String>>(){});
            }
        } catch (Exception e) {
            log.warn("JD 关键词缓存读取失败，跳过缓存: {}", e.getMessage());
        }

        List<String> keywords = extractByAi(job);

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
                List<String> keywords = new ArrayList<>();
                for (JsonNode node : keywordsNode) {
                    String keyword = node.asText().trim();
                    if (!keyword.isEmpty()) {
                        keywords.add(keyword);
                    }
                }
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
        Set<String> keywords = new HashSet<>();

        String text = (job.getTitle() + " " +
                job.getDescription() + " " +
                job.getRequirements()).toLowerCase();

        // 常见技术关键词库（可扩展）
        String[] commonKeywords = {
                "java", "python", "go", "rust", "c++", "javascript", "typescript",
                "spring", "springboot", "spring boot", "mybatis", "hibernate",
                "mysql", "postgresql", "redis", "mongodb", "elasticsearch",
                "docker", "kubernetes", "k8s", "jenkins", "git", "linux",
                "微服务", "分布式", "高并发", "消息队列", "kafka", "rabbitmq",
                "vue", "react", "angular", "html", "css", "前端", "后端",
                "数据分析", "机器学习", "ai", "人工智能", "算法"
        };

        for (String kw : commonKeywords) {
            if (text.contains(kw)) {
                keywords.add(kw);
            }
        }

        // 如果提取到的关键词太少，加一些默认词
        if (keywords.size() < 3) {
            keywords.add("java");
            keywords.add("spring");
            keywords.add("mysql");
        }

        return new ArrayList<>(keywords);
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
