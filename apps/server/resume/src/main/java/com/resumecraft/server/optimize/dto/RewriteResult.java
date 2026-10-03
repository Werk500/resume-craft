package com.resumecraft.server.optimize.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 改写节点的结构化输出。
 *
 * <p>为什么不再让模型直接返回纯 Markdown：纯文本无法区分"它到底新加了什么"，
 * 也就没法做防造假校验。改成 JSON 之后，"改了什么 + 依据是什么"变成可编程的数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RewriteResult {

    /** 优化后的完整 Markdown 简历 */
    @JsonAlias("optimized_resume")
    private String optimizedResume;

    /** 本轮新写进简历的技能及依据（没有则空列表） */
    @Builder.Default
    private List<AddedSkill> addedSkills = new ArrayList<>();
}
