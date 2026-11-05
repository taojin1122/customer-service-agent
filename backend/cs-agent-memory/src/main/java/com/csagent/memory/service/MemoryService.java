package com.csagent.memory.service;

import com.csagent.common.constant.AgentConstants;
import com.csagent.common.exception.BusinessException;
import com.csagent.memory.entity.ConversationEntity;
import com.csagent.memory.entity.MessageEntity;
import com.csagent.memory.repository.ConversationRepository;
import com.csagent.memory.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 对话记忆服务。
 * <p>
 * Agent 记忆体系：
 * - 长期记忆：会话 + 消息完整持久化到数据库（本实现使用 JPA，可切换 MySQL）
 * - 短期记忆：每次请求取最近 N 条消息（滑动窗口），组装为 LLM 上下文
 * - 工作记忆：单次 Agent 循环内的中间状态（由 Agent 引擎持有）
 * <p>
 * 记忆的取舍：窗口越大上下文越全但越贵越慢，需按业务调优。
 *
 * <p><b>归属模型</b>：会话属于访客（{@code visitorId}），人工坐席是接手人（{@code agentId}）。
 * 「我的会话」在不同视角下含义不同——访客看自己发起的、坐席看自己接手的，
 * 这两种查询必须分开实现，混用一个方法会出现"坐席能看到全租户访客对话"。</p>
 */
