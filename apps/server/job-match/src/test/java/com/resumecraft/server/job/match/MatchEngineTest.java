package com.resumecraft.job.match;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.job.domain.MatchResult;
import com.resumecraft.server.job.match.MatchEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class MatchEngineTest {

    @Mock
    private AiService aiService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MatchEngine matchEngine;   // 被测对象，Mockito 自动把上面的注入进去

    
    private Job job(String requirements){
        return Job.builder()
                .company("字节跳动")
                .title("后端开发工程师")
                .description("负责电商业务后台研发与高并发系统设计")
                .requirements(requirements)
                .build();
    }

    /** 让 AI 关键词抽取固定返回 4 个词，语义固定 80 分 */
    private void stubAi(String keywordsJson, String semanticJson) {
        when(aiService.chat(eq(PromptTemplates.EXTRACT_KEYWORDS_SYSTEM), anyString()))
                .thenReturn(keywordsJson);
        when(aiService.chat(eq(PromptTemplates.SEMANTIC_MATCH_SYSTEM), anyString()))
                .thenReturn(semanticJson);
    }
    
    @Test
    @DisplayName("40/40/20 公式：命中 3/4 + 语义 80 + 硬性 100 → 82 分，缺失词为 Kafka")
    void shouldCalculateWeightedScore() {
        stubAi("{\"keywords\":[\"Java\",\"Spring Boot\",\"MySQL\",\"Kafka\"]}",
                "{\"score\":80,\"reason\":\"项目经历与岗位贴合\"}");

        String resume = "张三，本科，3年工作经验，熟悉 Java、Spring Boot、MySQL，负责高并发订单系统";
        MatchResult result = matchEngine.execute(resume, job("本科及以上学历，1年以上经验"));

        // 75*0.4 + 80*0.4 + 100*0.2 = 30 + 32 + 20 = 82
        assertThat(result.getOverallScore()).isEqualTo(82.0);
        assertThat(result.getKeywordCoverage()).isEqualTo(75.0);
        assertThat(result.getSemanticSimilarity()).isEqualTo(80.0);
        assertThat(result.getHardRequirementScore()).isEqualTo(100.0);
        assertThat(result.getHardRequirementPassed()).isTrue();

        // 命中明细：4 个关键词命中 3 个，Kafka 缺失
        assertThat(result.getKeywordHits()).hasSize(4);
        assertThat(result.getMissingKeywords()).containsExactly("Kafka");
        assertThat(result.getDimensionDetails().getKeyword().getTotal()).isEqualTo(4);
        assertThat(result.getDimensionDetails().getKeyword().getHit()).isEqualTo(3);

    }

    @Test
    @DisplayName("硬性条件不满足：总分封顶 40 且 passed=false")
    void shouldCapScoreWhenHardRequirementFailed() {
        stubAi("{\"keywords\":[\"Java\"]}", "{\"score\":90,\"reason\":\"ok\"}");

        // JD 要求硕士，简历只有本科 → 学历不达标
        MatchResult result = matchEngine.execute("张三，本科，熟悉 Java", job("硕士及以上学历"));

        assertThat(result.getHardRequirementPassed()).isFalse();
        assertThat(result.getHardRequirementScore()).isEqualTo(0.0);
        // 未封顶应为 100*0.4 + 90*0.4 + 0*0.2 = 76，封顶后 40
        assertThat(result.getOverallScore()).isEqualTo(40.0);
        assertThat(result.getDimensionDetails().getHardRequirement().getFailedItems()).isNotEmpty();
    }

    @Test
    @DisplayName("语义 AI 超时/失败 → 降级 50 分而不是抛异常")
    void shouldFallbackSemanticScoreWhenAiFails() {
        when(aiService.chat(eq(PromptTemplates.EXTRACT_KEYWORDS_SYSTEM), anyString()))
                .thenReturn("{\"keywords\":[\"Java\"]}");
        when(aiService.chat(eq(PromptTemplates.SEMANTIC_MATCH_SYSTEM), anyString()))
                .thenThrow(new RuntimeException("AI 超时"));

        MatchResult result = matchEngine.execute(
                "张三，本科，3年工作经验，熟悉 Java",
                job("本科及以上学历，1年以上经验"));

        assertThat(result.getSemanticSimilarity()).isEqualTo(50.0);   // 降级默认分
        // 100*0.4 + 50*0.4 + 100*0.2 = 40 + 20 + 20 = 80
        assertThat(result.getOverallScore()).isEqualTo(80.0);
    }

}
