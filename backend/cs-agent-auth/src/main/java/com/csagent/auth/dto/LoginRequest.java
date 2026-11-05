package com.csagent.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 租户账号登录请求。
 *
 * <p><b>为什么不需要租户编码</b>：{@code t_user.username} 是全库唯一索引，
 * 只凭用户名就能唯一定位到一个账号，租户归属由数据库记录决定。
 * 用户少记一个字段，也少一个填错的机会。</p>
 *
 * <p><b>为什么响应里仍回传 tenantCode</b>：前端要展示"当前在哪个租户下工作"，
 * 而且租户管理员排查问题时需要这个信息。它只用于显示，不参与任何鉴权判断。</p>
 */
public record LoginRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password
) {
}
