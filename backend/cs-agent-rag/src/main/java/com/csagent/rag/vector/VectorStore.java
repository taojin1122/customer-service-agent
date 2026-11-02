package com.csagent.rag.vector;

import java.util.List;

/**
 * 向量存储接口。
 * <p>
 * 抽象「向量库」能力：写入向量、按相似度检索、按文档删除。
 * 实现可切换：
 * - SimpleMemoryVectorStore：进程内内存实现（默认，零依赖）
 * - RedisVectorStore：Redis Stack 向量检索（生产形态，见 docs）
 */
public interface VectorStore {

    /**
     * 写入向量条目
     */
    void upsert(VectorEntry entry);

    /**
     * 批量写入
     */
    void upsertAll(List<VectorEntry> entries);

    /**
     * 按查询向量检索 Top-K 相似条目
     */
    List<VectorEntry> similaritySearch(float[] queryVector, int topK);

    /**
     * 按文档 id 删除
     */
    void deleteByDocId(String docId);

    /**
     * 当前存储实现说明
     */
    String providerName();
}
