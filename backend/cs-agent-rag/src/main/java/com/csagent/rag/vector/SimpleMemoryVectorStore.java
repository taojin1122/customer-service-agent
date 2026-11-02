package com.csagent.rag.vector;

import lombok.extern.slf4j.Slf4j;
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
 */
@Slf4j
@Component
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
    public List<VectorEntry> similaritySearch(float[] queryVector, int topK) {
        return store.values().stream()
                .map(e -> {
                    VectorEntry copy = new VectorEntry(e.getId(), e.getContent(), e.getVector(),
                            e.getSourceDoc(), e.getDocId(), e.getChunkIndex(), 0);
                    copy.setScore(cosine(queryVector, e.getVector()));
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
    public String providerName() {
        return "SimpleMemoryVectorStore(内存向量库，demo)";
    }

    public int size() {
        return store.size();
    }

    private double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
