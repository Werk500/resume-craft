package com.resumecraft.server.resume.controller;

import java.io.IOException;
import java.util.List;

import com.resumecraft.server.common.security.AuthContext;
import com.resumecraft.server.resume.parser.dto.UpdateResumeTextRequest;
import com.resumecraft.server.resume.service.ResumeService;
import com.resumecraft.server.resume.dto.CreateFromTextRequest;
import jakarta.validation.Valid;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.resumecraft.server.common.ApiResponse;
import com.resumecraft.server.resume.domain.Resume;

/**
 * 简历上传与解析接口。
 *
 * POST /api/v1/resume/upload      上传并解析简历（multipart 字段名 file）
 * GET  /api/v1/resume/{id}        查询单份简历（含解析结果）
 * GET  /api/v1/resume             简历列表
 */
@RestController
@RequestMapping("/api/v1/resume")
public class ResumeController {

    @Resource
    private ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping("/upload")
    public ApiResponse<Resume> upload(@RequestParam("file") MultipartFile file) throws IOException {

        return ApiResponse.ok(resumeService.parseAndSave(file));
    }

    @PostMapping("/from-text")
    public ApiResponse<Resume> createFromText(@Valid @RequestBody CreateFromTextRequest request) {
        return ApiResponse.ok(
                resumeService.saveFromText(
                        AuthContext.getUserId(),
                        request.getRawText(),
                        request.getFileName()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Resume> getById(@PathVariable Long id) {
        return ApiResponse.ok(resumeService.findById(id));
    }

    @GetMapping
    public ApiResponse<List<Resume>> list() {
        return ApiResponse.ok(resumeService.findAll());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        resumeService.delete(id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/text")
    public ApiResponse<Resume> UpdateText(@PathVariable Long id, @Valid @RequestBody UpdateResumeTextRequest request ) {
        Resume resume = resumeService.updateText(id, request.getRawText());
        return ApiResponse.ok(resume);
    }
}
