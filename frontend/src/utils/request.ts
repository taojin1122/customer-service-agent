import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearToken, detectScope, getToken } from './token'

const request = axios.create({
  baseURL: '/api',
  timeout: 120000
})

/**
 * 401 跳转防抖。
 *
 * 一个页面首屏可能并发发出多个请求（比如概览页同时拉会话数、文档数、用量），
 * 令牌过期时这些请求会同时返回 401。不防抖的话会连续触发多次跳转，
 * 表现为页面疯狂闪烁，甚至把用户正在填的表单冲掉。
 */
let redirecting = false

/** 登录接口本身不参与 401 跳转：密码输错也会返回 401，跳转会让用户重填表单 */
const LOGIN_PATHS = ['/auth/login', '/platform/auth/login']

request.interceptors.request.use((config) => {
  const token = getToken(detectScope())
  if (token) {
    // 令牌由后端签发，前端只负责搬运，不解析内容（解析出的信息不可信）
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 后端约定：HTTP 200 且 success=false 表示业务失败（鉴权类错误已改为真实 401/403）
    if (res && res.success === false) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  (error) => {
    const status: number | undefined = error.response?.status
    const url: string = error.config?.url ?? ''
    const isLoginRequest = LOGIN_PATHS.some((path) => url.includes(path))

    if (status === 401 && !isLoginRequest) {
      const scope = detectScope()
      clearToken(scope)
      if (!redirecting) {
        redirecting = true
        // 用 location 而不是 router：router 依赖视图组件，而视图组件依赖本文件，
        // 相互 import 会造成循环依赖。整页跳转在这里的代价可以忽略。
        const loginPath = scope === 'platform' ? '/platform/login' : '/login'
        const redirect = encodeURIComponent(window.location.pathname)
        window.location.assign(`${loginPath}?redirect=${redirect}`)
      }
      return Promise.reject(new Error('登录已过期'))
    }

    if (status === 403) {
      ElMessage.error(error.response?.data?.message || '无权限访问该功能')
      return Promise.reject(error)
    }

    // 后端尚未迁移完所有错误码：部分业务失败仍是 HTTP 200 + success=false，
    // 已在上面的成功分支处理。这里兜住真正的网络与服务端异常。
    const message = error.response?.data?.message || error.message || '网络异常，请稍后重试'
    // 登录失败由登录页以表单内联错误展示。这里再弹一次，用户会看到两条重复提示，
    // 而且 Toast 会自动消失，反而盖住了需要停留的内联错误
    if (!isLoginRequest) {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default request
