package com.resumecraft.server.application.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ApplicationRecordUpdateRequest {

    @NotBlank(message = "投递状态不能为空")
    @Pattern(
            regexp = "pending|interviewing|rejected|no_response|accepted",
            message = "投递状态不合法"
    )
    private String status;
}
