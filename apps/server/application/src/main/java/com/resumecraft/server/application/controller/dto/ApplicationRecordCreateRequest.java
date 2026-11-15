package com.resumecraft.server.application.controller.dto;

import com.resumecraft.server.application.domain.ApplicationRecord;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ApplicationRecordCreateRequest {

    @NotNull(message = "请选择已确认的简历版本")
    @Positive(message = "简历版本 ID 必须大于 0")
    private Long resumeVersionId;

    @NotNull(message = "请选择目标岗位")
    @Positive(message = "岗位 ID 必须大于 0")
    private Long jobId;

    @NotNull(message = "请选择投递日期")
    private LocalDate appliedAt;

    @Size(max = 50, message = "投递渠道不能超过 50 个字符")
    private String channel;

    @Pattern(
            regexp = "pending|interviewing|rejected|no_response|accepted",
            message = "投递状态不合法"
    )
    private String status;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String notes;

    public ApplicationRecord toRecord() {
        return ApplicationRecord.builder()
                .resumeVersionId(resumeVersionId)
                .jobId(jobId)
                .appliedAt(appliedAt)
                .channel(channel)
                .status(status == null || status.isBlank() ? "pending" : status)
                .notes(notes)
                .build();
    }
}
