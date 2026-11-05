package com.csagent.server.config;

import com.csagent.auth.entity.PlatformUserEntity;
import com.csagent.auth.entity.TenantEntity;
import com.csagent.auth.entity.UserEntity;
import com.csagent.auth.repository.PlatformUserRepository;
import com.csagent.auth.repository.TenantRepository;
import com.csagent.auth.repository.UserRepository;
import com.csagent.common.constant.RoleConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 开发环境种子数据：首次启动时创建一个演示租户、一个管理员、一个坐席、一个平台账号。
 *
 * <p><b>为什么必须用配置开关控制（{@code cs-agent.init.seed-enabled}）</b>：
 * 自动创建固定密码的账号在生产环境是致命问题——任何人都能用 {@code admin/admin123}
 * 登进任意部署实例。开关默认关闭，只在 dev profile 打开。</p>
 *
 * <p><b>为什么要有"坐席"这个账号</b>：权限矩阵（AGENTS.md §9.3）里 AGENT 与 ADMIN 的可见范围不同，
 * 只有一个管理员账号就无法验证"坐席看不到知识库""坐席只看到自己的会话"这两条，
 * 而它们正是最容易写错的地方。</p>
 *
 * <p><b>幂等性</b>：以"租户表是否为空"作为判断依据。不用"try-catch 唯一键冲突"是因为
 * H2/MySQL 的唯一键异常类型不同，跨库行为不一致。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "cs-agent.init", name = "seed-enabled", havingValue = "true")
public class SeedDataInitializer implements ApplicationRunner {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PlatformUserRepository platformUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public SeedDataInitializer(TenantRepository tenantRepository,
                               UserRepository userRepository,
                               PlatformUserRepository platformUserRepository,
                               PasswordEncoder passwordEncoder,
                               @Value("${cs-agent.init.demo-password:admin123}") String demoPassword) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.platformUserRepository = platformUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tenantRepository.count() > 0) {
            log.info("[初始化] 已存在租户数据，跳过种子数据创建");
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        TenantEntity tenant = new TenantEntity();
        tenant.setId(newId());
        tenant.setCode("demo");
        tenant.setName("演示租户");
        tenant.setStatus(TenantEntity.STATUS_ACTIVE);
        tenant.setSeatQuota(10);
        tenant.setCreateTime(now);
        tenant.setUpdateTime(now);
        tenantRepository.save(tenant);

        userRepository.save(newUser(tenant.getId(), "admin", RoleConstants.ADMIN, now));
        userRepository.save(newUser(tenant.getId(), "agent", RoleConstants.AGENT, now));

        PlatformUserEntity platformUser = new PlatformUserEntity();
        platformUser.setId(newId());
        platformUser.setUsername("platform");
        platformUser.setPasswordHash(passwordEncoder.encode(demoPassword));
        platformUser.setStatus(PlatformUserEntity.STATUS_ACTIVE);
        platformUser.setCreateTime(now);
        platformUserRepository.save(platformUser);

        // 用 warn 而不是 info：这条日志在生产日志里出现就说明种子开关误开了，必须能被检索到
        log.warn("[初始化] 已创建演示数据（仅开发环境）：租户编码=demo，"
                + "账号 admin/{}（管理员）、agent/{}（坐席）、platform/{}（平台运营）。"
                + "生产环境请关闭 cs-agent.init.seed-enabled 并修改所有默认密码。",
                demoPassword, demoPassword, demoPassword);
    }

    private UserEntity newUser(String tenantId, String username, String role, LocalDateTime now) {
        UserEntity user = new UserEntity();
        user.setId(newId());
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(demoPassword));
        user.setRole(role);
        user.setStatus(UserEntity.STATUS_ACTIVE);
        user.setCreateTime(now);
        return user;
    }

    /**
     * 生成 32 位无横线 UUID 作为主键。
     *
     * <p>用 UUID 而不是自增：多实例部署时不需要依赖数据库序列，且主键不可枚举
     * （自增 id 会让攻击者能估算出有多少租户、多少个用户）。</p>
     */
    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
