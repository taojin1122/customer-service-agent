package com.csagent.rag.store;

import com.csagent.rag.service.KnowledgeDoc;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档元数据的内存实现（默认，零依赖）。
 *
 * <p>重启即丢，只适合本地跑通链路。生产/演示交付必须切到 {@code cs-agent.rag.storage=jdbc}，
 * 否则客户上传的资料在下次重启后消失。</p>
 *
 * <p>内部 key 用 {@code tenantId:docId}：两个租户恰好生成相同 docId 时不会互相覆盖，
 * 也让"{@code :} 前缀匹配"成为租户过滤的唯一依据，不必给 DTO 加租户字段。</p>
 */
@Component
@ConditionalOnProperty(name = "cs-agent.rag.storage", havingValue = "memory", matchIfMissing = true)
public class InMemoryKnowledgeDocStore implements KnowledgeDocStore {

    private static final String KEY_SEP = ":";

    private final Map<String, KnowledgeDoc> docs = new ConcurrentHashMap<>();

    @Override
    public void save(String tenantId, KnowledgeDoc doc) {
        docs.put(key(tenantId, doc.getId()), doc);
    }

    @Override
    public boolean delete(String tenantId, String docId) {
        return docs.remove(key(tenantId, docId)) != null;
    }

    @Override
    public List<KnowledgeDoc> listByTenant(String tenantId) {
        String prefix = tenantId + KEY_SEP;
        return docs.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(KnowledgeDoc::getCreateTime).reversed())
                .toList();
    }

    @Override
    public int countByTenant(String tenantId) {
        String prefix = tenantId + KEY_SEP;
        return (int) docs.keySet().stream().filter(k -> k.startsWith(prefix)).count();
    }

    private String key(String tenantId, String docId) {
        return tenantId + KEY_SEP + docId;
    }
}
