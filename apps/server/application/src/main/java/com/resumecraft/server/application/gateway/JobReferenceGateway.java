package com.resumecraft.server.application.gateway;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.common.dto.JobBriefDTO;
import com.resumecraft.server.common.exception.ServiceUnavailableException;
import com.resumecraft.server.common.feign.JobMatchClient;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JobReferenceGateway {
    @Resource
    private JobMatchClient jobMatchClient;

    @Resource
    private ObjectMapper objectMapper;

    @CircuitBreaker(name = "jobService", fallbackMethod = "getJobFallback")
    public JobBriefDTO getJob(Long jobId) {
        try {
            return jobMatchClient.getJob(jobId);
        } catch (FeignException e) {
            throw translate(e);
        }
    }

    public JobBriefDTO getJobFallback(Long jobId, Throwable throwable) {

        // 业务拒绝不是服务故障，原样抛出交给全局异常处理器。
        if (throwable instanceof IllegalArgumentException businessRejection) {
            throw businessRejection;
        }
        log.warn("获取岗位失败（熔断降级）: jobId={}, err={}", jobId, throwable.getMessage());
        throw new ServiceUnavailableException("岗位服务暂时不可用，请稍后重试", throwable);
    }

    private RuntimeException translate(FeignException exception) {
        int status = exception.status();
        // 4xx 是业务拒绝，不能计入服务故障。
        if (status >= 400 && status < 500) {
            return new IllegalArgumentException(extractMessage(exception));
        }
        // 5xx / 超时属于系统故障。
        return new ServiceUnavailableException("岗位服务暂时不可用，请稍后重试", exception);
    }

    private String extractMessage(FeignException exception) {
        try {
            JsonNode node = objectMapper.readTree(exception.contentUTF8());
            String message = node.path("message").asText(null);
            if (message != null && !message.isBlank()) {
                return message;
            }
        } catch (Exception ignored) {
            // 下游响应体不是统一的 ApiResponse 时，使用稳定的兜底文案。
        }
        return "岗位不存在或不可用";
    }
}
