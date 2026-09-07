package com.resumecraft.server.job.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class MatchBodyRequest {

    @NotNull
    Long resumeId;
    @NotNull
    Long jobId;

    private Long versionId;      // 传了=匹配某个优化版本，不传=匹配简历原文
    private Boolean forceRefresh; // true=跳过缓存，用于优化前后对比

}
