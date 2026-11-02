package com.csagent.agent.observability;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent 调用观测：Token 统计与调用计数（轻量实现，内存）。
 * <p>
 * 生产环境可用 Micrometer + Prometheus + Grafana 做完整可观测性，
 * 或接入 OpenTelemetry 做链路追踪。这里演示最核心的观测指标：
 * - 调用次数、成功率
 * - Token 消耗（输入/输出）
 * - 平均延迟
 */
@Component
public class AgentMetrics {

    private final AtomicLong totalCalls = new AtomicLong();
    private final AtomicLong successCalls = new AtomicLong();
    private final AtomicLong totalPromptTokens = new AtomicLong();
    private final AtomicLong totalCompletionTokens = new AtomicLong();
    private final Map<String, AtomicLong> toolCallCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> intentCounts = new ConcurrentHashMap<>();

    /**
     * 记录一次调用
     */
    public void record(String intentType, String toolName, boolean success, long promptTokens, long completionTokens, long costMs) {
        totalCalls.incrementAndGet();
        if (success) {
            successCalls.incrementAndGet();
        }
        totalPromptTokens.addAndGet(promptTokens);
        totalCompletionTokens.addAndGet(completionTokens);
        if (toolName != null) {
            toolCallCounts.computeIfAbsent(toolName, k -> new AtomicLong()).incrementAndGet();
        }
        if (intentType != null) {
            intentCounts.computeIfAbsent(intentType, k -> new AtomicLong()).incrementAndGet();
        }
    }

    /**
     * 快照（供 /metrics 接口返回）
     */
    public MetricsSnapshot snapshot() {
        MetricsSnapshot s = new MetricsSnapshot();
        s.setTotalCalls(totalCalls.get());
        s.setSuccessCalls(successCalls.get());
        s.setPromptTokens(totalPromptTokens.get());
        s.setCompletionTokens(totalCompletionTokens.get());
        s.setToolCallCounts(toolCallCounts.entrySet().stream()
                .collect(ConcurrentHashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue().get()),
                        Map::putAll));
        s.setIntentCounts(intentCounts.entrySet().stream()
                .collect(ConcurrentHashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue().get()),
                        Map::putAll));
        s.setSnapshotTime(LocalDateTime.now());
        return s;
    }

    @Data
    public static class MetricsSnapshot {
        private long totalCalls;
        private long successCalls;
        private long promptTokens;
        private long completionTokens;
        private Map<String, Long> toolCallCounts;
        private Map<String, Long> intentCounts;
        private LocalDateTime snapshotTime;
    }
}
