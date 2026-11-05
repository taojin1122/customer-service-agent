import axios from 'axios'

/**
 * 用户侧客服窗 API。
 *
 * <b>为什么不复用 utils/request.ts</b>：那个实例会带 Authorization 令牌、
 * 401 时整页跳登录页。访客没有账号，跳登录页等于把客服窗变成一个坏掉的弹窗。
 * 两个入口的鉴权前提完全不同，必须用两个实例（AGENTS.md §8.1① 的例外条款）。
 */

/** 访客标识的本地存储键 */
const VISITOR_KEY = 'cs-agent-visitor-id'

/**
 * 取（或首次生成）访客匿名标识。
 *
 * 它只用于"区分不同访客"，<b>不是身份凭证</b>：清掉浏览器存储就是一个新访客。
 * 所以服务端从不拿它做鉴权，只用来校验"这条会话是不是你发起的"。
 */
export function getVisitorId(): string {
  let id = localStorage.getItem(VISITOR_KEY)
  if (!id) {
    id = crypto.randomUUID().replace(/-/g, '')
    localStorage.setItem(VISITOR_KEY, id)
  }
  return id
}

const widgetClient = axios.create({
  baseURL: '/api/widget',
  timeout: 60000
})

widgetClient.interceptors.request.use((config) => {
  config.headers['X-Visitor-Id'] = getVisitorId()
  return config
})

widgetClient.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && res.success === false) {
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    const message = error.response?.data?.message || '网络异常，请稍后重试'
    return Promise.reject(new Error(message))
  }
)

/** 创建访客会话 */
export function createWidgetConversation(tenantCode: string) {
  return widgetClient.post(`/${tenantCode}/conversation`, {})
}

/** 会话状态（轮询是否已转人工 / 已有人接入） */
export function widgetConversationStatus(tenantCode: string, conversationId: string) {
  return widgetClient.get(`/${tenantCode}/conversation/${conversationId}`)
}

/** 会话消息（轮询人工回复） */
export function widgetMessages(tenantCode: string, conversationId: string) {
  return widgetClient.get(`/${tenantCode}/conversation/${conversationId}/messages`)
}

/** 访客发消息（已转人工后使用，不触发 AI） */
export function sendVisitorMessage(tenantCode: string, conversationId: string, content: string) {
  return widgetClient.post(`/${tenantCode}/message`, { conversationId, content })
}

/** 请求转人工 */
export function requestHuman(tenantCode: string, conversationId: string) {
  return widgetClient.post(`/${tenantCode}/request-human`, { conversationId })
}

/** 流式对话地址（fetchSSE 用，需要自行带上 X-Visitor-Id 头） */
export function widgetStreamUrl(tenantCode: string) {
  return `/api/widget/${tenantCode}/chat/stream`
}
