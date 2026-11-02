package com.csagent.rag.embedding;

import java.util.List;

/**
 * Embedding（文本向量化）服务接口。
 * <p>
 * RAG 流程第一步：把文本（用户问题 / 知识块）转换为向量。
 * 抽象接口便于切换实现：
 * - LocalHashEmbeddingService：本地实现，零外部依赖（demo 开箱即用）
 * - OpenAiCompatibleEmbeddingService：调用外部 Embedding API（如硅基流动 bge-m3）
 */
public interface EmbeddingService {

    /**
     * 文本 -> 向量
     */
    float[] embed(String text);

    /**
     * 批量文本 -> 向量
     */
    List<float[]> embedAll(List<String> texts);

    /**
     * 向量维度
     */
    int dimension();

    /**
     * 当前实现说明（用于日志/前端展示）
     */
    String providerName();
}
