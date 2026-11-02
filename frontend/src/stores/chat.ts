import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ChatMessage, Conversation, AgentTraceItem } from '@/types'
import { createConversation, listConversations, listMessages } from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<Conversation[]>([])
  const currentConversationId = ref('')
  const messages = ref<ChatMessage[]>([])
  const streaming = ref(false)

  /** 加载会话列表 */
  async function loadConversations() {
    const res = await listConversations()
    conversations.value = res.data || []
  }

  /** 新建会话 */
  async function newConversation() {
    const res = await createConversation()
    currentConversationId.value = res.data.id
    messages.value = []
    await loadConversations()
    return res.data
  }

  /** 切换会话 */
  async function switchConversation(id: string) {
    currentConversationId.value = id
    const res = await listMessages(id)
    messages.value = (res.data || []).map((m: any) => ({
      id: m.id,
      role: m.role,
      content: m.content || '',
      time: m.createTime,
      trace: []
    }))
  }

  /** 删除会话 */
  async function removeConversation(id: string) {
    const { deleteConversation } = await import('@/api/chat')
    await deleteConversation(id)
    if (currentConversationId.value === id) {
      currentConversationId.value = ''
      messages.value = []
    }
    await loadConversations()
  }

  /** 追加一条消息 */
  function pushMessage(msg: ChatMessage) {
    messages.value.push(msg)
  }

  /** 更新最后一条 assistant 消息（流式增量） */
  function updateLastAssistant(delta: string, trace: AgentTraceItem[]) {
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'assistant') {
      last.content += delta
      if (trace.length) last.trace = trace
    }
  }

  return {
    conversations,
    currentConversationId,
    messages,
    streaming,
    loadConversations,
    newConversation,
    switchConversation,
    removeConversation,
    pushMessage,
    updateLastAssistant
  }
})
