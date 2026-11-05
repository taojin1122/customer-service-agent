import request from '@/utils/request'
import type { Conversation } from '@/types'

/** 创建会话（内部调试台用，渠道 internal） */
export function createConversation(modelId?: string) {
  return request.post('/chat/conversation', { modelId })
}

/** 会话列表 */
export function listConversations() {
  return request.get('/chat/conversations')
}

/** 会话消息历史 */
export function listMessages(conversationId: string) {
  return request.get(`/chat/conversation/${conversationId}/messages`)
}

/** 删除会话 */
export function deleteConversation(conversationId: string) {
  return request.post(`/chat/conversation/${conversationId}/delete`)
}

/** 坐席工作台：待接入 + 我处理中的会话 */
export function listInbox() {
  return request.get('/chat/inbox')
}

/** 接管会话 */
export function takeOverConversation(conversationId: string) {
  return request.post(`/chat/conversation/${conversationId}/take-over`)
}

/** 以坐席身份回复访客 */
export function replyAsAgent(conversationId: string, content: string) {
  return request.post(`/chat/conversation/${conversationId}/agent-message`, { content })
}

/** 结束会话 */
export function closeConversation(conversationId: string) {
  return request.post(`/chat/conversation/${conversationId}/close`)
}
