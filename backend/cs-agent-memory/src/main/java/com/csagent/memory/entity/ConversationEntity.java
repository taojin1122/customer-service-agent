package com.csagent.memory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话实体（长期记忆：会话元数据持久化）。
 */
@Data
@Entity
@Table(name = "cs_conversation", indexes = {
        // 列表查询固定按"租户 + 是否删除 + 更新时间"排序，索引顺序与查询条件一致才用得上
        @Index(name = "idx_conv_tenant", columnList = "tenant_id,deleted,update_time"),
        // 坐席只能看自己的会话，该索引服务"租户 + 坐席"这一组合查询
        @Index(name = "idx_conv_tenant_user", columnList = "tenant_id,user_id")
})
public class ConversationEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    /** 会话标题（取首条用户消息） */
    @Column(name = "title", length = 200)
    private String title;

    /**
     * 所属租户。**所有查询都必须带上它**——漏一次就是跨租户读到别人的对话。
     * 允许为空仅为了兼容改造前的历史数据；新数据必须有值。
     */
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    /**
     * 会话归属的坐席账号。
     *
     * <p>为什么必须记到"人"而不只是"租户"：租户管理员能看到本租户全部会话，
     * 但普通坐席只能看自己的。如果只按租户过滤，坐席就能通过接口拉到同事与用户的对话，
     * 这是内部数据泄露（AGENTS.md §9.3）。</p>
     */
    @Column(name = "user_id", length = 64)
    private String userId;

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
