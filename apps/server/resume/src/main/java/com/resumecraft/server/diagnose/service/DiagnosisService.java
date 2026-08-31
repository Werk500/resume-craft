package com.resumecraft.server.diagnose.service;

import com.resumecraft.server.common.DiagnosisResponse;
import reactor.core.publisher.Flux;

public interface DiagnosisService {
    DiagnosisResponse diagnose(Long resumeId);

    Flux<String> diagnoseStream(Long resumeId);
}
