package com.resumecraft.server.ai;


/**
 * AI 能力门面接口 —— 业务层只依赖此接口，不感知底层是 Mock 还是真实大模型。
 */
public interface AiService {

    /**
     * 发起一次对话补全。
     *
     * @param systemPrompt 系统提示词（角色设定 + 输出格式约束）
     * @param userPrompt   用户提示词（业务数据，如简历原文）
     * @return 模型返回的文本
     */
    String chat(String systemPrompt, String userPrompt);
}
