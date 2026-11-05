package com.csagent.memory;

import com.csagent.common.constant.AgentConstants;
import com.csagent.common.exception.BusinessException;
import com.csagent.memory.entity.ConversationEntity;
import com.csagent.memory.entity.MessageEntity;
import com.csagent.memory.repository.ConversationRepository;
import com.csagent.memory.repository.MessageRepository;
import com.csagent.memory.service.MemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 会话归属与转人工状态机测试（AGENTS.md §8.1⑦ 第 4 条）。
 *
 * <p>守两件会直接出事故的事：
 * ① 访客 / 坐席只能操作与自己相关的会话——错了就是内部或跨租户数据泄露；
 * ② 转人工的状态流转与接手人写入——错了会出现两个坐席同时回一个访客（抢单）。</p>
 *
 * <p><b>为什么用 Mockito 而不是 {@code @DataJpaTest}</b>：Spring Boot 4.1.2 已不再提供
 * 该测试切片（`spring-boot-test-autoconfigure` 里只剩 {@code @JsonTest}），
 * 查询条件的正确性由 Repository 方法签名强制（每个方法都带 tenantId），
 * 这里专注验证"状态流转与归属判断"这段业务逻辑。</p>
 */
@ExtendWith(MockitoExtension.class)
class ConversationLifecycleTest {

    private static final String TENANT_A = "tenant-a";
    private static final String VISITOR_1 = "visitor-1";
    private static final String VISITOR_2 = "visitor-2";
    private static final String AGENT_1 = "agent-1";
    private static final String AGENT_2 = "agent-2";

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    private MemoryService memoryService;

    @BeforeEach
    void setUp() {
        memoryService = new MemoryService(conversationRepository, messageRepository);
        // save 的返回值就是入参：状态机只改字段，不需要数据库回填
        lenientSave();
    }

    @Test
    @DisplayName("新建会话处于机器人接待状态，且不挂到任何坐席名下")
    void newConversationStartsAsBot() {
        ConversationEntity conv = memoryService.createConversation(
                "deepseek-chat", TENANT_A, VISITOR_1, ConversationEntity.CHANNEL_WEB);

        assertThat(conv.getStatus()).isEqualTo(ConversationEntity.STATUS_BOT);
        assertThat(conv.getVisitorId()).isEqualTo(VISITOR_1);
        // 有 agentId 就等于"机器人接待却算在某人头上"，会让工作量统计与接管逻辑全部错乱
        assertThat(conv.getAgentId()).isNull();
        assertThat(conv.getChannel()).isEqualTo(ConversationEntity.CHANNEL_WEB);
    }

    @Test
    @DisplayName("转人工后进入待接入队列，接手后记录坐席并只出现在其名下")
    void requestHumanThenTakeOver() {
        ConversationEntity conv = waitingConversation();

        ConversationEntity taken = memoryService.takeOver(conv.getId(), TENANT_A, AGENT_1);

        assertThat(taken.getStatus()).isEqualTo(ConversationEntity.STATUS_HUMAN);
        assertThat(taken.getAgentId()).isEqualTo(AGENT_1);
    }

    @Test
    @DisplayName("请求转人工把状态从 bot 推到 waiting，且重复调用幂等")
    void requestHumanIsIdempotent() {
        ConversationEntity conv = conversation(ConversationEntity.STATUS_BOT);
        when(conversationRepository.findByIdAndTenantId(conv.getId(), TENANT_A)).thenReturn(Optional.of(conv));

        memoryService.requestHuman(conv.getId(), TENANT_A, VISITOR_1);
        assertThat(conv.getStatus()).isEqualTo(ConversationEntity.STATUS_WAITING);

        // 访客连点两次"转人工"是常见操作，第二次不应报错
        memoryService.requestHuman(conv.getId(), TENANT_A, VISITOR_1);
        assertThat(conv.getStatus()).isEqualTo(ConversationEntity.STATUS_WAITING);
    }

