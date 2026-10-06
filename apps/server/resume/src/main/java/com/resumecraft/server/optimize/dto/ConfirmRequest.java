package com.resumecraft.server.optimize.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ConfirmRequest {
    @NotEmpty(message = "必须给出每一项的处置结果")
    private List<ConfirmDecision> decisions;

    /** 用户删改过正文时整段覆盖；不传则以原正文为准 */
    private String content;

}