@Slf4j
@Service
public class MemoryService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public MemoryService(ConversationRepository conversationRepository, MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * 创建会话，记下它属于哪个租户、由哪个访客发起、从哪个渠道进来。
     *
     * <p>租户与访客由调用方从 {@code TenantContext} 取出后显式传入，
     * 而不是在本方法内部读 ThreadLocal：SSE 流式响应会在切换线程后调用到这里，
     * ThreadLocal 已失效，内部读会拿到 null，最终写成"无主会话"（谁都查不到，或谁都能查到）。</p>
     *
     * <p>新建会话一律是 {@code bot} 状态：转人工是访客的主动动作，不能默认挂起等人工，
     * 否则坐席工作台会被大量"其实机器人能答"的会话淹没。</p>
     *
     * @param channel 见 {@link ConversationEntity#CHANNEL_WEB} / {@link ConversationEntity#CHANNEL_INTERNAL}
     */
    public ConversationEntity createConversation(String modelId, String tenantId, String visitorId, String channel) {
        ConversationEntity entity = new ConversationEntity();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setTitle("新会话");
        entity.setModelId(modelId);
        entity.setTenantId(tenantId);
        entity.setVisitorId(visitorId);
        // 新建时不设 agentId：设了就等于"机器人接待却算在某个人头上"
        entity.setStatus(ConversationEntity.STATUS_BOT);
        entity.setChannel(channel);
        entity.setDeleted(false);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return conversationRepository.save(entity);
    }

    /**
     * 我发起的会话列表（访客视角）。
     *
     * @param tenantWide 为 true 时返回本租户全部会话（管理员视角），
     *                   为 false 时只返回 visitorId 自己的会话。
     *                   <b>这个判断不能交给前端</b>：前端传什么参数都不能影响返回范围，
     *                   否则访客改一下请求就能看到别人的对话。
     */
    public List<ConversationEntity> listConversations(String tenantId, String visitorId, boolean tenantWide) {
        if (tenantWide) {
            return conversationRepository.findByTenantIdAndDeletedFalseOrderByUpdateTimeDesc(tenantId);
        }
        return conversationRepository.findByTenantIdAndVisitorIdAndDeletedFalseOrderByUpdateTimeDesc(tenantId, visitorId);
    }

    /**
     * 坐席工作台的会话列表：等人接的 + 我接手的。
     *
     * <p>为什么不返回"本租户全部会话"：坐席之间互相看不到对方的对话是内部约定（AGENTS.md §9.3）。
     * 待接入的会话之所以对全体坐席可见，是因为它还没有归属，谁先接谁负责。</p>
     */
    public List<ConversationEntity> listInbox(String tenantId, String agentId) {
        List<ConversationEntity> inbox = new ArrayList<>(
                conversationRepository.findByTenantIdAndStatusAndDeletedFalseOrderByUpdateTimeDesc(
                        tenantId, ConversationEntity.STATUS_WAITING));
        for (ConversationEntity mine : conversationRepository
                .findByTenantIdAndAgentIdAndDeletedFalseOrderByUpdateTimeDesc(tenantId, agentId)) {
            // 只算"进行中"：已结束的会话留在工作台只会让列表越来越长
            if (ConversationEntity.STATUS_HUMAN.equals(mine.getStatus())) {
                inbox.add(mine);
            }
        }
        // 两边按时间混排后再统一排序，否则"待接入"永远在"进行中"上面，新消息会被压在列表底部
        inbox.sort(Comparator.comparing(ConversationEntity::getUpdateTime).reversed());
        return inbox;
    }

    /**
     * 获取会话，限定在当前租户内。
     *
     * <p>返回空表示"不存在或不属于你"——两种情况对调用方是同一件事，
     * 分开提示会变成探测别人会话是否存在的接口。</p>
     */
    public Optional<ConversationEntity> getConversation(String conversationId, String tenantId) {
        return conversationRepository.findByIdAndTenantId(conversationId, tenantId);
    }

    /**
     * 访客请求转人工：{@code bot → waiting}。
     *
     * <p>幂等：已经是 waiting 就原样返回，不报错——访客连点两次"转人工"是常见操作，
     * 报错只会让前端弹一个看不懂的提示。</p>
     */
    public ConversationEntity requestHuman(String conversationId, String tenantId, String visitorId) {
        ConversationEntity conv = requireOwnedConversation(conversationId, tenantId, visitorId, false);
        if (ConversationEntity.STATUS_WAITING.equals(conv.getStatus())) {
            return conv;
        }
        if (ConversationEntity.STATUS_CLOSED.equals(conv.getStatus())) {
            throw new BusinessException(409, "会话已结束，请重新发起会话");
        }
        if (ConversationEntity.STATUS_HUMAN.equals(conv.getStatus())) {
            throw new BusinessException(409, "已有坐席接入该会话");
        }
        conv.setStatus(ConversationEntity.STATUS_WAITING);
        conv.setUpdateTime(LocalDateTime.now());
        return conversationRepository.save(conv);
    }

    /**
     * 坐席接手会话：{@code waiting → human}，并记下接手人。
     *
     * <p>已被别人接走时抛 403 而不是静默改写：两个坐席同时点"接入"，
     * 后者应当看到"已被 XX 接管"，否则会出现两个人同时给访客回消息。</p>
     *
     * <p>同一坐席重复调用是幂等的：网络抖动重试不该报错。</p>
     */
    public ConversationEntity takeOver(String conversationId, String tenantId, String agentId) {
        ConversationEntity conv = conversationRepository.findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));

        if (ConversationEntity.STATUS_HUMAN.equals(conv.getStatus())) {
            if (agentId.equals(conv.getAgentId())) {
                return conv;
            }
            throw new BusinessException(403, "该会话已被其他坐席接管");
        }
        if (ConversationEntity.STATUS_CLOSED.equals(conv.getStatus())) {
            throw new BusinessException(409, "会话已结束");
        }
        // 直接从 bot 接管也放行：坐席看到机器人答得不对时可以直接介入，
        // 强迫访客先点"转人工"多此一举。
        conv.setAgentId(agentId);
        conv.setStatus(ConversationEntity.STATUS_HUMAN);
        conv.setUpdateTime(LocalDateTime.now());
        return conversationRepository.save(conv);
    }

    /**
     * 结束会话：{@code * → closed}。
     *
     * <p>只有访客本人、接手坐席或管理员能结束。别人能结束的后果是坐席可以掐断同事的会话。</p>
     */
    public ConversationEntity closeConversation(String conversationId, String tenantId,
                                                String operatorId, boolean tenantWide) {
        ConversationEntity conv = requireOwnedConversation(conversationId, tenantId, operatorId, tenantWide);
        if (ConversationEntity.STATUS_CLOSED.equals(conv.getStatus())) {
            return conv;
        }
        conv.setStatus(ConversationEntity.STATUS_CLOSED);
        conv.setUpdateTime(LocalDateTime.now());
        return conversationRepository.save(conv);
    }

    /**
     * 删除会话（逻辑删除）。
     *
     * <p>两级校验：先按租户查（跨租户一律视为"不存在"，不泄露对方会话是否存在），
     * 再按归属判断（访客只能删自己的，坐席只能删自己接手的，管理员可以删本租户的）。</p>
     *
     * <p>不校验归属的后果：知道会话 id 的人可以删除任意会话的记录。
     * 会话 id 是 UUID，猜中概率低，但它会出现在日志、浏览器历史和分享链接里。</p>
     */
    public void deleteConversation(String conversationId, String tenantId, String operatorId, boolean tenantWide) {
        ConversationEntity conv = requireOwnedConversation(conversationId, tenantId, operatorId, tenantWide);
        conv.setDeleted(true);
        conv.setUpdateTime(LocalDateTime.now());
        conversationRepository.save(conv);
    }

    /**
     * 取出会话并校验访问权，校验不过直接抛异常。
     *
     * <p>「跨租户」与「同租户无归属」给不同的错误：前者统一说"不存在"（否则等于提供
     * 探测其它租户会话是否存在的接口），后者明确说"无权"（同事之间的可见范围是内部约定，
     * 隐瞒反而让坐席以为系统坏了）。</p>
     *
     * <p>归属判定同时匹配访客与接手坐席：同一条会话，访客和接待他的坐席都应该能读，
     * 只认一方会导致"转人工之后访客刷新页面看不到自己的历史"。</p>
     */
    private ConversationEntity requireOwnedConversation(String conversationId, String tenantId,
                                                        String operatorId, boolean tenantWide) {
        ConversationEntity conv = conversationRepository.findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
        if (tenantWide) {
            return conv;
        }
        boolean owned = operatorId != null
                && (operatorId.equals(conv.getVisitorId()) || operatorId.equals(conv.getAgentId()));
        if (!owned) {
            throw new BusinessException(403, "无权访问该会话");
        }
        return conv;
    }

    /**
     * 更新会话标题（取首条访客消息前 20 字）
     */
    public void updateTitleIfNew(String conversationId, String firstContent) {
        ConversationEntity conv = conversationRepository.findById(conversationId).orElse(null);
        if (conv == null || !"新会话".equals(conv.getTitle())) {
            return;
        }
        String title = firstContent.length() > 20 ? firstContent.substring(0, 20) : firstContent;
        conv.setTitle(title);
        conv.setUpdateTime(LocalDateTime.now());
        conversationRepository.save(conv);
    }

    /**
     * 追加一条消息（长期记忆持久化）
     */
    public void appendMessage(String conversationId, String role, String content, String toolCallsJson) {
        MessageEntity entity = new MessageEntity();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setConversationId(conversationId);
        entity.setRole(role);
        entity.setContent(content);
        entity.setToolCalls(toolCallsJson);
        Long seq = messageRepository.findByConversationIdOrderBySeqNoAsc(conversationId)
                .stream().mapToLong(MessageEntity::getSeqNo).max().orElse(0L);
        entity.setSeqNo(seq + 1);
        entity.setCreateTime(LocalDateTime.now());
        messageRepository.save(entity);

        conversationRepository.findById(conversationId).ifPresent(c -> {
            c.setUpdateTime(LocalDateTime.now());
            conversationRepository.save(c);
        });
    }

    /**
     * 追加一条访客消息。
     *
     * <p>用语义化方法而不是让调用方手写 {@code appendMessage(..., "visitor", ...)}：
     * 角色字符串拼错不会编译报错，只会让这条消息在上下文回放时被当成"工具结果"丢掉。</p>
     */
    public void appendVisitorMessage(String conversationId, String content) {
        appendMessage(conversationId, AgentConstants.ROLE_VISITOR, content, null);
    }

    /**
     * 追加一条人工坐席消息（不做归属校验）。
     *
     * <p>仅用于已完成校验的内部调用与测试。Web 层请走
     * {@link #replyAsAgent(String, String, String, String)}，否则任何坐席都能往别人的会话里发言。</p>
     */
    public void appendAgentMessage(String conversationId, String content) {
        appendMessage(conversationId, AgentConstants.ROLE_AGENT, content, null);
    }

    /**
     * 坐席回复访客：校验"这条会话确实是我接手的"再写入。
     *
     * <p>只校验租户不够：同租户内的其他坐席拿到会话 id 就能插话，
     * 表现为"客户收到的消息不是接待他的那个客服发的"。归属校验必须落在写入口。</p>
     */
    public MessageEntity replyAsAgent(String conversationId, String tenantId, String agentId, String content) {
        ConversationEntity conv = conversationRepository.findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
        if (!agentId.equals(conv.getAgentId())) {
            throw new BusinessException(403, "只有接手的坐席才能回复该会话");
        }
        if (ConversationEntity.STATUS_CLOSED.equals(conv.getStatus())) {
            throw new BusinessException(409, "会话已结束");
        }
        appendMessage(conversationId, AgentConstants.ROLE_AGENT, content, null);
        // 返回刚写入的那条：调用方（坐席工作台）拿它做乐观回显，避免再拉一次全量消息
        List<MessageEntity> messages = messageRepository.findByConversationIdOrderBySeqNoAsc(conversationId);
        return messages.get(messages.size() - 1);
    }

    /**
     * 读取会话全部消息（用于前端历史回显）。
     *
     * <p>必须先校验会话归属再查消息：消息表本身没有 {@code tenant_id}，
     * 只靠 {@code conversationId} 查询的话，拿到别人的会话 id 就能读到完整聊天内容。
     * 把归属校验放在会话这一层，避免给消息表冗余存租户字段（也避免两处不一致）。</p>
     */
    public List<MessageEntity> listMessages(String conversationId, String tenantId,
                                            String operatorId, boolean tenantWide) {
        requireOwnedConversation(conversationId, tenantId, operatorId, tenantWide);
        return messageRepository.findByConversationIdOrderBySeqNoAsc(conversationId);
    }

    /**
     * 构建 LLM 上下文消息列表（短期记忆滑动窗口）。
     * <p>
     * 将持久化的消息（含历史工具调用记录）还原为 Spring AI Message，
     * 只取最近 {@code windowSize} 条，实现「有限窗口记忆」。
     *
     * <p>访客与人工坐席的消息都映射为模型能理解的两种角色：访客 → UserMessage，
     * 人工坐席 → AssistantMessage。模型不需要知道"这句是人说的还是机器说的"，
     * 但需要知道"这句是客户说的还是客服说的"。</p>
     */
    public List<Message> buildContextMessages(String conversationId, int windowSize) {
        List<MessageEntity> entities = messageRepository.findByConversationIdOrderBySeqNoAsc(conversationId);
        int size = Math.max(1, Math.min(windowSize, AgentConstants.DEFAULT_WINDOW_SIZE));
        int from = Math.max(0, entities.size() - size);
        List<Message> messages = new ArrayList<>();
        for (MessageEntity e : entities.subList(from, entities.size())) {
            switch (e.getRole()) {
                case AgentConstants.ROLE_VISITOR, AgentConstants.ROLE_USER_LEGACY ->
                        messages.add(new UserMessage(e.getContent()));
                case AgentConstants.ROLE_AGENT, AgentConstants.ROLE_ASSISTANT ->
                        messages.add(new AssistantMessage(e.getContent()));
                case AgentConstants.ROLE_SYSTEM -> messages.add(new SystemMessage(e.getContent()));
                default -> {
                    // 工具结果消息，尝试还原为 ToolResponseMessage
                    try {
                        // 2.0 起 ToolResponseMessage 构造器改为 protected，统一走 builder 构建
                        messages.add(ToolResponseMessage.builder()
                                .responses(List.of(new ToolResponseMessage.ToolResponse(
                                        "call_" + e.getId(),
                                        e.getToolCalls() == null ? e.getId() : e.getToolCalls(),
                                        e.getContent())))
                                .build());
                    } catch (Exception ex) {
                        log.warn("工具消息还原失败，跳过：{}", e.getId());
                    }
                }
            }
        }
        return messages;
    }

    /**
     * 构建上下文（默认窗口大小）
     */
    public List<Message> buildContextMessages(String conversationId) {
        return buildContextMessages(conversationId, AgentConstants.DEFAULT_WINDOW_SIZE);
    }
}