    @Test
    @DisplayName("会话已被别的坐席接走时，再接管会被拒绝；同一坐席重试幂等")
    void takeOverByAnotherAgentIsRejected() {
        ConversationEntity conv = conversation(ConversationEntity.STATUS_HUMAN);
        conv.setAgentId(AGENT_1);
        when(conversationRepository.findByIdAndTenantId(conv.getId(), TENANT_A)).thenReturn(Optional.of(conv));

        assertThatThrownBy(() -> memoryService.takeOver(conv.getId(), TENANT_A, AGENT_2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被其他坐席接管");

        assertThat(memoryService.takeOver(conv.getId(), TENANT_A, AGENT_1).getAgentId()).isEqualTo(AGENT_1);
    }

    @Test
    @DisplayName("非归属方不能读消息或删会话，管理员可以")
    void unrelatedUserCannotReadOrDelete() {
        ConversationEntity conv = conversation(ConversationEntity.STATUS_BOT);
        when(conversationRepository.findByIdAndTenantId(conv.getId(), TENANT_A)).thenReturn(Optional.of(conv));

        assertThatThrownBy(() -> memoryService.listMessages(conv.getId(), TENANT_A, VISITOR_2, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权");
        assertThatThrownBy(() -> memoryService.deleteConversation(conv.getId(), TENANT_A, VISITOR_2, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权");

        // 访客本人可以读；管理员（tenantWide）不受归属限制
        memoryService.listMessages(conv.getId(), TENANT_A, VISITOR_1, false);
        memoryService.listMessages(conv.getId(), TENANT_A, AGENT_2, true);
    }

    @Test
    @DisplayName("只有接手的坐席能往会话里发言")
    void onlyAssignedAgentCanReply() {
        ConversationEntity conv = conversation(ConversationEntity.STATUS_HUMAN);
        conv.setAgentId(AGENT_1);
        when(conversationRepository.findByIdAndTenantId(conv.getId(), TENANT_A)).thenReturn(Optional.of(conv));
        when(messageRepository.findByConversationIdOrderBySeqNoAsc(conv.getId()))
                .thenReturn(List.of(message(AgentConstants.ROLE_AGENT, "您好，我是客服")));

        assertThatThrownBy(() -> memoryService.replyAsAgent(conv.getId(), TENANT_A, AGENT_2, "我来插一句"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("接手的坐席");

        assertThat(memoryService.replyAsAgent(conv.getId(), TENANT_A, AGENT_1, "您好，我是客服").getRole())
                .isEqualTo(AgentConstants.ROLE_AGENT);
    }

    @Test
    @DisplayName("已结束的会话不能再接客或回话")
    void closedConversationRejectsFurtherActions() {
        ConversationEntity conv = conversation(ConversationEntity.STATUS_CLOSED);
        when(conversationRepository.findByIdAndTenantId(conv.getId(), TENANT_A)).thenReturn(Optional.of(conv));

        assertThatThrownBy(() -> memoryService.takeOver(conv.getId(), TENANT_A, AGENT_1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已结束");
        assertThatThrownBy(() -> memoryService.requestHuman(conv.getId(), TENANT_A, VISITOR_1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已结束");
    }

    @Test
    @DisplayName("上下文回放：访客消息是提问方，人工坐席与机器人都算客服方")
    void contextMapsRolesForLlm() {
        String conversationId = "conv-1";
        when(messageRepository.findByConversationIdOrderBySeqNoAsc(conversationId)).thenReturn(List.of(
                message(AgentConstants.ROLE_VISITOR, "电梯坏了"),
                message(AgentConstants.ROLE_AGENT, "已派单，师傅 30 分钟内到"),
                message(AgentConstants.ROLE_ASSISTANT, "还有什么可以帮您？")));

        List<Message> messages = memoryService.buildContextMessages(conversationId);

        assertThat(messages).hasSize(3);
        // 人工坐席的话必须落在 AssistantMessage：模型据此知道"客服已经回答过了"，
        // 否则会把它当成客户的新问题重复作答
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
    }

    @Test
    @DisplayName("历史数据里 role=user 的消息仍能回放，不因改造丢历史")
    void legacyUserRoleStillReplays() {
        String conversationId = "conv-legacy";
        when(messageRepository.findByConversationIdOrderBySeqNoAsc(conversationId)).thenReturn(
                List.of(message(AgentConstants.ROLE_USER_LEGACY, "改造前的问题")));

        assertThat(memoryService.buildContextMessages(conversationId).get(0))
                .isInstanceOf(UserMessage.class);
    }

    private ConversationEntity waitingConversation() {
        ConversationEntity conv = conversation(ConversationEntity.STATUS_WAITING);
        when(conversationRepository.findByIdAndTenantId(conv.getId(), TENANT_A)).thenReturn(Optional.of(conv));
        return conv;
    }

    private ConversationEntity conversation(String status) {
        ConversationEntity conv = new ConversationEntity();
        conv.setId(UUID.randomUUID().toString().replace("-", ""));
        conv.setTenantId(TENANT_A);
        conv.setVisitorId(VISITOR_1);
        conv.setStatus(status);
        conv.setChannel(ConversationEntity.CHANNEL_WEB);
        conv.setTitle("电梯坏了");
        conv.setDeleted(false);
        conv.setCreateTime(LocalDateTime.now());
        conv.setUpdateTime(LocalDateTime.now());
        return conv;
    }

    private MessageEntity message(String role, String content) {
        MessageEntity entity = new MessageEntity();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setRole(role);
        entity.setContent(content);
        entity.setSeqNo(1L);
        entity.setCreateTime(LocalDateTime.now());
        return entity;
    }

    /** save 一律回显入参：被测的是内存里的状态流转，不依赖数据库行为 */
    private void lenientSave() {
        org.mockito.Mockito.lenient()
                .when(conversationRepository.save(any(ConversationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }
}
