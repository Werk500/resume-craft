package com.resumecraft.server.optimize.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptimizeIteration {
    private int round;
    private Double keywordCoverage;        // 这一轮候选稿的覆盖率
    private Double gain;                   // 相比上一轮的变化（可能为负 → 说明改差了）
    private List<String> missingKeywords;  // 还缺什么
    private List<String> addedKeywords;    // 这一轮新覆盖了什么（相对上一轮）
    private boolean kept;                  // 是否被采纳（false = 回滚了）
}
