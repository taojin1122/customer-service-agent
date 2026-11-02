package com.csagent.rag.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库文档 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDoc {

    private String id;

    /** 文档名 */
    private String fileName;

    /** 文档类型（扩展名） */
    private String fileType;

    /** 分块数 */
    private int chunkCount;

    /** 字符数 */
    private int charCount;

    private LocalDateTime createTime;

    /** 分块内容预览（可选） */
    private List<String> chunkPreviews = new ArrayList<>();
}
