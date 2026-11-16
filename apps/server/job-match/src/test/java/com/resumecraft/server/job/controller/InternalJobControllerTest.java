package com.resumecraft.server.job.controller;

import com.resumecraft.server.common.dto.JobBriefDTO;
import com.resumecraft.server.job.domain.Job;
import com.resumecraft.server.job.domain.JobMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalJobControllerTest {

    private static final Long JOB_ID = 201L;

    @Mock
    private JobMapper jobMapper;

    @InjectMocks
    private InternalJobController controller;

    @Test
    @DisplayName("已知岗位只返回投递链路需要的摘要字段")
    void shouldReturnBriefForExistingJob() {
        Job job = Job.builder()
                .id(201L)
                .company("测试公司")
                .title("Java 后端开发")
                .location("杭州")
                .department("研发部")
                .description("这里是不应暴露的长文本 JD")
                .build();
        when(jobMapper.selectById(JOB_ID)).thenReturn(job);

        JobBriefDTO result = controller.getJob(JOB_ID);

        assertThat(result.getId()).isEqualTo(201L);
        assertThat(result.getCompany()).isEqualTo("测试公司");
        assertThat(result.getTitle()).isEqualTo("Java 后端开发");
        assertThat(result.getLocation()).isEqualTo("杭州");
        verify(jobMapper).selectById(JOB_ID);
    }

    @Test
    @DisplayName("岗位不存在时返回业务异常")
    void shouldThrowWhenJobDoesNotExist() {
        when(jobMapper.selectById(JOB_ID)).thenReturn(null);

        assertThatThrownBy(() -> controller.getJob(JOB_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("岗位不存在: 201");
    }
}
