package com.resumecraft.server.optimize.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepRecord {

    private String name;        // baseline-score / rewrite-iterate / audit-parallel / assemble
    private Boolean ai;         // 是否调用模型
    private Long durationMs;
    private String status;      // OK / DEGRADED（审计失败降级）/ FAILED
    private String detail;      // 一句话说明，如 "覆盖率 16.7 → 55.6（1 轮，保留 1 次）"
}
