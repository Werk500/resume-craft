package com.resumecraft.server.ai.impl;

import com.resumecraft.server.ai.EmbeddingService;

import java.util.List;

/**
 * 空实现：未配置 API Key 时由 EmbeddingConfig 装配。
 * 不做 {@code @Service} 注册，避免与 EmbeddingConfig 的 @Bean 形成重复定义。
 */
public class NoopEmbeddingServiceImpl implements EmbeddingService {

    @Override public float[] embed(String text) { return null; }
    @Override public List<float[]> embedBatch(List<String> texts) { return null; }
    @Override public String modelName() { return "noop"; }
    @Override public boolean available() { return false; }
}
