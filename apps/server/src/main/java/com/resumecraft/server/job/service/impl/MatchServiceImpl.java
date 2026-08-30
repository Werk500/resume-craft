package com.resumecraft.server.job.service.impl;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.common.ApiResponse;
import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.job.domain.JobMapper;
import com.resumecraft.server.job.domain.MatchResult;
import com.resumecraft.server.job.domain.MatchResultMapper;
import com.resumecraft.server.job.service.MatchService;
import com.resumecraft.server.resume.domain.Resume;
import com.resumecraft.server.resume.domain.ResumeMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class MatchServiceImpl implements MatchService {


    @Resource
    private ResumeMapper resumeMapper;
    @Resource
    private JobMapper jobMapper;
    @Resource
    private MatchResultMapper matchResultMapper;
    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private AiService aiService;

    /**
     * 人岗匹配分析
     * @param resumeId
     * @param jobId
     * @return
     */
    @Override
    public ApiResponse<MatchResult> match(Long resumeId, Long jobId) {
        // 并行查询简历和岗位
        CompletableFuture<Resume> resumeFuture = CompletableFuture.supplyAsync(() -> {
            Resume resume = resumeMapper.selectById(resumeId);
            if (resume == null) {
                throw new RuntimeException("简历不存在，resumeId: " + resumeId);
            }
            return resume;
        });

        CompletableFuture<Job> jobFuture = CompletableFuture.supplyAsync(() -> {
            Job job = jobMapper.selectById(jobId);
            if (job == null) {
                throw new RuntimeException("岗位不存在，jobId: " + jobId);
            }
            return job;
        });

        // 等待两个查询都完成
        CompletableFuture.allOf(resumeFuture, jobFuture).join();

        Resume resume = resumeFuture.join();
        Job job = jobFuture.join();
        log.info("开始人岗匹配分析，resumeId: {}, jobId: {}", resumeId, jobId);

        //3.拼prompt
        String resumeText = buildResumeText(resume);
        String userPrompt = PromptTemplates.matchUser(
                resumeText,
                job.getTitle(),
                job.getDescription(),
                job.getRequirements()
        );

        //4.调用AI
        String systemPrompt = PromptTemplates.MATCH_SYSTEM;
        String aiResponse = aiService.chat(systemPrompt, userPrompt);
        log.info("AI 响应: {}", aiResponse);

        //5.解析JSON
        MatchResult matchResult = parseMatchResult(aiResponse);
        matchResult.setJobId(jobId);
        matchResult.setResumeId(resumeId);

        //6.存match_result
        matchResultMapper.insert(matchResult);
        log.info("人岗匹配结果已保存，id: {}", matchResult.getId());

        // 7. 返回
        return  ApiResponse.ok(matchResult);
    }

    /**
     * 构建简历文本（根据你的 Resume 实体字段调整）
     */
    private String buildResumeText(Resume resume) {
        return "姓名：" + (resume.getParsedName() == null ? "未提供" : resume.getParsedName()) + "\n" +
                "邮箱：" + (resume.getParsedEmail() == null ? "未提供" : resume.getParsedEmail()) + "\n" +
                "电话：" + (resume.getParsedPhone() == null ? "未提供" : resume.getParsedPhone()) + "\n" +
                "简历原文：\n" + (resume.getRawText() == null ? "无" : resume.getRawText());

    }

    /**
     * 解析 AI 返回的 JSON
     */
    private MatchResult parseMatchResult(String aiResponse) {
        try {
            String jsonStr = aiResponse
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();

            JsonNode json = objectMapper.readTree(jsonStr);

            return MatchResult.builder()
                    .overallScore(getDouble(json, "overallScore"))
                    .keywordCoverage(getDouble(json, "keywordCoverage"))
                    .semanticSimilarity(getDouble(json, "semanticSimilarity"))
                    .hardRequirementScore(getDouble(json, "hardRequirementScore"))
                    .matchExplanation(getString(json, "matchExplanation"))
                    .build();
        } catch (Exception e) {
            log.error("解析 AI 响应 JSON 失败，原始响应: {}", aiResponse, e);
            throw new RuntimeException("AI 响应格式异常，无法解析匹配结果", e);
        }
    }

    //安全地从 JSON 对象中获取 Double 类型的值。
    private Double getDouble(JsonNode json, String key) {
        return json.has(key)//检查 JSON 中是否存在该字段
                && !json.get(key).isNull() ? json.get(key).asDouble() : null;//检查该字段的值是否为 null,如果字段存在且不为 null → 调用 asDouble() 返回数值
    }

    //安全地从 JSON 对象中获取 String 类型的值。
    private String getString(JsonNode json, String key) {
        return json.has(key) && !json.get(key).isNull() ? json.get(key).asText() : null;
    }
}
