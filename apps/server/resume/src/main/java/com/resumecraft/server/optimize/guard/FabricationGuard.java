package com.resumecraft.server.optimize.guard;

import com.resumecraft.server.optimize.dto.AddedSkill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 防造假校验：模型说"我把 Linux 写进简历了，依据是原文第 3 句"——
 * 这个依据必须能在<b>简历原文</b>里找到，否则这个词不许算数。
 *
 * <h3>为什么需要它</h3>
 * 实测：只靠提示词约束，模型确实不会凭空加 "C++/PHP/内容电商" 这种露眼的词，
 * 但会加 "Linux / NoSQL / RPC" 这类"看起来合理、原文却没有"的通用技能词。
 * 简历上多写一个自己不会的技能，面试当场就会翻车，所以必须由代码兜底。
 *
 * <h3>判定规则（宽松一点，避免误杀）</h3>
 * <ol>
 *   <li>技能词本身在原文出现过 → 有依据；</li>
 *   <li>否则看模型给的 evidence：把两边都做"去空格标点 + 小写"归一化后，
 *       计算 evidence 的字符二元组在原文中的命中率，≥60% 视为有依据
 *       （容忍模型改写措辞，但要求实质重合）；</li>
 *   <li>evidence 缺失或过短（&lt;6 字）→ 依据不足。</li>
 * </ol>
 *
 * <p>注意：校验基准必须是<b>用户原始简历</b>，不能是上一轮的改写结果——
 * 否则第一轮漏进来的无依据内容会在第二轮被当成"原文"而洗白。
 */
@Slf4j
@Component
public class FabricationGuard {

    /** 证据与原文的二元组重合率阈值 */
    private static final double EVIDENCE_OVERLAP_THRESHOLD = 0.6;

    /** 证据太短就不算数（"熟悉 Java" 这种片段没法验证） */
    private static final int MIN_EVIDENCE_LENGTH = 6;

    /**
     * 校验并原地标记 supported；返回其中"没有依据"的那些。
     *
     * @param originalText 用户原始简历正文（校验基准）
     * @param addedSkills  模型本轮声称新增的技能
     */
    public List<AddedSkill> verify(String originalText, List<AddedSkill> addedSkills) {
        List<AddedSkill> unsupported = new ArrayList<>();
        if (addedSkills == null || addedSkills.isEmpty()) {
            return unsupported;
        }

        String normalizedOriginal = normalize(originalText);

        for (AddedSkill skill : addedSkills) {
            if (skill == null) continue;

            boolean supported = isSupported(normalizedOriginal, skill);
            skill.setSupported(supported);

            if (!supported) {
                unsupported.add(skill);
                log.warn("疑似编造：技能=[{}] 依据=[{}]（原文中找不到），已移入待确认列表",
                        skill.getSkill(), skill.getEvidence());
            }
        }
        return unsupported;
    }

    private boolean isSupported(String normalizedOriginal, AddedSkill skill) {
        String skillName = normalize(skill.getSkill());

        // 1) 技能词本身在原文里出现过 → 最硬的依据
        if (!skillName.isEmpty() && normalizedOriginal.contains(skillName)) {
            return true;
        }

        // 2) 用 evidence 与原文做二元组重合度比对
        String evidence = normalize(skill.getEvidence());
        if (evidence.length() < MIN_EVIDENCE_LENGTH) {
            return false;
        }
        double overlap = bigramOverlap(evidence, normalizedOriginal);
        return overlap >= EVIDENCE_OVERLAP_THRESHOLD;
    }

    /** 字符二元组命中率：evidence 里每个相邻两字组合，在原文中能否找到 */
    private double bigramOverlap(String evidence, String text) {
        if (evidence.length() < 2) {
            return 0d;
        }
        int hit = 0;
        int total = 0;
        for (int i = 0; i + 2 <= evidence.length(); i++) {
            total++;
            if (text.contains(evidence.substring(i, i + 2))) {
                hit++;
            }
        }
        return total == 0 ? 0d : (double) hit / total;
    }

    /** 归一化：去掉空白与中英文标点、统一小写，让"Java、"与"java"能对上 */
    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase()
                .replaceAll("[\\s\\p{Punct}，。、；：？！（）《》【】“”‘’…—～·]", "");
    }
}
