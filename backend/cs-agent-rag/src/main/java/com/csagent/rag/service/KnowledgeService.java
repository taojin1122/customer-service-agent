package com.csagent.rag.service;

import com.csagent.common.exception.BusinessException;
import com.csagent.common.util.IdUtil;
import com.csagent.rag.embedding.EmbeddingService;
import com.csagent.rag.ingestion.DocumentParser;
import com.csagent.rag.ingestion.TextSplitter;
import com.csagent.rag.store.KnowledgeDocStore;
import com.csagent.rag.vector.VectorEntry;
import com.csagent.rag.vector.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库服务：文档管理 + 摄入管线（解析→分块→向量化→入库）。
 * <p>
 * RAG 两条链路：
 * - 离线（摄入）：上传文档 → Tika 解析 → 分块 → Embedding → 写入向量库
 * - 在线（检索）：用户问题 → Embedding → 向量库相似度检索 → Top-K 上下文
 *
 * <p><b>存储是可替换的</b>：文档元数据与向量块分别通过 {@link KnowledgeDocStore} 与
 * {@link VectorStore} 访问，内存实现与数据库实现由 {@code cs-agent.rag.storage} 切换。
 * 本类不关心用的是哪个——它只负责流程编排。</p>
 *
 * <p><b>所有对外方法都要求 tenantId，且没有"可选租户"的重载</b>：
 * 知识库是租户级私有数据，客户 A 的产品文档被客户 B 的机器人引用是数据泄露级事故
 * （AGENTS.md §1.1）。把 tenantId 做成必填参数，漏传时编译就失败。</p>
 */
@Slf4j
@Service
public class KnowledgeService {

    private final DocumentParser documentParser;
    private final TextSplitter textSplitter;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final KnowledgeDocStore docStore;

    @Value("${cs-agent.rag.chunk-size:500}")
    private int chunkSize;

    @Value("${cs-agent.rag.chunk-overlap:50}")
    private int chunkOverlap;

    @Value("${cs-agent.rag.top-k:4}")
    private int topK;

    public KnowledgeService(DocumentParser documentParser, TextSplitter textSplitter,
                            EmbeddingService embeddingService, VectorStore vectorStore,
                            KnowledgeDocStore docStore) {
        this.documentParser = documentParser;
        this.textSplitter = textSplitter;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.docStore = docStore;
    }

    /**
     * 上传并摄入文档（写入当前租户的知识库）
     */
    public KnowledgeDoc upload(MultipartFile file, String tenantId) {
        requireTenant(tenantId);
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
            entries.add(new VectorEntry(IdUtil.uuid(), chunk, vector, fileName, docId, tenantId, idx++, 0));
        }
        vectorStore.upsertAll(entries);

        KnowledgeDoc doc = new KnowledgeDoc(docId, fileName,
                fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "txt",
                chunks.size(), text.length(), LocalDateTime.now(),
                chunks.stream().limit(3).toList());
        // 先写向量再写元数据：反过来的话，元数据写成功、向量写失败会留下
        // "列表里有文档、检索永远命中不了"的假象，比整次失败更难排查
        docStore.save(tenantId, doc);

        log.info("知识库摄入完成：租户={}，{}，分块 {} 个，字符 {}，Embedding：{}，向量存储：{}",
                tenantId, fileName, chunks.size(), text.length(),
                embeddingService.providerName(), vectorStore.providerName());
        return doc;
    }

    /**
     * 删除文档及其向量。
     *
     * <p>先校验归属再删：只按 docId 删除等于允许任何租户管理员删掉别人的知识库，
     * 而知识库一旦被删，该租户所有对话会开始答错且很难定位（AGENTS.md §8.1②）。</p>
     */
    public void delete(String docId, String tenantId) {
        requireTenant(tenantId);
        if (!docStore.delete(tenantId, docId)) {
            // 不存在与"不属于本租户"返回同一句：区分开来会变成一个探测别人文档 id 的接口
            throw new BusinessException(404, "文档不存在或不属于当前租户");
        }
        vectorStore.deleteByDocId(docId);
        log.info("删除知识库文档：租户={}，docId={}", tenantId, docId);
    }

    /**
     * 文档列表（仅当前租户）
     */
    public List<KnowledgeDoc> list(String tenantId) {
        requireTenant(tenantId);
        return docStore.listByTenant(tenantId);
    }

    /**
     * 当前租户的文档数。
     *
     * <p>对话链路用它判断"这个租户有没有知识库"，为空就跳过检索——
     * 避免为没有知识库的租户白跑一次 Embedding（那是要花钱的）。</p>
     */
    public int docCount(String tenantId) {
        requireTenant(tenantId);
        return docStore.countByTenant(tenantId);
    }

    /**
     * 检索：问题向量化 → 当前租户内 Top-K 相似知识块
     */
    public List<VectorEntry> search(String query, int k, String tenantId) {
        requireTenant(tenantId);
        float[] queryVector = embeddingService.embed(query);
        return vectorStore.similaritySearch(queryVector, k > 0 ? k : topK, tenantId);
    }

    /**
     * 检索并拼装为 LLM 上下文文本
     */
    public String buildKnowledgeContext(String query, int k, String tenantId) {
        List<VectorEntry> hits = search(query, k, tenantId);
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
     * 当前租户的向量条目总数（监控用）
     */
    public int vectorCount(String tenantId) {
        requireTenant(tenantId);
        return vectorStore.countByTenant(tenantId);
    }

    public String embeddingProvider() {
        return embeddingService.providerName();
    }

    public String vectorStoreProvider() {
        return vectorStore.providerName();
    }

    /**
     * 空租户直接拒绝。
     *
     * <p>不写成"为空就跳过过滤"：那样一次上游漏传就会变成全库可见。
     * 这里的调用方主要是 Web 层（TenantContext.requireTenantId 已保证非空），
     * 该判断兜住的是将来新增的内部调用。</p>
     */
    private void requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(403, "缺少租户标识，无法访问知识库");
        }
    }
}
