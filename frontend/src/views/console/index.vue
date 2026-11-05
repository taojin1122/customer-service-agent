<template>
  <div class="console-layout">
    <!-- 左：会话队列 -->
    <aside class="inbox-pane">
      <div class="pane-header">
        <span class="pane-title">坐席工作台</span>
        <el-button size="small" text :loading="refreshing" @click="refreshInbox">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>

      <div class="inbox-scroll">
        <p class="group-title">待接入（{{ waitingList.length }}）</p>
        <ConversationItem
          v-for="conv in waitingList"
          :key="conv.id"
          :title="conv.title"
          :meta="visitorLabel(conv)"
          badge="待接入"
          badge-type="warning"
          :active="conv.id === activeId"
          @select="handleSelect(conv)"
        />
        <p v-if="!waitingList.length" class="group-empty">暂无等待接入的访客</p>

        <p class="group-title">进行中（{{ humanList.length }}）</p>
        <ConversationItem
          v-for="conv in humanList"
          :key="conv.id"
          :title="conv.title"
          :meta="visitorLabel(conv)"
          badge="进行中"
          badge-type="primary"
          :active="conv.id === activeId"
          @select="handleSelect(conv)"
        />
        <p v-if="!humanList.length" class="group-empty">还没有你接待的会话</p>
      </div>

      <div class="pane-footer">
        <router-link v-if="canEnterAdmin" to="/tenant/overview" class="nav-entry">
          <el-icon><DataBoard /></el-icon>
          <span>管理后台</span>
        </router-link>
        <UserMenu :name="auth.displayName" :role="auth.user?.role" @logout="logout" />
      </div>
    </aside>

    <!-- 中：对话区 -->
    <main class="chat-pane">
      <header class="chat-header">
        <div class="chat-title">
          <span>{{ activeConversation ? activeConversation.title : '未选择会话' }}</span>
          <el-tag v-if="activeConversation" size="small" :type="statusTag.type" effect="plain">
            {{ statusTag.text }}
          </el-tag>
        </div>
        <el-button
          v-if="activeConversation"
          size="small"
          text
          type="danger"
          @click="handleClose"
        >
          结束会话
        </el-button>
      </header>

      <div ref="messageListRef" class="message-list">
        <div v-if="!activeConversation" class="empty-state">
          <el-icon :size="40"><ChatDotRound /></el-icon>
          <p>从左侧选择一个会话开始接待</p>
        </div>
        <MessageBubble
          v-for="msg in messages"
          :key="msg.id"
          :role="msg.role"
          :content="msg.content"
        />
      </div>

      <div class="composer">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          resize="none"
          :disabled="!activeConversation"
          :placeholder="activeConversation ? '以客服身份回复，Enter 发送' : '请先选择会话'"
          @keydown.enter.exact.prevent="handleSend"
        />
        <div class="composer-actions">
          <span class="hint">Enter 发送</span>
          <el-button type="primary" :loading="sending" :disabled="!activeConversation" @click="handleSend">
            <el-icon v-if="!sending"><Promotion /></el-icon>&nbsp;发送
          </el-button>
        </div>
      </div>
    </main>

    <!-- 右：会话信息 -->
    <aside class="info-pane">
      <p class="info-title">会话信息</p>
      <template v-if="activeConversation">
        <div class="info-row"><span>状态</span><b>{{ statusTag.text }}</b></div>
        <div class="info-row"><span>访客</span><b>{{ visitorLabel(activeConversation) }}</b></div>
        <div class="info-row"><span>渠道</span><b>{{ channelText }}</b></div>
        <div class="info-row"><span>消息</span><b>{{ messages.length }} 条</b></div>
        <p class="info-tip">
          回复内容会立即写入会话，访客端通过轮询在数秒内看到。机器人不会再介入该会话。
        </p>
      </template>
      <p v-else class="info-placeholder">未选择会话</p>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ConversationItem from '@/components/ConversationItem.vue'
import MessageBubble from '@/components/MessageBubble.vue'
import UserMenu from '@/components/UserMenu.vue'
import { useLogout } from '@/composables/useLogout'
import { useAuthStore } from '@/stores/auth'
import { canAccess } from '@/constants/menu'
import {
  closeConversation,
  listInbox,
  listMessages,
  replyAsAgent,
  takeOverConversation
} from '@/api/chat'
import type { Conversation } from '@/types'

/**
 * 坐席工作台：接待来自用户侧客服窗的真实访客。
 *
 * <p>它与 AI 调试台（`/tenant/playground`）是两件事：那里是管理员验证机器人，
 * 这里是坐席面对客户。区别体现在数据上就是 `channel`（web / internal）与消息角色
 * （agent / assistant）。</p>
 *
 * <p><b>为什么用轮询而不是长连接</b>：实时推送需要 WebSocket 或 SSE 长连接，
 * 要处理断线重连、心跳、多标签页，属于独立一步。3 秒轮询在客服场景下延迟可接受，
 * 优先把"能看到、能回复、不会抢单"做对。</p>
 */

const POLL_INTERVAL = 3000

const auth = useAuthStore()
const { logout } = useLogout()

const inbox = ref<Conversation[]>([])
const activeId = ref('')
const messages = ref<any[]>([])
const inputText = ref('')
const sending = ref(false)
const refreshing = ref(false)
const messageListRef = ref<HTMLElement>()

let pollTimer: number | undefined

const waitingList = computed(() => inbox.value.filter((c) => c.status === 'waiting'))
const humanList = computed(() => inbox.value.filter((c) => c.status === 'human'))
const activeConversation = computed(() => inbox.value.find((c) => c.id === activeId.value))
const canEnterAdmin = computed(() => canAccess(auth.user?.role, 'overview'))

