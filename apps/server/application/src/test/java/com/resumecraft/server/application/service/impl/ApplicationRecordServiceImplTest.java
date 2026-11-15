package com.resumecraft.server.application.service.impl;

import com.resumecraft.server.application.domain.ApplicationRecord;
import com.resumecraft.server.application.domain.ApplicationRecordMapper;
import com.resumecraft.server.application.gateway.ResumeVersionGateway;
import com.resumecraft.server.common.dto.ResumeVersionDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationRecordServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long VERSION_ID = 101L;
    private static final Long JOB_ID = 201L;

    @Mock
    private ApplicationRecordMapper applicationRecordMapper;

    @Mock
    private ResumeVersionGateway resumeVersionGateway;

    @InjectMocks
    private ApplicationRecordServiceImpl service;

    @BeforeEach
    void setUpAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of())
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("本人已确认版本可以投递，并强制覆盖归属用户")
    void shouldCreateWithConfirmedOwnedVersion() {
        when(resumeVersionGateway.getVersion(VERSION_ID))
                .thenReturn(version(USER_ID, "CONFIRMED", null));

        ApplicationRecord input = record("pending");
        input.setId(999L);
        input.setUserId(888L);

        service.create(input);

        ArgumentCaptor<ApplicationRecord> captor =
                ArgumentCaptor.forClass(ApplicationRecord.class);
        verify(applicationRecordMapper).insert(captor.capture());
        ApplicationRecord saved = captor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("有待确认项且未确认的版本不能投递")
    void shouldRejectDraftVersionWithPendingItems() {
        when(resumeVersionGateway.getVersion(VERSION_ID))
                .thenReturn(version(USER_ID, "DRAFT", "{\"skills\":[{\"skill\":\"Go\"}]}"));

        assertThatThrownBy(() -> service.create(record("pending")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("该版本还有 AI 改动未确认，请确认后再投递");

        verify(applicationRecordMapper, never()).insert(
                org.mockito.ArgumentMatchers.any(ApplicationRecord.class)
        );
    }

    @Test
    @DisplayName("不能使用其他用户的版本投递")
    void shouldRejectVersionOwnedByAnotherUser() {
        when(resumeVersionGateway.getVersion(VERSION_ID))
                .thenReturn(version(USER_ID + 1, "CONFIRMED", null));

        assertThatThrownBy(() -> service.create(record("pending")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("简历版本不存在或无权使用");

        verifyNoInteractions(applicationRecordMapper);
    }

    @Test
    @DisplayName("历史版本无待确认项时兼容放行")
    void shouldAllowLegacyVersionWithoutPendingItems() {
        when(resumeVersionGateway.getVersion(VERSION_ID))
                .thenReturn(version(USER_ID, null, null));

        service.create(record("pending"));

        verify(applicationRecordMapper).insert(
                org.mockito.ArgumentMatchers.any(ApplicationRecord.class)
        );
    }

    @Test
    @DisplayName("非法状态在写库前被拒绝")
    void shouldRejectInvalidStatus() {
        when(resumeVersionGateway.getVersion(VERSION_ID))
                .thenReturn(version(USER_ID, "CONFIRMED", null));

        assertThatThrownBy(() -> service.create(record("offer")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("投递状态不合法: offer");

        verify(applicationRecordMapper, never()).insert(
                org.mockito.ArgumentMatchers.any(ApplicationRecord.class)
        );
    }

    private ApplicationRecord record(String status) {
        return ApplicationRecord.builder()
                .resumeVersionId(VERSION_ID)
                .jobId(JOB_ID)
                .appliedAt(LocalDate.now())
                .status(status)
                .build();
    }

    private ResumeVersionDTO version(Long userId, String status, String pendingJson) {
        return ResumeVersionDTO.builder()
                .id(VERSION_ID)
                .userId(userId)
                .status(status)
                .pendingJson(pendingJson)
                .build();
    }
}
