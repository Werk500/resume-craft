package com.resumecraft.server.optimize.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PendingSnapshot {

    private List<AddedSkill> skills;      // 无依据技能
    private List<Overstatement> claims;   // 疑似夸大

    /** 两项都空 → 视为"没有待确认项"，调用方据此写 null */
    public boolean isEmpty() {
        return (skills == null || skills.isEmpty())
                && (claims == null || claims.isEmpty());
    }
}
