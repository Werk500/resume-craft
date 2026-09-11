package com.resumecraft.server.resume.parser.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UpdateResumeTextRequest {

    @NotBlank(message = "rawText cannot be blank")
    private String rawText;

    // 可选，留存确认后版本
    private List<OcrBlock> blocks;
}
