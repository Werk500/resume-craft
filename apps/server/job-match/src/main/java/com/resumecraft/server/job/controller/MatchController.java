package com.resumecraft.server.job.controller;


import com.resumecraft.server.common.ApiResponse;
import com.resumecraft.server.job.dto.MatchBodyRequest;
import com.resumecraft.server.job.service.MatchService;
import jakarta.annotation.Resource;
import  com.resumecraft.server.job.domain.MatchResult;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequestMapping("/api/v1/match")
public class MatchController {

    @Resource
    private MatchService matchService;

    /**
     * 人岗匹配分析(路径参数）
     * @param resumeId
     * @param jobId
     * @return
     */
    @PostMapping("/{resumeId}/{jobId}")
    public MatchResult matchByPath(@PathVariable Long resumeId, @PathVariable Long jobId) {
        log.info("收到人岗匹配请求（路径参数），resumeId: {}, jobId: {}", resumeId, jobId);

        ApiResponse<MatchResult> response = matchService.match(resumeId, jobId);
        return response.getData();

    }

    /**
     * 人岗匹配分析(请求参数）
     * @param resumeId
     * @param jobId
     * @return
     */
    @PostMapping
    public MatchResult matchByParam(@RequestParam Long resumeId,
                                                 @RequestParam Long jobId) {
        log.info("收到人岗匹配请求（请求参数），resumeId: {}, jobId: {}", resumeId, jobId);

        return matchService.match(resumeId, jobId).getData();
    }

    /**
     * 人岗匹配分析（JSON Body）
     * POST /api/v1/match
     * {"resumeId": 123, "jobId": 456}
     */
    @PostMapping("/body")
    public MatchResult matchByBody(@RequestBody @Valid MatchBodyRequest request) {
        Long resumeId = request.getResumeId();
        Long jobId = request.getJobId();
        Long versionId = request.getVersionId();
        boolean equals = Boolean.TRUE.equals(request.getForceRefresh());

        if (resumeId == null || jobId == null) {
            throw new IllegalArgumentException("resumeId 和 jobId 不能为空");
        }

        log.info("收到人岗匹配请求（JSON Body），resumeId: {}, jobId: {}", resumeId, jobId);

        return matchService.match(resumeId, jobId,versionId,equals).getData();
    }


}
