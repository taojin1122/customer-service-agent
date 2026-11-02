package com.csagent.tool.api;

/**
 * 工具提供者标记接口。
 * <p>
 * 工具类实现此接口后，会被 Spring 自动收集并注入 {@code ToolRegistry}，
 * 由注册中心扫描其上的 {@code @AgentTool} 方法完成注册。
 * 相比全量 Bean 扫描，接口注入方式无循环依赖、类型安全、扫描范围明确。
 */
public interface AgentToolProvider {
}
