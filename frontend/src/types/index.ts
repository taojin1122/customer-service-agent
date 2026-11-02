/** 对话消息角色 */
export type MessageRole = 'user' | 'assistant' | 'tool' | 'system'

/** 前端展示的聊天消息 */
export interface ChatMessage {
  id: string
  role: MessageRole
  content: string
  /** 时间戳 */
  time?: string
  /** 关联的 Agent 执行轨迹（工具调用过程） */
  trace?: AgentTraceItem[]
  /** UI 状态：执行过程面板是否展开 */
  _expanded?: boolean
}

/** Agent 执行轨迹事件（用于过程可视化） */
export interface AgentTraceItem {
  type: 'intent' | 'think' | 'tool_call' | 'tool_result' | 'rag' | 'answer' | 'done' | 'error'
  data: string
}

/** 会话 */
export interface Conversation {
  id: string
  title: string
  modelId: string
  createTime: string
  updateTime: string
}

/** 模型信息 */
export interface ModelInfo {
  id: string
  name: string
  model: string
  defaultModel: boolean
}

/** 工具定义 */
export interface ToolDefinition {
  name: string
  description: string
  domain: string
  highRisk: boolean
}

/** 知识库文档 */
export interface KnowledgeDoc {
  id: string
  fileName: string
  fileType: string
  chunkCount: number
  charCount: number
  createTime: string
  chunkPreviews: string[]
}

/** 检索命中 */
export interface SearchHit {
  id: string
  content: string
  sourceDoc: string
  score: number
  chunkIndex: number
}

/** 系统指标 */
export interface Metrics {
  totalCalls: number
  successCalls: number
  promptTokens: number
  completionTokens: number
  toolCallCounts: Record<string, number>
  intentCounts: Record<string, number>
  snapshotTime: string
}
