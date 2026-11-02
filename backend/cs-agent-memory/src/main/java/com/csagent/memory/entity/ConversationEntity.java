package com.csagent.memory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话实体（长期记忆：会话元数据持久化）。
 */
@Data
@Entity
@Table(name = "cs_conversation")
public class ConversationEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    /** 会话标题（取首条用户消息） */
    @Column(name = "title", length = 200)
    private String title;

    /** 使用的模型 id */
    @Column(name = "model_id", length = 64)
    private String modelId;

    /** 是否已删除 */
    @Column(name = "deleted")
    private Boolean deleted = false;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
