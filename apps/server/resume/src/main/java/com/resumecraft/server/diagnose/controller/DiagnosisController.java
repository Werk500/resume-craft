package com.resumecraft.server.diagnose.controller;

import com.resumecraft.server.diagnose.service.DiagnosisService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.resumecraft.server.common.ApiResponse;
import com.resumecraft.server.common.DiagnosisResponse;
import reactor.core.publisher.Flux;

/**
 * AI 诊断接口。
 * GET /api/v1/diagnose/{resumeId}   获取指定简历的诊断报告
 */
@RestController
@RequestMapping("/api/v1")
public class DiagnosisController {

    @Resource
    private DiagnosisService diagnosisService;

    @GetMapping("/diagnose/{resumeId}")
    public ApiResponse<DiagnosisResponse> diagnose(@PathVariable Long resumeId) {
        return ApiResponse.ok(diagnosisService.diagnose(resumeId));
    }

    @GetMapping(value = "/diagnose/{resumeId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> diagnoseStream(@PathVariable Long resumeId) {
        return diagnosisService.diagnoseStream(resumeId);
    }
}
