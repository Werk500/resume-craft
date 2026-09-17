package com.resumecraft.server.common.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * job-match 服务内部接口客户端。
 *
 * <p>用途：简历删除时级联清理 PostgreSQL 中的向量数据。
 * 向量库是 job-match 独占的第二数据源，简历服务无法直连，
 * 因此走 /internal 接口触发，与既有的 ResumeClient 同构。
 */
@FeignClient(name = "job-match-service", path = "/internal")
public interface JobMatchClient {

    /**
     * 删除某份简历的全部向量（主简历 + 所有优化版本）。
     *
     * @return 实际删除的向量行数
     */
    @DeleteMapping("/vector/resume/{resumeId}")
    Integer deleteResumeVectors(@PathVariable("resumeId") Long resumeId);
}
