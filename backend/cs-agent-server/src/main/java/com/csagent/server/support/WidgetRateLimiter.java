package com.csagent.server.support;

import com.csagent.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 用户侧客服窗（匿名访客）的入口限流。
 *
 * <p><b>为什么匿名入口必须有它</b>：这个入口不需要登录、直接暴露在客户网站上，
 * 一次对话最坏要调 {@code DEFAULT_MAX_ITERATIONS} 次大模型。没有限流等于把
 * 按次计费的接口开放给公网——一个循环脚本就能把当月额度烧光（AGENTS.md §8.1④）。</p>
 *
 * <p><b>为什么用固定窗口而不是令牌桶</b>：固定窗口实现只有几十行、行为可预测、
 * 出问题一眼能看懂；令牌桶的平滑特性在"防刷"这个目标上并不带来额外收益。
 * 真正的分布式场景应换成 Redis + 滑动窗口（多实例下各进程各算一份会漏限），
 * 当前是单实例 demo，本地内存足够，但必须记住这个前提。</p>
 */
@Slf4j
@Component
public class WidgetRateLimiter {

    /** 窗口长度：1 分钟。太短起不到平滑作用，太长会让正常用户被误伤 */
    private static final long WINDOW_MS = 60_000L;

    /** 超过这个 key 数量就清理过期窗口，避免长期运行后 Map 无界增长 */
    private static final int CLEAN_THRESHOLD = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * 计数并校验，超限抛 429。
     *
     * @param key            限流维度，建议 {@code tenantCode:visitorId} 或 {@code tenantCode:ip}
     * @param limitPerMinute 每分钟允许的次数
     */
    public void acquire(String key, int limitPerMinute) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, current) -> {
            if (current == null || now - current.startMs >= WINDOW_MS) {
                return new Window(now);
            }
            current.count.incrementAndGet();
            return current;
        });

        if (window.count.get() > limitPerMinute) {
            log.warn("[限流] 触发上限：key={} limit={}/min", key, limitPerMinute);
            throw new BusinessException(429, "请求过于频繁，请稍后再试");
        }

        if (windows.size() > CLEAN_THRESHOLD) {
            evictExpired(now);
        }
    }

    private void evictExpired(long now) {
        windows.entrySet().removeIf(e -> now - e.getValue().startMs >= WINDOW_MS);
    }

    /**
     * 一个计数窗口。count 用 AtomicInteger 是因为同一个 key 可能被并发请求同时命中，
     * 普通的 int++ 在复合操作下会丢计数——限流器丢计数就是限不住。
     */
    private static final class Window {
        private final long startMs;
        private final AtomicInteger count = new AtomicInteger(1);

        private Window(long startMs) {
            this.startMs = startMs;
        }
    }
}
