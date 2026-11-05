package com.csagent.rag.store;

import com.csagent.rag.service.KnowledgeDoc;

import java.util.List;

/**
 * 知识库文档元数据的存储抽象。
 *
 * <p>抽出接口的原因：文档元数据原先存在 {@code ConcurrentHashMap} 里，进程重启即丢，
 * 而向量块如果是持久化的、元数据却是内存的，重启后会出现"检索得到内容、列表里没有文档"
 * 的半残状态——比全丢更难排查。两者必须用同一套存储策略切换。</p>
 *
 * <p><b>所有方法都要求 tenantId</b>，且实现不得提供"跨租户查询"的入口：
 * 知识库是租户私有数据，漏一次隔离就是数据泄露（AGENTS.md §1.1）。</p>
 */
public interface KnowledgeDocStore {

    /**
     * 保存（或覆盖）一条文档元数据
     */
    void save(String tenantId, KnowledgeDoc doc);

    /**
     * 删除文档元数据
     *
     * @return true 表示确实删掉了一条；false 表示该租户下没有这个文档
     */
    boolean delete(String tenantId, String docId);

    /**
     * 某租户的文档列表，按创建时间倒序
     */
    List<KnowledgeDoc> listByTenant(String tenantId);

    /**
     * 某租户的文档数
     */
    int countByTenant(String tenantId);
}
