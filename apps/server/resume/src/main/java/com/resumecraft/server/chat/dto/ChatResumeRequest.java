package com.resumecraft.server.chat.dto;

import lombok.Data;
import com.resumecraft.server.ai.dto.ChatMessage;

import java.util.List;

@Data
public class ChatResumeRequest {

    private List<ChatMessage> messages;
    private String targetJob;
    /**
     * 简历模板标识，取值：campus | tech | intern | experienced
     * 缺省按 campus 处理
     */
    private String templateId;
}
