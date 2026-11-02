package com.csagent.memory.repository;

import com.csagent.memory.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 会话持久化 Repository
 */
public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {

    List<ConversationEntity> findByDeletedFalseOrderByUpdateTimeDesc();
}
