package com.csagent.common.constant;

/**
 * Agent 相关常量
 */
public final class AgentConstants {

    private AgentConstants() {
    }

    /** 系统消息角色 */
    public static final String ROLE_SYSTEM = "system";
    /** 用户消息角色 */
    public static final String ROLE_USER = "user";
    /** 助手消息角色 */
    public static final String ROLE_ASSISTANT = "assistant";
    /** 工具消息角色 */
    public static final String ROLE_TOOL = "tool";

    /** 默认会话窗口大小（短期记忆条数） */
    public static final int DEFAULT_WINDOW_SIZE = 20;

    /** Agent 最大循环轮数（防止死循环） */
    public static final int DEFAULT_MAX_ITERATIONS = 8;

    /** 知识库默认检索条数 */
    public static final int DEFAULT_RAG_TOP_K = 4;
}
