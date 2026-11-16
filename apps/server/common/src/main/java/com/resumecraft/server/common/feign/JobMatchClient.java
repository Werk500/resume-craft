package com.resumecraft.server.common.feign;

import com.resumecraft.server.common.dto.JobBriefDTO;
import com.resumecraft.server.common.feign.dto.ScoreRequest;
import com.resumecraft.server.common.feign.dto.ScoreResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * job-match 服务内部接口客户端。
 *
 * <p>用途：简历删除时级联清理 PostgreSQL 中的向量数据；投递服务创建记录前
 * 通过内部接口校验岗位是否存在。调用方不直接依赖 job-match 的数据库。
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

    @PostMapping("/score")
    ScoreResponse score(@RequestBody ScoreRequest request);

    @DeleteMapping(
            "/vector/resume/{resumeId}/version/{versionId}"
    )
    Integer deleteResumeVersionVector(
            @PathVariable("resumeId") Long resumeId,
            @PathVariable("versionId") Long versionId
    );

    @GetMapping("/job/{id}")
    JobBriefDTO getJob(@PathVariable("id") Long id);
}
