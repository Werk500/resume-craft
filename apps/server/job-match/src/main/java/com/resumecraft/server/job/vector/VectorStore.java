package com.resumecraft.server.job.vector;


import com.resumecraft.server.ai.EmbeddingService;
import com.resumecraft.server.common.util.VectorUtils;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 向量库门面：负责"取向量 → 没有就生成并落库"的编排。
 *
 * 对上层（MatchEngine）只暴露 getOrCreateXxxVector 两个方法，
 * 所有降级判断都收敛在这里，MatchEngine 不需要知道 pgvector 的存在。
 */
@Slf4j
@Component
public class VectorStore {

    @Resource
    private EmbeddingRepository embeddingRepository;
    @Resource
    private EmbeddingService embeddingService;

    @Value("${app.vector.enabled:true}")
    private boolean vectorEnabled;

    /**
     * 取简历向量；缺失或内容变更时重新生成。
     *
     * @return 向量；任一步失败都返回 null，由上层走 AI 近似降级
     */
    public float[] getOrCreateResumeVector(Long resumeId, Long versionId, Long userId, String resumeText){
        if (!vectorEnabled || !embeddingService.available()
                || resumeId == null || resumeText == null || resumeText.isBlank()) {
            return null;
        }

        long vid = versionId == null ? 0L : versionId;

        try {
            //计算当前内容的hash
            String hash = sha256(resumeText);

            //从数据库查就hash
            String cachedHash = embeddingRepository.selectResumeHash(resumeId, vid);
            if (hash.equals(cachedHash)){
                float[] cached = VectorUtils.fromPgVector(embeddingRepository.selectResumeVector(resumeId, vid));
                if(cached != null){
                    return cached;
                }
            }
            //文本向量化
            float[] generated = embeddingService.embed(resumeText);
            if(generated == null){
                return null;
            }

            //先落库，再返回
            embeddingRepository.upsertResumeVector(resumeId, vid, userId, hash,
                    VectorUtils.toPgVector(generated),embeddingService.modelName());
            return generated;
        } catch (Exception e) {
            log.warn("简历向量获取失败，降级为 AI 近似打分: resumeId={}, versionId={}, err={}",
                    resumeId, vid, e.getMessage());
            return null;
        }
    }

    /** 取岗位向量；缺失或内容变更时重新生成 */
    public float[] getOrCreateJobVector(Long jobId, String jobText) {
        if (!vectorEnabled || !embeddingService.available() || jobText == null || jobText.isBlank()) {
            return null;
        }
        try {
            String hash = sha256(jobText);

            String cachedHash = embeddingRepository.selectJobHash(jobId);
            if (hash.equals(cachedHash)) {
                float[] cached = VectorUtils.fromPgVector(embeddingRepository.selectJobVector(jobId));
                if (cached != null) {
                    return cached;
                }
            }

            float[] generated = embeddingService.embed(jobText);
            if (generated == null) {
                return null;
            }
            embeddingRepository.upsertJobVector(jobId, hash,
                    VectorUtils.toPgVector(generated), embeddingService.modelName());
            return generated;

        } catch (Exception e) {
            log.warn("岗位向量获取失败，降级为 AI 近似打分: jobId={}, err={}", jobId, e.getMessage());
            return null;
        }
    }

    /**
     * 将任意一段文字，算成一个固定长度的"指纹"字符串。
     * @param text
     * @return
     */
    private String sha256(String text) {

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // SHA-256 一定存在，走到这里说明环境异常
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 删除某份简历的全部向量。
     *
     * <p>向量表在 PostgreSQL，简历服务连不到，因此由它通过 /internal 接口触发本方法。
     * 删除失败不抛异常：向量是可重新生成的派生数据，残留一条孤儿向量
     * 不影响正确性（下次同 resumeId 建简历会覆盖），但会污染存储，故记 warn。
     *
     * @return 实际删除行数；向量功能未启用时返回 0
     */
    public int deleteResumeVectors(Long resumeId) {
        if (resumeId == null) {
            return 0;
        }
        try {
            return embeddingRepository.deleteResumeVectors(resumeId);
        } catch (Exception e) {
            log.warn("删除简历向量失败（不影响简历删除本身）: resumeId={}, err={}",
                    resumeId, e.getMessage());
            return 0;
        }
    }
}
