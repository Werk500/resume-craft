package com.resumecraft.server.job.service;

import com.resumecraft.server.common.ApiResponse;
import com.resumecraft.server.job.domain.MatchResult;

public interface MatchService {

    ApiResponse<MatchResult> match(Long resumeId, Long jobId);

    ApiResponse<MatchResult> match(Long resumeId, Long jobId, Long versionId, boolean forceRefresh);
}
