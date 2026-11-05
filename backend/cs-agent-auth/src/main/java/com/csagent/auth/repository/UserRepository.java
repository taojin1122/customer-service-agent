package com.csagent.auth.repository;

import com.csagent.auth.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 租户账号仓储。
 *
 * <p><b>注意这里唯一的"按用户名查"是全库范围的</b>，这是设计要求，不是疏漏：
 * 登录接口只收用户名与密码，没有租户编码作为附加条件，因此必须能全局定位账号。
 * 查到之后的 {@code tenantId} 一律以数据库记录为准，绝不采用请求里携带的值——
 * 否则客户端改一个字段就能把会话挂到别的租户下。</p>
 *
 * <p>除此之外，没有提供任何"按租户列出全部账号"之外的无条件查询方法。</p>
 */
public interface UserRepository extends JpaRepository<UserEntity, String> {

    /**
     * 登录用：按全局唯一的用户名定位账号。
     */
    Optional<UserEntity> findByUsername(String username);

    /**
     * 开通账号前判断重名。
     *
     * <p>用户名全库唯一后，第二家客户的 {@code admin} 会与第一家冲突。
     * 开通流程必须先调本方法给出明确提示，而不是等数据库抛唯一键异常——
     * 那个异常信息里只有索引名，运营看不懂。</p>
     */
    boolean existsByUsername(String username);

    /**
     * 开通坐席前校验席位配额用，只数处于 ACTIVE 状态的 AGENT。
     *
     * <p>为什么排除 DISABLED：离职坐席不应持续占用付费席位，否则租户会为已停用账号付费。</p>
     */
    long countByTenantIdAndRoleAndStatus(String tenantId, String role, String status);
}
