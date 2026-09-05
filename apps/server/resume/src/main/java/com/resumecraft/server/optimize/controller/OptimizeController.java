package com.resumecraft.server.optimize.controller;

import com.resumecraft.server.common.ApiResponse;
import com.resumecraft.server.optimize.dto.RewriteRequest;
import com.resumecraft.server.optimize.dto.SaveRequest;
import com.resumecraft.server.optimize.dto.TargetedOptimizeResponse;
import com.resumecraft.server.optimize.service.OptimizeService;
import com.resumecraft.server.resume.domain.ResumeVersion;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 单段精修
     * @param request
     * @return
     */
    @PostMapping("/rewrite")
    public ApiResponse<String> rewrite(@Valid @RequestBody RewriteRequest request) {
        return ApiResponse.ok(optimizeService.rewrite(request.getOriginal(), request.getFocus()));
    }

    /**
     * 保存精修版本
     * @param resumeId
     * @param request
     * @return
     */
    @PostMapping("/{resumeId}/save")
    public ApiResponse<ResumeVersion> saveContent(@PathVariable Long resumeId,
                                                  @Valid @RequestBody SaveRequest request){
        return ApiResponse.ok(optimizeService.saveContent(resumeId,request.getContent(),request.getVersionName()));
    }

}
