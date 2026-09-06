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

}
