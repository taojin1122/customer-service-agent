package com.csagent.agent.service;

import com.csagent.agent.engine.AgentEngine;
import com.csagent.agent.event.AgentEvent;
import com.csagent.agent.intent.IntentClassifier;
import com.csagent.agent.intent.IntentResult;
import com.csagent.agent.model.ChatRequest;
import com.csagent.agent.observability.AgentMetrics;
import com.csagent.agent.security.PromptInjectionGuard;
import com.csagent.memory.service.MemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 智能客服对话门面服务。
 * <p>
 * 编排链路：安全检测 → 意图识别 → Agent 引擎（ReAct）→ 可观测性采集。
 * 意图识别作为前置路由，将业务意图送入 ReAct 循环；闲聊直接走 LLM。
 * 真实场景可按意图分流到不同 Agent（多 Agent 雏形）。
 */
@Slf4j
@Service
public class ChatAgentService {

    private final AgentEngine agentEngine;
    private final IntentClassifier intentClassifier;
    private final MemoryService memoryService;
    private final PromptInjectionGuard promptInjectionGuard;
    private final AgentMetrics agentMetrics;

    public ChatAgentService(AgentEngine agentEngine, IntentClassifier intentClassifier,
                            MemoryService memoryService, PromptInjectionGuard promptInjectionGuard,
                            AgentMetrics agentMetrics) {
        this.agentEngine = agentEngine;
        this.intentClassifier = intentClassifier;
        this.memoryService = memoryService;
        this.promptInjectionGuard = promptInjectionGuard;
        this.agentMetrics = agentMetrics;
    }

    /**
     * 流式对话：安全检测 → 意图识别 → Agent 执行事件
     */
    public Flux<AgentEvent> streamChat(ChatRequest request) {
        // 1. 安全检测：提示注入防护
        PromptInjectionGuard.CheckResult check = promptInjectionGuard.checkUserInput(request.getMessage());
        if (!check.isSafe()) {
            return Flux.just(AgentEvent.of("error",
                    "您的输入包含不安全内容，已拦截：" + check.getReason() + "。如有疑问请联系人工客服 400-888-0000。"));
        }

        // 2. 意图识别
        IntentResult intent = intentClassifier.classify(request.getMessage());
        log.info("[意图识别] type={}, confidence={}, entities={}",
                intent.getType(), intent.getConfidence(), intent.getEntities());

        long startMs = System.currentTimeMillis();
        AtomicBoolean done = new AtomicBoolean(false);

        // 3. Agent 执行
        return Flux.concat(
                Flux.just(AgentEvent.of("intent",
                        "{\"type\":\"" + intent.getType() + "\",\"description\":\""
                                + intent.getDescription() + "\",\"confidence\":" + intent.getConfidence() + "}")),
                agentEngine.stream(request)
        ).doOnComplete(() -> {
            long cost = System.currentTimeMillis() - startMs;
            agentMetrics.record(intent.getType(), null, true, 0, 0, cost);
            done.set(true);
        }).doOnError(e -> {
            log.error("[Agent] 对话异常", e);
            agentMetrics.record(intent.getType(), null, false, 0, 0, System.currentTimeMillis() - startMs);
        });
    }

    /**
     * 同步对话（内部测试 / 简单调用）
     */
    public Mono<String> syncChat(ChatRequest request) {
        return streamChat(request)
                .filter(e -> "answer".equals(e.getType()))
                .map(AgentEvent::getData)
                .reduce("", (a, b) -> a + b)
                .timeout(Duration.ofSeconds(60));
    }
}
