import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchMe, loginPlatform, loginTenant, type TenantLoginPayload, type UserInfo } from '@/api/auth'
import { Role } from '@/constants/role'
import { clearToken, detectScope, getToken, setToken, type AuthScope } from '@/utils/token'

/**
 * 登录态。
 *
 * 三件事刻意不做：
 * 1. 不解析 JWT 取用户信息——令牌内容是签名而非加密，前端读到的信息不可信；
 * 2. 不在登录后立刻信任本地状态——页面刷新时会调 /auth/me 向后端确认一次，
 *    保证角色被改动后能及时生效；
 * 3. 不把 token 存进这个 store 的 state——它已经在 sessionStorage 里，
 *    再存一份就有两份真相，容易出现"store 有 token 但存储没有"的错乱。
 */
export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfo | null>(null)
  const scope = ref<AuthScope>(detectScope())

  const isLoggedIn = computed(() => user.value !== null)
  const isAdmin = computed(() => user.value?.role === Role.ADMIN)
  const isPlatform = computed(() => user.value?.platform === true)
  const displayName = computed(() => user.value?.username ?? '')

  /** 租户账号登录（ADMIN / AGENT）。用户名全库唯一，无需租户编码 */
  async function loginAsTenant(payload: TenantLoginPayload) {
    const res = await loginTenant(payload)
    applyLogin('tenant', res.data.token, res.data.user)
    return res.data.user
  }

  /** 平台账号登录 */
  async function loginAsPlatform(payload: { username: string; password: string }) {
    const res = await loginPlatform(payload)
    applyLogin('platform', res.data.token, res.data.user)
    return res.data.user
  }

  /**
   * 恢复登录态（页面刷新后调用）。
   *
   * 令牌存在但 /auth/me 失败时（过期、账号被停用），清掉本地令牌并返回 false，
   * 由路由守卫把用户送回登录页。不在这里抛异常：初始化阶段的异常会打断整个应用挂载。
   */
  async function restore(): Promise<boolean> {
    const currentScope = detectScope()
    if (!getToken(currentScope)) {
      return false
    }
    try {
      const res = await fetchMe()
      user.value = res.data
      scope.value = currentScope
      return true
    } catch {
      clearToken(currentScope)
      user.value = null
      return false
    }
  }

  /** 退出登录：只清当前端的令牌，不影响另一个标签页里的平台端会话 */
  function logout() {
    clearToken(scope.value)
    user.value = null
  }

  function applyLogin(target: AuthScope, token: string, info: UserInfo) {
    setToken(target, token)
    scope.value = target
    user.value = info
  }

  return {
    user,
    scope,
    isLoggedIn,
    isAdmin,
    isPlatform,
    displayName,
    loginAsTenant,
    loginAsPlatform,
    restore,
    logout
  }
})
