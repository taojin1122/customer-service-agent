package com.csagent.memory.repository;

import com.csagent.memory.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 消息持久化 Repository
 */
public interface MessageRepository extends JpaRepository<MessageEntity, String> {

    List<MessageEntity> findByConversationIdOrderBySeqNoAsc(String conversationId);
}
