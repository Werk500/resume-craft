package com.resumecraft.server.job.agent;


import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.job.domain.MatchResult;
import com.resumecraft.server.job.dto.JobPageResult;
import com.resumecraft.server.job.service.JobService;
import com.resumecraft.server.job.service.MatchService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 暴露给 Agent 的工具集。
 *
 * <h3>设计要点</h3>
 * <ol>
 *   <li><b>敏感参数不暴露给模型</b>：凡是涉及权限的字段（如 userId）都不能做成工具参数，
 *       否则模型可能被诱导传入他人 ID 造成越权。当前两个工具只接收
 *       resumeId / jobId，其归属校验由各自的 Service 层完成。</li>
 *   <li><b>返回值转成紧凑文本</b>：工具的返回值会进入模型上下文，
 *       直接返回整个 Job 对象（含 description/requirements）会消耗大量 token，
 *       因此只返回模型决策所需的关键字段。</li>
 *   <li><b>匹配分不由模型计算</b>：calculateMatch 调用的是可解释的规则引擎，
 *       保证结果可复现；模型只负责编排与解释。</li>
 * </ol>
 */
@Slf4j
@Component
public class JobAgentTools {

    /** 工具返回列表时的最大条数，避免上下文被塞满 */
    private static final int MAX_LIST_SIZE = 8;

    @Resource
    private JobService jobService;
    @Resource
    private MatchService matchService;

    @Tool("在岗位库中搜索岗位。company 为公司名（可为空）；keyword 为岗位或技术关键词（可为空）。返回岗位的 ID、公司、标题、城市。")
    public String searchJobs(@P("公司名，如 字节跳动，可为空") String company,
                             @P("岗位或技术关键词，如 后端、Java，可为空") String keyword){

        //常量既限制返回条数，也限制查询条数
        JobPageResult pageResult = jobService.search(company, keyword, 1, MAX_LIST_SIZE);

        if (pageResult == null || pageResult.getList() == null || pageResult.getList().isEmpty()) {
            return "没有找到匹配的岗位。";
        }

        StringBuilder sb = new StringBuilder();
        for (Job job : pageResult.getList()) {
            sb.append(String.format("#%d | %s | %s | %s%n", job.getId(),job.getCompany(),
                    job.getTitle(),job.getLocation()));
        }

        sb.append("（共 ").append(pageResult.getTotal()).append(" 条符合条件）");

        return sb.toString();
    }


    @Tool("计算某份简历与某个岗位的匹配度。返回总分、关键词覆盖、语义相似度、硬性条件是否通过、缺失的关键词。")
    public String calculateMatch(@P("简历 ID") Long resumeId,
                                 @P("岗位 ID") Long jobId){

        MatchResult result = matchService.match(resumeId, jobId, null, true).getData();
        if (result == null) {
            return "匹配计算失败";
        }

        List<String> missingList = result.getMissingKeywords();
        String missing = (missingList == null || missingList.isEmpty())
                ? "无"
                : String.join("、", missingList);

        String mode = (result.getDimensionDetails() == null
                || result.getDimensionDetails().getSemantic() == null)
                ? "未知"
                : result.getDimensionDetails().getSemantic().getMode();

        return String.format("""
                        总分：%s
                        关键词覆盖：%s
                        语义相似度：%s
                        硬性条件：%s
                        缺失关键词：%s
                        语义评分来源：%s""",
                result.getOverallScore(),
                result.getKeywordCoverage(),
                result.getSemanticSimilarity(),
                Boolean.TRUE.equals(result.getHardRequirementPassed()) ? "通过" : "未通过",
                missing,
                mode);
    }
}
