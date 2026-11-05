package com.csagent.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台账号：我们自己公司的运营/运维，权限跨所有租户。
 *
 * <p><b>为什么单独一张表而不是给 {@link UserEntity} 加个 {@code type} 字段</b>：
 * 平台账号不需要 {@code tenant_id}，而租户账号必须有。同一张表里必然出现
 * "tenant_id 可为空"的情况，此后每个查询都要额外判空，漏判一次就是跨租户读取。
 * 分表后，平台账号在物理上不可能出现在 {@code t_user} 的查询结果里
 * （AGENTS.md §9.5）。</p>
 *
 * <p><b>签发 token 时的差异</b>：平台账号的令牌里没有 {@code tenantId}，
 * 业务侧 {@code TenantContext.requireTenantId()} 会直接拒绝，因此平台账号天然无法调用租户接口。</p>
 */
@Data
@Entity
@Table(name = "t_platform_user", indexes = {
        @Index(name = "uk_platform_user_username", columnList = "username", unique = true)
})
public class PlatformUserEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "username", length = 64, nullable = false)
    private String username;

    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    public static final String STATUS_ACTIVE = "ACTIVE";
}
