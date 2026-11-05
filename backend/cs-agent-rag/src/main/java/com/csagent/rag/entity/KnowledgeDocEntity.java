package com.csagent.rag.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档元数据（持久化）。
 *
 * <p>主键用 {@code tenantId:docId} 组合串而不是单独的 docId：docId 是业务侧 UUID，
 * 不同租户理论上可能碰撞，一旦碰撞就是"B 租户上传后覆盖了 A 租户的文档记录"。
 * 组合主键从物理层面排除这种可能。</p>
 */
@Data
@Entity
@Table(name = "cs_knowledge_doc", indexes = {
        // 列表与计数都按"租户 + 创建时间"取，索引顺序与查询一致才生效
        @Index(name = "idx_kdoc_tenant", columnList = "tenant_id,create_time")
})
public class KnowledgeDocEntity {

    /** 组合主键：tenantId + ":" + docId */
    @Id
    @Column(name = "id", length = 200)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    /** 业务文档 id（返回给前端、用于删除） */
    @Column(name = "doc_id", length = 64, nullable = false)
    private String docId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_type", length = 32)
    private String fileType;

    @Column(name = "chunk_count")
    private int chunkCount;

    @Column(name = "char_count")
    private int charCount;

    /**
     * 分块预览（JSON 数组字符串）。
     *
     * <p>不建子表：预览只是"列表里给个提示"，不参与检索，单独建表会让一次上传多写 N 行。
     * 也不存全部分块内容——正文在向量块表里，这里重复存会带来两份不一致的数据。</p>
     */
    @Column(name = "previews", columnDefinition = "TEXT")
    private String previews;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
