package com.resumecraft.server.application.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.common.dto.JobBriefDTO;
import com.resumecraft.server.common.exception.ServiceUnavailableException;
import com.resumecraft.server.common.feign.JobMatchClient;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobReferenceGatewayTest {

    private static final Long JOB_ID = 201L;

    @Mock
    private JobMatchClient jobMatchClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private JobReferenceGateway gateway;

    @Test
    @DisplayName("正常查询原样返回岗位摘要")
    void shouldReturnJobOnSuccess() {
        JobBriefDTO job = new JobBriefDTO();
        job.setId(JOB_ID);
        when(jobMatchClient.getJob(JOB_ID)).thenReturn(job);

        assertThat(gateway.getJob(JOB_ID)).isSameAs(job);
    }

    @Test
    @DisplayName("4xx 保留岗位不存在的业务文案")
    void shouldKeepBusinessMessageOn4xx() {
        stubFeignError(400, """
                {"code":400,"message":"岗位不存在: 201","data":null}
                """);

        assertThatThrownBy(() -> gateway.getJob(JOB_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("岗位不存在: 201");
    }

    @Test
    @DisplayName("响应体不可解析时使用稳定的兜底文案")
    void shouldFallbackMessageWhenBodyIsNotJson() {
        stubFeignError(404, "not-json");

        assertThatThrownBy(() -> gateway.getJob(JOB_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("岗位不存在或不可用");
    }

    @Test
    @DisplayName("5xx 转换为 503 语义且不暴露内部错误")
    void shouldTranslate5xxToServiceUnavailable() {
        stubFeignError(500, """
                {"code":500,"message":"数据库连接失败","data":null}
                """);

        assertThatThrownBy(() -> gateway.getJob(JOB_ID))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("岗位服务暂时不可用，请稍后重试")
                .hasMessageNotContaining("数据库连接失败");
    }

    @Test
    @DisplayName("fallback 不能把业务拒绝降级成 503")
    void fallbackShouldRethrowBusinessRejection() {
        IllegalArgumentException failure = new IllegalArgumentException("岗位不存在: 201");

        assertThatThrownBy(() -> gateway.getJobFallback(JOB_ID, failure))
                .isSameAs(failure);
    }

    @Test
    @DisplayName("fallback 遇到系统故障时转换为 503")
    void fallbackShouldTranslateSystemFailure() {
        RuntimeException failure = new RuntimeException("connection reset");

        assertThatThrownBy(() -> gateway.getJobFallback(JOB_ID, failure))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("岗位服务暂时不可用，请稍后重试");
    }

    private void stubFeignError(int status, String body) {
        when(jobMatchClient.getJob(JOB_ID))
                .thenThrow(FeignException.errorStatus(
                        "JobMatchClient#getJob",
                        feign.Response.builder()
                                .status(status)
                                .reason("test")
                                .request(feign.Request.create(
                                        feign.Request.HttpMethod.GET,
                                        "http://job-match-service/internal/job/" + JOB_ID,
                                        Map.of(),
                                        null,
                                        null,
                                        null
                                ))
                                .headers(Map.of(
                                        "Content-Type",
                                        List.of("application/json")
                                ))
                                .body(body, StandardCharsets.UTF_8)
                                .build()
                ));
    }
}
