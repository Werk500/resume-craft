package com.resumecraft.server.application.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.common.dto.ResumeVersionDTO;
import com.resumecraft.server.common.exception.ServiceUnavailableException;
import com.resumecraft.server.common.feign.ResumeClient;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 简历版本查询网关。
 *
 * <p>投递服务只依赖该网关，不直接处理 Feign 异常；4xx 保留业务语义，
 * 5xx / 超时统一映射为可重试的 503。
 */
@Slf4j
@Component
public class ResumeVersionGateway {

    @Resource
    private ResumeClient resumeClient;

    @Resource
    private ObjectMapper objectMapper;

    @CircuitBreaker(name = "resumeService", fallbackMethod = "getVersionFallback")
    public ResumeVersionDTO getVersion(Long versionId) {
        try {
            return resumeClient.getVersion(versionId);
        } catch (FeignException e) {
            throw translate(e);
        }
    }

    public ResumeVersionDTO getVersionFallback(Long versionId, Throwable throwable) {
        if (throwable instanceof IllegalArgumentException businessRejection) {
            throw businessRejection;
        }
        log.warn("查询简历版本失败（熔断降级）: versionId={}, err={}", versionId, throwable.getMessage());
        throw new ServiceUnavailableException("简历服务暂时不可用，请稍后重试", throwable);
    }

    private RuntimeException translate(FeignException exception) {
        int status = exception.status();
        if (status >= 400 && status < 500) {
            return new IllegalArgumentException(extractMessage(exception));
        }
        return new ServiceUnavailableException("简历服务暂时不可用，请稍后重试", exception);
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
        return "简历版本不存在或不可用";
    }
}
