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

    /** 块序号 */
    private int chunkIndex;

    /** 相似度得分（检索时填充） */
    private double score;
}
