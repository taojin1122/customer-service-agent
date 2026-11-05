<template>
  <div class="message-row" :class="role">
    <div class="avatar" :class="role">
      <el-icon v-if="isVisitor"><User /></el-icon>
      <el-icon v-else><Service /></el-icon>
    </div>

    <div class="bubble-wrap">
      <div class="bubble">
        <div class="content">{{ content }}</div>
      </div>

      <!--
        Agent 执行过程只对机器人回复展示：坐席工作台里的人工消息没有思考/工具调用，
        传 trace 也不会有内容，这个判断留在这里由组件自己兜住，调用方不必区分。
      -->
      <div v-if="trace && trace.length" class="trace-panel">
        <button class="trace-title" type="button" @click="expanded = !expanded">
          <el-icon><MagicStick /></el-icon>
          Agent 执行过程（{{ trace.length }} 步）
          <el-icon class="arrow" :class="{ expanded }"><ArrowDown /></el-icon>
        </button>
        <div v-if="expanded" class="trace-body">
          <div v-for="(item, i) in trace" :key="i" class="trace-item" :class="item.type">
            <span class="trace-badge">{{ traceLabel(item.type) }}</span>
            <span class="trace-text">{{ formatTrace(item) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { AgentTraceItem } from '@/types'

/**
 * 一条对话消息气泡。
 *
 * <p>抽成组件的直接原因：AI 调试台与坐席工作台都要渲染消息，两边的差异只在
 * "有没有执行轨迹"和"人工消息的来源标签"，气泡本身完全一致。
 * 不抽的话，改一次气泡样式要改两个文件，而且迟早会出现只有一边修好的 bug。</p>
 *
 * <p>它不读 store、不发请求：调用方把消息传进来即可，这样两个页面能用同一份渲染逻辑。</p>
 */
const props = defineProps<{
  /** 消息角色：visitor / agent / assistant / tool / system（user 为历史兼容） */
  role: string
  content: string
  /** 机器人执行轨迹，仅 assistant 消息会有 */
  trace?: AgentTraceItem[]
}>()

/** 展开状态放在组件内部：它纯粹是视觉状态，父组件不该关心 */
const expanded = ref(true)

/** `user` 是改造前的角色值，历史消息里仍存在 */
const isVisitor = computed(() => props.role === 'visitor' || props.role === 'user')

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

/**
 * 轨迹项的展示文本。
 *
 * 事件里的 data 是后端序列化的 JSON 字符串，解析失败时原样展示而不是抛错——
 * 轨迹面板是辅助信息，它坏了不该让整条消息渲染失败。
 */
function formatTrace(item: AgentTraceItem): string {
  try {
    if (item.type === 'tool_call') {
      const d = JSON.parse(item.data)
      return `${d.tool}(${JSON.stringify(d.args)})`
    }
    if (item.type === 'tool_result') {
      const d = JSON.parse(item.data)
      return d.output || d.error || ''
    }
    if (item.type === 'intent') {
      const d = JSON.parse(item.data)
      return `${d.description}（置信度 ${(d.confidence * 100).toFixed(0)}%）`
    }
  } catch {
    return item.data
  }
  return item.data
}
</script>

<style scoped>
.message-row {
  display: flex;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.message-row.user,
.message-row.visitor {
  flex-direction: row-reverse;
}

.avatar {
  display: grid;
  flex: none;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  background: var(--color-bg);
  color: var(--color-text-secondary);
  font-size: 15px;
}

.avatar.user,
.avatar.visitor {
  background: var(--color-primary);
  color: var(--color-text-inverse);
}

.avatar.assistant {
  background: var(--color-success-soft);
  color: var(--color-success);
}

/* 人工坐席用强调色：转人工后访客要一眼看出换了人 */
.avatar.agent {
  background: var(--color-accent-soft);
  color: var(--color-accent);
}

.bubble-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  max-width: 78%;
}

.message-row.user .bubble-wrap,
.message-row.visitor .bubble-wrap {
  align-items: flex-end;
}

.bubble {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  font-size: var(--text-sm);
  line-height: var(--leading-lg);
}

.message-row.user .bubble,
.message-row.visitor .bubble {
  border-color: transparent;
  background: var(--color-primary);
  color: var(--color-text-inverse);
}

.message-row.agent .bubble {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
}

.content {
  white-space: pre-wrap;
  word-break: break-word;
}

.trace-panel {
  width: 100%;
  margin-top: var(--space-2);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg);
}

.trace-title {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 6px 10px;
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: var(--text-xs);
  text-align: left;
  cursor: pointer;
}

.trace-title .arrow {
  margin-left: auto;
  transition: transform var(--duration-fast) var(--ease-out);
}

.trace-title .arrow.expanded {
  transform: rotate(180deg);
}

.trace-body {
  padding: 0 10px 8px;
}

.trace-item {
  display: flex;
  gap: 8px;
  padding: 3px 0;
  color: var(--color-text-secondary);
  font-size: var(--text-xs);
  line-height: var(--leading-lg);
}

.trace-badge {
  flex: none;
  min-width: 56px;
  color: var(--color-primary);
}

.trace-text {
  word-break: break-all;
}

.trace-item.error .trace-badge {
  color: var(--color-danger);
}
</style>
