package com.resumecraft.server.job.match;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.job.domain.MatchResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.simpleframework.xml.util.Match;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 人岗匹配引擎
 * <p>
 * 职责：执行简历与职位的匹配计算
 * - 关键词匹配（40%）
 * - 语义评分（40%）
 * - 硬性条件（20%）
 */
@Slf4j
@Component
public class MatchEngine {


    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private AiService aiService;



    /**
     * 执行匹配计算
     *
     * @param resumeText 简历文本（原文或优化版本）
     * @param job        职位信息
     * @return 匹配结果
     */
    public MatchResult execute(String resumeText, Job job) {

        log.info("开始匹配计算，职位: {}", job.getTitle());

        //1.AI抽取关键词
        List<String> keywords = extractKeywords(job);

        //2.关键词匹配（40%）
        KeywordMatchResult keywordResult = calculateKeywordMatch(resumeText, keywords);
        double keywordScore = keywordResult.getScore();

        //3.语义评分（40%）
        SemanticResult semanticResult = calculateSemanticMatch(resumeText, job);
        double semanticScore = semanticResult.getScore();

        //4.硬性条件（20%）
        HardRequirementResult hardResult = calculateHardRequirements(resumeText, job);
        double hardScore = hardResult.getScore();

        //5.计算总分
        double overall = keywordScore * 0.4 + semanticScore * 0.4 + hardScore * 0.2;

        // 6. 硬性不满足 → 总分上限 40 分
        if (!hardResult.isAllMet()) {
            overall = Math.min(overall, 40);
            log.info("硬性条件不满足，总分上限设为40分，实际: {}", overall);
        }

        overall = Math.round(overall);

        log.info("匹配计算完成，总分: {}", overall);

        return MatchResult.builder()
                .overallScore(overall)
                .keywordCoverage(round(keywordScore))
                .semanticSimilarity(round(semanticScore))
                .hardRequirementScore(round(hardScore))
                .hardRequirementPassed(hardResult.isAllMet())
                .keywordHits(keywordResult.getHits())
                .missingKeywords(keywordResult.getMissingKeywords())
                .matchExplanation(buildMatchExplanation(semanticResult, keywordResult, hardResult))
                .dimensionDetails(buildDimensionDetails(keywordResult, semanticResult, hardResult))
                .build();

    }


