package com.csagent.rag.vector;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 向量存储条目：知识块 + 向量 + 元数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorEntry {

    /** 知识块唯一 id */
    private String id;

    /** 文本内容 */
    private String content;

    /** 向量 */
    private float[] vector;

    /** 来源文档名 */
    private String sourceDoc;

    /** 来源文档 id */
    private String docId;

    /**
     * 所属租户。
     *
     * <p><b>必须跟着条目一起存</b>：向量库里没有"表"和"行"的概念，检索时唯一的过滤依据
     * 就是这个字段。不存的话，客户 A 上传的产品文档会被客户 B 的问题检索出来，
     * 这是数据泄露级事故，且无法靠事后补 WHERE 补救（AGENTS.md §1.1）。</p>
     */
    private String tenantId;

    /** 块序号 */
    private int chunkIndex;

    /** 相似度得分（检索时填充） */
    private double score;
}
