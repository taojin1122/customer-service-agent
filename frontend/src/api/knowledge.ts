import request from '@/utils/request'
import type { KnowledgeDoc, SearchHit } from '@/types'

/** 上传文档 */
export function uploadDoc(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/knowledge/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 文档列表 */
export function listDocs() {
  return request.get('/knowledge/docs')
}

/** 删除文档 */
export function deleteDoc(docId: string) {
  return request.post('/knowledge/delete', null, { params: { docId } })
}

/** 检索测试 */
export function searchKnowledge(query: string, k = 3) {
  return request.get('/knowledge/search', { params: { query, k } })
}
