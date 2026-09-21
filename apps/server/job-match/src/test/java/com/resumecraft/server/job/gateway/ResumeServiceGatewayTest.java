package com.resumecraft.server.job.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.common.dto.ResumeBriefDTO;
import com.resumecraft.server.common.feign.ResumeClient;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 验证 resume 服务调用网关的异常语义化。
 *
 * <p>这些用例原本在 {@code MatchServiceFeignTest} 中——测试的是 {@code MatchServiceImpl.callResume}
 * 的异常翻译。接入熔断后该逻辑迁移到本网关，测试也随之迁移：
 * <b>谁负责翻译，就测谁</b>。
 *
 * <p>说明：本测试不启动 Spring 容器，因此 {@code @CircuitBreaker} 的 AOP 不参与，
 * 测的是"Feign 异常 → 业务异常"这一层翻译逻辑。熔断状态流转由联调验收覆盖。
 */
@ExtendWith(MockitoExtension.class)
class ResumeServiceGatewayTest {

    @Mock
    private ResumeClient resumeClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ResumeServiceGateway gateway;

    private static final Long RESUME_ID = 1001L;

    // ---------------------------------------------------------- 4xx：业务拒绝

    @Test
    @DisplayName("4xx → IllegalArgumentException，message 取自响应体")
    void shouldTranslate4xxToIllegalArgument() {
        stubGetResumeThrows(400,
                "{\"code\":400,\"message\":\"简历未解析完成\",\"data\":null}");

        assertThatThrownBy(() -> gateway.getResume(RESUME_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("简历未解析完成");
    }

    @Test
    @DisplayName("404 → 同样翻译为 IllegalArgumentException，message 透传")
    void shouldPassThroughMessageOn404() {
        stubGetResumeThrows(404,
                "{\"code\":404,\"message\":\"简历不存在\",\"data\":null}");

        assertThatThrownBy(() -> gateway.getResume(RESUME_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("简历不存在");
    }

    @Test
    @DisplayName("响应体无法解析时 → 返回兜底文案")
    void shouldFallbackMessageWhenBodyUnparsable() {
        stubGetResumeThrows(400, "这不是 JSON");

        assertThatThrownBy(() -> gateway.getResume(RESUME_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("简历当前状态不允许匹配，请先核对解析内容");
    }

    // ---------------------------------------------------------- 5xx：服务故障

    @Test
    @DisplayName("500 → RuntimeException，且不暴露原始 message")
    void shouldTranslate5xxAndHideOriginalMessage() {
        stubGetResumeThrows(500,
                "{\"code\":500,\"message\":\"内部错误\",\"data\":null}");

        assertThatThrownBy(() -> gateway.getResume(RESUME_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("简历服务暂时不可用，请稍后重试")
                .hasMessageNotContaining("内部错误");
    }

    @Test
    @DisplayName("503 → 同样走 5xx 兜底")
    void shouldTranslate503() {
        stubGetResumeThrows(503, "");

        assertThatThrownBy(() -> gateway.getResume(RESUME_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("简历服务暂时不可用，请稍后重试");
    }

    // ---------------------------------------------------------- 消费端校验

    @Test
    @DisplayName("isAvailable：简历存在返回 true")
    void shouldReturnTrueWhenResumeExists() {
        when(resumeClient.getResume(RESUME_ID))
                .thenReturn(ResumeBriefDTO.builder().id(RESUME_ID).userId(16L).build());

        assertThat(gateway.isAvailable(RESUME_ID)).isTrue();
    }

    @Test
    @DisplayName("isAvailable：4xx（已删除/REVIEW）返回 false，不抛异常")
    void shouldReturnFalseOn4xx() {
        stubGetResumeThrows(400,
                "{\"code\":400,\"message\":\"简历不存在: 1001\",\"data\":null}");

        // 消费端只关心"能不能继续生成向量"，不该因业务拒绝而抛异常
        assertThat(gateway.isAvailable(RESUME_ID)).isFalse();
    }

    // ---------------------------------------------------------- 正常路径

    @Test
    @DisplayName("正常返回 → DTO 原样透传")
    void shouldReturnDtoOnSuccess() {
        ResumeBriefDTO brief = ResumeBriefDTO.builder()
                .id(RESUME_ID)
                .userId(16L)
                .rawText("张三，本科，熟悉 Java")
                .build();
        when(resumeClient.getResume(RESUME_ID)).thenReturn(brief);

        assertThat(gateway.getResume(RESUME_ID)).isSameAs(brief);
    }

    // ---------------------------------------------------------- 降级方法本身的行为

    @Test
    @DisplayName("fallback 遇到业务拒绝必须原样抛出 —— 不能把 400 降级成 503")
    void fallbackShouldRethrowBusinessRejection() {
        // Resilience4j 的 ignoreExceptions 只影响失败率统计，不阻止 fallback 被调用。
        // 若 fallback 一律返回 null，"简历不存在(400)"会被调用方误判为"服务不可用(503)"。
        IllegalArgumentException business = new IllegalArgumentException("简历不存在: 1001");

        assertThatThrownBy(() -> gateway.getResumeFallback(RESUME_ID, business))
                .isSameAs(business);
        assertThatThrownBy(() -> gateway.getVersionFallback(RESUME_ID, business))
                .isSameAs(business);
    }

    @Test
    @DisplayName("fallback 遇到服务故障才真正降级（返回 null）")
    void fallbackShouldDegradeOnServiceFailure() {
        RuntimeException outage = new RuntimeException("简历服务暂时不可用，请稍后重试");

        assertThat(gateway.getResumeFallback(RESUME_ID, outage)).isNull();
        assertThat(gateway.getVersionFallback(RESUME_ID, outage)).isNull();
    }

    /**
     * 让 getResume 抛出指定状态码的 FeignException。
     *
     * <p>这里用 {@link FeignException#errorStatus} 构造<b>真实异常实例</b>，
     * 而不是 mock FeignException。原因：
     * <ul>
     *   <li>mock 时 {@code status()} / {@code contentUTF8()} 需要单独打桩，
     *       Mockito 严格模式会把它们判定为 UnnecessaryStubbing（已实测）；</li>
     *   <li>真实实例的 status / body 与生产环境完全一致，测试更可信；</li>
     *   <li>无需 mock 开销，跑得更快。</li>
     * </ul>
     *
     * <p>注意：{@code errorStatus} 对 4xx 返回 {@code FeignClientException}、
     * 对 5xx 返回 {@code FeignServerException}，正是网关要区分的两类异常。
     */
    private void stubGetResumeThrows(int status, String body) {
        when(resumeClient.getResume(RESUME_ID))
                .thenThrow(FeignException.errorStatus("ResumeClient#getResume",
                        feign.Response.builder()
                                .status(status)
                                .reason("test")
                                .request(feign.Request.create(feign.Request.HttpMethod.GET,
                                        "http://resume-service/internal/resume/1001",
                                        java.util.Map.of(), null, null, null))
                                .headers(java.util.Map.of("Content-Type",
                                        java.util.List.of("application/json")))
                                .body(body, java.nio.charset.StandardCharsets.UTF_8)
                                .build()));
    }
}
