package com.resumecraft.server.optimize.service.impl;

import com.resumecraft.server.optimize.dto.ConfirmDecision;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 确认决策的纯校验与归一化逻辑。
 */
final class ConfirmDecisionNormalizer {

    private ConfirmDecisionNormalizer() {
    }

    static Map<String, ConfirmDecision> normalizeAndValidate(
            List<ConfirmDecision> decisions
    ) {
        Map<String, ConfirmDecision> decisionMap = new LinkedHashMap<>();

        for (ConfirmDecision decision : decisions) {
            if (decision == null) {
                throw new IllegalArgumentException("处置项不能为空");
            }

            String type = normalizeCode(decision.getType());
            String target = decision.getTarget() == null
                    ? "" : decision.getTarget().trim();
            String action = normalizeCode(decision.getAction());

            if (!"SKILL".equals(type) && !"CLAIM".equals(type)) {
                throw new IllegalArgumentException(
                        "非法处置类型: " + decision.getType()
                );
            }
            if (target.isBlank()) {
                throw new IllegalArgumentException("处置项缺少 target");
            }
            if (!"ACCEPT".equals(action) && !"REMOVE".equals(action)) {
                throw new IllegalArgumentException(
                        "非法 action: " + decision.getAction()
                                + "，只能是 ACCEPT / REMOVE"
                );
            }

            decision.setType(type);
            decision.setTarget(target);
            decision.setAction(action);

            String key = type + ":" + target;
            if (decisionMap.putIfAbsent(key, decision) != null) {
                throw new IllegalArgumentException("处置项重复：" + key);
            }
        }

        return decisionMap;
    }

    private static String normalizeCode(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
