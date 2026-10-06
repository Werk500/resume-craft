package com.resumecraft.server.optimize.dto;

public enum NextStep {
    READY,            // 可以投递
    NEEDS_CONFIRM,    // 先处置 AI 待确认项
    NEEDS_KEYWORDS,   // 先补关键词
    NOT_MATCHED,      // 岗位不匹配，建议换方向
    DEGRADED          // 降级运行，无法评估
}
