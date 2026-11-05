<template>
  <div class="widget-page">
    <section class="widget-card">
      <header class="widget-header">
        <div class="bot-avatar">
          <el-icon :size="18"><Service /></el-icon>
        </div>
        <div class="bot-info">
          <span class="bot-name">在线客服</span>
          <span class="bot-status">
            <i class="dot" :class="status"></i>{{ statusText }}
          </span>
        </div>
        <el-button
          v-if="status === 'bot'"
          class="human-btn"
          size="small"
          text
          @click="handleRequestHuman"
        >
          转人工
        </el-button>
      </header>

      <div ref="listRef" class="message-list">
        <div v-if="!messages.length" class="empty">
          <p>您好，我是智能客服，请问有什么可以帮您？</p>
          <p class="empty-tip">需要真人协助时，可点右上角「转人工」。</p>
        </div>

        <div v-for="item in messages" :key="item.id" class="row" :class="item.role">
          <div class="bubble">{{ item.content }}</div>
          <span v-if="item.role === 'agent'" class="who">人工客服</span>
        </div>
      </div>

      <footer class="composer">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="1"
          resize="none"
          :disabled="status === 'closed'"
          :placeholder="status === 'bot' ? '请输入您的问题…' : '请输入消息，客服稍后回复…'"
          @keydown.enter.exact.prevent="handleSend"
        />
        <el-button type="primary" :loading="sending" :disabled="status === 'closed'" @click="handleSend">
          发送
        </el-button>
      </footer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  createWidgetConversation,
  getVisitorId,
  requestHuman,
  sendVisitorMessage,
  widgetConversationStatus,
  widgetMessages,
  widgetStreamUrl
} from '@/api/widget'
import { fetchSSE } from '@/utils/sse'
import type { ConversationStatus, MessageRole } from '@/types'

/**
 * 用户侧客服窗（匿名访客入口）。
 *
 * <b>身份</b>：没有登录态，访客标识由浏览器本地生成（见 api/widget.ts）。
 * 它只能区分访客，不能当身份凭证——服务端只拿它校验"这条会话是不是你发起的"。
 *
 * <b>两种对话模式</b>：
 * - bot：输入走 SSE 对话接口，机器人流式回答；
 * - waiting / human：输入只落库给坐席，机器人不再抢答（否则坐席永远接不上话），
 *   同时轮询拉取人工回复。
 *
 * <b>为什么轮询而不是长连接</b>：实时推送需要 WebSocket/SSE 长连接与连接管理，
 * 是独立一步；3 秒轮询对客服场景的感知延迟可接受，且实现简单、故障面小。
 */

interface WidgetMessage {
  id: string
  role: Extract<MessageRole, 'visitor' | 'assistant' | 'agent'>
  content: string
}

const route = useRoute()
const tenantCode = computed(() => String(route.params.tenantCode || ''))

const conversationId = ref('')
const status = ref<ConversationStatus>('bot')
const messages = ref<WidgetMessage[]>([])
const inputText = ref('')
const sending = ref(false)
const listRef = ref<HTMLElement>()

/** 轮询定时器：转人工后才需要，机器人接待阶段完全不需要 */
let pollTimer: number | undefined

const statusText = computed(() => {
  switch (status.value) {
    case 'waiting':
      return '正在为您转接人工…'
    case 'human':
      return '人工客服已接入'
    case 'closed':
      return '会话已结束'
    default:
      return '机器人接待中'
  }
})

onMounted(async () => {
  await ensureConversation()
})

onBeforeUnmount(() => {
  stopPolling()
})

async function ensureConversation() {
  if (!tenantCode.value) {
    ElMessage.error('链接缺少租户标识')
    return
  }
  try {
    const res = await createWidgetConversation(tenantCode.value)
    conversationId.value = res.data.conversationId
    status.value = res.data.status || 'bot'
  } catch (e: any) {
    // 租户编码错误或租户已停用时后端统一返回 404，这里给一句人话
    ElMessage.error(e.message || '客服暂不可用')
  }
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || sending.value || !conversationId.value) {
    return
  }
  inputText.value = ''
  pushMessage('visitor', text, `v-${Date.now()}`)

  if (status.value === 'bot') {
    await streamAnswer(text)
  } else {
    await sendToAgent(text)
  }
}

/** 机器人接待：SSE 流式追问 */
async function streamAnswer(text: string) {
  sending.value = true
  pushMessage('assistant', '', `a-${Date.now()}`)
  const target = messages.value[messages.value.length - 1]

  try {
    await fetchSSE(
      widgetStreamUrl(tenantCode.value),
      { conversationId: conversationId.value, message: text, useRag: true },
      (type, data) => {
        if (type === 'answer') {
          // 直接改数组里的对象（已是被 Vue 代理的实例），改外面那个引用不会触发渲染
          target.content += data
          scrollToBottom()
          return
        }
        if (type === 'error') {
          target.content = data || '服务暂时不可用，请稍后再试'
        }
      },
      { headers: { 'X-Visitor-Id': getVisitorId() } }
    )
  } catch (e: any) {
    target.content = '网络异常，请稍后重试'
  } finally {
    if (!target.content) {
      target.content = '抱歉，我没有理解，请再说一次'
    }
    sending.value = false
    scrollToBottom()
  }
}

