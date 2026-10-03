package com.resumecraft.server.optimize.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型声称"新写进简历"的一个技能/关键词，以及它声称的原文依据。
 *
 * <p>为什么要让模型交依据：只要能给出依据，就能用代码回到简历原文里核对。
 * 核对不通过的一律进 {@code pendingSkills}（待用户确认），而不是直接信模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddedSkill {

    /** 新写进简历的技能/关键词，例如 "Linux" */
    private String skill;

    /** 模型声称的原文依据（应为简历原文里的原句片段） */
    private String evidence;

    /** 代码校验结果：原文里能否找到依据。true=有依据，false=疑似编造 */
    private Boolean supported;
}
