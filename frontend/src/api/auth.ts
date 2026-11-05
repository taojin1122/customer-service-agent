import request from '@/utils/request'

/** 后端统一响应结构，与 com.csagent.common.result.Result 对应 */
export interface ApiResult<T> {
  success: boolean
  code: number
  message: string
  data: T
}

/** 登录后返回的身份信息，用于渲染侧边栏与权限判断 */
export interface UserInfo {
  userId: string
  username: string
  role: string
  tenantId: string | null
  tenantCode: string | null
  platform: boolean
}

export interface LoginResult {
  token: string
  tokenType: string
  expiresInSeconds: number
  user: UserInfo
}

export interface TenantLoginPayload {
  /** 用户名全库唯一，仅凭它即可定位账号，因此不再需要租户编码 */
  username: string
  password: string
}

export interface PlatformLoginPayload {
  username: string
  password: string
}

/** 租户账号登录（ADMIN / AGENT） */
export function loginTenant(payload: TenantLoginPayload) {
  return request.post<unknown, ApiResult<LoginResult>>('/auth/login', payload)
}

/** 平台账号登录 */
export function loginPlatform(payload: PlatformLoginPayload) {
  return request.post<unknown, ApiResult<LoginResult>>('/platform/auth/login', payload)
}

/**
 * 拉取当前登录身份。
 *
 * 页面刷新后用它恢复登录态，而不是直接解 JWT：
 * 令牌内容是签名而非加密，前端能读但没有校验能力；若后端改了角色，
 * 前端按旧令牌渲染会显示过期信息。
 */
export function fetchMe() {
  return request.get<unknown, ApiResult<UserInfo>>('/auth/me')
}
