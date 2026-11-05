<template>
  <div class="app-layout">
    <!-- 左侧：会话列表 -->
    <aside class="sidebar">
      <div class="sidebar-header">
        <div class="logo">
          <el-icon :size="22"><ChatDotRound /></el-icon>
          <span>智能客服 Agent</span>
        </div>
      </div>
      <el-button class="new-btn" type="primary" @click="handleNewConversation">
        <el-icon><Plus /></el-icon>&nbsp;新会话
      </el-button>
      <div class="conversation-list">
        <div
          v-for="conv in store.conversations"
          :key="conv.id"
          class="conversation-item"
          :class="{ active: conv.id === store.currentConversationId }"
          @click="handleSwitch(conv.id)"
        >
          <el-icon><Message /></el-icon>
          <span class="conv-title">{{ conv.title }}</span>
          <el-icon class="del-icon" @click.stop="handleDelete(conv.id)"><Delete /></el-icon>
        </div>
      </div>
      <div class="sidebar-footer">
        <router-link v-if="auth.isAdmin" to="/tenant/knowledge" class="nav-entry">
          <el-icon><FolderOpened /></el-icon>
          <span>知识库管理</span>
        </router-link>

        <div class="user-row">
          <div class="user-meta">
            <span class="user-name" :title="auth.displayName">{{ auth.displayName }}</span>
            <span class="user-role">{{ roleLabel }}</span>
          </div>
          <button
            class="logout-btn"
            type="button"
            title="退出登录"
            aria-label="退出登录"
            @click="handleLogout"
          >
            <el-icon><SwitchButton /></el-icon>
          </button>
        </div>
      </div>
    </aside>

    <!-- 右侧：对话区 -->
    <main class="chat-area">
      <div class="chat-header">
        <span class="agent-name">小易 · 智能客服</span>
        <span class="agent-status" v-if="store.streaming">
          <el-icon class="is-loading"><Loading /></el-icon> Agent 思考中...
        </span>
        <el-select v-model="selectedModel" placeholder="选择模型" size="small" style="width: 180px" @change="handleModelChange">
          <el-option v-for="m in models" :key="m.id" :label="m.name" :value="m.id" />
        </el-select>
      </div>

      <div class="message-list" ref="messageListRef">
        <div v-if="store.messages.length === 0" class="empty-state">
          <el-icon :size="48" color="#c0c4cc"><ChatLineRound /></el-icon>
          <p>您好！我是智能客服助手小易，可以帮您：</p>
          <div class="suggestions">
            <el-tag v-for="s in suggestions" :key="s" class="suggestion-tag" @click="sendSuggestion(s)">
              {{ s }}
            </el-tag>
          </div>
        </div>

        <div v-for="msg in store.messages" :key="msg.id" class="message-row" :class="msg.role">
          <div class="avatar" :class="msg.role">
            <el-icon v-if="msg.role === 'user'"><User /></el-icon>
            <el-icon v-else><Service /></el-icon>
          </div>
          <div class="bubble-wrap">
            <div class="bubble">
              <div class="msg-content">{{ msg.content }}</div>
            </div>
            <!-- 工具调用过程可视化 -->
            <div v-if="msg.role === 'assistant' && msg.trace && msg.trace.length" class="trace-panel">
              <div class="trace-title" @click="toggleTrace(msg)">
                <el-icon><MagicStick /></el-icon>
                Agent 执行过程（{{ msg.trace.length }} 步）
                <el-icon class="arrow" :class="{ expanded: msg._expanded }"><ArrowDown /></el-icon>
              </div>
              <div v-if="msg._expanded" class="trace-body">
                <div v-for="(item, i) in msg.trace" :key="i" class="trace-item" :class="item.type">
                  <span class="trace-badge">{{ traceLabel(item.type) }}</span>
                  <span class="trace-text">{{ formatTrace(item) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="input-area">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          placeholder="请输入您的问题，例如：我家水管漏水了 / 查询3-2-501的账单"
          resize="none"
          @keydown.enter.exact.prevent="handleSend"
        />
        <div class="input-actions">
          <span class="hint">Enter 发送</span>
          <el-button type="primary" :loading="store.streaming" @click="handleSend">
            <el-icon v-if="!store.streaming"><Promotion /></el-icon>&nbsp;发送
          </el-button>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { Role } from '@/constants/role'
import { listModels } from '@/api/system'
import { fetchSSE } from '@/utils/sse'
import type { AgentTraceItem, ModelInfo } from '@/types'

const router = useRouter()
const store = useChatStore()
const auth = useAuthStore()
const inputText = ref('')
const messageListRef = ref<HTMLElement>()
const models = ref<ModelInfo[]>([])
const selectedModel = ref('')

/** 角色展示文案：直接把 ADMIN/AGENT 显示给客服看没有意义 */
const roleLabel = computed(() => (auth.user?.role === Role.ADMIN ? '管理员' : '坐席'))

const suggestions = [
  '我家水管漏水了',
  '查询3-2-501的账单',
  '我家的报修单进度怎么样了',
  '你们几点下班？'
]

/**
 * 退出登录。
 *
 * 顺序不能反：先清业务缓存，再清令牌。
 * 若先清令牌，紧接着的会话列表请求会因为 401 触发一次跳转，
 * 与这里的 replace('/login') 撞在一起，表现为闪两次页面。
 */
async function handleLogout() {
  store.reset()
  auth.logout()
  await router.replace('/login')
}

onMounted(async () => {
  await store.loadConversations()
  try {
    const res = await listModels()
    models.value = res.data || []
    const def = models.value.find((m) => m.defaultModel)
    selectedModel.value = def ? def.id : models.value[0]?.id || ''
  } catch (e) {
    console.error('加载模型列表失败', e)
  }
})

function traceLabel(type: string): string {
  const map: Record<string, string> = {
    intent: '意图',
    think: '思考',
    tool_call: '工具调用',
    tool_result: '工具结果',
    rag: '知识检索',
    answer: '回答',
    done: '完成',
    error: '错误'
  }
  return map[type] || type
}

function formatTrace(item: AgentTraceItem): string {
  if (item.type === 'tool_call') {
    try {
      const d = JSON.parse(item.data)
      return `${d.tool}(${JSON.stringify(d.args)})`
    } catch {
      return item.data
    }
  }
  if (item.type === 'tool_result') {
    try {
      const d = JSON.parse(item.data)
      return d.output || d.error || ''
    } catch {
      return item.data
    }
  }
  if (item.type === 'intent') {
    try {
      const d = JSON.parse(item.data)
      return `${d.description}（置信度 ${(d.confidence * 100).toFixed(0)}%）`
    } catch {
      return item.data
    }
  }
  return item.data
}

function toggleTrace(msg: any) {
  msg._expanded = !msg._expanded
}

async function handleNewConversation() {
  await store.newConversation()
  inputText.value = ''
}

async function handleSwitch(id: string) {
  await store.switchConversation(id)
  scrollToBottom()
}

async function handleDelete(id: string) {
  try {
    await ElMessageBox.confirm('确定删除该会话吗？', '提示', { type: 'warning' })
    await store.removeConversation(id)
  } catch {
    /* 取消 */
  }
}

function handleModelChange() {
  ElMessage.success(`已切换模型`)
}

function sendSuggestion(text: string) {
  inputText.value = text
  handleSend()
}

function scrollToBottom() {
  nextTick(() => {
    messageListRef.value?.scrollTo({ top: messageListRef.value.scrollHeight, behavior: 'smooth' })
  })
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text) return
  if (store.streaming) return

  // 确保有会话
  if (!store.currentConversationId) {
    await store.newConversation()
  }

  // 推送用户消息
  store.pushMessage({ id: `u-${Date.now()}`, role: 'user', content: text })
  inputText.value = ''
  store.streaming = true
  scrollToBottom()

  // 创建 assistant 占位消息
  const assistantMsg: any = {
    id: `a-${Date.now()}`,
    role: 'assistant',
    content: '',
    trace: [],
    _expanded: true
  }
  store.pushMessage(assistantMsg)

  const trace: AgentTraceItem[] = []

  try {
    await fetchSSE(
      '/api/chat/stream',
      {
        conversationId: store.currentConversationId,
        message: text,
        modelId: selectedModel.value || undefined,
        useRag: true,
        useTools: true
      },
      (type, data) => {
        // 回答内容：增量追加到气泡（不在轨迹面板重复展示）
        if (type === 'answer') {
          assistantMsg.content += data
          scrollToBottom()
          return
        }
        if (type === 'done') {
          store.streaming = false
          scrollToBottom()
          return
        }
        if (type === 'error') {
          assistantMsg.content = data
          store.streaming = false
          return
        }
        // 过程类事件（意图/思考/工具调用/知识检索等）记录到执行轨迹
        const item: AgentTraceItem = { type: type as AgentTraceItem['type'], data }
        trace.push(item)
        assistantMsg.trace = [...trace]
      }
    )
  } catch (e: any) {
    console.error(e)
    assistantMsg.content = '网络异常，请确认后端服务已启动（端口 8080）'
    ElMessage.error('对话失败：' + e.message)
  } finally {
    store.streaming = false
    // 刷新会话列表（标题可能已更新）
    await store.loadConversations()
  }
}
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: 260px;
  background: var(--color-sidebar);
  color: #fff;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
}

