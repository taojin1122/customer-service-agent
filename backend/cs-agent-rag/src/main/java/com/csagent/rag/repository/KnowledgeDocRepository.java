package com.csagent.rag.repository;

import com.csagent.rag.entity.KnowledgeDocEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 知识库文档仓储。
 *
 * <p>与 {@code ConversationRepository} 同样的约定：<b>不提供任何无条件查询方法</b>，
 * 每个方法签名里都带 {@code tenantId}，让"忘记过滤"从运行时错误变成编译错误。</p>
 */
public interface KnowledgeDocRepository extends JpaRepository<KnowledgeDocEntity, String> {

    List<KnowledgeDocEntity> findByTenantIdOrderByCreateTimeDesc(String tenantId);

    int countByTenantId(String tenantId);

    void deleteByTenantIdAndDocId(String tenantId, String docId);

    boolean existsByTenantIdAndDocId(String tenantId, String docId);
}
