package com.resumecraft.server.application.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.common.dto.ResumeVersionDTO;
import com.resumecraft.server.common.exception.ServiceUnavailableException;
import com.resumecraft.server.common.feign.ResumeClient;
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
class ResumeVersionGatewayTest {

    private static final Long VERSION_ID = 101L;

    @Mock
    private ResumeClient resumeClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ResumeVersionGateway gateway;

    @Test
    @DisplayName("正常查询原样返回版本")
    void shouldReturnVersionOnSuccess() {
        ResumeVersionDTO version = ResumeVersionDTO.builder()
                .id(VERSION_ID)
                .userId(7L)
                .status("CONFIRMED")
                .build();
        when(resumeClient.getVersion(VERSION_ID)).thenReturn(version);

        assertThat(gateway.getVersion(VERSION_ID)).isSameAs(version);
    }

    @Test
    @DisplayName("4xx 保留业务错误，不误报服务故障")
    void shouldKeepBusinessMessageOn4xx() {
        stubFeignError(400, """
                {"code":400,"message":"简历版本不存在","data":null}
                """);

        assertThatThrownBy(() -> gateway.getVersion(VERSION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("简历版本不存在");
    }

    @Test
    @DisplayName("5xx 转换为 503 语义且不暴露内部错误")
    void shouldTranslate5xxToServiceUnavailable() {
        stubFeignError(500, """
                {"code":500,"message":"数据库连接失败","data":null}
                """);

        assertThatThrownBy(() -> gateway.getVersion(VERSION_ID))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("简历服务暂时不可用，请稍后重试")
                .hasMessageNotContaining("数据库连接失败");
    }

    @Test
    @DisplayName("fallback 不能把业务拒绝降级成 503")
    void fallbackShouldRethrowBusinessRejection() {
        IllegalArgumentException failure = new IllegalArgumentException("简历版本不存在");

        assertThatThrownBy(() -> gateway.getVersionFallback(VERSION_ID, failure))
                .isSameAs(failure);
    }

    private void stubFeignError(int status, String body) {
        when(resumeClient.getVersion(VERSION_ID))
                .thenThrow(FeignException.errorStatus(
                        "ResumeClient#getVersion",
                        feign.Response.builder()
                                .status(status)
                                .reason("test")
                                .request(feign.Request.create(
                                        feign.Request.HttpMethod.GET,
                                        "http://resume-service/internal/version/" + VERSION_ID,
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
