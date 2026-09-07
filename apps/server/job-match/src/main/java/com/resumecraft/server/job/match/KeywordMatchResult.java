package com.resumecraft.server.job.match;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 关键词匹配的中间计算结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeywordMatchResult {


    /** 覆盖度 0~100，计算过程用 double 保留精度 */
    private double score;

    /** JD 核心关键词总数 */
    private int total;

    /** 命中数量 */
    private int hit;

    /** 每个关键词是否命中（前端渲染命中/缺失标签用） */
    private List<KeywordHit> hits;

    /** 未命中关键词 */
    private List<String> missingKeywords;
}
