package com.resumecraft.server.job.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.common.ApiResponse;
import com.resumecraft.server.job.agent.JobAgent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

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
    @Autowired
    private ObjectMapper objectMapper;

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

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody Map<String,String> body){
        String message = body.get("message");
        if(message==null || message.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        String sessionId = body.getOrDefault("sessionId", UUID.randomUUID().toString());

        return Flux.create(sink -> {
            sink.next(frame("start",Map.of("sessionId",sessionId)));

            try{
                jobAgent.chatStream(sessionId, message)
                        //当 Agent 决定调用某个工具时,触发这个函数。
                        .beforeToolExecution(exec ->
                                sink.next(frame("tool",
                                        Map.of("name",exec.request().name()))))
                        //模型每吐出一个 token(词/字),触发一次。
                        .onPartialResponse(token ->
                                sink.next(frame("delta",Map.of("text",token))))
                        .onCompleteResponse(response ->{
                            sink.next(frame("done",Map.of()));//先发一帧 done
                            sink.complete();// 再关闭流
                        })
                        .onError(error -> {
                            log.warn("Agent 流式对话失败: sessionId={}, err={}",
                                    sessionId, error.getMessage());
                            sink.next(frame("error", Map.of("message",
                                    error.getMessage() == null ? "对话失败" : error.getMessage())));
                            sink.complete();
                        })
                        .start();
            }catch (Exception e) {
                log.error("启动 Agent 流式对话失败", e);
                sink.next(frame("error", Map.of("message", "启动对话失败")));
                sink.complete();
            }
        });
    }

    /** 组装统一格式的 SSE JSON 帧 */
    //把 type 和 payload 合并成一个 JSON 字符串。
    private String frame(String type, Map<String, String> payload) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("type", type);
        data.putAll(payload);

        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("SSE 帧序列化失败: {}", e.getMessage());
            return "{\"type\":\"error\",\"message\":\"序列化失败\"}";
        }
    }
}
