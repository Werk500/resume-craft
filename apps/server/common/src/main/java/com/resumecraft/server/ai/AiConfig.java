package com.resumecraft.server.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 通用配置：全局唯一 ChatClient 单例。
 * 底层 ChatModel 由 Spring AI 按 spring.ai.openai.* 自动装配。
 * 后续优化（M3）/匹配（M4）等业务直接注入 ChatClient 复用。
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                // 可在此追加全局默认行为，例如：
                // .defaultSystem("你是一个简历助手")
                // .defaultAdvisors(...)
                .build();
    }
}
