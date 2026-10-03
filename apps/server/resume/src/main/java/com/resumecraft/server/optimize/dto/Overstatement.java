package com.resumecraft.server.optimize.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Overstatement {

    private String claim;      // 改写稿里的可疑表述（必须能在改写稿里找到）
    private String original;   // 原文对应表述；原文没有就为空
    private String reason;     // 为什么算拔高（一句话）
    private Boolean confirmed; // 代码核实结果
}
