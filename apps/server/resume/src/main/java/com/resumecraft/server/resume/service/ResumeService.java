package com.resumecraft.server.resume.service;

import com.resumecraft.server.resume.domain.Resume;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ResumeService {
    Resume parseAndSave(MultipartFile file);

    Resume findById(Long id);

    List<Resume> findAll();

    void delete(Long id);

    Resume saveFromText(Long userId, String rawText, String fileName);
}
