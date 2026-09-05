package com.resumecraft.server.optimize.service;

import com.resumecraft.server.resume.domain.ResumeVersion;

import java.util.List;

public interface ResumeVersionService {
    List<ResumeVersion> list(Long resumeId);

    Void delete(Long id);
}
