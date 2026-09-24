package com.resumecraft.server.job.agent;


import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * 求职助手 Agent。
 *
 * <h3>这个接口没有实现类</h3>
 * LangChain4j 的 AiServices 会通过动态代理生成实现：
 * 把 @SystemMessage 与工具清单组装成 Function Calling 请求发给模型，
 * 由模型决定调用哪个工具、传什么参数，再把工具返回值喂回模型继续推理。
 *
 * <h3>职责边界</h3>
 * Agent 只负责<b>编排与解释</b>——决定调用顺序、解释分数含义、给出下一步建议。
 * 评分本身由 MatchEngine 的 40/40/20 规则引擎完成，保证可复现。
 * 系统提示词中明确禁止模型自行编造分数。
 */
public interface JobAgent {

    @SystemMessage("""
            你是求职助手，帮助用户分析简历与岗位的匹配情况。

            你可以使用以下工具：
            - searchJobs：在岗位库中搜索岗位
            - calculateMatch：计算某份简历与某个岗位的匹配度

            工作规则：
            1. 所有分数与关键词结论必须来自 calculateMatch 的返回值，严禁自行编造。
            2. 若用户没给岗位 ID，先调用 searchJobs 找到候选岗位，并用一句话说明推荐理由。
            3. 得到匹配结果后，除了报分数，还要解释主要差距（看缺失关键词）并给出下一步建议。
            4. 回答用中文，简洁直接，不要罗列冗长的技术细节。
            5. 如果用户的需求超出你的工具能力（例如修改简历内容），如实说明并建议使用对应功能页。
            """)
    String chat(@MemoryId String sessionId, @UserMessage String userMessage);


    /**
     * 流式对话。
     *
     * <p>返回 TokenStream 而非 String：调用方可以在流式过程中订阅
     * 工具调用事件（beforeToolExecution）与 token 增量（onPartialResponse），
     * 从而给用户实时反馈，而不是干等数秒。
     *
     * <p>注意：必须由调用方显式调用 {@code start()} 才会真正发起请求。
     */
    TokenStream chatStream(@MemoryId String sessionId, @UserMessage String userMessage);
}
