package com.resumecraft.server.optimize.guard;

import com.resumecraft.server.optimize.dto.Overstatement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
@Slf4j
public class OverstatementChecker {

    /**
     * 拔高词黑名单：这些词天然带有"夸大、拔高"的倾向。
     * 命中且原文没有对应词 → 疑似夸大。按业务需要扩充。
     */
    private static final List<String> OVERSTATEMENT_WORDS = List.of(
            "主导", "精通", "海量", "百万级", "落地", "上线", "负责",
            "牵头", "操盘", "从0到1", "从零到一", "业界领先", "顶尖",
            "亿级", "千万级", "大幅提升", "显著提升", "颠覆", "重构"
    );

    /** 预编译标点正则，避免每次 normalize 都重新编译（正则编译有一定开销） */
    private static final Pattern PUNCT_PATTERN =
            Pattern.compile("[\\s\\p{Punct}，。、；：？！（）《》【】“”‘’…—～·]");

    /**
     * 校验模型指认的拔高表述，返回其中"经代码核实确属拔高"的那些。
     *
     * @param rewrittenText 本轮改写后的简历正文
     * @param overstatements 模型指认的疑似拔高列表
     * @return 经核实确属拔高的表述列表（confirmed=true）；被丢弃的不在结果里
     */
    public List<Overstatement> check(String rewrittenText,
                                     List<Overstatement> overstatements) {

        List<Overstatement> confirmed = new ArrayList<>();

        if(overstatements == null || overstatements.isEmpty()) return confirmed;

        String normalizedRewritten = normalize(rewrittenText);

        for (Overstatement item : overstatements) {
            if (item==null)  continue;

            String claim = normalize(item.getClaim());

            // 规则 1：claim 必须能在改写稿里找到，否则是审计幻觉 → 丢弃
            if(claim.isEmpty()||!normalizedRewritten.contains(claim)){
                log.warn("拔高审计幻觉：claim=[{}] 在改写稿中找不到，已丢弃", item.getClaim());
                continue;
            }

            // 规则 2：命中拔高词黑名单 且 原文没有对应词 → 保留为疑似夸大
            String hitWord = matchOverstatementWord(claim);
            if (hitWord == null) {
                continue;
            }

            item.setConfirmed(true);
            confirmed.add(item);
            log.info("疑似拔高：claim=[{}] 命中拔高词=[{}] 原文依据=[{}]",
                    item.getClaim(), hitWord, item.getOriginal());

        }
        return confirmed;

    }

    private String matchOverstatementWord(String normalizedClaim) {
        for (String word : OVERSTATEMENT_WORDS) {
            String w = normalize(word);
            if (!w.isEmpty() && normalizedClaim.contains(w)) return word;
        }
        return null;
    }

    /** 归一化：去掉空白与中英文标点、统一小写（与 FabricationGuard 保持一致） */
    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return PUNCT_PATTERN.matcher(text.toLowerCase()).replaceAll("");
    }
}
