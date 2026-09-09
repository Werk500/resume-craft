package com.resumecraft.server.resume.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateFromTextRequest {
    private String fileName;

    @NotBlank(message = "rawText cannot be blank")
    private String rawText;
}
