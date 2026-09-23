package com.resumecraft.server.job.controller;

import com.resumecraft.server.common.ApiResponse;
import com.resumecraft.server.job.agent.JobAgent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 求职助手对话接口。
 *
 * <p>非流式版本（MVP）。先验证工具调用链路是否稳定，再考虑改成 SSE 流式。
 * 流式改造可参考项目里已有的诊断流式实现。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    @Resource
    private JobAgent jobAgent;

    /**
     * 求职助手对话。
     *
     * <p>{@code @PostMapping} 不可省略 —— 只有 {@code @RequestMapping} 加在类上时，
     * 方法若没有映射注解，Spring 不会把它注册为处理器方法，
     * 请求该路径会得到 405 Method Not Allowed。
     */
    @PostMapping("/chat")
    public ApiResponse<Map<String,String>> chat(@RequestBody Map<String,String> body){
        String message = body.get("message");

        if(message==null || message.isEmpty()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }


        //标记"同一个用户的多轮对话"
        String sessionId = body.getOrDefault("sessionId", UUID.randomUUID().toString());

        long start = System.currentTimeMillis();
        String answer = jobAgent.chat(sessionId, message);

        log.info("Agent 对话完成: sessionId={}, 耗时={}ms", sessionId, System.currentTimeMillis() - start);

        return ApiResponse.ok(Map.of("sessionId", sessionId, "answer", answer));

    }
}
