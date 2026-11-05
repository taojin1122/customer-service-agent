package com.csagent.rag.store;

import com.csagent.rag.entity.KnowledgeDocEntity;
import com.csagent.rag.repository.KnowledgeDocRepository;
import com.csagent.rag.service.KnowledgeDoc;
import com.csagent.rag.support.JsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档元数据的数据库实现（MySQL / H2 均可）。
 *
 * <p>激活方式：{@code cs-agent.rag.storage=jdbc}（application-mysql.yml 已默认开启）。
 * 与 {@link InMemoryKnowledgeDocStore} 是同一接口的两个实现，业务代码不需要知道用的是哪个。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cs-agent.rag.storage", havingValue = "jdbc")
public class JdbcKnowledgeDocStore implements KnowledgeDocStore {

    private static final String KEY_SEP = ":";

    private final KnowledgeDocRepository repository;

    public JdbcKnowledgeDocStore(KnowledgeDocRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(String tenantId, KnowledgeDoc doc) {
        KnowledgeDocEntity entity = new KnowledgeDocEntity();
        entity.setId(tenantId + KEY_SEP + doc.getId());
        entity.setTenantId(tenantId);
        entity.setDocId(doc.getId());
        entity.setFileName(doc.getFileName());
        entity.setFileType(doc.getFileType());
        entity.setChunkCount(doc.getChunkCount());
        entity.setCharCount(doc.getCharCount());
        entity.setCreateTime(doc.getCreateTime());
        entity.setPreviews(writePreviews(doc.getChunkPreviews()));
        repository.save(entity);
    }

    @Override
    @Transactional
    public boolean delete(String tenantId, String docId) {
        if (!repository.existsByTenantIdAndDocId(tenantId, docId)) {
            return false;
        }
        repository.deleteByTenantIdAndDocId(tenantId, docId);
        return true;
    }

    @Override
    public List<KnowledgeDoc> listByTenant(String tenantId) {
        return repository.findByTenantIdOrderByCreateTimeDesc(tenantId).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public int countByTenant(String tenantId) {
        return repository.countByTenantId(tenantId);
    }

    private KnowledgeDoc toDto(KnowledgeDocEntity entity) {
        return new KnowledgeDoc(entity.getDocId(), entity.getFileName(), entity.getFileType(),
                entity.getChunkCount(), entity.getCharCount(), entity.getCreateTime(),
                readPreviews(entity.getPreviews()));
    }

    /**
     * 预览序列化失败不阻断上传：预览只是列表里的一句提示，
     * 为它让整次文档摄入失败，代价明显不对等。
     */
    private String writePreviews(List<String> previews) {
        try {
            return JsonCodec.write(previews == null ? List.of() : previews);
        } catch (Exception e) {
            log.warn("文档分块预览序列化失败，已存空数组", e);
            return "[]";
        }
    }

    private List<String> readPreviews(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return JsonCodec.readStringList(json);
        } catch (Exception e) {
            log.warn("文档分块预览反序列化失败，返回空列表");
            return new ArrayList<>();
        }
    }
}
