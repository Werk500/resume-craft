package com.resumecraft.server.optimize.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveRequest {
    @NotBlank(message = "内容不能为空")
    private String content;

    private String versionName;
}
