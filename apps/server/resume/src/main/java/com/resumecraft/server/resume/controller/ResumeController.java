package com.resumecraft.server.resume.controller;

import java.io.IOException;
import java.util.List;

import com.resumecraft.server.resume.service.ResumeService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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

    @GetMapping("/{id}")
    public ApiResponse<Resume> getById(@PathVariable Long id) {
        return ApiResponse.ok(resumeService.findById(id));
    }

    @GetMapping
    public ApiResponse<List<Resume>> list() {
        return ApiResponse.ok(resumeService.findAll());
    }
}
