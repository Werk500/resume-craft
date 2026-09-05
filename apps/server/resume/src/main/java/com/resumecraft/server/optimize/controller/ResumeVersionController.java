package com.resumecraft.server.optimize.controller;


import com.resumecraft.server.common.ApiResponse;
import com.resumecraft.server.optimize.service.ResumeVersionService;
import com.resumecraft.server.resume.domain.ResumeMapper;
import com.resumecraft.server.resume.domain.ResumeVersion;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("api/v1/version")
public class ResumeVersionController {

    @Resource
    private ResumeVersionService resumeVersionService;

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

}
