package com.resumecraft.server.chat.dto;

import lombok.Data;
import com.resumecraft.server.ai.dto.ChatMessage;

import java.util.List;

@Data
public class ChatResumeRequest {

    private List<ChatMessage> messages;
    private String targetJob;
}
