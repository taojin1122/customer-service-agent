package com.csagent.server.controller;

import com.csagent.agent.observability.AgentMetrics;
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
     * 模型列表
     */
    @GetMapping("/models")
    public Result<List<ModelInfo>> listModels() {
        return Result.success(llmRegistry.listModels());
    }

    /**
     * 工具列表
     */
    @GetMapping("/tools")
    public Result<List<ToolDefinition>> listTools() {
        return Result.success(toolRegistry.listDefinitions());
    }

    /**
     * 知识库状态
     */
    @GetMapping("/knowledge/status")
    public Result<Map<String, Object>> knowledgeStatus() {
        return Result.success(Map.of(
                "docCount", knowledgeService.list().size(),
                "vectorCount", knowledgeService.vectorCount(),
                "embeddingProvider", knowledgeService.embeddingProvider(),
                "vectorStoreProvider", knowledgeService.vectorStoreProvider()
        ));
    }

    /**
     * 运行指标
     */
    @GetMapping("/metrics")
    public Result<AgentMetrics.MetricsSnapshot> metrics() {
        return Result.success(agentMetrics.snapshot());
    }
}
