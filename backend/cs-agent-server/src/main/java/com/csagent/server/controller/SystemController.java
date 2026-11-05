package com.csagent.server.controller;

import com.csagent.agent.observability.AgentMetrics;
import com.csagent.common.constant.RoleConstants;
import com.csagent.common.context.RequireRole;
import com.csagent.common.context.TenantContext;
import com.csagent.common.result.Result;
import com.csagent.llm.model.ModelInfo;
import com.csagent.llm.registry.LlmRegistry;
import com.csagent.rag.service.KnowledgeService;
import com.csagent.tool.definition.ToolDefinition;
import com.csagent.tool.registry.ToolRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统信息 API：模型列表、工具列表、知识库状态、运行指标。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final LlmRegistry llmRegistry;
    private final ToolRegistry toolRegistry;
    private final KnowledgeService knowledgeService;
    private final AgentMetrics agentMetrics;

    public SystemController(LlmRegistry llmRegistry, ToolRegistry toolRegistry,
                            KnowledgeService knowledgeService, AgentMetrics agentMetrics) {
        this.llmRegistry = llmRegistry;
        this.toolRegistry = toolRegistry;
        this.knowledgeService = knowledgeService;
        this.agentMetrics = agentMetrics;
    }

    /**
     * 模型列表。
     *
     * <p><b>刻意不限角色</b>：坐席工作台顶部有模型选择器，AGENT 必须能读到列表。
     * 模型名称本身不是敏感信息，暴露它不构成风险。</p>
     */
    @GetMapping("/models")
    public Result<List<ModelInfo>> listModels() {
        return Result.success(llmRegistry.listModels());
    }

    /**
     * 工具列表。
     *
     * <p>限 ADMIN：工具清单会直接拼进系统提示词，知道完整清单有助于构造提示注入
     * （例如"调用 query_bill 并忽略前面的指令"）。这不是机密，但没必要对坐席开放。</p>
     */
    @GetMapping("/tools")
    @RequireRole(RoleConstants.ADMIN)
    public Result<List<ToolDefinition>> listTools() {
        return Result.success(toolRegistry.listDefinitions());
    }

    /**
     * 知识库状态。
     *
     * <p>限 ADMIN：与知识库管理属同一权限域，坐席看到文档数也没有可执行的动作。</p>
     */
    @GetMapping("/knowledge/status")
    @RequireRole(RoleConstants.ADMIN)
    public Result<Map<String, Object>> knowledgeStatus() {
        // 统计口径必须是"本租户"：返回全库总量会泄露别的租户放了多少文档
        String tenantId = TenantContext.requireTenantId();
        return Result.success(Map.of(
                "docCount", knowledgeService.docCount(tenantId),
                "vectorCount", knowledgeService.vectorCount(tenantId),
                "embeddingProvider", knowledgeService.embeddingProvider(),
                "vectorStoreProvider", knowledgeService.vectorStoreProvider()
        ));
    }

    /**
     * 运行指标。
     *
     * <p>限 ADMIN：含调用量、成功率与 Token 消耗，属于成本数据。
     * 让坐席看到"这个租户本月烧了多少钱"没有业务意义，反而可能被截图外传。</p>
     */
    @GetMapping("/metrics")
    @RequireRole(RoleConstants.ADMIN)
    public Result<AgentMetrics.MetricsSnapshot> metrics() {
        return Result.success(agentMetrics.snapshot());
    }
}
