package com.csagent.server.controller;

import com.csagent.agent.event.AgentEvent;
import com.csagent.agent.model.ChatRequest;
import com.csagent.agent.service.ChatAgentService;
import com.csagent.auth.entity.TenantEntity;
import com.csagent.auth.repository.TenantRepository;
import com.csagent.common.constant.RoleConstants;
import com.csagent.common.context.CurrentUser;
import com.csagent.common.exception.BusinessException;
import com.csagent.common.result.Result;
import com.csagent.memory.entity.ConversationEntity;
import com.csagent.memory.entity.MessageEntity;
import com.csagent.memory.service.MemoryService;
import com.csagent.server.support.WidgetRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 用户侧客服窗（匿名访客）接口。
 *
 * <p><b>为什么必须与租户后台接口分开一个 Controller</b>：这是全项目唯一不需要登录的入口
 * （AGENTS.md §8.1① 的例外条款）。它接收的 {@code tenantCode} 含义是"我要找哪个租户的机器人"
 * （<b>路由</b>），不是"我是谁"（<b>身份</b>）。两者混在同一个 Controller 里，
 * 迟早会有人顺手把后台接口也塞进来，而那里的身份必须来自 JWT。</p>
 *
 * <p><b>身份怎么来</b>：访客没有账号，用浏览器本地生成的匿名标识（请求头 {@code X-Visitor-Id}）。
 * 它<b>不能用于鉴权</b>——可以随意伪造。真正的约束是：任何按 id 访问的接口都必须校验
 * "这条会话的 visitorId 等于你带来的 visitorId"，伪造成本因此变成"还要猜到会话的 UUID"。</p>
 *
 * <p><b>必须限流</b>：这里直通大模型，且对公网开放。限流维度是"租户 + 访客"，
 * 见 {@link WidgetRateLimiter}。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/widget/{tenantCode}")
public class WidgetController {

    /** 访客匿名标识的请求头。放 Header 而不是 Body：SSE 请求体里只有对话内容，混入身份字段容易与业务参数混淆 */
    private static final String VISITOR_HEADER = "X-Visitor-Id";

    /** 访客标识的最大长度，与数据库字段一致；同时挡掉超长垃圾数据 */
    private static final int VISITOR_ID_MAX_LENGTH = 64;

    /** 每分钟允许新建的会话数：正常用户不会一分钟开 5 个会话，超出的基本是脚本 */
    private static final int LIMIT_CREATE_CONVERSATION = 5;

    /** 每分钟允许的对话轮次 */
    private static final int LIMIT_CHAT = 30;

    /** 每分钟允许的读取（历史/状态轮询） */
    private static final int LIMIT_READ = 120;

    private final TenantRepository tenantRepository;
    private final MemoryService memoryService;
    private final ChatAgentService chatAgentService;
    private final WidgetRateLimiter rateLimiter;

