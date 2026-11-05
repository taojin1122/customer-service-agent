package com.csagent.rag.repository;

import com.csagent.rag.entity.KnowledgeChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 知识块仓储。
 *
 * <p>检索需要"按租户取全部再算相似度"，所以这里是 {@code List} 而不是分页——
 * 分页会切断 Top-K 的语义（第 2 页可能有比第 1 页更相似的块）。
 * 这个取舍成立的前提是单租户块数不大，见 {@link com.csagent.rag.entity.KnowledgeChunkEntity} 的说明。</p>
 */
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunkEntity, String> {

    List<KnowledgeChunkEntity> findByTenantId(String tenantId);

    int countByTenantId(String tenantId);

    void deleteByDocId(String docId);
}
