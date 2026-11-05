package com.csagent.rag.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 知识块 + 向量（持久化）。
 *
 * <p><b>向量为什么存 JSON 文本而不是 BLOB</b>：这是 demo 规模下的务实选择——
 * 文本可读、跨库通用、调试时能直接看到数值；代价是体积大（一个 1024 维 float 约 10KB 文本）。
 * 文档量上到几万块后，正确做法是换专用向量库（PgVector / Redis Stack / Milvus），
 * 而不是继续在这个表上做全表扫描。</p>
 *
 * <p>检索方式相应地是「按租户取出全部 → 内存算余弦 → 取 Top-K」。
 * 这个实现在单租户几千块时可用，性能拐点大约在万级，届时必须换方案。</p>
 */
@Data
@Entity
@Table(name = "cs_knowledge_chunk", indexes = {
        // 检索固定按租户取全部，删除按文档删，两个索引覆盖这两条路径
        @Index(name = "idx_kchunk_tenant", columnList = "tenant_id"),
        @Index(name = "idx_kchunk_doc", columnList = "tenant_id,doc_id")
})
public class KnowledgeChunkEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    /**
     * 所属租户。<b>检索时唯一的过滤依据</b>，绝不能为空——
     * 空值意味着这条向量会被任何租户的查询跳过（或更糟：被无租户条件的查询捞出来）。
     */
    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "doc_id", length = 64, nullable = false)
    private String docId;

    @Column(name = "source_doc", length = 255)
    private String sourceDoc;

    @Column(name = "chunk_index")
    private int chunkIndex;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    /** 向量（JSON 数组字符串，如 [0.01,-0.23,...]） */
    @Column(name = "vector", columnDefinition = "LONGTEXT")
    private String vector;
}
