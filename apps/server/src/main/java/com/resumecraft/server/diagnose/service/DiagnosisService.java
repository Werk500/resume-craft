package com.resumecraft.server.diagnose.service;

import com.resumecraft.server.common.DiagnosisResponse;

public interface DiagnosisService {
    DiagnosisResponse diagnose(Long resumeId);
}
