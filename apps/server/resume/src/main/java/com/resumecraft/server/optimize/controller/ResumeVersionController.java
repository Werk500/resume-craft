package com.resumecraft.server.optimize.controller;


import com.resumecraft.server.common.ApiResponse;
import com.resumecraft.server.optimize.service.OptimizeService;
import com.resumecraft.server.optimize.service.ResumeVersionService;
import com.resumecraft.server.resume.domain.ResumeMapper;
import com.resumecraft.server.resume.domain.ResumeVersion;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("api/v1/version")
public class ResumeVersionController {

    @Resource
    private ResumeVersionService resumeVersionService;
    @Resource
    private OptimizeService optimizeService;
    /**
     * 获取简历的所有版本列表（倒序）
     * GET /api/v1/version?resumeId=x
     */
    @GetMapping
    public ApiResponse<List<ResumeVersion>> listVersions(@RequestParam Long resumeId){
        return ApiResponse.ok(resumeVersionService.list(resumeId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteVersion(@PathVariable Long id){
        try {
            resumeVersionService.delete(id);
            log.info("删除版本成功, versionId: {}", id);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            log.warn("参数错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage().contains("不存在") ? 404 : 403, e.getMessage());
        }
    }


    @GetMapping("/{id}/export")
    @Operation(summary = "导出简历版本", description = "将指定版本的简历导出为 PDF 或 DOCX 文件")
    public ResponseEntity<byte[]> exportVersion(@PathVariable Long id, @RequestParam String format) {
        byte[] data = optimizeService.exportVersion(id, format);
        String lower = format.toLowerCase();
        MediaType mediaType = "pdf".equals(lower)
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        String fileName = URLEncoder.encode("resume-version-" + id + "." + lower, StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(data);
    }
}
