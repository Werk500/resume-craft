package com.resumecraft.server.ai;

import java.util.List;

/**
 * 向量化能力门面 —— 与 AiService 同级的抽象。
 *
 * 设计约定：所有方法"失败返回 null / false，不抛异常"。
 * 上层降级逻辑只需判空，不用满屏 try-catch。
 */
public interface EmbeddingService {

    /** 单条文本向量化，失败返回 null（不抛异常，调用方走降级） */
    float[] embed(String text);
    /** 批量向量化 */
    List<float[]> embedBatch(List<String> texts);
    /** 当前模型标识，写入 embedding.model 字段 */
    String modelName();
    /** 是否可用（Key 未配置时为 false） */
    boolean available();
}
