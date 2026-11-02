package com.csagent.agent.model;

import lombok.Data;

/**
 * 对话请求 DTO
 */
@Data
public class ChatRequest {

    /** 会话 id（为空则创建新会话） */
    private String conversationId;

    /** 用户输入 */
    private String message;

    /** 模型 id（为空用默认） */
    private String modelId;

    /** 是否启用 RAG 知识库增强（默认 true） */
    private Boolean useRag = true;

    /** 是否启用工具调用（默认 true） */
    private Boolean useTools = true;
}
