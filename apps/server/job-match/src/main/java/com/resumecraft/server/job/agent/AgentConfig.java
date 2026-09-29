package com.resumecraft.server.job.agent;

import com.resumecraft.server.common.metrics.AiObservability;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 装配。
 *
 * <p>手工用 AiServices.builder 构建，而非使用 @AiService 注解——
 * 后者属于 langchain4j-spring-boot-starter（当前仍是 beta），
 * 显式装配可避免引入额外自动配置。
 */
@Slf4j
@Configuration
public class AgentConfig {

    @Bean
    public JobAgent jobAgent(ChatModel agentChatModel,
                             StreamingChatModel agentStreamingChatModel,
                             JobAgentTools tools,
                             ChatMemoryStore redisChatMemoryStore, AgentGuardrails guardrails) {
        return AiServices.builder(JobAgent.class)
                .chatModel(agentChatModel)                    // 返回 String 的方法走这条
                .streamingChatModel(agentStreamingChatModel)  // 返回 TokenStream 的方法走这条
                .tools(tools)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(20).chatMemoryStore(redisChatMemoryStore).build())
                .maxToolCallingRoundTrips(guardrails.maxToolCallingRoundTrips())
                .hallucinatedToolNameStrategy(guardrails.hallucinatedToolNameStrategy())
                .toolArgumentsErrorHandler(guardrails.toolArgumentsErrorHandler())
                .build();
    }

}