    /**
     * AI 从 JD 抽取核心关键词，失败时规则兜底
     */
    private List<String> extractKeywords(Job job) {
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
     * 计算关键词匹配度
     */
    private KeywordMatchResult calculateKeywordMatch(String resumeText, List<String> keywords) {
        // 归一化简历文本
        String normalizedResume = normalizeText(resumeText);

        List<KeywordHit> hits = new ArrayList<>();
        List<String> missingKeywords = new ArrayList<>();

        for (String keyword : keywords) {
            String normalizedKeyword = normalizeKeyword(keyword);
            boolean isHit = normalizedResume.contains(normalizedKeyword);
            hits.add(buildHit(keyword, isHit));
            if (!isHit) {
                missingKeywords.add(keyword);
            }
        }

        int total = keywords.size();
        int hitCount = hits.size() - missingKeywords.size();
        double score = total > 0 ? (double) hitCount / total * 100 : 0;

        return KeywordMatchResult.builder()
                .score(score)
                .total(total)
                .hit(hitCount)
                .hits(hits)
                .missingKeywords(missingKeywords)
                .build();

    }

    /**
     * 归一化文本：大小写、全半角、特殊字符处理
     */
    private String normalizeText(String text) {
        if (text == null) return "";

        String normalized = text
                // 转小写
                .toLowerCase()
                // 全半角转换（仅处理字母数字）
                .replaceAll("Ａ", "a").replaceAll("Ｂ", "b").replaceAll("Ｃ", "c")
                .replaceAll("Ｄ", "d").replaceAll("Ｅ", "e").replaceAll("Ｆ", "f")
                .replaceAll("Ｇ", "g").replaceAll("Ｈ", "h").replaceAll("Ｉ", "i")
                .replaceAll("Ｊ", "j").replaceAll("Ｋ", "k").replaceAll("Ｌ", "l")
                .replaceAll("Ｍ", "m").replaceAll("Ｎ", "n").replaceAll("Ｏ", "o")
                .replaceAll("Ｐ", "p").replaceAll("Ｑ", "q").replaceAll("Ｒ", "r")
                .replaceAll("Ｓ", "s").replaceAll("Ｔ", "t").replaceAll("Ｕ", "u")
                .replaceAll("Ｖ", "v").replaceAll("Ｗ", "w").replaceAll("Ｘ", "x")
                .replaceAll("Ｙ", "y").replaceAll("Ｚ", "z")
                // 数字全半角
                .replaceAll("０", "0").replaceAll("１", "1").replaceAll("２", "2")
                .replaceAll("３", "3").replaceAll("４", "4").replaceAll("５", "5")
                .replaceAll("６", "6").replaceAll("７", "7").replaceAll("８", "8")
                .replaceAll("９", "9")
                // 常见变体统一
                .replaceAll("springboot", "spring boot")
                .replaceAll("spring-boot", "spring boot")
                .replaceAll("spring cloud", "springcloud")
                .replaceAll("\\s+", " ")  // 多个空格合并
                .trim();

        return normalized;
    }

    /**
     * 归一化关键词
     */
    private String normalizeKeyword(String keyword) {
        if (keyword == null) return "";

        return keyword
                .toLowerCase()
                .replaceAll("springboot", "spring boot")
                .replaceAll("spring-boot", "spring boot")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private KeywordHit buildHit(String keyword, boolean hit) {
        return KeywordHit.builder()
                .keyword(keyword)
                .hit(hit)
                .build();
    }

    /**
     * AI 语义匹配评分
     */
    private SemanticResult calculateSemanticMatch(String resumeText, Job job) {
        try {
            String userPrompt = PromptTemplates.semanticMatchUser(
                    resumeText,
                    job.getTitle(),
                    job.getDescription(),
                    job.getRequirements()
            );

            String response = aiService.chat(PromptTemplates.SEMANTIC_MATCH_SYSTEM, userPrompt);

            String jsonStr = response
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();

            JsonNode json = objectMapper.readTree(jsonStr);

            double score = json.has("score") && !json.get("score").isNull()
                    ? json.get("score").asDouble()
                    : 50;

            String reason = json.has("reason") && !json.get("reason").isNull()
                    ? json.get("reason").asText()
                    : "AI 语义分析完成";

            // 限制分数范围
            score = Math.max(0, Math.min(100, score));

            return SemanticResult.builder()
                    .score(score)
                    .reason(reason)
                    .build();

        } catch (Exception e) {
            log.warn("AI 语义评分失败，使用默认分: {}", e.getMessage());
            return SemanticResult.builder()
                    .score(50)
                    .reason("语义评分服务暂时不可用，使用默认分")
                    .build();
        }
    }

    /**
     * 硬性条件匹配
     */
    private HardRequirementResult calculateHardRequirements(String resumeText, Job job) {
        String normalizedResume = normalizeText(resumeText);

        // 解析学历要求
        String educationReq = parseEducationRequirement(job.getRequirements());
        boolean educationMet = checkEducation(normalizedResume, educationReq);

        // 解析年限要求
        String yearReq = parseYearRequirement(job.getRequirements());
        boolean yearMet = checkYear(normalizedResume, yearReq);

        List<String> failedItems = new ArrayList<>();
        if (!educationMet) {
            failedItems.add("学历要求: " + educationReq);
        }
        if (!yearMet) {
            failedItems.add("年限要求: " + yearReq);
        }

        // 硬性得分：都满足 = 100，否则 0（但硬性不满足会触发总分上限 40）
        double score = (educationMet && yearMet) ? 100 : 0;

        return HardRequirementResult.builder()
                .score(score)
                .educationRequirement(educationReq)
                .educationMet(educationMet)
                .yearRequirement(yearReq)
                .yearMet(yearMet)
                .failedItems(failedItems)
                .isAllMet(educationMet && yearMet)
                .build();
    }

    /**
     * 解析学历要求
     */
    private String parseEducationRequirement(String text) {
        if (text == null) return "未指定";

        String lower = text.toLowerCase();
        if (lower.contains("博士") || lower.contains("doctor")) return "博士";
        if (lower.contains("硕士") || lower.contains("研究生") || lower.contains("master")) return "硕士";
        if (lower.contains("本科") || lower.contains("学士") || lower.contains("bachelor")) return "本科";
        if (lower.contains("大专") || lower.contains("专科") || lower.contains("associate")) return "大专";
        if (lower.contains("高中") || lower.contains("high school")) return "高中";

        return "未指定";
    }

    /**
     * 检查学历是否满足
     */
    private boolean checkEducation(String resumeText, String requirement) {
        if ("未指定".equals(requirement)) return true;

        // 学历等级映射
        Map<String, Integer> levelMap = new HashMap<>();
        levelMap.put("高中", 1);
        levelMap.put("大专", 2);
        levelMap.put("本科", 3);
        levelMap.put("硕士", 4);
        levelMap.put("博士", 5);

        int reqLevel = levelMap.getOrDefault(requirement, 0);
        if (reqLevel == 0) return true;

        // 从简历中提取最高学历
        int maxLevel = 0;
        String lowerResume = resumeText.toLowerCase();
        for (Map.Entry<String, Integer> entry : levelMap.entrySet()) {
            if (lowerResume.contains(entry.getKey().toLowerCase())) {
                maxLevel = Math.max(maxLevel, entry.getValue());
            }
        }

        return maxLevel >= reqLevel;
    }

    /**
     * 解析年限要求
     */
    private String parseYearRequirement(String text) {
        if (text == null) return "未指定";

        // 匹配数字+年/年以上等模式
        Pattern pattern = Pattern.compile("(\\d+)\\s*年");
        java.util.regex.Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1) + "年以上";
        }

        if (text.contains("应届") || text.contains("毕业生")) {
            return "应届生";
        }

        return "未指定";
    }

