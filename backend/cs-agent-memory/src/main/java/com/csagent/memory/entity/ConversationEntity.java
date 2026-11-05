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
 *
 * <p><b>一条会话对应一次"对客服务"</b>，而不是"某个内部账号的聊天记录"。
 * 发起方是终端访客（{@link #visitorId}），机器人或人工坐席是应答方。
 * 转人工只是把 {@link #status} 从 {@code bot} 推进到 {@code human} 并记下接手人
 * （{@link #agentId}），会话对象本身不变——变了就会丢掉访客已经说过的话。</p>
 */
@Data
@Entity
@Table(name = "cs_conversation", indexes = {
        // 列表查询固定按"租户 + 是否删除 + 更新时间"排序，索引顺序与查询条件一致才用得上
        @Index(name = "idx_conv_tenant", columnList = "tenant_id,deleted,update_time"),
        // 访客视角：只看自己发起的会话（内部调试台里访客就是坐席本人）
        @Index(name = "idx_conv_visitor", columnList = "tenant_id,visitor_id"),
        // 坐席工作台按状态取待接入
        @Index(name = "idx_conv_status", columnList = "tenant_id,status"),
        // 坐席工作台取"我接手的"
        @Index(name = "idx_conv_agent", columnList = "tenant_id,agent_id")
})
public class ConversationEntity {

    /** 机器人独自接待（默认） */
    public static final String STATUS_BOT = "bot";
    /** 访客已请求转人工，等待坐席接入 */
    public static final String STATUS_WAITING = "waiting";
    /** 已有坐席接手，人机混合（机器人可继续给建议，坐席决定是否采用） */
    public static final String STATUS_HUMAN = "human";
    /** 会话结束，只读 */
    public static final String STATUS_CLOSED = "closed";

    /** 内部调试渠道：坐席自己试用 AI，不是真实对客，不计入客服统计 */
    public static final String CHANNEL_INTERNAL = "internal";
    /** 网页客服窗 */
    public static final String CHANNEL_WEB = "web";

    @Id
    @Column(name = "id", length = 64)
    private String id;

    /** 会话标题（取首条访客消息） */
    @Column(name = "title", length = 200)
    private String title;

    /**
     * 所属租户。**所有查询都必须带上它**——漏一次就是跨租户读到别人的对话。
     */
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    /**
     * 会话发起方（终端访客）。
     *
     * <p>客服窗的访客没有账号，这里存的是前端生成的匿名标识（localStorage 里的 UUID），
     * 用于"同一个浏览器回来还能接着上次的会话"。内部调试台的会话则记为你自己的 userId。</p>
     *
     * <p><b>不要把它当作用户账号</b>：匿名标识可以伪造，它只能用于区分访客，不能用于鉴权。</p>
     */
    @Column(name = "visitor_id", length = 64)
    private String visitorId;

    /**
     * 接手的人工坐席，为空表示仍由机器人接待。
     *
     * <p>为什么既要有 status 又要有 agentId：status 回答"当前谁在服务"，
     * agentId 回答"具体是哪个人"。少了后者的后果是坐席看不到"我接手的会话"，
     * 只能看到全租户的进行中会话（内部数据泄露，AGENTS.md §9.3）。</p>
     */
    @Column(name = "agent_id", length = 64)
    private String agentId;

    /** 会话状态，取值见本类 STATUS_* 常量 */
    @Column(name = "status", length = 20)
    private String status = STATUS_BOT;

    /** 来源渠道，取值见本类 CHANNEL_* 常量 */
    @Column(name = "channel", length = 20)
    private String channel;

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
