package com.resumecraft.server.diagnose.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)// AI 多吐字段不报错
@Builder
@NoArgsConstructor  // 无参构造器
@AllArgsConstructor  // 全参构造器
public class DiagnosisResult {

    private Double totalScore;
    private Double completenessScore;
    private Double expressionScore;
    private Double matchScore;
    private List<String> suggestions;
}
