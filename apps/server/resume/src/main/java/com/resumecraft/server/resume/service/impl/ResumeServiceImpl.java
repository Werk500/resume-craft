package com.resumecraft.server.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resumecraft.server.common.security.AuthContext;
import com.resumecraft.server.diagnose.domain.Diagnosis;
import com.resumecraft.server.diagnose.domain.DiagnosisMapper;
import com.resumecraft.server.file.FileStorageService;
import com.resumecraft.server.resume.domain.Resume;
import com.resumecraft.server.resume.domain.ResumeMapper;
import com.resumecraft.server.resume.domain.ResumeVersion;
import com.resumecraft.server.resume.domain.ResumeVersionMapper;
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
    @Resource
    private DiagnosisMapper diagnosisMapper;
    @Resource
    private ResumeVersionMapper resumeVersionMapper;

    /**
     * 上传并解析简历。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Resume parseAndSave(MultipartFile file) {

        //获取当前用户ID
        Long userId = AuthContext.getUserId();
        log.info("当前用户ID: {} 上传简历", userId);

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
                    .userId(userId)
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



    /** 按 ID 查询简历（含解析结果），校验归属防越权 */
    @Override
    public Resume findById(Long id) {
        Resume r = resumeMapper.selectById(id);
        if (r == null || !r.getUserId().equals(AuthContext.getUserId())) {
            // 不存在或非本人资源统一返回"不存在"，避免泄露资源是否存在
            throw new IllegalArgumentException("简历不存在：id=" + id);
        }
        return r;
    }

    /** 简历列表（仅当前用户，倒序） */
    @Override
    public List<Resume> findAll() {
        return resumeMapper.selectList(
                new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getUserId, AuthContext.getUserId())
                        .orderByDesc(Resume::getId));
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        //1.查询简历（含归属校验）
        Resume resume = findById(id);
        log.info("开始删除简历: id={}, userId={}, fileName={}",
                id, resume.getUserId(), resume.getFileName());

        //2.删除关联的简历版本记录
        int versionDeleted = resumeVersionMapper.delete(
                new LambdaQueryWrapper<ResumeVersion>()
                        .eq(ResumeVersion::getResumeId, id));
        log.info("删除简历版本记录: {} 条", versionDeleted);

        //3.删除关联的诊断记录
        int diagnosisDeleted = diagnosisMapper.delete(
                new LambdaQueryWrapper<Diagnosis>()
                        .eq(Diagnosis::getResumeId, id));
        log.info("删除诊断记录: {} 条", diagnosisDeleted);

        //4.删除简历主记录
        int resumeDeleted = resumeMapper.deleteById(id);
        log.info("删除简历主记录: {} 条", resumeDeleted);

        //5.删除磁盘上的文件（失败只记日志，不影响已提交的数据库事务）
        try {
            fileStorageService.delete(resume.getFilePath());
            log.info("文件删除成功: {}", resume.getFilePath());
        } catch (Exception e) {
            log.error("文件删除失败: filePath={}, error={}", resume.getFilePath(), e.getMessage(), e);
        }
        log.info("简历删除成功: id={}", id);
    }
}
