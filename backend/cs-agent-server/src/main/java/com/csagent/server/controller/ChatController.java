package com.csagent.server.controller;

import com.csagent.agent.event.AgentEvent;
import com.csagent.agent.model.ChatRequest;
import com.csagent.agent.service.ChatAgentService;
import com.csagent.common.constant.RoleConstants;
import com.csagent.common.context.CurrentUser;
import com.csagent.common.context.RequireRole;
import com.csagent.common.context.TenantContext;
import com.csagent.common.exception.BusinessException;
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
import java.util.Map;

/**
 * 租户内部对话 API：AI 调试台 + 坐席接待。
 * <p>
 * SSE（Server-Sent Events）是 Agent 流式输出的主流方案：
 * 服务端把「意图识别、思考、工具调用、RAG 检索、最终回答」以事件流方式推送，
 * 前端逐帧渲染，用户体验接近打字机效果。
 *
 * <p><b>本类所有接口的身份都来自 {@link TenantContext}（即 JWT），不读任何请求参数</b>：
 * 一旦允许前端传 {@code tenantId} / {@code userId}，改一个 JSON 字段就能读别人的会话
 * （AGENTS.md §8.1①）。访客入口是另一个类（{@code WidgetController}），
 * 那里没有登录态，安全性由"独立入口 + 限流 + 会话归属校验"保证。</p>
 *
 * <p><b>会话归属</b>：会话属于<b>访客</b>（{@code visitorId}），人工坐席是接手人（{@code agentId}）。
 * 内部调试台里"访客"就是当前账号自己；真实访客的会话来自用户侧客服窗，
 * 坐席通过 {@code /inbox} 看到待接入队列、{@code take-over} 接管、{@code agent-message} 回复。</p>
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
     *
     * <p>渠道固定为 internal：这批接口服务的是<b>内部调试台</b>（坐席自己试 AI），
     * 会话的"访客"就是坐席本人。真实访客的会话由用户侧客服窗创建，渠道是 web，
     * 两者分开统计，避免"内部试用"把对客会话量冲高。</p>
     */
    @PostMapping("/conversation")
    public Result<ConversationEntity> createConversation(@RequestBody(required = false) ChatRequest request) {
        CurrentUser user = TenantContext.require();
        String modelId = request != null ? request.getModelId() : null;
        return Result.success(memoryService.createConversation(
                modelId, TenantContext.requireTenantId(), user.userId(), ConversationEntity.CHANNEL_INTERNAL));
    }

    /**
     * 我发起的会话列表（AI 调试台的左侧列表）。
     *
     * <p><b>固定"只看自己的"，即使调用者是管理员</b>：这个接口的语义是"我的调试记录"，
     * 不是"全租户会话总览"。如果按角色放开，管理员打开调试台会看到所有真实访客的对话——
     * 既干扰调试，也让"调试台"这个定位变得可疑。</p>
     *
     * <p>管理端将来需要的"本租户会话总览"应当是独立接口（带筛选与分页），
     * 而不是让这里按角色返回不同范围：同一个接口返回两种语义，
     * 前端无法判断拿到的是"我的"还是"全部"，统计与分页都对不上。</p>
     */
    @GetMapping("/conversations")
    public Result<List<ConversationEntity>> listConversations() {
        CurrentUser user = TenantContext.require();
        String tenantId = TenantContext.requireTenantId();
        return Result.success(memoryService.listConversations(tenantId, user.userId(), false));
    }

    /**
     * 会话消息历史。
     */
    @GetMapping("/conversation/{id}/messages")
    public Result<List<MessageEntity>> listMessages(@PathVariable String id) {
        CurrentUser user = TenantContext.require();
        String tenantId = TenantContext.requireTenantId();
        return Result.success(memoryService.listMessages(id, tenantId, user.userId(), user.isTenantAdmin()));
    }

    /**
     * 删除会话（逻辑删除）。
     */
    @PostMapping("/conversation/{id}/delete")
    public Result<Void> deleteConversation(@PathVariable String id) {
        CurrentUser user = TenantContext.require();
        String tenantId = TenantContext.requireTenantId();
        memoryService.deleteConversation(id, tenantId, user.userId(), user.isTenantAdmin());
        return Result.success();
    }

    /**
     * 坐席工作台：待接入 + 我处理中的会话。
     *
     * <p>只返回这两类，而不是本租户全部会话：坐席之间互相看不到对方的对话是内部约定。
     * 待接入的会话之所以对所有坐席可见，是因为它还没有归属，谁先接谁负责。</p>
     */
    @GetMapping("/inbox")
    @RequireRole({RoleConstants.ADMIN, RoleConstants.AGENT})
    public Result<List<ConversationEntity>> inbox() {
        CurrentUser user = TenantContext.require();
        return Result.success(memoryService.listInbox(TenantContext.requireTenantId(), user.userId()));
    }

    /**
     * 坐席接管会话。
     *
     * <p>被别的坐席抢先接管时返回 403，前端据此提示"已被 XX 接管"并刷新列表，
     * 而不是静默成功——静默会出现两个坐席同时给同一个访客回消息。</p>
     */
    @PostMapping("/conversation/{id}/take-over")
    @RequireRole({RoleConstants.ADMIN, RoleConstants.AGENT})
    public Result<ConversationEntity> takeOver(@PathVariable String id) {
        CurrentUser user = TenantContext.require();
        return Result.success(memoryService.takeOver(id, TenantContext.requireTenantId(), user.userId()));
    }

    /**
     * 坐席回复访客（人工消息）。
     *
     * <p>不走 SSE：这是坐席到访客的单向消息，不需要模型参与。
     * 归属校验在 {@code MemoryService.replyAsAgent} 内，只有接手的坐席能发言。</p>
     */
    @PostMapping("/conversation/{id}/agent-message")
    @RequireRole({RoleConstants.ADMIN, RoleConstants.AGENT})
    public Result<MessageEntity> replyAsAgent(@PathVariable String id, @RequestBody Map<String, String> body) {
        CurrentUser user = TenantContext.require();
        String content = body == null ? null : body.get("content");
        if (content == null || content.isBlank()) {
            throw new BusinessException("回复内容不能为空");
        }
        return Result.success(memoryService.replyAsAgent(
                id, TenantContext.requireTenantId(), user.userId(), content.trim()));
    }

    /**
     * 结束会话。
     *
     * <p>管理员可以结束本租户任意会话（处理客诉、清理挂死会话），坐席只能结束自己接手的。</p>
     */
    @PostMapping("/conversation/{id}/close")
    @RequireRole({RoleConstants.ADMIN, RoleConstants.AGENT})
    public Result<Void> close(@PathVariable String id) {
        CurrentUser user = TenantContext.require();
        memoryService.closeConversation(id, TenantContext.requireTenantId(), user.userId(), user.isTenantAdmin());
        return Result.success();
    }
}