    /**
     * 检查年限是否满足
     */
    private boolean checkYear(String resumeText, String requirement) {
        if ("未指定".equals(requirement)) return true;
        if ("应届生".equals(requirement)) {
            return resumeText.contains("应届") || resumeText.contains("毕业生");
        }

        // 提取要求的年限
        Pattern pattern = Pattern.compile("(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(requirement);
        if (!matcher.find()) return true;

        int requiredYears = Integer.parseInt(matcher.group(1));

        // 从简历中提取工作年限
        Pattern yearPattern = Pattern.compile("(\\d+)\\s*年.*?(?:工作|经验|经历)");
        java.util.regex.Matcher yearMatcher = yearPattern.matcher(resumeText);

        if (yearMatcher.find()) {
            int actualYears = Integer.parseInt(yearMatcher.group(1));
            return actualYears >= requiredYears;
        }

        // 如果没有明确的工作年限，检查是否有工作经历描述
        if (resumeText.contains("工作经历") || resumeText.contains("工作经验")) {
            // 有经历但无法确定年限，给予部分通过（但默认不通过）
            return false;
        }

        return false;
    }

    private Double round(double value) {
        return Math.round(value * 10) / 10.0;
    }


    private String buildMatchExplanation(SemanticResult semantic, KeywordMatchResult keyword, HardRequirementResult hard) {
        StringBuilder sb = new StringBuilder("语义贴合度：")
                .append(semantic.getReason());

        if (!keyword.getMissingKeywords().isEmpty()) {
            sb.append("；缺失关键词：")
                    .append(String.join("、", keyword.getMissingKeywords()));
        }
        if (!hard.getFailedItems().isEmpty()) {
            sb.append("；硬性条件：")
                    .append(String.join("；", hard.getFailedItems()));
        }
        return sb.toString();
    }

    private MatchDimensionDetails buildDimensionDetails(KeywordMatchResult keyword, SemanticResult semantic, HardRequirementResult hard) {
        return MatchDimensionDetails.builder()
                .keyword(MatchDimensionDetails.Keyword.builder()
                        .total(keyword.getTotal())
                        .hit(keyword.getHit())
                        .missingKeywords(keyword.getMissingKeywords())
                        .build())
                .semantic(MatchDimensionDetails.Semantic.builder()
                        .score(round(semantic.getScore()))
                        .reason(semantic.getReason())
                        .mode("AI_APPROX")
                        .build())
                .hardRequirement(MatchDimensionDetails.HardRequirement.builder()
                        .educationRequirement(hard.getEducationRequirement())
                        .educationMet(hard.isEducationMet())
                        .yearRequirement(hard.getYearRequirement())
                        .yearMet(hard.isYearMet())
                        .failedItems(hard.getFailedItems())
                        .build())
                .build();
    }

}
