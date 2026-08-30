package com.resumecraft.server.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resumecraft.server.file.FileStorageService;
import com.resumecraft.server.resume.domain.Resume;
import com.resumecraft.server.resume.domain.ResumeMapper;
import com.resumecraft.server.resume.extractor.ResumeInfoExtractor;
import com.resumecraft.server.resume.parser.ResumeParser;
import com.resumecraft.server.resume.service.ResumeService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.EmptyFileException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;


/**
 * 简历服务实现：上传 → 存储 → 解析 → 提取关键信息 → 入库。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {
    private static final long STREAM_THRESHOLD = 10 * 1024 * 1024;  // 10MB

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    private static final String TEMP_FILE_PREFIX = "resume_";

    @Resource
    private List<ResumeParser> parsers;
    @Resource
    private FileStorageService fileStorageService;
    @Resource
    private ResumeMapper resumeMapper;
    @Resource
    private ResumeInfoExtractor infoExtractor;

    /**
     * 上传并解析简历。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Resume parseAndSave(MultipartFile file) {

        validateFile(file);

        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            originalName = "unknown";
        }

        log.info("开始解析简历: {}, 大小: {} bytes", originalName, file.getSize());

        try {
            // 1. 解析
            String fileType = resolveFileType(originalName);
            ResumeParser parser = findParser(fileType);

            byte[] fileBytes = file.getBytes();

            String filePath = fileStorageService.store(new ByteArrayInputStream(fileBytes), originalName);

            String rawText = parser.parse(new ByteArrayInputStream(fileBytes));

            // 2. 提取信息
            String email = infoExtractor.extractEmail(rawText);
            String phone = infoExtractor.extractPhone(rawText);

            // 3. 构建并保存（MyBatis-Plus）
            Resume resume = Resume.builder()
                    .fileName(originalName)
                    .filePath(filePath)
                    .fileType(fileType)
                    .rawText(rawText)
                    .parsedEmail(email)
                    .parsedPhone(phone)
                    .build();
            resumeMapper.insert(resume);
            log.info("简历保存成功: id={}, 文件={}", resume.getId(), originalName);
            return resume;
        } catch (IOException e) {
            log.error("简历处理失败: {}", originalName, e);
            throw new RuntimeException(e);
        }
    }



    /** 按 ID 查询简历（含解析结果） */
    @Override
    public Resume findById(Long id) {
        Resume r = resumeMapper.selectById(id);
        if (r == null) {
            throw new IllegalArgumentException("简历不存在：id=" + id);
        }
        return r;
    }

    /** 简历列表（倒序） */
    @Override
    public List<Resume> findAll() {
        return resumeMapper.selectList(
                new LambdaQueryWrapper<Resume>().orderByDesc(Resume::getId));
    }

    // ---- 私有工具 ---- //

    private String resolveFileType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new IllegalArgumentException("无法识别文件类型：" + fileName);
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    private ResumeParser findParser(String fileType) {
        return parsers.stream()
                .filter(p -> p.supports(fileType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "不支持的文件类型：" + fileType + "，目前支持 pdf / docx / 图片(OCR 待集成)"));
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new EmptyFileException(new File("上传的文件为空"));
        }
        long size = file.getSize();
        if (size > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "文件过大: " + size / 1024 / 1024 + "MB，最大允许 " +
                            MAX_FILE_SIZE / 1024 / 1024 + "MB");
        }
    }


}