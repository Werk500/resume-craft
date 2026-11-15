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
    /** ACCEPT=保留正文；REMOVE=用户已将对应内容从正文删除 */
    private String action;
    private String note;    // 可选：用户备注
}
