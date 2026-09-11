package com.resumecraft.server.resume.parser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OcrParseResult {

    private List<OcrBlock> blocks;// 每个文本块的详细信息
    private Double overallConfidence;// 整体置信度
    private String rawText; // blocks拼出的全文
}
