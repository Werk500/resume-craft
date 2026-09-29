package com.resumecraft.server.job.agent;


import com.resumecraft.server.common.metrics.AiObservability;
import com.resumecraft.server.job.dto.JobPageResult;
import com.resumecraft.server.job.service.JobService;
import com.resumecraft.server.job.service.MatchService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Agent 三条护栏的确定性验证。
 *
 * <p>为什么需要桩模型：真实模型不会发出工具清单里没有的函数名（DeepSeek 试过两次强诱导都不编），
 * 参数错误也很难稳定构造，所以这两条护栏在线上几乎无法自然触发——只能靠桩模型把
 * "模型犯错"这一幕固定重放。
 */
@ExtendWith(MockitoExtension.class)
public class AgentGuardrailTest {

    private static final String MEMORY_ID = "16:test-session";

    @Mock private JobService jobService;
    @Mock private MatchService matchService;
    @Mock
    private AiObservability observability;

    /** 真实工具对象：@Tool 注解必须能被 AiServices 扫到，不能用 mock */
    @InjectMocks
    private JobAgentTools tools;

    @Test
    @DisplayName("模型编造工具名 → 不打断整轮，护栏把可用工具告诉模型")
    void shouldNotFailTurnWhenToolNameHallucinated(){
        JobAgent agent = agent(
                new StubChatModel(toolCall("listMyResumes", "{}"),
                        AiMessage.from("好的，我改用现有工具。")),
                new AgentGuardrails(observability, 6));

        String answer = agent.chat(MEMORY_ID, "把我账号下的简历列出来");

        assertThat(answer).isEqualTo("好的，我改用现有工具。");
        verify(observability).recordAgentGuardrail("hallucinated_tool");
    }

    @Test
    @DisplayName("工具参数不合法 → 不打断整轮，护栏把错误交回模型")
    void shouldNotFailTurnWhenToolArgumentsInvalid() {
        JobAgent agent = agent(
                new StubChatModel(toolCall("calculateMatch", "{\"resumeId\":\"这不是数字\",\"jobId\":9}"),
                        AiMessage.from("好的，我改用现有工具。")),
                new AgentGuardrails(observability, 6));

        String answer = agent.chat(MEMORY_ID, "算一下简历和岗位 9 的匹配度");

        assertThat(answer).isEqualTo("好的，我改用现有工具。");
        verify(observability).recordAgentGuardrail("arguments_error");
        verifyNoInteractions(matchService);   // 参数没解析成功，工具不该被真正执行
    }

    @Test
    @DisplayName("轮数超限 → 异常消息带框架固定文案（锁住 isRoundLimitError 的匹配依据）")
    void shouldThrowRoundLimitErrorWithRecognisableMessage() {
        when(jobService.search(any(), any(), anyInt(), anyInt()))
                .thenReturn(JobPageResult.builder().list(List.of()).total(0L).page(1L).size(8L).build());

        JobAgent agent = agent(
                new StubChatModel(toolCall("searchJobs", "{\"keyword\":\"Java\"}")),
                new AgentGuardrails(observability, 2));

        assertThatThrownBy(() -> agent.chat(MEMORY_ID, "把所有岗位都算一遍"))
                .hasMessageContaining("tool calling round trips");
    }


    private JobAgent agent(ChatModel model, AgentGuardrails guardrails) {
        return AiServices.builder(JobAgent.class)
                .chatModel(model)
                .tools(tools)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .maxToolCallingRoundTrips(guardrails.maxToolCallingRoundTrips())
                .hallucinatedToolNameStrategy(guardrails.hallucinatedToolNameStrategy())
                .toolArgumentsErrorHandler(guardrails.toolArgumentsErrorHandler())
                .build();
    }

    private static AiMessage toolCall(String name, String arguments) {
        return AiMessage.from(ToolExecutionRequest.builder()
                .id("call-" + name)
                .name(name)
                .arguments(arguments)
                .build());
    }

    /** 按顺序返回预设响应的桩模型；用完后重复最后一条 */
    private static class StubChatModel implements ChatModel {
        private final List<AiMessage> response;
        private final AtomicInteger calls = new AtomicInteger();

        StubChatModel(AiMessage... response) {
            this.response = List.of(response);
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            int index = Math.min(calls.getAndIncrement(), response.size() - 1);
            return ChatResponse.builder().aiMessage(response.get(index)).build();
        }
    }
}
