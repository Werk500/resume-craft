package com.resumecraft.server.job.agent;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 装配。
 *
 * <p>手工用 AiServices.builder 构建，而非使用 @AiService 注解——
 * 后者属于 langchain4j-spring-boot-starter（当前仍是 beta），
 * 显式装配可避免引入额外自动配置。
 */
@Configuration
public class AgentConfig {

    @Bean
    public JobAgent jobAgent(ChatModel agentChatModel,JobAgentTools tools) {
        return AiServices.builder(JobAgent.class)
                .chatModel(agentChatModel)
                .tools(tools)
                .chatMemoryProvider(sessionId ->
                        MessageWindowChatMemory.withMaxMessages(20))
                .build();
    }
}
