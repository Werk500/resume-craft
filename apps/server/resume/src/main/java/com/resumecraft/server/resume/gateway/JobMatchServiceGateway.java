package com.resumecraft.server.resume.gateway;


import com.resumecraft.server.common.feign.JobMatchClient;
import com.resumecraft.server.common.feign.dto.ScoreRequest;
import com.resumecraft.server.common.feign.dto.ScoreResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JobMatchServiceGateway {


    @Resource
    private JobMatchClient jobMatchClient;

    /**
     * 删除简历的全部向量。
     *
     * <p>降级策略：静默降级返回 0。
     * 向量是可重建的派生数据，清理失败不应阻断用户的删除操作。
     */
    @CircuitBreaker(name = "jobMatchService", fallbackMethod = "deleteVectorsFallback")
    public int deleteVectors(Long resumeId) {
        Integer deleted = jobMatchClient.deleteResumeVectors(resumeId);
        return deleted == null ? 0 : deleted;
    }

    public int deleteVectorsFallback(Long resumeId, Throwable t) {
        log.warn("删除简历向量失败（熔断降级，不影响简历删除）: resumeId={}, err={}",
                resumeId, t.getMessage());
        return 0;
    }

    @CircuitBreaker(name = "jobMatchService", fallbackMethod = "scoreFallback")
    public ScoreResponse score(String resumeText, Long jobId) {
        ScoreRequest request = ScoreRequest.builder()
                .resumeText(resumeText)
                .jobId(jobId)
                .build();
        return jobMatchClient.score(request);

    }
    public ScoreResponse scoreFallback(String resumeText, Long jobId, Throwable t) {
        log.warn("内部打分不可用（降级，优化闭环退化为单次改写）: jobId={}, err={}", jobId, t.getMessage());
        return null;
    }

}
