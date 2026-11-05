package com.csagent.auth.dto;

/**
 * 登录成功响应：令牌 + 前端渲染首屏所需的最小身份信息。
 *
 * <p><b>为什么把身份信息一起返回</b>：前端拿到 token 后立刻要渲染侧边栏（按角色过滤菜单），
 * 若不带回身份就得再发一次请求，首屏会闪一下空菜单。</p>
 *
 * <p><b>为什么不返回密码哈希、状态等字段</b>：响应本身就是对外契约，
 * 多带一个字段就多一处泄露面，也让前端误以为可以依赖它。</p>
 */
public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        UserInfo user
) {

    /**
     * @param platform 是否平台账号。前端据此决定跳 {@code /platform} 还是 {@code /console}
     */
    public record UserInfo(
            String userId,
            String username,
            String role,
            String tenantId,
            String tenantCode,
            boolean platform
    ) {
    }
}
