package com.csagent.rag.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 文档内容解析器（基于 Apache Tika）。
 * <p>
 * 支持 txt / md / pdf / docx / xlsx 等常见格式，统一抽取为纯文本，
 * 供后续分块与向量化使用。
 */
@Slf4j
@Component
public class DocumentParser {

    private final Tika tika = new Tika();

    /**
     * 解析文档流为纯文本
     */
    public String parse(InputStream inputStream, String fileName) {
        try {
            String text = tika.parseToString(inputStream);
            return text == null ? "" : text.trim();
        } catch (Exception e) {
            log.error("文档解析失败：{}", fileName, e);
            throw new IllegalArgumentException("文档解析失败：" + fileName + "（" + e.getMessage() + "）");
        }
    }
}