    public WidgetController(TenantRepository tenantRepository, MemoryService memoryService,
                            ChatAgentService chatAgentService, WidgetRateLimiter rateLimiter) {
        this.tenantRepository = tenantRepository;
        this.memoryService = memoryService;
        this.chatAgentService = chatAgentService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * 新建访客会话。
     *
     * <p>前端先拿 conversationId 再开始对话，好处是首句回答不会因为"建会话失败"而丢掉；
     * 不传也不会崩——对话接口在缺 conversationId 时会兜底新建（渠道同样是 web）。</p>
     */
    @PostMapping("/conversation")
    public Result<Map<String, Object>> createConversation(
            @PathVariable String tenantCode,
            @RequestHeader(name = VISITOR_HEADER, required = false) String visitorId,
            @RequestBody(required = false) ChatRequest request) {

        TenantEntity tenant = requireActiveTenant(tenantCode);
        String visitor = requireVisitorId(visitorId);
        rateLimiter.acquire(rateKey(tenantCode, visitor), LIMIT_CREATE_CONVERSATION);

        String modelId = request != null ? request.getModelId() : null;
        ConversationEntity conversation = memoryService.createConversation(
                modelId, tenant.getId(), visitor, ConversationEntity.CHANNEL_WEB);

        log.info("[访客] 新建会话：租户={} 访客={} 会话={}", tenantCode, shortId(visitor), conversation.getId());
        return Result.success(Map.of(
                "conversationId", conversation.getId(),
                "status", conversation.getStatus()));
    }

    /**
     * 访客提问（SSE 流式）。
     *
     * <p>身份在这里被构造成 {@link CurrentUser} 再传给引擎：SSE 的订阅会切到异步线程，
     * {@code TenantContext}（ThreadLocal）此时已失效，必须在请求线程上就把身份取成普通对象。</p>
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AgentEvent> stream(
            @PathVariable String tenantCode,
            @RequestHeader(name = VISITOR_HEADER, required = false) String visitorId,
            @RequestBody ChatRequest request) {

        TenantEntity tenant = requireActiveTenant(tenantCode);
        String visitor = requireVisitorId(visitorId);
        rateLimiter.acquire(rateKey(tenantCode, visitor), LIMIT_CHAT);

        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new BusinessException("消息不能为空");
        }

        // 前端不允许决定"用不用工具"：访客入口只提供问答能力，开工具等于把业务动作暴露给公网
        request.setUseTools(false);

        return chatAgentService.streamChat(request, visitorUser(tenant, visitor));
    }

    /**
     * 会话状态（访客侧轮询用）。
     *
     * <p>只回 status，不回 agentId：坐席的内部账号 id 对访客没有意义，
     * 泄露出去反而方便别人猜同一租户里的其他账号。</p>
     */
    @GetMapping("/conversation/{conversationId}")
    public Result<Map<String, Object>> conversationStatus(
            @PathVariable String tenantCode,
            @PathVariable String conversationId,
            @RequestHeader(name = VISITOR_HEADER, required = false) String visitorId) {

        TenantEntity tenant = requireActiveTenant(tenantCode);
        String visitor = requireVisitorId(visitorId);
        rateLimiter.acquire(rateKey(tenantCode, visitor), LIMIT_READ);

        ConversationEntity conv = requireVisitorConversation(conversationId, tenant.getId(), visitor);
        return Result.success(Map.of(
                "conversationId", conv.getId(),
                "status", conv.getStatus(),
                // 是否已有人接待，前端据此切换"机器人正在转接"与"客服已接入"
                "human", ConversationEntity.STATUS_HUMAN.equals(conv.getStatus())));
    }

    /**
     * 会话消息历史（访客侧轮询用，用于看到人工坐席的回复）。
     *
     * <p>当前是轮询而非推送：真正的实时推送需要 WebSocket / SSE 长连接，
     * 会引入连接管理与心跳，属于独立一步；轮询的延迟（默认 3 秒）对客服场景可接受。</p>
     */
    @GetMapping("/conversation/{conversationId}/messages")
    public Result<List<MessageEntity>> messages(
            @PathVariable String tenantCode,
            @PathVariable String conversationId,
            @RequestHeader(name = VISITOR_HEADER, required = false) String visitorId) {

        TenantEntity tenant = requireActiveTenant(tenantCode);
        String visitor = requireVisitorId(visitorId);
        rateLimiter.acquire(rateKey(tenantCode, visitor), LIMIT_READ);

        requireVisitorConversation(conversationId, tenant.getId(), visitor);
        return Result.success(memoryService.listMessages(conversationId, tenant.getId(), visitor, false));
    }

    /**
     * 访客在"已转人工"之后发消息（不触发 AI）。
     *
     * <p>转人工之后如果还走对话接口，机器人会继续抢答，坐席看到的永远是自己接不上话的会话。
     * 所以这里只落库、由坐席轮询读取，模型不参与。</p>
     */
    @PostMapping("/message")
    public Result<Map<String, Object>> sendVisitorMessage(
            @PathVariable String tenantCode,
            @RequestHeader(name = VISITOR_HEADER, required = false) String visitorId,
            @RequestBody Map<String, String> body) {

        TenantEntity tenant = requireActiveTenant(tenantCode);
        String visitor = requireVisitorId(visitorId);
        rateLimiter.acquire(rateKey(tenantCode, visitor), LIMIT_CHAT);

        String conversationId = body == null ? null : body.get("conversationId");
        String content = body == null ? null : body.get("content");
        if (conversationId == null || conversationId.isBlank() || content == null || content.isBlank()) {
            throw new BusinessException("会话与内容不能为空");
        }

        ConversationEntity conv = requireVisitorConversation(conversationId, tenant.getId(), visitor);
        if (ConversationEntity.STATUS_BOT.equals(conv.getStatus())) {
            throw new BusinessException(409, "当前由机器人接待，请直接提问");
        }
        if (ConversationEntity.STATUS_CLOSED.equals(conv.getStatus())) {
            throw new BusinessException(409, "会话已结束，请重新发起会话");
        }

        memoryService.appendVisitorMessage(conversationId, content.trim());
        return Result.success(Map.of("conversationId", conversationId, "status", conv.getStatus()));
    }

    /**
     * 访客请求转人工。
     */
    @PostMapping("/request-human")
    public Result<Map<String, Object>> requestHuman(
            @PathVariable String tenantCode,
            @RequestHeader(name = VISITOR_HEADER, required = false) String visitorId,
            @RequestBody Map<String, String> body) {

        TenantEntity tenant = requireActiveTenant(tenantCode);
        String visitor = requireVisitorId(visitorId);
        rateLimiter.acquire(rateKey(tenantCode, visitor), LIMIT_CREATE_CONVERSATION);

        String conversationId = body == null ? null : body.get("conversationId");
        if (conversationId == null || conversationId.isBlank()) {
            throw new BusinessException("conversationId 不能为空");
        }

        ConversationEntity conv = memoryService.requestHuman(conversationId, tenant.getId(), visitor);
        log.info("[访客] 请求转人工：租户={} 访客={} 会话={}", tenantCode, shortId(visitor), conversationId);
        return Result.success(Map.of("conversationId", conv.getId(), "status", conv.getStatus()));
    }

    /**
     * 按编码取租户，并要求租户处于启用状态。
     *
     * <p>租户被停用后客服窗要立刻不可用——否则客户能继续用，我们却以为已经停了服务。
     * 一律返回 404 而不是"已停用"：不必告诉外部这个编码是否曾经存在。</p>
     */
    private TenantEntity requireActiveTenant(String tenantCode) {
        TenantEntity tenant = tenantRepository.findByCode(tenantCode)
                .orElseThrow(() -> new BusinessException(404, "客服不存在或已停用"));
        if (!TenantEntity.STATUS_ACTIVE.equals(tenant.getStatus())) {
            throw new BusinessException(404, "客服不存在或已停用");
        }
        return tenant;
    }

    /**
     * 校验访客标识。
     *
     * <p>限制字符集不只是"整洁"：这个值会进日志和数据库，放任特殊字符等于给了
     * 日志注入与超长字段写入的入口。</p>
     */
    private String requireVisitorId(String visitorId) {
        if (visitorId == null || visitorId.isBlank()) {
            throw new BusinessException(400, "缺少访客标识（" + VISITOR_HEADER + "）");
        }
        String trimmed = visitorId.trim();
        if (trimmed.length() > VISITOR_ID_MAX_LENGTH) {
            throw new BusinessException(400, "访客标识过长");
        }
        if (!trimmed.matches("[A-Za-z0-9_-]+")) {
            throw new BusinessException(400, "访客标识格式不合法");
        }
        return trimmed;
    }

    /**
     * 取会话并校验它属于当前访客。
     *
     * <p>不存在与不属于自己返回同一个 404：区分开就等于提供一个"这个会话 id 存在吗"的探测接口。</p>
     */
    private ConversationEntity requireVisitorConversation(String conversationId, String tenantId, String visitorId) {
        ConversationEntity conv = memoryService.getConversation(conversationId, tenantId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
        if (!visitorId.equals(conv.getVisitorId())) {
            throw new BusinessException(404, "会话不存在");
        }
        return conv;
    }

    /**
     * 构造访客身份。
     *
     * <p>username 只用于日志，截断到 8 位避免日志被长 id 撑乱。</p>
     */
    private CurrentUser visitorUser(TenantEntity tenant, String visitorId) {
        return CurrentUser.tenantUser(tenant.getId(), visitorId, "访客-" + shortId(visitorId), RoleConstants.GUEST);
    }

    private String rateKey(String tenantCode, String visitorId) {
        return "widget:" + tenantCode + ":" + visitorId;
    }

    private String shortId(String id) {
        return id.length() > 8 ? id.substring(0, 8) : id;
    }
}
