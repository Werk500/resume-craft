package com.resumecraft.server.optimize.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RewriteRequest {

    /** 选中的原段落 */
    @NotBlank(message = "原文不能为空")
    private String original;

    /** 侧重方向：DATA(数据成果) / METHOD(过程方法) / IMPACT(项目影响力) */
    @NotBlank(message = "方向不能为空")
    private String focus;
}