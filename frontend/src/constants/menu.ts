import { Role } from './role'

/**
 * 租户端菜单白名单。
 *
 * <b>为什么菜单权限用常量而不是数据库</b>：现在只有两个角色、五个菜单，
 * 做成 `t_role` / `t_permission` / `t_role_permission` 的维护成本高于收益（AGENTS.md §9.4）。
 * 等角色超过 5 个、或出现"同角色在不同租户权限不同"时再升级，届时改动点只在本文件与后端注解。
 *
 * <b>这只是体验层</b>：隐藏菜单挡不住直接调接口。真正的边界是后端的 `@RequireRole`，
 * 两者必须同时存在，缺一个就是"假安全"。
 */
export interface MenuItem {
  /** 唯一标识，用于日志与测试断言 */
  key: string
  label: string
  path: string
  /** Element Plus 图标组件名，main.ts 已全局注册 */
  icon: string
  /** 允许看到该菜单的角色；空数组表示所有登录用户可见 */
  roles: string[]
  /** 未实现的功能标 true：仍然显示但置灰，避免用户点进去看到空白页 */
  disabled?: boolean
}

export const TENANT_MENUS: MenuItem[] = [
  {
    key: 'overview',
    label: '概览',
    path: '/tenant/overview',
    icon: 'DataBoard',
    roles: [Role.ADMIN]
  },
  {
    key: 'console',
    label: '坐席工作台',
    path: '/console',
    icon: 'ChatDotRound',
    roles: [Role.ADMIN, Role.AGENT]
  },
  {
    key: 'knowledge',
    label: '知识库',
    path: '/tenant/knowledge',
    icon: 'FolderOpened',
    roles: [Role.ADMIN]
  },
  {
    // 调试台与知识库同属"配置者的验证工具"：配完知识库要能立刻试答得对不对。
    // 它只产生 internal 会话，不会进入坐席工作台。
    key: 'playground',
    label: 'AI 调试台',
    path: '/tenant/playground',
    icon: 'Monitor',
    roles: [Role.ADMIN]
  },
  {
    key: 'agents',
    label: '坐席管理',
    path: '/tenant/agents',
    icon: 'User',
    roles: [Role.ADMIN],
    disabled: true
  },
  {
    key: 'persona',
    label: '人设与话术',
    path: '/tenant/persona',
    icon: 'Setting',
    roles: [Role.ADMIN],
    disabled: true
  }
]

/**
 * 判断某角色能否访问某个菜单。
 *
 * <b>这是全项目唯一的口径</b>。禁止在组件里散写 `role === 'ADMIN'`：
 * 散写的结果是将来加角色时需要全局搜索替换，必然漏改一处，
 * 而漏掉的那一处就是越权入口（AGENTS.md §9.4）。
 */
export function canAccess(role: string | undefined, menuKey: string): boolean {
  if (!role) {
    return false
  }
  const menu = TENANT_MENUS.find((m) => m.key === menuKey)
  if (!menu) {
    return false
  }
  return menu.roles.length === 0 || menu.roles.includes(role)
}

/**
 * 取某角色可见的菜单列表。
 *
 * 返回的是新数组，调用方修改它不会影响白名单定义——
 * 直接返回引用时，某个组件 `push` 一项进去会导致其它页面也看到。
 */
export function menusFor(role: string | undefined): MenuItem[] {
  if (!role) {
    return []
  }
  return TENANT_MENUS.filter((m) => m.roles.length === 0 || m.roles.includes(role))
}

/**
 * 某角色被拒绝访问后的兜底落点。
 *
 * 不能一律回 `/console`：入参是"角色"，落点就该由该角色的菜单推导出来。
 * 写死路径的话，将来调整菜单顺序或下线某个页面，这里会把人送到一个同样没权限的地址，
 * 表现为"页面打不开并且一直在跳"。
 *
 * 跳过 `disabled` 项——那些页面即使有权限也还没实现。
 */
export function defaultPathFor(role: string | undefined): string {
  const first = menusFor(role).find((m) => !m.disabled)
  // 没有任何可用菜单时回登录页：留在受保护页面里只会反复触发守卫
  return first ? first.path : '/login'
}
