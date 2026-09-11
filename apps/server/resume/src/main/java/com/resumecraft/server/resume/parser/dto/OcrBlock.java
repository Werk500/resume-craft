package com.resumecraft.server.resume.parser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OcrBlock {

    private String text;
    private Double confidence; // 0~1
    private String reason; // 为什么低/高置信
}
