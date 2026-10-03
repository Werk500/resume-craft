package com.resumecraft.server.common.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ScoreRequest {

    private String resumeText;   // 要打分的那一版文本
    private Long jobId;
//    private Boolean includeSemantic = false;  // 闭环里不开，省一次模型/向量调用
}
