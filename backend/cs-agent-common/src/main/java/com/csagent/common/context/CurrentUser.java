package com.csagent.common.context;

import com.csagent.common.constant.RoleConstants;

/**
 * 当前请求的身份快照：谁、属于哪个租户、什么角色。
 *
 * <p><b>这个对象从哪来</b>：由 JWT 过滤器解析令牌后构造，写入 {@link TenantContext}。
 * 业务代码只读不写，<b>绝不接受前端传入</b>——一旦允许从请求体取 tenantId，
 * 改一个 JSON 字段就能横向越权到别的租户（AGENTS.md §8.1①）。</p>
 *
 * <p><b>为什么用 record</b>：它是纯数据载体，一经构造就不该被修改。
 * 用可变 POJO 的话，某处随手 {@code setTenantId(...)} 就能在请求中途换租户，
 * 属于最难排查的一类越权。</p>
 *
 * @param tenantId 租户 ID；平台账号为 {@code null}（平台运营跨租户，不属于任何租户）
 * @param userId   用户 ID
 * @param username 登录名，仅用于日志与审计展示
 * @param role     角色字面量，见 {@link com.csagent.common.constant.RoleConstants}
 * @param platform 是否为平台账号。单独用布尔字段而不是靠 {@code role} 判断，
 *                 是因为"是否平台账号"决定的是<b>数据可见范围</b>（跨租户 vs 单租户），
 *                 属于比角色更底层的隔离维度；混在角色判断里，将来新增平台角色时会漏判
 */
public record CurrentUser(
        String tenantId,
        String userId,
        String username,
        String role,
        boolean platform
) {

    /**
     * 构造租户账号身份。
     */
    public static CurrentUser tenantUser(String tenantId, String userId, String username, String role) {
        return new CurrentUser(tenantId, userId, username, role, false);
    }

    /**
     * 构造平台账号身份。平台账号没有 tenantId，这是它与租户账号最本质的区别。
     */
    public static CurrentUser platformUser(String userId, String username, String role) {
        return new CurrentUser(null, userId, username, role, true);
    }

    /**
     * 是否租户管理员。
     *
     * <p>抽成方法而不是各处写 {@code RoleConstants.ADMIN.equals(user.role())}：
     * "管理员能看本租户全部会话、坐席只能看自己的"这条规则此前在 4 个接口里各写了一遍，
     * 新增角色（如将来的班长）时必然漏改一处，漏掉的那处就是越权入口（AGENTS.md §2.4）。</p>
     */
    public boolean isTenantAdmin() {
        return RoleConstants.ADMIN.equals(role);
    }

    /**
     * 是否匿名访客（用户侧客服窗）。
     */
    public boolean isGuest() {
        return RoleConstants.GUEST.equals(role);
    }
}
