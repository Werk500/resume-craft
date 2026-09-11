package com.resumecraft.server.job.service.impl;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.common.dto.ResumeBriefDTO;
import com.resumecraft.server.common.feign.ResumeClient;
import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.job.domain.JobMapper;
import com.resumecraft.server.job.domain.MatchResult;
import com.resumecraft.server.job.domain.MatchResultMapper;
import com.resumecraft.server.job.match.MatchEngine;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.configuration.IMockitoConfiguration;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;


/**
 * 针对 MatchServiceImpl.callResume 的错误透传测试。
 *
 * 覆盖：
 *  ① 4xx → IllegalArgumentException，message 来自响应体
 *  ② 5xx → RuntimeException("简历服务暂时不可用，请稍后重试")
 *  ③ 响应体解析失败时 → 返回兜底文案
 */
@ExtendWith(MockitoExtension.class)
public class MatchServiceFeignTest {

    @Mock
    private ResumeClient resumeClient;
    @Mock
    private JobMapper jobMapper;

    @Mock
    private MatchResultMapper matchResultMapper;        // 新增：正常路径要落库
    @Mock
    private StringRedisTemplate stringRedisTemplate;    // 新增：缓存读写（mock 默认返回 null，会被 try/catch 兜住）
    @Mock
    private MatchEngine matchEngine;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();


    @InjectMocks
    private MatchServiceImpl matchService;

    private static final Long RESUME_ID = 1001L;
    private static final Long JOB_ID = 2001L;

    /**
     * 关键：match() 会先查岗位，岗位不存在会直接抛异常，测不到 Feign 分支。
     */
    @BeforeEach
    void setUp() {
        Job job = Job.builder()
                .id(JOB_ID)
                .company("字节跳动")
                .title("后端开发工程师")
                .description("负责电商业务后台研发与高并发系统设计")
                .requirements("本科及以上学历，1年以上经验")
                .build();
        when(jobMapper.selectById(JOB_ID)).thenReturn(job);
    }


    // ============================================================
    // ① 4xx → IllegalArgumentException，message 来自响应体
    // ============================================================
    @Test
    @DisplayName("Feign 4xx：抛 IllegalArgumentException，message 来自响应体")
    void shouldThrowIllegalArgumentExceptionWithMessageFromBodyOn4xx() {

        // given: 响应体形如 {"code":400,"message":"简历未解析完成","data":null}
        String body = "{\"code\":400,\"message\":\"简历未解析完成\",\"data\":null}";
        FeignException feignEx = mockFeignException(400, body);

        when(resumeClient.getResume(RESUME_ID)).thenThrow(feignEx);
        assertThatThrownBy(() -> matchService.match(RESUME_ID, JOB_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("简历未解析完成");
    }

    @Test
    @DisplayName("Feign 404：message 来自响应体")
    void shouldPassThroughMessageOn404(){
        String body = "{\"code\":404,\"message\":\"简历不存在\",\"data\":null}";
        FeignException feignEx = mockFeignException(404, body);

        when(resumeClient.getResume(anyLong())).thenThrow(feignEx);

        assertThatThrownBy(() -> matchService.match(RESUME_ID, JOB_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("简历不存在");
    }


    // ============================================================
    // ② 5xx → RuntimeException("简历服务暂时不可用，请稍后重试")
    // ============================================================
    @Test
    @DisplayName("Feign 5xx：抛 RuntimeException，文案为服务不可用")
    void shouldThrowRuntimeExceptionOn5xx(){
        String body = "{\"code\":500,\"message\":\"内部错误\",\"data\":null}";
        FeignException feignEx = mockFeignException(500, body);

        when(resumeClient.getResume(RESUME_ID)).thenThrow(feignEx);

        assertThatThrownBy(() -> matchService.match(RESUME_ID, JOB_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("简历服务暂时不可用，请稍后重试")
                // 5xx 时不再暴露原始 message
                .hasMessageNotContaining("内部错误");
    }

    @Test
    @DisplayName("Feign 503：同样走 5xx 兜底逻辑")
    void shouldThrowRuntimeExceptionOn503() {
        FeignException feignEx = mockFeignException(503, "");

        when(resumeClient.getResume(RESUME_ID)).thenThrow(feignEx);

        assertThatThrownBy(() -> matchService.match(RESUME_ID, JOB_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("简历服务暂时不可用，请稍后重试");
    }


    @Test
    @DisplayName("Feign 正常返回：匹配引擎结果正常透传")
    void shouldReturnMatchResultOnSuccess() {
        ResumeBriefDTO brief = ResumeBriefDTO.builder()
                .id(RESUME_ID)
                .userId(16L)
                .fileName("demo.md")
                .rawText("张三，本科，熟悉 Java")
                .build();
        when(resumeClient.getResume(RESUME_ID)).thenReturn(brief);

        MatchResult engineResult = MatchResult.builder()
                .resumeId(RESUME_ID)
                .jobId(JOB_ID)
                .overallScore(82.0)
                .build();
        when(matchEngine.execute(anyString(), any(Job.class))).thenReturn(engineResult);

        var response = matchService.match(RESUME_ID, JOB_ID);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getOverallScore()).isEqualTo(82.0);

        matchResultMapper.insert(engineResult);
    }



    /**
     * FeignException 是抽象类，但 status() 和 contentUTF8() 不是 final，
     * 可以直接 mock。注意：Mockito 对抽象类默认会用默认值（0 / null），
     * 所以必须显式 stub status() 和 contentUTF8()。
     */
    private FeignException mockFeignException(int status, String body) {

        //创建一个假对象
        FeignException ex = org.mockito.Mockito.mock(FeignException.class);

        //当 ex.status() 被调用时，返回 status
        doReturn(status).when(ex).status();
        //当 ex.contentUTF8() 被调用时，返回 body 这个字符串。
        doReturn(body).when(ex).contentUTF8();

        //把假对象返回给调用者
        return ex;
    }

}
