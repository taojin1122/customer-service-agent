/**
 * 登录令牌的存取。
 *
 * 单独成一个模块而不是塞进 store：request.ts 需要读 token，store 需要写 token，
 * 若两者互相 import 会形成循环依赖（Vite 下表现为某个 import 变成 undefined，
 * 报错位置还完全无关，极难排查）。这里做成零依赖的纯函数层，两边都只依赖它。
 *
 * 用 sessionStorage 而不是 localStorage：
 * 运营常同时开着平台端和租户端两个标签页调试，localStorage 共享会导致后登录的
 * 覆盖前一个，切回去就变成"莫名其妙掉登录"。sessionStorage 天然按标签页隔离。
 */

/** 端类型：决定用哪一份令牌，也决定 401 时跳哪个登录页 */
export type AuthScope = 'tenant' | 'platform'

const KEYS: Record<AuthScope, string> = {
  tenant: 'cs_tenant_token',
  platform: 'cs_platform_token'
}

export function getToken(scope: AuthScope): string | null {
  return sessionStorage.getItem(KEYS[scope])
}

export function setToken(scope: AuthScope, token: string): void {
  sessionStorage.setItem(KEYS[scope], token)
}

export function clearToken(scope: AuthScope): void {
  sessionStorage.removeItem(KEYS[scope])
}

/**
 * 判断当前标签页处于哪个端。
 *
 * 靠"哪份令牌存在"来判断，而不是靠 URL：刷新页面时 URL 可能还没解析完，
 * 但令牌已经在 sessionStorage 里。两者不一致时以令牌为准，避免请求白发一次 401。
 */
export function detectScope(): AuthScope {
  if (getToken('platform')) {
    return 'platform'
  }
  return 'tenant'
}

/** 是否已登录（任一端） */
export function hasAnyToken(): boolean {
  return Boolean(getToken('tenant') || getToken('platform'))
}