.new-btn {
  margin: 12px;
  width: calc(100% - 24px);
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px;
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  color: #cfd8e3;
  font-size: 13px;
}

.conversation-item:hover {
  background: rgba(255, 255, 255, 0.08);
}

.conversation-item.active {
  background: rgba(64, 158, 255, 0.25);
  color: #fff;
}

.conv-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.del-icon {
  opacity: 0;
  transition: opacity 0.2s;
}

.conversation-item:hover .del-icon {
  opacity: 0.8;
}

.sidebar-footer {
  padding: 10px 12px 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.nav-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  color: #cbd5e1;
  font-size: 13px;
  text-decoration: none;
  transition: background 0.15s, color 0.15s;
}

.nav-entry:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}

.user-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px dashed rgba(255, 255, 255, 0.08);
}

.user-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.35;
}

.user-name {
  color: #fff;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  color: #94a3b8;
  font-size: 11px;
}

.logout-btn {
  flex: none;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #94a3b8;
  font-size: 15px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.logout-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.logout-btn:focus-visible {
  outline: 2px solid #93c5fd;
  outline-offset: 1px;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
}

.chat-header {
  height: 56px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 16px;
}

.agent-name {
  font-weight: 600;
  font-size: 15px;
}

.agent-status {
  color: #409eff;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.empty-state {
  text-align: center;
  color: #909399;
  margin-top: 80px;
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 16px;
}

.suggestion-tag {
  cursor: pointer;
  font-size: 13px;
  padding: 4px 12px;
}

.message-row {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.message-row.user {
  flex-direction: row-reverse;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.avatar.user {
  background: #409eff;
  color: #fff;
}

.avatar.assistant {
  background: #67c23a;
  color: #fff;
}

.bubble-wrap {
  max-width: 70%;
  display: flex;
  flex-direction: column;
}

.message-row.user .bubble-wrap {
  align-items: flex-end;
}

.bubble {
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.message-row.user .bubble {
  background: var(--color-primary);
  color: var(--color-text-inverse);
}

.message-row.assistant .bubble {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
}

.trace-panel {
  margin-top: 8px;
  background: #fdf6ec;
  border: 1px solid #faecd8;
  border-radius: 6px;
  font-size: 12px;
  overflow: hidden;
}

.trace-title {
  padding: 8px 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #b88230;
  cursor: pointer;
}

.trace-title .arrow {
  margin-left: auto;
  transition: transform 0.2s;
}

.trace-title .arrow.expanded {
  transform: rotate(180deg);
}

.trace-body {
  padding: 4px 12px 10px;
  border-top: 1px dashed #faecd8;
}

.trace-item {
  display: flex;
  gap: 8px;
  padding: 4px 0;
  align-items: flex-start;
}

.trace-badge {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  color: #fff;
  background: #909399;
}

.trace-item.tool_call .trace-badge {
  background: #409eff;
}

.trace-item.tool_result .trace-badge {
  background: #67c23a;
}

.trace-item.think .trace-badge {
  background: #e6a23c;
}

.trace-item.rag .trace-badge {
  background: #9c27b0;
}

.trace-item.error .trace-badge {
  background: #f56c6c;
}

.trace-text {
  color: #606266;
  word-break: break-all;
  white-space: pre-wrap;
}

.input-area {
  background: var(--color-surface);
  border-top: 1px solid var(--color-border);
  padding: 12px 20px;
}

.input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.hint {
  color: #909399;
  font-size: 12px;
}
</style>
