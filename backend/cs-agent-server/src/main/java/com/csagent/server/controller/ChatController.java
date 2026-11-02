package com.csagent.server.controller;

import com.csagent.agent.event.AgentEvent;
import com.csagent.agent.model.ChatRequest;
import com.csagent.agent.service.ChatAgentService;
import com.csagent.common.result.Result;
import com.csagent.memory.entity.ConversationEntity;
import com.csagent.memory.entity.MessageEntity;
import com.csagent.memory.service.MemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 对话 API：SSE 流式输出（Agent 事件流）。
 * <p>
 * SSE（Server-Sent Events）是 Agent 流式输出的主流方案：
 * 服务端把「意图识别、思考、工具调用、RAG 检索、最终回答」以事件流方式推送，
 * 前端逐帧渲染，用户体验接近打字机效果。
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatAgentService chatAgentService;
    private final MemoryService memoryService;

    public ChatController(ChatAgentService chatAgentService, MemoryService memoryService) {
        this.chatAgentService = chatAgentService;
        this.memoryService = memoryService;
    }

    /**
     * 流式对话（SSE）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AgentEvent> stream(@RequestBody ChatRequest request) {
        return chatAgentService.streamChat(request);
    }

    /**
     * 创建会话
     */
    @PostMapping("/conversation")
    public Result<ConversationEntity> createConversation(@RequestBody(required = false) ChatRequest request) {
        String modelId = request != null ? request.getModelId() : null;
        return Result.success(memoryService.createConversation(modelId));
    }

    /**
     * 会话列表
     */
    @GetMapping("/conversations")
    public Result<List<ConversationEntity>> listConversations() {
        return Result.success(memoryService.listConversations());
    }

    /**
     * 会话消息历史
     */
    @GetMapping("/conversation/{id}/messages")
    public Result<List<MessageEntity>> listMessages(@PathVariable String id) {
        return Result.success(memoryService.listMessages(id));
    }

    /**
     * 删除会话
     */
    @PostMapping("/conversation/{id}/delete")
    public Result<Void> deleteConversation(@PathVariable String id) {
        memoryService.deleteConversation(id);
        return Result.success();
    }
}
