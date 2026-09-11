package com.resumecraft.server.resume.parser;

import com.resumecraft.server.resume.parser.dto.OcrBlock;
import com.resumecraft.server.resume.parser.dto.OcrParseResult;

import java.util.List;

/**
 * OCR 状态判定器。
 */
public final class OcrStatusDecider {

    /** 整体置信度阈值：低于此值判定为 REVIEW */
    public static final double OVERALL_THRESHOLD = 0.6;

    /** 单块置信度阈值：低于此值判定为 REVIEW */
    public static final double BLOCK_THRESHOLD = 0.5;

    /** 状态：通过 */
    public static final String STATUS_OK = "OK";

    /** 状态：需人工复核 */
    public static final String STATUS_REVIEW = "REVIEW";

    /** 工具类，禁止实例化 */
    private OcrStatusDecider() {
    }

    /**
     * 判断 OCR 状态：整体 &lt; 0.6 或任一 block &lt; 0.5 → REVIEW，否则 OK。
     *
     * @param result OCR 解析结果，可为 null
     * @return {@link #STATUS_OK} 或 {@link #STATUS_REVIEW}
     */
    public static String determineOcrStatus(OcrParseResult result) {
        // 入参为 null，视为无法判定 → 走复核
        if (result == null) {
            return STATUS_REVIEW;
        }
        // 1. 整体置信度检查
        Double overall = result.getOverallConfidence();
        if (overall == null || overall < OVERALL_THRESHOLD) {
            return STATUS_REVIEW;
        }
        // 2. 逐块置信度检查
        List<OcrBlock> blocks = result.getBlocks();
        if (blocks != null) {
            for (OcrBlock block : blocks) {
                if (block == null) {
                    return STATUS_REVIEW;
                }
                Double c = block.getConfidence();
                if (c == null || c < BLOCK_THRESHOLD) {
                    return STATUS_REVIEW;
                }
            }
        }

        return STATUS_OK;
    }
}