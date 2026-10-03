package com.resumecraft.server.job.controller;

import com.resumecraft.server.common.feign.dto.ScoreRequest;
import com.resumecraft.server.common.feign.dto.ScoreResponse;
import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.job.domain.JobMapper;
import com.resumecraft.server.job.domain.MatchResult;
import com.resumecraft.server.job.match.MatchEngine;
import com.resumecraft.server.job.service.MatchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/internal/score")
public class InternalScoreController {

    @Resource
    private JobMapper jobMapper;
    @Resource
    private MatchEngine matchEngine;

    @PostMapping
    public ScoreResponse score (@RequestBody ScoreRequest req) {
        Job job = jobMapper.selectById(req.getJobId());
        if (job == null) {
            throw new IllegalArgumentException("岗位不存在: " + req.getJobId());
        }
        // 只算确定性维度：关键词覆盖 + 硬性要求；语义维度按开关决定是否算
        MatchResult r = matchEngine.executeDeterministic(req.getResumeText(), job);
        return ScoreResponse.builder()
                .keywordCoverage(r.getKeywordCoverage())
                .missingKeywords(r.getMissingKeywords())
                .hardRequirementScore(r.getHardRequirementScore())
                .hardRequirementPassed(r.getHardRequirementPassed())
                .build();
    }
}
