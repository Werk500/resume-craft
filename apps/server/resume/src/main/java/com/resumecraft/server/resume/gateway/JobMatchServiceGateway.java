package com.resumecraft.server.resume.gateway;


import com.resumecraft.server.common.feign.JobMatchClient;
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

}
