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
 * <p>提供两个入口：{@code /chat} 同步返回完整答案（便于排查与对比），
 * {@code /chat/stream} 以 SSE 逐帧推送，前端据此做「工具态 → 打字机」两阶段渲染。
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

        /*
         * 关于「工具调用那一轮」的 token：
         *
         * onPartialResponse 对每一轮模型输出都会触发，包括模型「决定调用哪个工具」
         * 的那一轮。那一轮模型会先自言自语一句（例如 "I'll search for backend
         * positions at ByteDance for you."），随后才发出 tool call——这句话语义
         * 上是内部推理，不是给用户看的答案（同步接口也不会返回它）。
         *
         * 如果直接转发，前端只能先渲染再在收到 tool 帧时丢弃，用户会看到文字
         * 闪一下就没。所以在第一个工具执行之前先把 token 攒着不发：
         *   · 一旦发现有工具调用 → 整段丢掉，用户只看到「正在搜索岗位…」
         *   · 整轮都没有工具调用 → 说明这些文字就是答案，在结束时补发
         *
         * 代价：不调工具的简短回答（例如「你好」）不会逐字打字，而是在结束时
         * 一次性出现。相比之下「答案闪一下消失」更伤体验，故如此取舍。
         */
        StringBuilder pendingBeforeTool = new StringBuilder();
        boolean[] toolSeen = {false};

        return Flux.create(sink -> {
            sink.next(frame("start",Map.of("sessionId",sessionId)));

            try{
                jobAgent.chatStream(sessionId, message)
                        //当 Agent 决定调用某个工具时,触发这个函数。
                        .beforeToolExecution(exec -> {
                            toolSeen[0] = true;
                            // 这些 token 属于工具轮的内部推理，丢弃
                            pendingBeforeTool.setLength(0);
                            sink.next(frame("tool",
                                    Map.of("name",exec.request().name())));
                        })
                        //模型每吐出一个 token(词/字),触发一次。
                        .onPartialResponse(token -> {
                            if (toolSeen[0]) {
                                // 工具已执行过，之后吐出的就是面向用户的答案，直接转发
                                sink.next(frame("delta",Map.of("text",token)));
                            } else {
                                pendingBeforeTool.append(token);
                            }
                        })
                        .onCompleteResponse(response ->{
                            // 整轮都没调用工具：之前攒下的就是答案，补发
                            if (!toolSeen[0] && pendingBeforeTool.length() > 0) {
                                sink.next(frame("delta",
                                        Map.of("text", pendingBeforeTool.toString())));
                                pendingBeforeTool.setLength(0);
                            }
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
