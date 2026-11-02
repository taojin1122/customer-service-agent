import request from '@/utils/request'
import type { ModelInfo, ToolDefinition, Metrics } from '@/types'

/** 模型列表 */
export function listModels() {
  return request.get('/system/models')
}

/** 工具列表 */
export function listTools() {
  return request.get('/system/tools')
}

/** 知识库状态 */
export function knowledgeStatus() {
  return request.get('/system/knowledge/status')
}

/** 运行指标 */
export function getMetrics() {
  return request.get('/system/metrics')
}
