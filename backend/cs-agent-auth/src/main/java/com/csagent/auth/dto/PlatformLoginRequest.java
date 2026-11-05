package com.csagent.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 平台账号登录请求。
 *
 * <p><b>为什么与 {@link LoginRequest} 分成两个接口而不是合一个</b>：平台账号没有租户，
 * 租户账号必须有租户。合并后接口里会出现"tenantCode 在某些情况下可不填"的隐式约定，
 * 而前端正是靠两个独立的登录页来区分入口的（AGENTS.md §9.1）。分开后，
 * 租户登录页不渲染租户编码输入框，也就不存在"用平台账号尝试登租户端"的路径。</p>
 */
public record PlatformLoginRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password
) {
}
