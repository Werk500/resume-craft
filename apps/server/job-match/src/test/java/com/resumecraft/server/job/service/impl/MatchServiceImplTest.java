package com.resumecraft.server.job.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.common.dto.ResumeBriefDTO;
import com.resumecraft.server.common.exception.ServiceUnavailableException;
import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.job.domain.JobMapper;
import com.resumecraft.server.job.domain.MatchResult;
import com.resumecraft.server.job.domain.MatchResultMapper;
import com.resumecraft.server.job.gateway.ResumeServiceGateway;
import com.resumecraft.server.job.match.MatchContext;
import com.resumecraft.server.job.match.MatchEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * MatchServiceImpl 的调用编排测试。
 *
 * <p>职责边界：Feign 异常翻译已迁移到 {@link ResumeServiceGateway}，
 * 由 {@code ResumeServiceGatewayTest} 覆盖。本测试只验证编排逻辑——
 * 拿到数据后如何组装、拿不到时抛出什么异常、结果如何落库。
 */
@ExtendWith(MockitoExtension.class)
class MatchServiceImplTest {

    @Mock
    private ResumeServiceGateway resumeServiceGateway;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private MatchResultMapper matchResultMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private MatchEngine matchEngine;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MatchServiceImpl matchService;

    private static final Long RESUME_ID = 1001L;
    private static final Long JOB_ID = 2001L;

    @BeforeEach
    void setUp() {
        // match() 会先查岗位，岗位为 null 会提前返回，测不到后续分支
        when(jobMapper.selectById(JOB_ID)).thenReturn(Job.builder()
                .id(JOB_ID)
                .company("字节跳动")
                .title("后端开发工程师")
                .description("负责电商业务后台研发与高并发系统设计")
                .requirements("本科及以上学历，1年以上经验")
                .build());
    }

    @Test
    @DisplayName("正常路径：网关返回简历 → 调用引擎 → 结果透传")
    void shouldReturnEngineResultOnSuccess() {
        when(resumeServiceGateway.getResume(RESUME_ID)).thenReturn(ResumeBriefDTO.builder()
                .id(RESUME_ID)
                .userId(16L)
                .fileName("demo.md")
                .rawText("张三，本科，熟悉 Java")
                .build());

        MatchResult engineResult = MatchResult.builder()
                .resumeId(RESUME_ID)
                .jobId(JOB_ID)
                .overallScore(82.0)
                .build();
        when(matchEngine.execute(anyString(), any(Job.class), any(MatchContext.class)))
                .thenReturn(engineResult);

        var response = matchService.match(RESUME_ID, JOB_ID);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getOverallScore()).isEqualTo(82.0);
    }

    @Test
    @DisplayName("网关降级返回 null → 抛服务不可用（而不是「简历不存在」）")
    void shouldThrowServiceUnavailableWhenGatewayDegrades() {
        // null 表示熔断打开后的降级结果，是"服务不可用"而非"资源不存在"，
        // 必须映射为 500 而不是 400
        when(resumeServiceGateway.getResume(RESUME_ID)).thenReturn(null);

        assertThatThrownBy(() -> matchService.match(RESUME_ID, JOB_ID))
                // 必须用专门的 ServiceUnavailableException（映射 503），
                // 用 RuntimeeException 会被全局兜底分支吞掉 message，前端只看到"服务器内部错误"
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("简历服务暂时不可用，请稍后重试")
                .isNotInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("岗位不存在 → 提前失败，不调用网关")
    void shouldFailFastWhenJobNotFound() {
        when(jobMapper.selectById(JOB_ID)).thenReturn(null);

        assertThatThrownBy(() -> matchService.match(RESUME_ID, JOB_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("岗位不存在");
    }
}
