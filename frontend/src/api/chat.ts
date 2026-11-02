import request from '@/utils/request'
import type { Conversation } from '@/types'

/** 创建会话 */
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
