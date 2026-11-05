package com.csagent.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户：SaaS 模式下每一家客户公司就是一条记录。
 *
 * <p><b>为什么这张表必须最先有</b>：知识库、会话、工具订阅最终都要挂 {@code tenant_id}。
 * 后补字段容易，但"历史上已经混在一起的数据分不出归属"是补不回来的，
 * 只能清洗或丢弃（AGENTS.md §1.1 的优先级铁律）。</p>
 */
@Data
@Entity
@Table(name = "t_tenant", indexes = {
        // 登录与路由都按租户编码查，且要求唯一：编码重复会导致两个租户的流量串到一起
        @Index(name = "uk_tenant_code", columnList = "code", unique = true)
})
public class TenantEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    /**
     * 租户编码：对外可读的短标识（如 {@code yangcheng-power}）。
     * 用途是将来按域名/子域名路由到租户；不用自增 id 是因为域名里不能出现纯数字的歧义。
     */
    @Column(name = "code", length = 64, nullable = false)
    private String code;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    /**
     * 状态：{@code ACTIVE} / {@code DISABLED}。
     * 停用后必须拦在登录环节，而不是只藏菜单——否则已签发的 token 仍然能继续调用接口。
     */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    /**
     * 席位数上限：商业模式的收费抓手，限制的是 AGENT 数量（平台运营与租户管理员不占席位）。
     * 现在只存不校验，开通坐席时再校验；字段先留着，避免将来改表。
     */
    @Column(name = "seat_quota")
    private Integer seatQuota;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";
}
