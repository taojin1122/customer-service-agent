package com.csagent.rag.vector;

import java.util.List;

/**
 * 向量存储接口。
 * <p>
 * 抽象「向量库」能力：写入向量、按相似度检索、按文档删除。
 * 实现可切换：
 * - SimpleMemoryVectorStore：进程内内存实现（默认，零依赖）
 * - RedisVectorStore：Redis Stack 向量检索（生产形态，见 docs）
 *
 * <p><b>为什么检索方法强制带 tenantId（而不是可选参数）</b>：
 * 可选参数一旦漏传，编译能过、测试可能也过，但结果是跨租户检索到别人的文档。
 * 用必填参数把"忘记隔离"变成编译错误，是这类隔离唯一可靠的实施方式
 * （AGENTS.md §8.1① 与 §1.1 的知识库隔离要求）。</p>
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
     * 在指定租户内按查询向量检索 Top-K 相似条目。
     *
     * <p>实现必须只返回 {@code tenantId} 匹配的条目，不允许"全库检索后再由调用方过滤"——
     * 调用方过滤等于把跨租户数据取出来了，只是没展示。</p>
     *
     * @param queryVector 查询向量
     * @param topK        返回条数上限
     * @param tenantId    当前租户，不能为空
     */
    List<VectorEntry> similaritySearch(float[] queryVector, int topK, String tenantId);

    /**
     * 按文档 id 删除
     *
     * <p>调用方需先校验该文档属于当前租户；本方法只管删除，不承担鉴权。</p>
     */
    void deleteByDocId(String docId);

    /**
     * 指定租户的条目数（用于状态展示与容量排查）
     */
    int countByTenant(String tenantId);

    /**
     * 当前存储实现说明
     */
    String providerName();
}
