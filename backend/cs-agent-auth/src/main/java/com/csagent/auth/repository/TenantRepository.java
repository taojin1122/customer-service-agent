package com.csagent.auth.repository;

import com.csagent.auth.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 租户仓储。
 */
public interface TenantRepository extends JpaRepository<TenantEntity, String> {

    /**
     * 按租户编码查租户，登录时用于把用户输入的 {@code tenantCode} 换成内部 {@code tenantId}。
     *
     * <p>为什么不让前端直接传 {@code tenantId}：内部 id 是随机串，前端持有它等于把
     * "数据库主键"暴露给浏览器；而编码是人可读的、允许公开的标识。</p>
     */
    Optional<TenantEntity> findByCode(String code);
}
