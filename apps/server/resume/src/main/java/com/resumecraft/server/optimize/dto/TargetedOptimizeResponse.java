package com.resumecraft.server.optimize.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetedOptimizeResponse {
    private Long versionId;             // 保存的优化版本 id（resume_version 表）
    /** AI 返回的 key 是 optimizedResume，@JsonAlias 兼容两种命名 */
    @JsonAlias("optimizedResume")
    private String optimizedContent;    // 优化后的 Markdown 全文（前端直接展示）
    private List<String> gaps;          // 缺口提示：缺失技能/经验
    private List<String> changes;       // 改动说明：每处改了什么、为什么
}
