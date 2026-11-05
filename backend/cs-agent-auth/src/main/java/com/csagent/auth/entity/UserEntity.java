package com.csagent.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户账号：属于某一个租户的登录用户，角色只有 ADMIN 或 AGENT。
 *
 * <p><b>为什么与平台账号分表</b>：两者的隔离要求是相反的——租户账号必须被死死限制在
 * 自己的 {@code tenant_id} 内，平台账号必须能跨租户。混在一张表里，任何一处漏写
 * {@code WHERE tenant_id = ?} 的查询都可能让租户账号读到平台账号（AGENTS.md §9.5）。</p>
 */
@Data
@Entity
@Table(name = "t_user", indexes = {
        // 登录只凭"用户名 + 密码"，没有租户编码可作辅助条件，因此用户名必须全库唯一。
        // 这是"登录少填一个字段"的代价：同一时间全平台只有一个 admin，
        // 第二个租户开通账号时必须换名（如 yc-admin），否则插入会撞唯一键。
        @Index(name = "uk_user_username", columnList = "username", unique = true),
        // 坐席管理页要按租户列出成员，仍需要租户维度的索引
        @Index(name = "idx_user_tenant", columnList = "tenant_id")
})
public class UserEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    /**
     * 登录名，<b>全库唯一</b>。
     *
     * <p>全局唯一是登录接口能只收"用户名 + 密码"的前提——否则数据库无法判断
     * 该拿哪个租户下的同名账号去比对密码。</p>
     */
    @Column(name = "username", length = 64, nullable = false)
    private String username;

    /**
     * BCrypt 哈希，长度固定 60 字符，留 100 给将来换算法（如 argon2 更长）。
     * <b>禁止存明文或 MD5</b>：一旦库被拖走，等于全租户账号失守。
     */
    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    /**
     * 角色字面量，见 {@link com.csagent.common.constant.RoleConstants}。
     * 用字符串不用枚举：新增角色不改表、不改历史数据（AGENTS.md §9.2）。
     */
    @Column(name = "role", length = 32, nullable = false)
    private String role;

    /**
     * 状态：{@code ACTIVE} / {@code DISABLED}。
     * 坐席离职时置为 DISABLED 即可，不物理删除——历史会话还要保留 {@code user_id} 追责。
     */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";
}
