package com.resumecraft.server.ai.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecraft.server.ai.EmbeddingService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;


/**
 * 阿里云百炼（DashScope）文本向量化实现 —— OpenAI 兼容协议。
 *
 * 自定义 HTTP 客户端而非复用 Spring AI 的 EmbeddingModel，原因：
 *   1. dimensions 是百炼的非标准扩展参数，需要显式透传；
 *   2. 需要精确控制超时与失败语义（失败返回 null 供上层降级）；
 *   3. 避免与 spring.ai.openai.* 的自动装配互相干扰。
 */
@Slf4j
public class DashScopeEmbeddingServiceImpl implements EmbeddingService {

    /** 百炼单次请求的 input 上限 */
    private static final int MAX_BATCH_SIZE = 10;

    @Resource
    private ObjectMapper objectMapper;

    private final RestClient restClient;
    private final String model;
    private final int dimensions;
    private final boolean available;

    public DashScopeEmbeddingServiceImpl(String apiKey,
                                         String baseUrl,
                                         String model,
                                         int dimensions,
                                         int timeoutMs){
        this.model = model;
        this.dimensions = dimensions;
        this.available = apiKey != null && !apiKey.isBlank();

        //配置 HTTP 请求工厂（超时控制）
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        //连接超时
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 3000)));
        //读取超时
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        //规范化 baseUrl（清理地址）
        String normalized = (baseUrl == null || baseUrl.isBlank())
                ? "https://dashscope.aliyuncs.com/compatible-mode"
                : baseUrl.trim();
        // 末尾有斜杠会导致 RestClient 拼接出双斜杠，这里统一去掉
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        this.restClient = RestClient.builder()
                .baseUrl(normalized)
                .defaultHeader("Authorization","Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(factory)
                .build();
    }


    @Override
    public float[] embed(String text) {

        if (!available || text == null || text.isBlank()) {
            return null;
        }

        List<float[]> result = doEmbed(Collections.singletonList(text));

        return result.isEmpty() ? null : result.get(0);
    }

    /**
     * 批量向量化
     * @param texts
     * @return
     */
    @Override
    public List<float[]> embedBatch(List<String> texts) {

        if (!available || texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        List<float[]> all = new ArrayList<>(texts.size());
        int total = texts.size();
        for (int from = 0; from < total; from+= MAX_BATCH_SIZE) {
            //避免越界
            int to = Math.min(from + MAX_BATCH_SIZE, total);
            List<float[]> part = doEmbed(texts.subList(from, to));

            //补齐长度，保证与入参下标一一对应
            while (part.size() < (to - from)) {
                part.add(null);
            }
            all.addAll(part);
        }

        return all;
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public boolean available() {
        return available;
    }

    private List<float[]> doEmbed(List<String> strings) {
        ArrayList<float[]> fallback = new ArrayList<>(Collections.nCopies(strings.size(), null));

        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", strings);
        body.put("dimensions", dimensions);
        body.put("encoding_format", "float");

        String raw = restClient.post()
                .uri("/v1/embeddings")
                .body(body)
                .retrieve()
                .body(String.class);

        if (raw == null || raw.isBlank()) {
            log.warn("Embedding 返回空响应, model={}", model);
            return fallback;
        }

        try {
            return parseResponse(raw,strings.size());
        } catch (Exception e) {
            // 降级语义：不抛异常，交给 MatchEngine 回落到 AI_APPROX
            log.warn("Embedding 调用失败, model={}, inputCount={}, err={}",
                    model, strings.size(), e.getMessage());
            return fallback;
        }
    }

    /**
     * 解析响应。按 index 字段回填，而非只依赖数组顺序 ——
     * 百炼并不保证返回顺序与入参完全一致。
     */
    private List<float[]> parseResponse(String raw, int expected) throws JsonProcessingException {
        List<float[]> result = new ArrayList<>(Collections.nCopies(expected, null));

        JsonNode root = objectMapper.readTree(raw);
        JsonNode data = root.path("data");

        if (!data.isArray() || data.isEmpty()) {
            log.warn("Embedding 响应缺少 data 字段: {}", abbreviate(raw));
            return result;
        }

        for (JsonNode item : data) {
            int index = item.path("index").asInt(-1);
            if (index < 0 || index >= expected) {
                continue; // index 缺失或不合法，跳过而非错位填充
            }

            JsonNode vec = item.path("embedding");
            float[] vector = toFloatArray(vec);

            if (vector != null) {
                if (vector.length != dimensions) {
                    log.warn("Embedding 维度不符: 期望 {}, 实际 {}", dimensions, vector.length);
                    continue;
                }
                result.set(index, vector);
            }

        }
        return result;
    }


    /**
     * 日志截断工具
     * @param s
     * @return
     */
    private String abbreviate(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() <= 300 ? s : s.substring(0, 300) + "...";
    }

    /** 兼容两种返回形态：数字数组，或数字字符串数组 */
    private float[] toFloatArray(JsonNode vec){
        if (vec ==null ||!vec.isArray() ||vec.isEmpty()) {
            return null;
        }

        float[] arr = new float[vec.size()];
        for (int i = 0; i < vec.size(); i++) {
            JsonNode n = vec.get(i);
            if(n.isNumber()){
                arr[i] = (float) n.asDouble();
            }else {
                try {
                    arr[i] = Float.parseFloat(n.asText());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return arr;

    }


}
