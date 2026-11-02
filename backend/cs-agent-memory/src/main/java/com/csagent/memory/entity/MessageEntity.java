package com.csagent.memory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息实体（长期记忆：完整对话记录持久化）。
 */
@Data
@Entity
@Table(name = "cs_message")
public class MessageEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    /** 所属会话 */
    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    /** 角色：user / assistant / system / tool */
    @Column(name = "role", length = 20)
    private String role;

    /** 消息内容 */
    @Lob
    @Column(name = "content")
    private String content;

    /** 工具调用记录（JSON，assistant 消息可能有） */
    @Lob
    @Column(name = "tool_calls")
    private String toolCalls;

    /** 排序号（保证消息顺序） */
    @Column(name = "seq_no")
    private Long seqNo;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
