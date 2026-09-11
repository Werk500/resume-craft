package com.resumecraft.server.chat.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.dto.ChatMessage;
import com.resumecraft.server.ai.impl.PromptTemplates;
import com.resumecraft.server.ai.AiService;
import com.resumecraft.server.chat.dto.ChatResumeRequest;
import com.resumecraft.server.common.ApiResponse;
import com.resumecraft.server.common.security.AuthContext;
import com.resumecraft.server.resume.domain.Resume;
import com.resumecraft.server.resume.dto.CreateFromTextRequest;
import com.resumecraft.server.resume.service.ResumeService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/chat")
@Slf4j
public class ResumeChatController {

    @Resource
    private AiService aiService;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private ResumeService resumeService;

    private static final int MAX_MESSAGES = 50;
    private static final int MAX_TOTAL_CHARS = 20_000;



    @PostMapping(value = "/resume/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatResumeRequest request) {
        List<ChatMessage> messages = request.getMessages();
        validateMessages(messages);

        String userPrompt = PromptTemplates.buildUserPrompt(messages, request.getTargetJob());
        log.info("对话创建简历: 消息数={}, targetJob={}",
                messages.size(), request.getTargetJob());
        return aiService.chatStream(PromptTemplates.RESUME_CREATE_SYSTEM,userPrompt)
                // 包成 SSE JSON，避免 Markdown 换行在流式传输时丢失
                .map(this::toSseJson);
    }

    private void validateMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("消息不能为空");
        }

        if (messages.size() > MAX_MESSAGES) {
            throw new IllegalArgumentException("消息数量超过限制：" + MAX_MESSAGES);
        }

        int totalChars = 0;
        for (ChatMessage message : messages) {
            String role = message.getRole();
            if (!"user".equals(role) && !"assistant".equals(role)) {
                throw new IllegalArgumentException("role 只能是 user 或 assistant");
            }
            String content = message.getContent();
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("消息内容不能为空");
            }
            totalChars += content.length();
        }

        if (totalChars > MAX_TOTAL_CHARS) {
            throw new IllegalArgumentException("对话内容过长，请精简后重试");
        }

        // 最后一轮必须由用户发起，否则模型会重复输出
        if (!"user".equals(messages.get(messages.size() - 1).getRole())) {
            throw new IllegalArgumentException("最后一条消息必须是 user");
        }
    }

    private String toSseJson(String delta) {
        try {
            return objectMapper.writeValueAsString(Map.of("delta", delta == null ? "" : delta));
        } catch (Exception e) {
            log.warn("SSE delta 序列化失败: {}", e.getMessage());
            return "{\"delta\":\"\"}";
        }
    }

    @PostMapping("/from-text")
    public ApiResponse<Resume> createFromText(@Valid @RequestBody CreateFromTextRequest request) {
        return ApiResponse.ok(
                resumeService.saveFromText(
                        AuthContext.getUserId(),
                        request.getRawText(),
                        request.getFileName()));
    }




}
