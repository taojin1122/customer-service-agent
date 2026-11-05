package com.csagent.server.controller;

import com.csagent.agent.event.AgentEvent;
import com.csagent.agent.model.ChatRequest;
import com.csagent.agent.service.ChatAgentService;
import com.csagent.common.constant.RoleConstants;
import com.csagent.common.context.CurrentUser;
import com.csagent.common.context.TenantContext;
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
 *
 * <p><b>本类所有接口的身份都来自 {@link TenantContext}（即 JWT），不读任何请求参数</b>：
 * 一旦允许前端传 {@code tenantId} / {@code userId}，改一个 JSON 字段就能读别人的会话
 * （AGENTS.md §8.1①）。</p>
 *
 * <p><b>已知欠账</b>：本类直接返回 JPA 实体，不符合 §8.1③（出参用 DTO）。
 * 迁移 DTO 需要前端同步调整，排在 §8.3 第 6 项，与前端重写一并处理。</p>
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
     * 流式对话（SSE）。
     *
     * <p><b>为什么在这里就把身份取出来</b>：这个方法运行在请求线程上，
     * {@link TenantContext} 有效；而返回的 {@code Flux} 会被 Spring MVC 交给异步任务订阅，
     * 那时 ThreadLocal 已经失效。先取成普通对象再传下去，是唯一的可靠做法。</p>
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AgentEvent> stream(@RequestBody ChatRequest request) {
        CurrentUser user = TenantContext.require();
        return chatAgentService.streamChat(request, user);
    }

    /**
     * 创建会话。
     */
    @PostMapping("/conversation")
    public Result<ConversationEntity> createConversation(@RequestBody(required = false) ChatRequest request) {
        CurrentUser user = TenantContext.require();
        String modelId = request != null ? request.getModelId() : null;
        return Result.success(memoryService.createConversation(
                modelId, TenantContext.requireTenantId(), user.userId()));
    }

    /**
     * 会话列表。
     *
     * <p>可见范围由<b>角色</b>决定：管理员看本租户全部，坐席只看自己的。
     * 这个判断取自 JWT 里的 role，不接受前端传参——否则坐席改一下请求参数就能拉全租户的对话。</p>
     */
    @GetMapping("/conversations")
    public Result<List<ConversationEntity>> listConversations() {
        CurrentUser user = TenantContext.require();
        String tenantId = TenantContext.requireTenantId();
        boolean tenantWide = RoleConstants.ADMIN.equals(user.role());
        return Result.success(memoryService.listConversations(tenantId, user.userId(), tenantWide));
    }

    /**
     * 会话消息历史。
     */
    @GetMapping("/conversation/{id}/messages")
    public Result<List<MessageEntity>> listMessages(@PathVariable String id) {
        CurrentUser user = TenantContext.require();
        String tenantId = TenantContext.requireTenantId();
        boolean tenantWide = RoleConstants.ADMIN.equals(user.role());
        return Result.success(memoryService.listMessages(id, tenantId, user.userId(), tenantWide));
    }

    /**
     * 删除会话（逻辑删除）。
     */
    @PostMapping("/conversation/{id}/delete")
    public Result<Void> deleteConversation(@PathVariable String id) {
        CurrentUser user = TenantContext.require();
        String tenantId = TenantContext.requireTenantId();
        boolean tenantWide = RoleConstants.ADMIN.equals(user.role());
        memoryService.deleteConversation(id, tenantId, user.userId(), tenantWide);
        return Result.success();
    }
}
