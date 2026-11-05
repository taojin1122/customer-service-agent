package com.csagent.auth.repository;

import com.csagent.auth.entity.PlatformUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 平台账号仓储。
 */
public interface PlatformUserRepository extends JpaRepository<PlatformUserEntity, String> {

    Optional<PlatformUserEntity> findByUsername(String username);
}
