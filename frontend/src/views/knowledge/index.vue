<template>
  <!--
    页面外壳（侧边菜单、顶栏、内容区内边距）由 layouts/TenantLayout.vue 提供，
    这里只负责内容本身，不再自绘 topbar——否则会出现两层标题栏。
  -->
  <div class="knowledge-page">
    <!-- 上传区 -->
    <el-card class="upload-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>上传文档</span>
          <span class="provider-info">
            <el-tag size="small" type="info" effect="plain">{{ status.embeddingProvider || '加载中...' }}</el-tag>
            <el-tag size="small" effect="plain">{{ status.vectorStoreProvider || '' }}</el-tag>
          </span>
        </div>
      </template>
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
      <template #header>
        <span>检索测试</span>
      </template>
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
/* 内容区已由布局提供内边距，这里只排布卡片间距 */
.knowledge-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  /* 长表格撑满整个宽屏会难以阅读，限制最大宽度后居中 */
  max-width: 1100px;
  margin: 0 auto;
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.provider-info {
  display: flex;
  gap: var(--space-2);
  margin-left: auto;
}

.upload-actions {
  margin-top: var(--space-3);
}

.search-row {
  display: flex;
  gap: var(--space-3);
}

.search-results {
  margin-top: var(--space-3);
}

.search-hit {
  margin-bottom: var(--space-2);
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg);
}

.hit-head {
  display: flex;
  gap: var(--space-3);
  margin-bottom: var(--space-1);
  font-size: var(--text-xs);
}

.hit-index {
  color: var(--color-primary);
  font-weight: 600;
}

.hit-score {
  color: var(--color-warning);
}

.hit-source {
  color: var(--color-text-secondary);
}

.hit-content {
  color: var(--color-text);
  font-size: var(--text-sm);
  line-height: var(--leading-lg);
}
</style>
