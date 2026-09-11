package com.resumecraft.server.resume.parser;

import com.resumecraft.server.resume.parser.dto.OcrBlock;
import com.resumecraft.server.resume.parser.dto.OcrParseResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;


/**
 * OcrStatusDecider 阈值边界测试。
 */
public class OcrStatusDeciderTest {

    @Test
    @DisplayName("overall=0.6 恰好等于阈值 → OK")
    void overallExactlyThreshold_shouldBeOk() {
        OcrParseResult result = buildResult(0.6, 0.9);
        assertThat(OcrStatusDecider.determineOcrStatus(result))
                .isEqualTo(OcrStatusDecider.STATUS_OK);
    }

    @Test
    @DisplayName("overall 略低于 0.6 → REVIEW")
    void overallJustBelowThreshold_shouldBeReview() {
        OcrParseResult result = buildResult(0.5999, 0.9);
        assertThat(OcrStatusDecider.determineOcrStatus(result))
                .isEqualTo(OcrStatusDecider.STATUS_REVIEW);
    }

    @Test
    @DisplayName("overall 明显低于 0.6（0.3）→ REVIEW")
    void overallWellBelowThreshold_shouldBeReview() throws Exception {
        String status = OcrStatusDecider.determineOcrStatus(buildResult(0.3, 0.9));
        assertThat(status).isEqualTo("REVIEW");
    }


    @Test
    @DisplayName("overall=null → REVIEW")
    void overallNull_shouldBeReview() throws Exception {
        String status = OcrStatusDecider.determineOcrStatus(buildResult(null, 0.9));
        assertThat(status).isEqualTo("REVIEW");
    }

    @Test
    @DisplayName("overall 高于阈值 → OK")
    void overallAboveThreshold_shouldBeOk() throws Exception {
        String status = OcrStatusDecider.determineOcrStatus(buildResult(0.95, 0.9));
        assertThat(status).isEqualTo("OK");
    }



    @Test
    @DisplayName("单块 confidence=0.5 恰好等于阈值 → OK")
    void blockExactlyThreshold_shouldBeOk() throws Exception {
        String status = OcrStatusDecider.determineOcrStatus(buildResult(0.9, 0.5));
        assertThat(status).isEqualTo("OK");
    }

    @Test
    @DisplayName("单块 confidence 略低于 0.5（0.4999）→ REVIEW")
    void blockJustBelowThreshold_shouldBeReview() throws Exception {
        String status = OcrStatusDecider.determineOcrStatus(buildResult(0.9, 0.4999));
        assertThat(status).isEqualTo("REVIEW");
    }

    @Test
    @DisplayName("多块中有任意一块低于 0.5 → REVIEW")
    void anyBlockBelowThreshold_shouldBeReview() throws Exception {
        String status = OcrStatusDecider.determineOcrStatus(buildResult(0.9, 0.8, 0.3, 0.9));
        assertThat(status).isEqualTo("REVIEW");
    }

    @Test
    @DisplayName("多块全部 >= 0.5 → OK")
    void allBlocksAboveThreshold_shouldBeOk() throws Exception {
        String status = OcrStatusDecider.determineOcrStatus(buildResult(0.9, 0.5, 0.6, 0.9));
        assertThat(status).isEqualTo("OK");
    }

    /** 构造一个 OcrParseResult，overall + blocks 可控 */
    private OcrParseResult buildResult(Double overall, Double... blockConfidences) {
        OcrParseResult result = new OcrParseResult();
        result.setOverallConfidence(overall);
        if (blockConfidences != null) {
            List<OcrBlock> blocks = new ArrayList<>();
            for (Double c : blockConfidences) {
                OcrBlock b = new OcrBlock();
                b.setConfidence(c);
                blocks.add(b);
            }
            result.setBlocks(blocks);
        }
        return result;
    }
}
