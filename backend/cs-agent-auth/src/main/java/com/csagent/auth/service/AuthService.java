package com.csagent.auth.service;

import com.csagent.auth.dto.LoginRequest;
import com.csagent.auth.dto.LoginResponse;
import com.csagent.auth.dto.PlatformLoginRequest;
import com.csagent.auth.entity.PlatformUserEntity;
import com.csagent.auth.entity.TenantEntity;
import com.csagent.auth.entity.UserEntity;
import com.csagent.auth.jwt.JwtService;
import com.csagent.auth.repository.PlatformUserRepository;
import com.csagent.auth.repository.TenantRepository;
import com.csagent.auth.repository.UserRepository;
import com.csagent.common.constant.RoleConstants;
import com.csagent.common.context.CurrentUser;
import com.csagent.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录服务：把"租户编码 + 用户名 + 密码"换成一张令牌。
 *
 * <p><b>两条安全纪律，改代码时不要破坏</b>：</p>
 * <p>1. <b>不区分"用户不存在"与"密码错误"</b>。分开提示等于给出账号枚举接口，
 * 攻击者可以先批量确认哪些用户名存在，再针对性地撞库。</p>
 * <p>2. <b>用户不存在时也要做一次密码比对</b>。BCrypt 比对耗时约几十毫秒，若不存在时直接返回，
 * 响应时间会明显更短，攻击者靠计时就能判断用户名是否存在（时序侧信道）。</p>
 */
@Slf4j
@Service
public class AuthService {

    /**
     * 用户不存在时用于"陪跑"比对的假哈希。
     * 目的只是消耗与真实比对相当的时间，值本身无意义，不能对应任何真实密码。
     */
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private static final String INVALID_CREDENTIALS = "用户名或密码错误";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PlatformUserRepository platformUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(TenantRepository tenantRepository,
                       UserRepository userRepository,
                       PlatformUserRepository platformUserRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.platformUserRepository = platformUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * 租户账号登录（ADMIN / AGENT）。
     *
     * <p>用户名在 {@code t_user} 上是全库唯一索引，因此只凭用户名即可定位账号，
     * 无需租户编码。租户归属一律以数据库记录为准——见下面的注释。</p>
     */
    public LoginResponse loginTenant(LoginRequest request) {
        UserEntity user = userRepository.findByUsername(request.username().trim()).orElse(null);

        if (user == null) {
            // 陪跑一次哈希比对，抹平时序差异，理由见类注释第 2 条
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            throw new BusinessException(401, INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(401, INVALID_CREDENTIALS);
        }

        // 状态检查放在密码校验之后：若放在前面，"账号已停用"的提示会让攻击者在不知道密码的
        // 情况下确认该账号存在。
        if (!UserEntity.STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BusinessException(403, "账号已停用，请联系管理员");
        }

        // 账号本身可用，还要确认它所属的租户没被停用。
        // 这里用 user.getTenantId() 而不是任何来自请求的值：请求里根本没有租户字段，
        // 这是设计上的刻意——租户身份一旦可被客户端指定，就等于没有隔离。
        TenantEntity tenant = tenantRepository.findById(user.getTenantId())
                .orElseThrow(() -> new BusinessException(403, "账号所属租户不存在，请联系平台运营"));
        if (!TenantEntity.STATUS_ACTIVE.equals(tenant.getStatus())) {
            throw new BusinessException(403, "该租户已停用，请联系平台运营");
        }

        CurrentUser currentUser = CurrentUser.tenantUser(
                tenant.getId(), user.getId(), user.getUsername(), user.getRole());

        log.info("[鉴权] 租户账号登录成功：tenant={} user={} role={}",
                tenant.getCode(), user.getUsername(), user.getRole());

        return buildResponse(currentUser, tenant.getCode());
    }

    /**
     * 平台账号登录（我们自己的运营）。
     *
     * <p>与租户登录完全分开的代码路径：即使 {@code t_user} 里被人塞进一个同名账号，
     * 也不会影响这里，因为查询的是另一张表。</p>
     */
    public LoginResponse loginPlatform(PlatformLoginRequest request) {
        PlatformUserEntity user = platformUserRepository
                .findByUsername(request.username().trim())
                .orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            throw new BusinessException(401, INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(401, INVALID_CREDENTIALS);
        }

        if (!PlatformUserEntity.STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BusinessException(403, "账号已停用");
        }

        // 平台账号没有 tenantId：这一点决定了它天然无法调用任何租户接口，
        // 因为 TenantContext.requireTenantId() 会抛 403
        CurrentUser currentUser = CurrentUser.platformUser(
                user.getId(), user.getUsername(), RoleConstants.PLATFORM_ADMIN);

        log.info("[鉴权] 平台账号登录成功：user={}", user.getUsername());

        return buildResponse(currentUser, null);
    }

    private LoginResponse buildResponse(CurrentUser user, String tenantCode) {
        String token = jwtService.issue(user);
        return new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpireSeconds(),
                new LoginResponse.UserInfo(
                        user.userId(),
                        user.username(),
                        user.role(),
                        user.tenantId(),
                        tenantCode,
                        user.platform()
                )
        );
    }
}
