package com.resumecraft.server.job.agent;

import com.resumecraft.server.common.metrics.AiObservability;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@Slf4j
public class AgentGuardrails {

    private final AiObservability observability;
    private final int maxToolCallingRoundTrips;

    public AgentGuardrails(AiObservability observability,
                           @Value("${app.agent.max-tool-rounds:6}") int maxToolCallingRoundTrips) {
        this.observability = observability;
        this.maxToolCallingRoundTrips = maxToolCallingRoundTrips;
    }

    public int maxToolCallingRoundTrips() { return maxToolCallingRoundTrips; }

    /** 模型编造工具名：默认 THROW_EXCEPTION 会打断整轮，这里改成回一条消息给它 */
    public Function<ToolExecutionRequest, ToolExecutionResultMessage> hallucinatedToolNameStrategy() {
        return request -> {
            observability.recordAgentGuardrail("hallucinated_tool");
            log.warn("Agent 调用了不存在的工具: {}", request.name());
            return ToolExecutionResultMessage.from(request,
                    "没有名为 " + request.name() + " 的工具。可用工具只有 searchJobs 与 calculateMatch，"
                            + "请改用其中之一；若都做不到，请如实告诉用户。");
        };
    }

    /** 参数不合法：同步模式默认 RETHROW（整轮失败），这里改成把错误交回模型让它自我纠正 */
    public ToolArgumentsErrorHandler toolArgumentsErrorHandler() {
        return (error, ctx) -> {
            observability.recordAgentGuardrail("arguments_error");
            log.warn("Agent 工具参数错误: tool={}, err={}",
                    ctx.toolExecutionRequest().name(), error.getMessage());
            return ToolErrorHandlerResult.text(
                    "调用 " + ctx.toolExecutionRequest().name() + " 的参数不合法："
                            + error.getMessage() + "。请核对参数类型与取值后重试。");
        };
    }
}
