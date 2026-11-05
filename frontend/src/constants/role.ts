/**
 * 角色常量。
 *
 * 必须与后端 `com.csagent.common.constant.RoleConstants` 逐字一致。
 * 拼错（例如写成小写 admin）不会在编译期报错，只会让权限判断静默失效——
 * 表现为"管理员看不到管理菜单"，排查时第一反应通常是后端问题，很浪费时间。
 * 这是 AGENTS.md §9.6 明确列出的一条约定。
 */
export const Role = {
  /** 平台运营：我们自己公司的人，权限跨所有租户 */
  PLATFORM_ADMIN: 'PLATFORM_ADMIN',
  /** 租户管理员：客户公司的配置者 */
  ADMIN: 'ADMIN',
  /** 坐席：客户公司的一线客服 */
  AGENT: 'AGENT'
} as const

export type RoleType = (typeof Role)[keyof typeof Role]

/** 是否租户管理员（可管理配置、可看本租户全部会话） */
export function isAdmin(role?: string | null): boolean {
  return role === Role.ADMIN
}

/** 是否平台账号（跨租户，不能进租户端） */
export function isPlatform(role?: string | null): boolean {
  return role === Role.PLATFORM_ADMIN
}
