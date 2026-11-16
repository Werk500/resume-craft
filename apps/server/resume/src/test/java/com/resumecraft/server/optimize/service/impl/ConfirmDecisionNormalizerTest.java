package com.resumecraft.server.optimize.service.impl;

import com.resumecraft.server.optimize.dto.ConfirmDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfirmDecisionNormalizerTest {

    @Test
    @DisplayName("大写、小写和混合大小写都能归一化")
    void shouldNormalizeTypeAndActionCaseInsensitively() {
        Map<String, ConfirmDecision> result =
                ConfirmDecisionNormalizer.normalizeAndValidate(List.of(
                        decision("skill", " Linux ", "accept"),
                        decision("Claim", "负责核心系统", "Remove")
                ));

        assertThat(result).containsOnlyKeys(
                "SKILL:Linux",
                "CLAIM:负责核心系统"
        );
        assertThat(result.get("SKILL:Linux").getType()).isEqualTo("SKILL");
        assertThat(result.get("SKILL:Linux").getAction()).isEqualTo("ACCEPT");
        assertThat(result.get("CLAIM:负责核心系统").getAction()).isEqualTo("REMOVE");
    }

    @Test
    @DisplayName("非法类型会在归一化后拒绝")
    void shouldRejectUnknownType() {
        assertThatThrownBy(() -> ConfirmDecisionNormalizer.normalizeAndValidate(
                List.of(decision("UNKNOWN", "Java", "ACCEPT"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("非法处置类型: UNKNOWN");
    }

    @Test
    @DisplayName("大小写不同但语义相同的重复决策会被识别")
    void shouldRejectDuplicateAfterNormalization() {
        assertThatThrownBy(() -> ConfirmDecisionNormalizer.normalizeAndValidate(
                List.of(
                        decision("SKILL", "Linux", "ACCEPT"),
                        decision("skill", "Linux", "accept")
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("处置项重复：SKILL:Linux");
    }

    @Test
    @DisplayName("空 target 和非法 action 会被拒绝")
    void shouldRejectInvalidTargetAndAction() {
        assertThatThrownBy(() -> ConfirmDecisionNormalizer.normalizeAndValidate(
                List.of(decision("SKILL", " ", "ACCEPT"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("处置项缺少 target");

        assertThatThrownBy(() -> ConfirmDecisionNormalizer.normalizeAndValidate(
                List.of(decision("CLAIM", "夸大描述", "IGNORE"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("非法 action: IGNORE，只能是 ACCEPT / REMOVE");
    }

    private ConfirmDecision decision(String type, String target, String action) {
        return ConfirmDecision.builder()
                .type(type)
                .target(target)
                .action(action)
                .build();
    }
}
