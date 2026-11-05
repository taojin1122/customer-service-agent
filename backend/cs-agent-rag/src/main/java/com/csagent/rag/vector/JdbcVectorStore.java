package com.csagent.rag.vector;

import com.csagent.rag.entity.KnowledgeChunkEntity;
import com.csagent.rag.repository.KnowledgeChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 向量存储的数据库实现：把知识块与向量写进 MySQL，重启不丢。
 *
 * <p><b>检索方式是"取出全租户向量 → 内存算余弦 → 取 Top-K"</b>，不是数据库内相似度计算。
 * 这是 demo 规模下的务实取舍：MySQL 没有原生向量索引（8.0 无、需要 9.x 的 VECTOR 类型或
 * 外挂插件），强行在 SQL 里做余弦只能全表扫描 + 逐行函数计算，比拉回内存更慢。</p>
 *
 * <p>性能拐点大致在单租户万级块数；到那时应换专用向量库（PgVector / Redis Stack / Milvus），
 * 而不是在这条路上继续优化。届时只需换掉本类，{@link VectorStore} 接口不变。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cs-agent.rag.storage", havingValue = "jdbc")
public class JdbcVectorStore implements VectorStore {

    private final KnowledgeChunkRepository repository;

    /** 容器提供的 Jackson 3 实例（见 JdbcKnowledgeDocStore 中的同款说明） */
    private final ObjectMapper objectMapper;

    public JdbcVectorStore(KnowledgeChunkRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void upsert(VectorEntry entry) {
        repository.save(toEntity(entry));
    }

    @Override
    public void upsertAll(List<VectorEntry> entries) {
        List<KnowledgeChunkEntity> entities = new ArrayList<>(entries.size());
        for (VectorEntry entry : entries) {
            entities.add(toEntity(entry));
        }
        // 批量保存：一次上传可能几十个块，逐条 save 会产生同样多次数据库往返
        repository.saveAll(entities);
    }

    @Override
    public List<VectorEntry> similaritySearch(float[] queryVector, int topK, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            // 空租户等于"查全部"，宁可返回空也不越权
            log.warn("向量检索缺少租户标识，已跳过检索");
            return new ArrayList<>();
        }

        List<VectorEntry> scored = new ArrayList<>();
        for (KnowledgeChunkEntity entity : repository.findByTenantId(tenantId)) {
            float[] vector = readVector(entity.getId(), entity.getVector());
            if (vector == null || vector.length != queryVector.length) {
                // 维度对不上说明 embedding 模型换过，旧向量无法比较；跳过并留痕，
                // 让运维知道"有些文档需要重新摄入"，而不是悄悄给出错误的相似度
                log.warn("向量维度不匹配，已跳过：chunk={}，库内={}，查询={}",
                        entity.getId(), vector == null ? "解析失败" : vector.length, queryVector.length);
                continue;
            }
            VectorEntry entry = new VectorEntry(entity.getId(), entity.getContent(), vector,
                    entity.getSourceDoc(), entity.getDocId(), entity.getTenantId(),
                    entity.getChunkIndex(), 0);
            entry.setScore(VectorMath.cosine(queryVector, vector));
            scored.add(entry);
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(VectorEntry::getScore).reversed())
                .limit(topK)
                .toList();
    }

    @Override
    @Transactional
    public void deleteByDocId(String docId) {
        repository.deleteByDocId(docId);
    }

    @Override
    public int countByTenant(String tenantId) {
        return repository.countByTenantId(tenantId);
    }

    @Override
    public String providerName() {
        return "JdbcVectorStore(数据库持久化，内存算余弦)";
    }

    private KnowledgeChunkEntity toEntity(VectorEntry entry) {
        KnowledgeChunkEntity entity = new KnowledgeChunkEntity();
        entity.setId(entry.getId());
        entity.setTenantId(entry.getTenantId());
        entity.setDocId(entry.getDocId());
        entity.setSourceDoc(entry.getSourceDoc());
        entity.setChunkIndex(entry.getChunkIndex());
        entity.setContent(entry.getContent());
        entity.setVector(writeVector(entry.getId(), entry.getVector()));
        return entity;
    }

    private String writeVector(String chunkId, float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (RuntimeException e) {
            // 写不进去就不能落库：没有向量的知识块在检索里永远命中不了，
            // 存下来只会让"文档显示已摄入但搜不到"变成一个更难查的问题
            throw new IllegalStateException("向量序列化失败：" + chunkId, e);
        }
    }

    private float[] readVector(String chunkId, String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (RuntimeException e) {
            log.warn("向量解析失败，已跳过：chunk={}", chunkId);
            return null;
        }
    }

}
