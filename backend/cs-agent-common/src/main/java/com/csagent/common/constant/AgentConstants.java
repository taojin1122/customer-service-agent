package com.csagent.common.constant;

/**
 * Agent 相关常量
 */
public final class AgentConstants {

    private AgentConstants() {
    }

    /** 系统消息角色 */
    public static final String ROLE_SYSTEM = "system";

    /**
     * 终端访客的消息。
     *
     * <p>与 {@link #ROLE_USER} 的区别是<b>身份</b>而非格式：`user` 是 LLM 协议里的"提问方"，
     * `visitor` 是业务上的"来访客户"。落库用 visitor，转成 LLM 消息时再映射为 UserMessage——
     * 两者混用会让"这条话是谁说的"变得无法回答（坐席试聊和真实客户提问会长得一模一样）。</p>
     */
    public static final String ROLE_VISITOR = "visitor";

    /** 人工坐席发出的消息（转人工后） */
    public static final String ROLE_AGENT = "agent";

    /**
     * 机器人回复。
     *
     * <p>转成 LLM 消息时，人工坐席说的话同样映射为 AssistantMessage：
     * 在模型眼里"客服方说过的话"就是助手历史，不区分是人还是机器。
     * 区分只发生在展示层与审计层。</p>
     */
    public static final String ROLE_ASSISTANT = "assistant";

    /** 工具消息角色 */
    public static final String ROLE_TOOL = "tool";

    /**
     * 历史数据兼容：改造前访客消息存的是 `user`。
     * 新数据不再写入该值，仅保留在读取时做映射，避免旧会话回放时丢消息。
     */
    public static final String ROLE_USER_LEGACY = "user";

    /** 默认会话窗口大小（短期记忆条数） */
    public static final int DEFAULT_WINDOW_SIZE = 20;

    /** Agent 最大循环轮数（防止死循环） */
    public static final int DEFAULT_MAX_ITERATIONS = 8;

    /** 知识库默认检索条数 */
    public static final int DEFAULT_RAG_TOP_K = 4;
}
