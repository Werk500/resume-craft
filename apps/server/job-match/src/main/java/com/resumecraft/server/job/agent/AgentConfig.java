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
                             ChatMemoryStore redisChatMemoryStore, AiObservability observability) {
        return AiServices.builder(JobAgent.class)
                .chatModel(agentChatModel)                    // 返回 String 的方法走这条
                .streamingChatModel(agentStreamingChatModel)  // 返回 TokenStream 的方法走这条
                .tools(tools)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(20).chatMemoryStore(redisChatMemoryStore).build())
                // ① 工具轮数上限：默认为 100，等于没有上限
                .maxToolCallingRoundTrips(6)
                // ② 模型编造工具名：默认 THROW_EXCEPTION，会把整轮打断
                .hallucinatedToolNameStrategy(request ->{
                    observability.recordAgentGuardrail("hallucinated_tool");
                    log.warn("Agent 调用了不存在的工具: {}", request.name());
                    return ToolExecutionResultMessage.from(request,
                            "没有名为 " + request.name() + " 的工具。可用工具只有 searchJobs 与 calculateMatch，"
                                    + "请改用其中之一；若都做不到，请如实告诉用户。");
                })
                .toolArgumentsErrorHandler((error,ctx) ->{
                    observability.recordAgentGuardrail("arguments_error");
                    log.warn("Agent 工具参数错误: tool={}, err={}",
                            ctx.toolExecutionRequest().name(), error.getMessage());
                    return ToolErrorHandlerResult.text(
                            "调用 " + ctx.toolExecutionRequest().name() + " 的参数不合法："
                                    + error.getMessage() + "。请核对参数类型与取值后重试。");
                })
                .build();
    }

}
