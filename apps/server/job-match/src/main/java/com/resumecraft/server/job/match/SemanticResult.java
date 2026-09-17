package com.resumecraft.server.job.match;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SemanticResult {

    private double score;
    private String reason;
    private String mode;      // ← 新增：EMBEDDING / AI_APPROX / RULE_FALLBACK
}
