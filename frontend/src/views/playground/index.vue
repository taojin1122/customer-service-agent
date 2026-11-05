<template>
  <!--
    AI 调试台：管理员配置完知识库/人设后，用真实对话验证效果的地方。
    它服务的是"配置者"，不是坐席——所以不接客，会话渠道是 internal。
  -->
  <div class="playground">
    <aside class="conv-pane">
      <div class="pane-header">
        <span>我的会话</span>
        <el-button size="small" type="primary" @click="handleNewConversation">
          <el-icon><Plus /></el-icon>&nbsp;新会话
        </el-button>
      </div>

      <div class="conv-list">
        <ConversationItem
          v-for="conv in store.conversations"
          :key="conv.id"
          :title="conv.title"
          :active="conv.id === store.currentConversationId"
          deletable
          @select="handleSwitch(conv.id)"
          @delete="handleDelete(conv.id)"
        />
        <p v-if="!store.conversations.length" class="empty-tip">还没有会话，点「新会话」开始</p>
      </div>
    </aside>

    <section class="chat-pane">
      <header class="chat-header">
        <span class="agent-name">AI 调试台</span>
        <span v-if="store.streaming" class="thinking">
          <el-icon class="is-loading"><Loading /></el-icon> Agent 思考中…
        </span>
        <el-select
          v-model="selectedModel"
          placeholder="选择模型"
          size="small"
          class="model-select"
          @change="handleModelChange"
        >
          <el-option v-for="m in models" :key="m.id" :label="m.name" :value="m.id" />
        </el-select>
      </header>

      <div ref="messageListRef" class="message-list">
        <div v-if="store.messages.length === 0" class="empty-state">
          <el-icon :size="40"><ChatLineRound /></el-icon>
          <p>用真实问题验证机器人答得对不对</p>
          <div class="suggestions">
            <el-tag v-for="s in suggestions" :key="s" class="suggestion-tag" @click="sendSuggestion(s)">
              {{ s }}
            </el-tag>
          </div>
        </div>

        <MessageBubble
          v-for="msg in store.messages"
          :key="msg.id"
          :role="msg.role"
          :content="msg.content"
          :trace="msg.trace"
        />
      </div>

      <div class="composer">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          placeholder="输入问题，例如：我家水管漏水了 / 查询 3-2-501 的账单"
          resize="none"
          @keydown.enter.exact.prevent="handleSend"
        />
        <div class="composer-actions">
          <span class="hint">Enter 发送</span>
          <el-button type="primary" :loading="store.streaming" @click="handleSend">
            <el-icon v-if="!store.streaming"><Promotion /></el-icon>&nbsp;发送
          </el-button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ConversationItem from '@/components/ConversationItem.vue'
import MessageBubble from '@/components/MessageBubble.vue'
import { useChatStore } from '@/stores/chat'
import { listModels } from '@/api/system'
import { fetchSSE } from '@/utils/sse'
import type { AgentTraceItem, ModelInfo } from '@/types'

const store = useChatStore()
const inputText = ref('')
const messageListRef = ref<HTMLElement>()
const models = ref<ModelInfo[]>([])
const selectedModel = ref('')

const suggestions = ['我家水管漏水了', '查询3-2-501的账单', '我家的报修单进度怎么样了', '你们几点下班？']

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
    /* 用户取消 */
  }
}

function handleModelChange() {
  ElMessage.success('已切换模型')
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
  if (!text || store.streaming) {
    return
  }
  if (!store.currentConversationId) {
    await store.newConversation()
  }

  store.pushMessage({ id: `u-${Date.now()}`, role: 'visitor', content: text })
  inputText.value = ''
  store.streaming = true
  scrollToBottom()

  store.pushMessage({ id: `a-${Date.now()}`, role: 'assistant', content: '', trace: [], _expanded: true } as any)
  // push 之后再取回来：数组里拿到的是被响应式代理过的实例，
  // 直接改 push 时那个原始对象的属性不会触发渲染（流式回答会「一个字都不出现」）
  const target = store.messages[store.messages.length - 1] as any
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
        if (type === 'answer') {
          target.content += data
          scrollToBottom()
          return
        }
        if (type === 'done') {
          store.streaming = false
          scrollToBottom()
          return
        }
        if (type === 'error') {
          target.content = data
          store.streaming = false
          return
        }
        // 过程类事件（意图/思考/工具调用/知识检索）进执行轨迹
        trace.push({ type: type as AgentTraceItem['type'], data })
        target.trace = [...trace]
      }
    )
  } catch (e: any) {
    target.content = '网络异常，请确认后端服务已启动'
    ElMessage.error('对话失败：' + e.message)
  } finally {
    store.streaming = false
    // 刷新会话列表（首条消息会更新标题）
    await store.loadConversations()
  }
}
</script>

<style scoped>
.playground {
  display: flex;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.conv-pane {
  display: flex;
  flex-direction: column;
  flex: none;
  width: 240px;
  border-right: 1px solid var(--color-border);
}

.pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: var(--space-3);
  border-bottom: 1px solid var(--color-border);
  font-size: var(--text-sm);
  font-weight: 600;
}

.conv-list {
  flex: 1;
  padding: var(--space-2);
  overflow-y: auto;
}

.empty-tip {
  padding: var(--space-4) var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--text-xs);
  text-align: center;
}

.chat-pane {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--color-border);
}

.agent-name {
  font-size: var(--text-base);
  font-weight: 600;
}

.thinking {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--color-text-secondary);
  font-size: var(--text-xs);
}

.model-select {
  width: 180px;
  margin-left: auto;
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
  gap: var(--space-3);
  padding-top: 15vh;
  color: var(--color-text-tertiary);
  font-size: var(--text-sm);
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--space-2);
  max-width: 420px;
}

.suggestion-tag {
  cursor: pointer;
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
</style>
