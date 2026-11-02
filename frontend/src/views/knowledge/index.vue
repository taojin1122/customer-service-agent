<template>
  <div class="app-layout">
    <header class="topbar">
      <router-link to="/chat" class="back-link">
        <el-icon><ArrowLeft /></el-icon> 返回对话
      </router-link>
      <span class="title">知识库管理（RAG）</span>
      <span class="provider-info">
        <el-tag size="small" type="info">{{ status.embeddingProvider || '加载中...' }}</el-tag>
        <el-tag size="small">{{ status.vectorStoreProvider || '' }}</el-tag>
      </span>
    </header>

    <div class="content">
      <!-- 上传区 -->
      <el-card class="upload-card" shadow="never">
        <el-upload
          drag
          :auto-upload="false"
          :limit="1"
          accept=".txt,.md,.pdf,.docx"
          :on-change="handleFileChange"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">支持 txt / md / pdf / docx，单文件 ≤ 20MB</div>
          </template>
        </el-upload>
        <div class="upload-actions">
          <el-button type="primary" :loading="uploading" :disabled="!selectedFile" @click="handleUpload">
            摄入文档（解析 → 分块 → 向量化）
          </el-button>
        </div>
      </el-card>

      <!-- 检索测试 -->
      <el-card class="search-card" shadow="never">
        <div class="search-row">
          <el-input v-model="searchQuery" placeholder="输入问题测试检索，如：宠物可以进电梯吗" @keydown.enter="handleSearch" />
          <el-button type="success" @click="handleSearch">检索测试</el-button>
        </div>
        <div v-if="searchHits.length" class="search-results">
          <div v-for="(hit, i) in searchHits" :key="hit.id" class="search-hit">
            <div class="hit-head">
              <span class="hit-index">Top {{ i + 1 }}</span>
              <span class="hit-score">相似度 {{ hit.score.toFixed(3) }}</span>
              <span class="hit-source">来源：{{ hit.sourceDoc }}</span>
            </div>
            <div class="hit-content">{{ hit.content }}</div>
          </div>
        </div>
      </el-card>

      <!-- 文档列表 -->
      <el-card class="doc-card" shadow="never">
        <template #header>
          <span>已摄入文档（{{ docs.length }}）</span>
        </template>
        <el-table :data="docs" style="width: 100%">
          <el-table-column prop="fileName" label="文件名" min-width="200" />
          <el-table-column prop="fileType" label="类型" width="80" />
          <el-table-column prop="chunkCount" label="分块数" width="90" />
          <el-table-column prop="charCount" label="字符数" width="100" />
          <el-table-column prop="createTime" label="摄入时间" width="180" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button type="danger" size="small" text @click="handleDeleteDoc(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { uploadDoc, listDocs, deleteDoc, searchKnowledge } from '@/api/knowledge'
import { knowledgeStatus } from '@/api/system'
import type { KnowledgeDoc, SearchHit } from '@/types'

const docs = ref<KnowledgeDoc[]>([])
const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const searchQuery = ref('')
const searchHits = ref<SearchHit[]>([])
const status = ref<any>({})

onMounted(async () => {
  await loadDocs()
  try {
    const res = await knowledgeStatus()
    status.value = res.data || {}
  } catch {
    /* ignore */
  }
})

async function loadDocs() {
  const res = await listDocs()
  docs.value = res.data || []
}

function handleFileChange(file: UploadFile) {
  selectedFile.value = file.raw || null
}

async function handleUpload() {
  if (!selectedFile.value) return
  uploading.value = true
  try {
    const res = await uploadDoc(selectedFile.value)
    ElMessage.success(`摄入成功：${res.data.fileName}，${res.data.chunkCount} 个分块`)
    selectedFile.value = null
    await loadDocs()
    const st = await knowledgeStatus()
    status.value = st.data || {}
  } catch (e: any) {
    ElMessage.error('摄入失败：' + e.message)
  } finally {
    uploading.value = false
  }
}

async function handleDeleteDoc(id: string) {
  await deleteDoc(id)
  ElMessage.success('已删除')
  await loadDocs()
}

async function handleSearch() {
  if (!searchQuery.value.trim()) return
  const res = await searchKnowledge(searchQuery.value.trim(), 3)
  searchHits.value = res.data || []
  if (!searchHits.value.length) {
    ElMessage.info('未检索到相关内容')
  }
}
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
  background: #f5f7fa;
}

.topbar {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 0 20px;
}

.back-link {
  color: #409eff;
  text-decoration: none;
  display: flex;
  align-items: center;
  gap: 4px;
}

.title {
  font-weight: 600;
  font-size: 15px;
}

.provider-info {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.content {
  max-width: 1000px;
  margin: 20px auto;
  padding: 0 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.upload-actions {
  margin-top: 12px;
}

.search-row {
  display: flex;
  gap: 12px;
}

.search-results {
  margin-top: 12px;
}

.search-hit {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 10px;
  background: #fafafa;
}

.hit-head {
  display: flex;
  gap: 12px;
  margin-bottom: 6px;
  font-size: 12px;
}

.hit-index {
  color: #409eff;
  font-weight: 600;
}

.hit-score {
  color: #e6a23c;
}

.hit-source {
  color: #909399;
}

.hit-content {
  font-size: 13px;
  color: #303133;
  line-height: 1.6;
}
</style>