/** 已转人工：消息只落库给坐席 */
async function sendToAgent(text: string) {
  sending.value = true
  try {
    await sendVisitorMessage(tenantCode.value, conversationId.value, text)
  } catch (e: any) {
    ElMessage.error(e.message || '发送失败')
  } finally {
    sending.value = false
  }
}

async function handleRequestHuman() {
  if (!conversationId.value) {
    return
  }
  try {
    await requestHuman(tenantCode.value, conversationId.value)
    status.value = 'waiting'
    ElMessage.success('已为您转接人工，请稍候')
    startPolling()
  } catch (e: any) {
    ElMessage.error(e.message || '转人工失败')
  }
}

function startPolling() {
  stopPolling()
  // 3 秒一次：慢了访客会觉得没人管，快了只是无意义的请求
  pollTimer = window.setInterval(poll, 3000)
  poll()
}

function stopPolling() {
  if (pollTimer !== undefined) {
    window.clearInterval(pollTimer)
    pollTimer = undefined
  }
}

async function poll() {
  if (!conversationId.value) {
    return
  }
  try {
    const st = await widgetConversationStatus(tenantCode.value, conversationId.value)
    status.value = st.data.status
    if (status.value === 'closed') {
      stopPolling()
    }

    const res = await widgetMessages(tenantCode.value, conversationId.value)
    appendAgentMessages(res.data || [])
  } catch {
    // 轮询失败保持静默：偶发网络抖动不该在访客界面上弹错误
  }
}

/**
 * 追加还没见过的人工回复。
 *
 * 只处理 agent 角色：机器人回答已经由流式增量渲染过，
 * 全量替换会导致正在输出的气泡闪烁。
 */
function appendAgentMessages(serverMessages: any[]) {
  let appended = false
  for (const item of serverMessages) {
    if (item.role !== 'agent') {
      continue
    }
    if (messages.value.some((m) => m.id === item.id)) {
      continue
    }
    pushMessage('agent', item.content || '', item.id)
    appended = true
  }
  if (appended) {
    scrollToBottom()
  }
}

function pushMessage(role: WidgetMessage['role'], content: string, id: string) {
  messages.value.push({ id, role, content })
  scrollToBottom()
}

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}
</script>

<style scoped>
.widget-page {
  display: grid;
  place-items: center;
  height: 100vh;
  padding: var(--space-4);
  background: var(--color-bg);
}

.widget-card {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 440px;
  height: min(680px, 100%);
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
}

.widget-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--color-border);
}

.bot-avatar {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: var(--radius-full);
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.bot-info {
  display: flex;
  flex-direction: column;
  line-height: var(--leading-sm);
}

.bot-name {
  font-size: var(--text-base);
  font-weight: 600;
}

.bot-status {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--color-text-secondary);
  font-size: var(--text-xs);
}

/* 状态点配文字，不只靠颜色传达（无障碍要求） */
.dot {
  width: 6px;
  height: 6px;
  border-radius: var(--radius-full);
  background: var(--color-success);
}

.dot.waiting {
  background: var(--color-warning);
}

.dot.human {
  background: var(--color-primary);
}

.dot.closed {
  background: var(--color-text-tertiary);
}

.human-btn {
  margin-left: auto;
}

.message-list {
  flex: 1;
  padding: var(--space-4);
  overflow-y: auto;
}

.empty {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-top: var(--space-6);
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  text-align: center;
}

.empty-tip {
  color: var(--color-text-tertiary);
  font-size: var(--text-xs);
}

.row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-bottom: var(--space-3);
}

.row.visitor {
  align-items: flex-end;
}

.bubble {
  max-width: 80%;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg);
  font-size: var(--text-sm);
  line-height: var(--leading-lg);
  white-space: pre-wrap;
  word-break: break-word;
}

.row.visitor .bubble {
  border-color: transparent;
  background: var(--color-primary);
  color: var(--color-text-inverse);
}

/* 人工回复用强调色边框，让访客一眼看出换了人 */
.row.agent .bubble {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
}

.who {
  margin-top: 2px;
  color: var(--color-text-tertiary);
  font-size: var(--text-xs);
}

.composer {
  display: flex;
  gap: var(--space-2);
  padding: var(--space-3);
  border-top: 1px solid var(--color-border);
}

.composer :deep(.el-textarea__inner) {
  padding: 6px 10px;
  font-size: var(--text-sm);
}
</style>
