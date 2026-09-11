package com.resumecraft.server.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.common.security.AuthContext;
import com.resumecraft.server.common.cache.CacheKeys;
import com.resumecraft.server.diagnose.domain.Diagnosis;
import com.resumecraft.server.diagnose.domain.DiagnosisMapper;
import com.resumecraft.server.file.FileStorageService;
import com.resumecraft.server.resume.domain.Resume;
import com.resumecraft.server.resume.domain.ResumeMapper;
import com.resumecraft.server.resume.domain.ResumeVersion;
import com.resumecraft.server.resume.domain.ResumeVersionMapper;
import com.resumecraft.server.resume.extractor.ResumeInfoExtractor;
import com.resumecraft.server.resume.parser.ConfidenceParser;
import com.resumecraft.server.resume.parser.OcrStatusDecider;
import com.resumecraft.server.resume.parser.ResumeParser;
import com.resumecraft.server.resume.parser.dto.OcrBlock;
import com.resumecraft.server.resume.parser.dto.OcrParseResult;
import com.resumecraft.server.resume.service.ResumeService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.EmptyFileException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ObjectMapper objectMapper;

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

            //构建Resume基础字段
            Resume resume1 = Resume.builder()
                    .userId(userId)
                    .fileName(originalName)
                    .fileType(fileType)
                    .filePath(filePath)
                    .build();

            //结构化解析(图片OCR)
            if (parser instanceof ConfidenceParser confidenceParser){

                OcrParseResult result = confidenceParser.parseWithBlocks(new ByteArrayInputStream(fileBytes));
                resume1.setRawText(result.getRawText());
                resume1.setOcrConfidence(result.getOverallConfidence());
                resume1.setOcrBlocksJson(objectMapper.writeValueAsString(result.getBlocks()));
                resume1.setOcrStatus(OcrStatusDecider.determineOcrStatus(result));
            }//普通解析（PDF / Word）
            else {
                String rawText = parser.parse(new ByteArrayInputStream(fileBytes));
                resume1.setRawText(rawText);
                resume1.setOcrStatus("OK");
            }

            // 2. 提取信息
            String email = infoExtractor.extractEmail(resume1.getRawText());
            String phone = infoExtractor.extractPhone(resume1.getRawText());


            resumeMapper.insert(resume1);
            log.info("简历保存成功: id={}, 文件={}", resume1.getId(), originalName);

            fillOcrBlocks(resume1);

            return resume1;
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
        fillOcrBlocks(r);
        return r;
    }

    /** 简历列表（仅当前用户，倒序） */
    @Override
    public List<Resume> findAll() {
        List<Resume> list = resumeMapper.selectList(
                new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getUserId, AuthContext.getUserId())
                        .orderByDesc(Resume::getId)
        );

        list.forEach(this::fillOcrBlocks);
        return list;
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

        // 关联数据清理：投递记录关联版本，需先于版本删除；匹配记录直接按简历清理
        // （跨服务但同库，这里用 JdbcTemplate 避免反向依赖 job-match / application 模块）
        int applicationDeleted = jdbcTemplate.update(
                "DELETE FROM application_record WHERE user_id = ? AND resume_version_id IN " +
                        "(SELECT id FROM resume_version WHERE resume_id = ?)",
                resume.getUserId(), id);
        int matchDeleted = jdbcTemplate.update(
                "DELETE FROM match_result WHERE user_id = ? AND resume_id = ?",
                resume.getUserId(), id);
        log.info("删除关联投递记录: {} 条, 匹配记录: {} 条", applicationDeleted, matchDeleted);

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
        evictResumeCaches(id);
        log.info("简历删除成功: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Resume saveFromText(Long userId, String rawText, String fileName) {

        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("简历内容不能为空");
        }

        //生成时间戳
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

        String finalFileName = (fileName == null || fileName.isBlank())
                ? "AI对话创建-" + timestamp + ".md"
                : fileName;

        //构建文件路径
        String filePath = String.format("chat/%d/%s.md", userId, timestamp);

        //构建Resume实体
        Resume resume = Resume.builder()
                .userId(userId)
                .fileName(finalFileName)
                .filePath(filePath)
                .fileType("md")
                .rawText(rawText)
                .ocrStatus("OK")
                .build();
        resumeMapper.insert(resume);
        log.info("对话创建简历成功: id={}, userId={}", resume.getId(), userId);
        return resume;

    }

    @Override
    public Resume updateText(Long resumeId, String rawText) {
        if (rawText == null  || rawText.isBlank()) {
            throw new IllegalArgumentException("简历内容不能为空");
        }

        Resume resume = findById(resumeId);
        resume.setRawText(rawText);
        resume.setOcrStatus("OK");   // 人工核对后置为 OK
        resume.setUpdateTime(LocalDateTime.now());

        resumeMapper.updateById(resume);

        // 正文已变更，清理诊断/优化/匹配缓存，避免返回过期结果
        evictResumeCaches(resumeId);
        fillOcrBlocks(resume);
        return resume;
    }

//    /**
//     * 判断 OCR 状态：整体 < 0.6 或任一 block < 0.5 → REVIEW，否则 OK
//     */
//    private String determineOcrStatus(OcrParseResult result) {
//        Double overall = result.getOverallConfidence();
//        if (overall == null || overall < 0.6) {
//            return "REVIEW";
//        }
//        List<OcrBlock> blocks = result.getBlocks();
//        if (blocks != null) {
//            for (OcrBlock block : blocks) {
//                Double c = block.getConfidence();
//                if (c == null || c < 0.5) {
//                    return "REVIEW";
//                }
//            }
//        }
//        return "OK";
//    }

    /**
     * 清理该简历相关的 Redis 缓存（诊断 / 优化 / 定向优化 / 匹配）。
     *
     * <p>说明：这里用 keys() 是为了实现简单；生产环境应改用 SCAN 迭代，避免大 key 空间阻塞。
     */
    private void evictResumeCaches(Long resumeId) {
        try {
            List<String> keys = new ArrayList<>();
            keys.add(CacheKeys.diagnose(resumeId));
            keys.add(CacheKeys.diagnoseNull(resumeId));
            addKeys(keys, CacheKeys.optimizePattern(resumeId));
            addKeys(keys, CacheKeys.targetedOptimizePattern(resumeId));
            addKeys(keys, CacheKeys.matchPattern(resumeId));

            if (!keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                log.info("已清理简历相关缓存: resumeId={}, 清理key数={}", resumeId, keys.size());
            }
        } catch (Exception e) {
            log.warn("清理简历缓存失败: resumeId={}", resumeId, e);
        }
    }

    private void addKeys(List<String> target, String pattern) {
        var matched = stringRedisTemplate.keys(pattern);
        if (!matched.isEmpty()) {
            target.addAll(matched);
        }
    }

    private Resume fillOcrBlocks(Resume r) {
        if (r != null && r.getOcrBlocksJson() != null && !r.getOcrBlocksJson().isBlank()) {
            try {
                r.setOcrBlocks(objectMapper.readValue(r.getOcrBlocksJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, OcrBlock.class)));
            } catch (Exception e) {
                log.warn("OCR blocks 反序列化失败, resumeId={}", r.getId(), e);
            }
        }
        return r;
    }
}
