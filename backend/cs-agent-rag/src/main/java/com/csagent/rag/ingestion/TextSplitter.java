package com.csagent.rag.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分块器（Text Splitter）。
 * <p>
 * RAG 关键环节：长文档必须切成小块再向量化，原因：
 * 1. 单块语义更聚焦，检索命中更准
 * 2. 块太大浪费 Token、稀释相似度
 * 3. 块太小丢失上下文
 * <p>
 * 本实现为「固定长度 + 重叠窗口」策略，是工业界最常用方案之一。
 */
@Component
public class TextSplitter {

    /**
     * 按固定大小 + 重叠切分文本
     *
     * @param text       原文
     * @param chunkSize  块大小（字符数）
     * @param overlap    相邻块重叠字符数
     */
    public List<String> split(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        // 先按空行分段，保留段落结构
        String[] paragraphs = text.split("\\n\\s*\\n");
        StringBuilder current = new StringBuilder();
        for (String p : paragraphs) {
            String para = p.trim();
            if (para.isEmpty()) {
                continue;
            }
            if (current.length() + para.length() + 1 > chunkSize && current.length() > 0) {
                chunks.add(current.toString());
                // 保留尾部 overlap 字符用于衔接
                int keep = Math.min(overlap, current.length());
                current = new StringBuilder(current.substring(current.length() - keep));
            }
            if (para.length() > chunkSize) {
                // 超长段落按字符硬切
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    current = new StringBuilder();
                }
                for (int i = 0; i < para.length(); i += chunkSize - overlap) {
                    int end = Math.min(i + chunkSize, para.length());
                    if (end - i >= chunkSize - overlap) {
                        chunks.add(para.substring(i, end));
                    }
                    if (end == para.length()) {
                        break;
                    }
                }
            } else {
                if (current.length() > 0) {
                    current.append("\n");
                }
                current.append(para);
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }
}
