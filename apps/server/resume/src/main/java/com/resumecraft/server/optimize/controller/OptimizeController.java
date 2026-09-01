package com.resumecraft.server.optimize.controller;

import com.resumecraft.server.common.ApiResponse;
import com.resumecraft.server.optimize.dto.TargetedOptimizeResponse;
import com.resumecraft.server.optimize.service.OptimizeService;
import com.resumecraft.server.resume.domain.ResumeVersion;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 一键优化接口。
 *
 * POST /api/v1/optimize/{resumeId}?targetJob=xxx   AI 优化简历并保存为版本
 */
@RestController
@RequestMapping("/api/v1/optimize")
public class OptimizeController {

    @Resource
    private OptimizeService optimizeService;

    @PostMapping("/{resumeId}")
    public ApiResponse<ResumeVersion> optimize(@PathVariable Long resumeId,
                                               @RequestParam(required = false) String targetJob) {
        return ApiResponse.ok(optimizeService.optimize(resumeId, targetJob));
    }

    @PostMapping("/{resumeId}/targeted")
    public ApiResponse<TargetedOptimizeResponse> targetedOptimize(@PathVariable Long resumeId,
                                                                  @RequestParam Long jobId) {
        return ApiResponse.ok(optimizeService.targetedOptimize(resumeId, jobId));
    }

}