const statusTag = computed(() => {
  switch (activeConversation.value?.status) {
    case 'waiting':
      return { text: '待接入', type: 'warning' as const }
    case 'human':
      return { text: '人工接待中', type: 'primary' as const }
    case 'closed':
      return { text: '已结束', type: 'info' as const }
    default:
      return { text: '机器人接待', type: 'success' as const }
  }
})

const channelText = computed(() => (activeConversation.value?.channel === 'web' ? '网页客服窗' : '内部调试'))

onMounted(async () => {
  await refreshInbox()
  startPolling()
})

onBeforeUnmount(stopPolling)

async function refreshInbox() {
  refreshing.value = true
  try {
    const res = await listInbox()
    inbox.value = res.data || []
    // 已结束/被移除的会话不该继续留在右侧详情里
    if (activeId.value && !inbox.value.some((c) => c.id === activeId.value)) {
      activeId.value = ''
      messages.value = []
    }
  } catch (e: any) {
    ElMessage.error(e.message || '加载会话列表失败')
  } finally {
    refreshing.value = false
  }
}

/**
 * 选中会话。
 *
 * 点击"待接入"的会话即代表接手：先 takeOver 再拉消息。
 * 若在 3 秒内被别人抢先，后端返回 403，这里提示并刷新列表——
 * 静默成功会让两个坐席同时给同一个访客回话。
 */
async function handleSelect(conv: Conversation) {
  activeId.value = conv.id
  if (conv.status === 'waiting') {
    try {
      await takeOverConversation(conv.id)
      await refreshInbox()
    } catch (e: any) {
      ElMessage.warning(e.message || '该会话已被其他坐席接管')
      activeId.value = ''
      await refreshInbox()
      return
    }
  }
  await loadMessages()
}

async function loadMessages() {
  if (!activeId.value) {
    return
  }
  try {
    const res = await listMessages(activeId.value)
    messages.value = res.data || []
    scrollToBottom()
  } catch (e: any) {
    ElMessage.error(e.message || '加载消息失败')
  }
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || !activeId.value || sending.value) {
    return
  }
  sending.value = true
  try {
    await replyAsAgent(activeId.value, text)
    inputText.value = ''
    await loadMessages()
  } catch (e: any) {
    ElMessage.error(e.message || '发送失败')
  } finally {
    sending.value = false
  }
}

async function handleClose() {
  if (!activeId.value) {
    return
  }
  try {
    await closeConversation(activeId.value)
    ElMessage.success('会话已结束')
    activeId.value = ''
    messages.value = []
    await refreshInbox()
  } catch (e: any) {
    ElMessage.error(e.message || '结束会话失败')
  }
}

function startPolling() {
  stopPolling()
  pollTimer = window.setInterval(async () => {
    await refreshInbox()
    if (activeId.value) {
      await loadMessages()
    }
  }, POLL_INTERVAL)
}

function stopPolling() {
  if (pollTimer !== undefined) {
    window.clearInterval(pollTimer)
    pollTimer = undefined
  }
}

function visitorLabel(conv: Conversation) {
  const id = conv.visitorId || ''
  return id ? `访客 ${id.slice(0, 8)}` : '访客'
}

function scrollToBottom() {
  nextTick(() => {
    messageListRef.value?.scrollTo({ top: messageListRef.value.scrollHeight })
  })
}
</script>

<style scoped>
.console-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--color-bg);
}

.inbox-pane {
  display: flex;
  flex-direction: column;
  flex: none;
  width: 280px;
  border-right: 1px solid var(--color-border);
  background: var(--color-surface);
}

.pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--header-height);
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--color-border);
}

.pane-title {
  font-size: var(--text-base);
  font-weight: 600;
}

.inbox-scroll {
  flex: 1;
  padding: var(--space-2);
  overflow-y: auto;
}

.group-title {
  margin: var(--space-3) var(--space-2) var(--space-1);
  color: var(--color-text-tertiary);
  font-size: var(--text-xs);
}

.group-empty {
  padding: 0 var(--space-2) var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--text-xs);
}

.pane-footer {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-3);
  border-top: 1px solid var(--color-border);
}

.nav-entry {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2);
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  text-decoration: none;
}

.nav-entry:hover {
  background: var(--color-bg);
  color: var(--color-primary);
}

.chat-pane {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  background: var(--color-surface);
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--header-height);
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--color-border);
}

.chat-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-base);
  font-weight: 600;
}

.message-list {
  flex: 1;
  padding: var(--space-4);
  overflow-y: auto;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding-top: 20vh;
  color: var(--color-text-tertiary);
  font-size: var(--text-sm);
}

.composer {
  padding: var(--space-3);
  border-top: 1px solid var(--color-border);
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-3);
  margin-top: var(--space-2);
}

.hint {
  color: var(--color-text-tertiary);
  font-size: var(--text-xs);
}

.info-pane {
  flex: none;
  width: 240px;
  padding: var(--space-4);
  border-left: 1px solid var(--color-border);
  background: var(--color-surface);
}

.info-title {
  margin-bottom: var(--space-3);
  font-size: var(--text-sm);
  font-weight: 600;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: var(--space-2) 0;
  border-bottom: 1px dashed var(--color-border);
  color: var(--color-text-secondary);
  font-size: var(--text-xs);
}

.info-row b {
  color: var(--color-text);
  font-weight: 500;
}

.info-tip {
  margin-top: var(--space-4);
  color: var(--color-text-tertiary);
  font-size: var(--text-xs);
  line-height: var(--leading-lg);
}

.info-placeholder {
  color: var(--color-text-tertiary);
  font-size: var(--text-xs);
}
</style>
