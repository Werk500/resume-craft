package com.resumecraft.server.common.metrics;
/** AI 调用类型，作为指标的 type 标签值，避免各处硬编码字符串写错 */
public enum AiCallType {
    CHAT("chat"),
    CHAT_STREAM("chat_stream"),
    OCR("ocr"),
    EMBEDDING("embedding");

    private final String tag;

    AiCallType(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }
}
