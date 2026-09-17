package com.resumecraft.server.job.controller;

import com.resumecraft.server.job.vector.VectorStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 向量库内部接口。
 *
 * <p>供 resume-service 在删除简历时调用，级联清理 PostgreSQL 中的向量数据。
 * 走 /internal/** 路径，由网关拦截外部访问 + X-Internal-Token 校验。
 */
@Slf4j
@RestController
@RequestMapping("/internal/vector")
public class InternalVectorController {

    @Resource
    private VectorStore vectorStore;

    @DeleteMapping("/resume/{resumeId}")
    public Integer deleteResumeVectors(@PathVariable Long resumeId) {
        int deleted = vectorStore.deleteResumeVectors(resumeId);
        log.info("internal 删除简历向量 resumeId={}, deleted={}, traceId={}",
                resumeId, deleted, MDC.get("traceId"));
        return deleted;
    }
}
