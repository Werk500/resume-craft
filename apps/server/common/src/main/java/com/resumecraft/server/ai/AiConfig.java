package com.resumecraft.server.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 通用配置：全局唯一 ChatClient 单例。
 * 底层 ChatModel 由 Spring AI 按 spring.ai.openai.* 自动装配。
 *
 * 注意：本配置在 common 中被所有服务继承，但非 AI 服务（auth/application）
 * 配了 spring.ai.model.chat=none 没有 ChatModel——因此 ChatClient 必须"有 ChatModel 才提供"，
 * 否则非 AI 服务启动时会因缺少 ChatModel 而崩溃。
 */
@Configuration
public class AiConfig {

    @Bean
    @ConditionalOnMissingBean(ChatClient.class)
    public ChatClient chatClient(ObjectProvider<ChatModel> chatModelProvider) {
        ChatModel chatModel = chatModelProvider.getIfUnique();
        if (chatModel == null) {
            // 无 ChatModel（非 AI 服务）→ 不提供 ChatClient，避免启动失败
            return null;
        }
        return ChatClient.builder(chatModel)
                // 可在此追加全局默认行为，例如：
                // .defaultSystem("你是一个简历助手")
                // .defaultAdvisors(...)
                .build();
    }
}
