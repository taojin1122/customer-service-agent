package com.csagent.auth.web;

import com.csagent.auth.dto.LoginRequest;
import com.csagent.auth.dto.LoginResponse;
import com.csagent.auth.dto.PlatformLoginRequest;
import com.csagent.auth.service.AuthService;
import com.csagent.common.context.CurrentUser;
import com.csagent.common.context.TenantContext;
import com.csagent.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录与身份接口。
 *
 * <p><b>为什么租户登录与平台登录是两个独立路径</b>：见 {@link PlatformLoginRequest} 的说明。
 * 路径上刻意分成 {@code /api/auth/*} 与 {@code /api/platform/auth/*}，
 * 将来平台的其它接口都落在 {@code /api/platform/**} 下，便于在网关层整体隔离。</p>
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 租户账号登录（ADMIN / AGENT）。无需令牌。
     */
    @PostMapping("/auth/login")
    public Result<LoginResponse> loginTenant(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.loginTenant(request));
    }

    /**
     * 平台账号登录。无需令牌。
     */
    @PostMapping("/platform/auth/login")
    public Result<LoginResponse> loginPlatform(@Valid @RequestBody PlatformLoginRequest request) {
        return Result.success(authService.loginPlatform(request));
    }

    /**
     * 当前登录身份：前端刷新页面时用它恢复登录态。
     *
     * <p>为什么不让前端直接解 JWT：令牌里的内容是签名而非加密，前端能读到但没有校验能力；
     * 若前端自行解析展示"我是管理员"，一旦后端角色被改，页面会显示过期信息。
     * 每次刷新向后端确认一次，代价是一次轻量请求，换来的是角色变更及时生效。</p>
     */
    @GetMapping("/auth/me")
    public Result<CurrentUser> me() {
        return Result.success(TenantContext.require());
    }
}
