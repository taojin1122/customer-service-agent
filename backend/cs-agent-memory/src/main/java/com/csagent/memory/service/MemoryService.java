package com.csagent.memory.service;

import com.csagent.common.constant.AgentConstants;
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
     * 创建会话
     */
    public ConversationEntity createConversation(String modelId) {
        ConversationEntity entity = new ConversationEntity();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setTitle("新会话");
        entity.setModelId(modelId);
        entity.setDeleted(false);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return conversationRepository.save(entity);
    }

    /**
     * 会话列表
     */
    public List<ConversationEntity> listConversations() {
        return conversationRepository.findByDeletedFalseOrderByUpdateTimeDesc();
    }

    /**
     * 获取会话
     */
    public Optional<ConversationEntity> getConversation(String conversationId) {
        return conversationRepository.findById(conversationId);
    }

    /**
     * 删除会话（逻辑删除）
     */
    public void deleteConversation(String conversationId) {
        conversationRepository.findById(conversationId).ifPresent(c -> {
            c.setDeleted(true);
            c.setUpdateTime(LocalDateTime.now());
            conversationRepository.save(c);
        });
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
     * 读取会话全部消息（用于前端历史回显）
     */
    public List<MessageEntity> listMessages(String conversationId) {
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
