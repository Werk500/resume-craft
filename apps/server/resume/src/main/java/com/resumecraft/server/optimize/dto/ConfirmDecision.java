package com.resumecraft.server.optimize.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmDecision {
    private String type;    // SKILL | CLAIM，分别对应 pendingSkills / pendingClaims
    private String target;  // 待确认项的名字（技能词或改写稿原话）
    private String action;  // ACCEPT=保留 / REMOVE=不认领（已删或需删）
    private String note;    // 可选：用户备注
}
