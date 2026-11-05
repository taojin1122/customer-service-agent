package com.csagent.common.constant;

/**
 * 角色常量：平台层与租户层的角色字面量。
 *
 * <p><b>为什么用 String 而不是 enum</b>：角色会随业务增加（如将来的 SUPERVISOR 班长），
 * 用 enum 时新增角色要改代码 + 兼容历史数据里存的值，成本高；用 String 则只需在白名单里加一行。
 * 代价是拼错不报错、只在运行时静默判错，所以前端 {@code constants/role.ts} 必须与本类逐字一致，
 * 这是 AGENTS.md §9.6 明确的一条约定。</p>
 *
 * <p><b>为什么不带 Spring Security 的 {@code ROLE_} 前缀</b>：本项目的授权走自研
 * {@link com.csagent.common.context.RequireRole} 注解，不经过 Spring Security 的
 * {@code hasRole()} 表达式，因此不需要前缀。前缀只在用 {@code @PreAuthorize} 时才必须，
 * 多一层转换反而给排查越权问题时增加干扰。</p>
 */
public final class RoleConstants {

    /** 平台运营：我们自己公司的运营/运维，权限跨所有租户，不存在 tenantId */
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    /** 租户管理员：客户公司里负责配置系统的人，可管知识库/人设/工具订阅/坐席账号 */
    public static final String ADMIN = "ADMIN";

    /** 坐席：客户公司的一线客服，只能接待与查看自己的会话 */
    public static final String AGENT = "AGENT";

    private RoleConstants() {
    }
}
