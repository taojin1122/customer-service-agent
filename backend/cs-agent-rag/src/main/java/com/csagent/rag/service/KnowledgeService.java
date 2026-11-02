package com.csagent.rag.service;

import com.csagent.common.util.IdUtil;
import com.csagent.rag.embedding.EmbeddingService;
import com.csagent.rag.ingestion.DocumentParser;
import com.csagent.rag.ingestion.TextSplitter;
import com.csagent.rag.vector.SimpleMemoryVectorStore;
import com.csagent.rag.vector.VectorEntry;
import com.csagent.rag.vector.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库服务：文档管理 + 摄入管线（解析→分块→向量化→入库）。
 * <p>
 * RAG 两条链路：
 * - 离线（摄入）：上传文档 → Tika 解析 → 分块 → Embedding → 写入向量库
 * - 在线（检索）：用户问题 → Embedding → 向量库相似度检索 → Top-K 上下文
 */
@Slf4j
@Service
public class KnowledgeService {

    private final DocumentParser documentParser;
    private final TextSplitter textSplitter;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    /** 文档元数据：id -> doc（内存管理，演示用；生产可落 MySQL） */
    private final Map<String, KnowledgeDoc> docs = new ConcurrentHashMap<>();

    @Value("${cs-agent.rag.chunk-size:500}")
    private int chunkSize;

    @Value("${cs-agent.rag.chunk-overlap:50}")
    private int chunkOverlap;

    @Value("${cs-agent.rag.top-k:4}")
    private int topK;

    public KnowledgeService(DocumentParser documentParser, TextSplitter textSplitter,
                            EmbeddingService embeddingService, VectorStore vectorStore) {
        this.documentParser = documentParser;
        this.textSplitter = textSplitter;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    /**
     * 上传并摄入文档
     */
    public KnowledgeDoc upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String fileName = file.getOriginalFilename();
        String text;
        try {
            text = documentParser.parse(file.getInputStream(), fileName);
        } catch (IOException e) {
            throw new IllegalArgumentException("文件读取失败：" + fileName, e);
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("文档内容为空，无法摄入：" + fileName);
        }

        String docId = IdUtil.uuid();
        List<String> chunks = textSplitter.split(text, chunkSize, chunkOverlap);
        List<VectorEntry> entries = new ArrayList<>(chunks.size());
        int idx = 0;
        for (String chunk : chunks) {
            float[] vector = embeddingService.embed(chunk);
            entries.add(new VectorEntry(IdUtil.uuid(), chunk, vector, fileName, docId, idx++, 0));
        }
        vectorStore.upsertAll(entries);
        log.info("知识库摄入完成：{}，分块 {} 个，字符 {}，Embedding 实现：{}",
                fileName, chunks.size(), text.length(), embeddingService.providerName());

        KnowledgeDoc doc = new KnowledgeDoc(docId, fileName,
                fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "txt",
                chunks.size(), text.length(), LocalDateTime.now(),
                chunks.stream().limit(3).toList());
        docs.put(docId, doc);
        return doc;
    }

    /**
     * 删除文档及其向量
     */
    public void delete(String docId) {
        vectorStore.deleteByDocId(docId);
        docs.remove(docId);
        log.info("删除知识库文档：{}", docId);
    }

    /**
     * 文档列表
     */
    public List<KnowledgeDoc> list() {
        return docs.values().stream()
                .sorted(Comparator.comparing(KnowledgeDoc::getCreateTime).reversed())
                .toList();
    }

    /**
     * 检索：问题向量化 → Top-K 相似知识块
     */
    public List<VectorEntry> search(String query, int k) {
        float[] queryVector = embeddingService.embed(query);
        return vectorStore.similaritySearch(queryVector, k > 0 ? k : topK);
    }

    /**
     * 检索并拼装为 LLM 上下文文本
     */
    public String buildKnowledgeContext(String query, int k) {
        List<VectorEntry> hits = search(query, k);
        if (hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            VectorEntry h = hits.get(i);
            sb.append("【资料").append(i + 1).append("】（来源：").append(h.getSourceDoc())
                    .append("，相似度 ").append(String.format("%.3f", h.getScore())).append("）\n")
                    .append(h.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 向量库条目总数（监控用）
     */
    public int vectorCount() {
        if (vectorStore instanceof SimpleMemoryVectorStore memory) {
            return memory.size();
        }
        return -1;
    }

    public String embeddingProvider() {
        return embeddingService.providerName();
    }

    public String vectorStoreProvider() {
        return vectorStore.providerName();
    }
}
