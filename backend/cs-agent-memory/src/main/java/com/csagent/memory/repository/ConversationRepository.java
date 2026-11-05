package com.csagent.memory.repository;

import com.csagent.memory.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 会话持久化 Repository。
 *
 * <p><b>本接口刻意不提供任何"无条件查询"方法</b>：原先有一个
 * {@code findByDeletedFalseOrderByUpdateTimeDesc()}，它返回全库会话，
 * 被 Controller 直接调用后导致任何登录用户都能看到所有租户的对话。
 * 现在所有查询方法签名里都强制带 {@code tenantId}，调用方不可能"忘了过滤"——
 * 这种通过接口设计消除犯错可能的做法，比靠 code review 记得加 WHERE 可靠
 * （AGENTS.md §8.1②）。</p>
 */
public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {

    /**
     * 租户管理员视角：本租户全部会话。
     */
    List<ConversationEntity> findByTenantIdAndDeletedFalseOrderByUpdateTimeDesc(String tenantId);

    /**
     * 坐席视角：只看自己的会话。
     */
    List<ConversationEntity> findByTenantIdAndUserIdAndDeletedFalseOrderByUpdateTimeDesc(String tenantId, String userId);

    /**
     * 按 id 取会话，同时限定租户。
     *
     * <p>用它替代 {@code findById} 做归属校验：{@code findById} 只回答"存在吗"，
     * 不回答"是不是你的"。传入别人的 id 时会查到数据，随后就被当成合法操作执行——
     * 这正是越权删除会话的成因。</p>
     */
    Optional<ConversationEntity> findByIdAndTenantId(String id, String tenantId);
}
