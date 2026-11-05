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
     * 创建会话，并记下它属于哪个租户、哪个坐席。
     *
     * <p>租户与坐席由调用方从 {@code TenantContext} 取出后显式传入，
     * 而不是在本方法内部读 ThreadLocal：SSE 流式响应会在切换线程后调用到这里，
     * ThreadLocal 已失效，内部读会拿到 null，最终写成"无主会话"（谁都查不到，或谁都能查到）。</p>
     */
    public ConversationEntity createConversation(String modelId, String tenantId, String userId) {
        ConversationEntity entity = new ConversationEntity();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setTitle("新会话");
        entity.setModelId(modelId);
        entity.setTenantId(tenantId);
        entity.setUserId(userId);
        entity.setDeleted(false);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return conversationRepository.save(entity);
    }

    /**
     * 会话列表。
     *
     * @param tenantWide 为 true 时返回本租户全部会话（管理员视角），
     *                   为 false 时只返回当前坐席自己的会话。
     *                   <b>这个判断不能交给前端</b>：前端传什么参数都不能影响返回范围，
     *                   否则坐席改一下请求就能看到全租户的对话。
     */
    public List<ConversationEntity> listConversations(String tenantId, String userId, boolean tenantWide) {
        if (tenantWide) {
            return conversationRepository.findByTenantIdAndDeletedFalseOrderByUpdateTimeDesc(tenantId);
        }
        return conversationRepository.findByTenantIdAndUserIdAndDeletedFalseOrderByUpdateTimeDesc(tenantId, userId);
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
     * 删除会话（逻辑删除）。
     *
     * <p>两级校验：先按租户查（跨租户一律视为"不存在"，不泄露对方会话是否存在），
     * 再按坐席判断（同租户内坐席不能删同事的会话，管理员可以）。</p>
     *
     * <p>不校验归属的后果：知道会话 id 的人可以删除任意用户的对话记录。
     * 会话 id 是 UUID，猜中概率低，但它会出现在日志、浏览器历史和分享链接里。</p>
     */
    public void deleteConversation(String conversationId, String tenantId, String userId, boolean tenantWide) {
        ConversationEntity conv = requireOwnedConversation(conversationId, tenantId, userId, tenantWide);
        conv.setDeleted(true);
        conv.setUpdateTime(LocalDateTime.now());
        conversationRepository.save(conv);
    }

    /**
     * 取出会话并校验访问权，校验不过直接抛异常。
     *
     * <p>「跨租户」与「同租户跨坐席」给不同的错误：前者统一说"不存在"（否则等于提供
     * 探测其它租户会话是否存在的接口），后者明确说"无权"（同事之间的可见范围是内部约定，
     * 隐瞒反而让坐席以为系统坏了）。</p>
     */
    private ConversationEntity requireOwnedConversation(String conversationId, String tenantId,
                                                        String userId, boolean tenantWide) {
        ConversationEntity conv = conversationRepository.findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
        if (!tenantWide && !userId.equals(conv.getUserId())) {
            throw new BusinessException(403, "无权访问该会话");
        }
        return conv;
    }

    /**
     * 更新会话标题（取首条用户消息前 20 字）
     */
    public void updateTitleIfNew(String conversationId, String userContent) {
        ConversationEntity conv = conversationRepository.findById(conversationId).orElse(null);
        if (conv == null || !"新会话".equals(conv.getTitle())) {
            return;
        }
        String title = userContent.length() > 20 ? userContent.substring(0, 20) : userContent;
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
     * 读取会话全部消息（用于前端历史回显）。
     *
     * <p>必须先校验会话归属再查消息：消息表本身没有 {@code tenant_id}，
     * 只靠 {@code conversationId} 查询的话，拿到别人的会话 id 就能读到完整聊天内容。
     * 把归属校验放在会话这一层，避免给消息表冗余存租户字段（也避免两处不一致）。</p>
     */
    public List<MessageEntity> listMessages(String conversationId, String tenantId, String userId, boolean tenantWide) {
        requireOwnedConversation(conversationId, tenantId, userId, tenantWide);
        return messageRepository.findByConversationIdOrderBySeqNoAsc(conversationId);
    }

    /**
     * 构建 LLM 上下文消息列表（短期记忆滑动窗口）。
     * <p>
     * 将持久化的消息（含历史工具调用记录）还原为 Spring AI Message，
     * 只取最近 {@code windowSize} 条，实现「有限窗口记忆」。
     */
    public List<Message> buildContextMessages(String conversationId, int windowSize) {
        List<MessageEntity> entities = messageRepository.findByConversationIdOrderBySeqNoAsc(conversationId);
        int size = Math.max(1, Math.min(windowSize, AgentConstants.DEFAULT_WINDOW_SIZE));
        int from = Math.max(0, entities.size() - size);
        List<Message> messages = new ArrayList<>();
        for (MessageEntity e : entities.subList(from, entities.size())) {
            switch (e.getRole()) {
                case AgentConstants.ROLE_USER -> messages.add(new UserMessage(e.getContent()));
                case AgentConstants.ROLE_ASSISTANT -> messages.add(new AssistantMessage(e.getContent()));
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
