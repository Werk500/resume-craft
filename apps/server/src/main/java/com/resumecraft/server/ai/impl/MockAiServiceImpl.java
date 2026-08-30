package com.resumecraft.server.ai.impl;

import com.resumecraft.server.ai.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;


/**
 * Mock 实现：无 API Key 时联调用。
 * app.ai.mock=true（或未配置）时激活。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.ai.mock", havingValue = "true", matchIfMissing = true)
public class MockAiServiceImpl implements AiService {

    public MockAiServiceImpl() {
        log.warn("===== 当前使用 [Mock AI] 实现，返回固定假数据 =====");
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        log.info("[MockAI] 收到请求, systemPrompt长度={}, userPrompt长度={}",
                systemPrompt.length(), userPrompt.length());
        // 返回一段合法 JSON，模拟未来 DeepSeek 的真实返回结构
        return """
                {
                  "totalScore": 72.5,
                  "completenessScore": 85.0,
                  "expressionScore": 66.0,
                  "matchScore": 0,
                  "suggestions": [
                    "【Mock】工作经历建议补充量化数据，如'提升转化率15%'",
                    "【Mock】项目经历建议按 STAR 法则重写",
                    "【Mock】技能清单建议按熟练度分层排列"
                  ]
                }
                """;
    }
}
