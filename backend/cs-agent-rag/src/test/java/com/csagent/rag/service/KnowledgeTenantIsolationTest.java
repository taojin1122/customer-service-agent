package com.csagent.rag.service;

import com.csagent.common.exception.BusinessException;
import com.csagent.rag.embedding.LocalHashEmbeddingService;
import com.csagent.rag.ingestion.DocumentParser;
import com.csagent.rag.ingestion.TextSplitter;
import com.csagent.rag.store.InMemoryKnowledgeDocStore;
import com.csagent.rag.vector.SimpleMemoryVectorStore;
import com.csagent.rag.vector.VectorEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 知识库租户隔离测试（AGENTS.md §8.1⑦ 第 4 条）。
 *
 * <p><b>为什么这条必须有测试</b>：知识库隔离失效的表现是"客户 A 的产品文档被客户 B 的机器人
 * 检索到并念出来"，属于数据泄露级事故。这类问题靠人工点页面极难发现
 * （要恰好用 B 的账号问出一个命中 A 文档的问题），只能靠可重复的断言守住。</p>
 *
 * <p>不启动 Spring 上下文：本测试验证的是纯业务逻辑，全部依赖都是可直接 new 的实现，
 * 启动上下文只会让反馈慢十几秒，不增加任何保障。</p>
 */
class KnowledgeTenantIsolationTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        // 用内存实现跑：本测试验证的是隔离逻辑，与存储介质无关；
        // 数据库实现的差异（SQL、序列化）应在集成测试里验证
        service = new KnowledgeService(new DocumentParser(), new TextSplitter(),
                new LocalHashEmbeddingService(), new SimpleMemoryVectorStore(),
                new InMemoryKnowledgeDocStore());
        // chunkSize 等字段由 @Value 注入，纯单测里不会执行注入。
        // 不手动设置会是 0，分块器按 0 切会把文本切成空块，测试就变成"验证空数据"了
        ReflectionTestUtils.setField(service, "chunkSize", 200);
        ReflectionTestUtils.setField(service, "chunkOverlap", 20);
        ReflectionTestUtils.setField(service, "topK", 4);
    }

    @Test
    @DisplayName("A 租户上传的文档，B 租户既看不到列表也检索不到")
    void documentsAreInvisibleAcrossTenants() {
        KnowledgeDoc doc = upload(TENANT_A, "物业收费标准：电梯维护费每月每户 30 元。");

        assertThat(service.list(TENANT_A))
                .extracting(KnowledgeDoc::getId)
                .containsExactly(doc.getId());
        assertThat(service.list(TENANT_B)).isEmpty();

        List<VectorEntry> hitsOfA = service.search("电梯维护费多少钱", 3, TENANT_A);
        assertThat(hitsOfA).isNotEmpty();
        assertThat(service.search("电梯维护费多少钱", 3, TENANT_B)).isEmpty();

        assertThat(service.docCount(TENANT_A)).isEqualTo(1);
        assertThat(service.docCount(TENANT_B)).isZero();
        assertThat(service.vectorCount(TENANT_A)).isPositive();
        assertThat(service.vectorCount(TENANT_B)).isZero();
    }

    @Test
    @DisplayName("B 租户删不掉 A 租户的文档，A 的数据不受影响")
    void deleteIsScopedToTenant() {
        KnowledgeDoc doc = upload(TENANT_A, "住户公约：宠物进入公共区域需牵绳。");

        assertThatThrownBy(() -> service.delete(doc.getId(), TENANT_B))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于当前租户");
        assertThat(service.list(TENANT_A)).hasSize(1);

        service.delete(doc.getId(), TENANT_A);
        assertThat(service.list(TENANT_A)).isEmpty();
        assertThat(service.vectorCount(TENANT_A)).isZero();
    }

    @Test
    @DisplayName("缺少租户标识时拒绝访问，而不是退化成全库可见")
    void missingTenantIsRejected() {
        assertThatThrownBy(() -> service.list(null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search("随便问问", 3, "")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.vectorCount(" ")).isInstanceOf(BusinessException.class);
    }

    private KnowledgeDoc upload(String tenantId, String content) {
        MockMultipartFile file = new MockMultipartFile("file", "policy.txt", "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
        return service.upload(file, tenantId);
    }
}
