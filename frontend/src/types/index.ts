/**
 * 对话消息角色。
 *
 * `visitor` = 终端访客，`agent` = 人工坐席，`assistant` = 机器人。
 * 三者分开的原因：转人工后访客看到的"客服"有两个来源，界面上要能区分是机器人答的
 * 还是人答的（坐席自己也要能判断"这句是不是我已经回过了"）。
 * `user` 仅用于兼容改造前的历史消息。
 */
export type MessageRole = 'visitor' | 'agent' | 'assistant' | 'tool' | 'system' | 'user'

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

/** 会话状态：机器人接待 / 等待人工 / 人工中 / 已结束 */
export type ConversationStatus = 'bot' | 'waiting' | 'human' | 'closed'

/** 会话来源渠道：内部调试台 / 网页客服窗 */
export type ConversationChannel = 'internal' | 'web'

/** 会话 */
export interface Conversation {
  id: string
  title: string
  modelId: string
  /** 发起方（终端访客）；内部调试台里就是当前账号自己 */
  visitorId?: string
  /** 接手的人工坐席，空表示仍由机器人接待 */
  agentId?: string
  status?: ConversationStatus
  channel?: ConversationChannel
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
