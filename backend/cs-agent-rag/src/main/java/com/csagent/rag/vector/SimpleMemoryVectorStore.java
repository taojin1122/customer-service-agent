package com.csagent.rag.vector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存向量存储（默认实现，零外部依赖，始终注册为兜底）。
 * <p>
 * 基于余弦相似度做暴力检索，适合 demo 与小数据量场景；
 * 数据量增大后应切换 Redis Stack / Milvus / Qdrant 等专业向量库。
 *
 * <p><b>重启即丢</b>：需要持久化时把 {@code cs-agent.rag.storage} 设为 {@code jdbc}，
 * 由 {@link JdbcVectorStore} 接管（接口相同，业务代码无感）。</p>
 *
 * <p><b>租户隔离在这里落地</b>：检索前先按 {@code tenantId} 过滤，再做相似度排序。
 * 顺序不能反——全库算分再过滤虽然结果相同，但意味着别的租户的向量被读出来参与计算，
 * 换成远端向量库时这种写法会变成真实的跨租户数据拉取。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cs-agent.rag.storage", havingValue = "memory", matchIfMissing = true)
public class SimpleMemoryVectorStore implements VectorStore {

    /** id -> 向量条目 */
    private final Map<String, VectorEntry> store = new ConcurrentHashMap<>();

    @Override
    public void upsert(VectorEntry entry) {
        store.put(entry.getId(), entry);
    }

    @Override
    public void upsertAll(List<VectorEntry> entries) {
        for (VectorEntry e : entries) {
            store.put(e.getId(), e);
        }
    }

    @Override
    public List<VectorEntry> similaritySearch(float[] queryVector, int topK, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            // 空租户等于"查全部"，宁可返回空也不越权
            log.warn("向量检索缺少租户标识，已跳过检索");
            return new ArrayList<>();
        }
        return store.values().stream()
                .filter(e -> tenantId.equals(e.getTenantId()))
                .map(e -> {
                    VectorEntry copy = new VectorEntry(e.getId(), e.getContent(), e.getVector(),
                            e.getSourceDoc(), e.getDocId(), e.getTenantId(), e.getChunkIndex(), 0);
                    copy.setScore(VectorMath.cosine(queryVector, e.getVector()));
                    return copy;
                })
                .sorted(Comparator.comparingDouble(VectorEntry::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public void deleteByDocId(String docId) {
        store.entrySet().removeIf(entry -> docId.equals(entry.getValue().getDocId()));
    }

    @Override
    public int countByTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return 0;
        }
        return (int) store.values().stream()
                .filter(e -> tenantId.equals(e.getTenantId()))
                .count();
    }

    @Override
    public String providerName() {
        return "SimpleMemoryVectorStore(内存向量库，demo)";
    }

    public int size() {
        return store.size();
    }
}
