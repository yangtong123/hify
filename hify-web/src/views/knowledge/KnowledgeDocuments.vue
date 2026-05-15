<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type UploadInstance, type UploadProps, type UploadRequestOptions } from 'element-plus'
import { Back, Delete, Loading, UploadFilled, View } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import {
  deleteKnowledgeDocument,
  getKnowledgeBase,
  getKnowledgeDocument,
  listKnowledgeDocumentChunks,
  listKnowledgeDocuments,
  uploadKnowledgeDocument,
  type KnowledgeBaseResponse,
  type KnowledgeDocumentResponse,
  type RetrievedChunkDto,
} from '@/api/knowledge'

type DocumentStatus = 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED'

const route = useRoute()
const router = useRouter()

const kbId = computed(() => Number(route.params.kbId))
const knowledgeBase = ref<KnowledgeBaseResponse | null>(null)
const documents = ref<KnowledgeDocumentResponse[]>([])
const loading = ref(false)
const uploadVisible = ref(false)
const uploadRef = ref<UploadInstance>()
const pollTimers = new Map<number, number>()
const pollingRequests = new Set<number>()

const chunksVisible = ref(false)
const chunksLoading = ref(false)
const currentDocument = ref<KnowledgeDocumentResponse | null>(null)
const chunks = ref<RetrievedChunkDto[]>([])
const expandedChunkKeys = ref<number[]>([])

const pageTitle = computed(() => knowledgeBase.value?.name || '文档管理')

function normalizedDocStatus(status: string): DocumentStatus {
  if (status === 'DONE' || status === 'COMPLETED') return 'DONE'
  if (status === 'FAILED') return 'FAILED'
  if (status === 'PENDING') return 'PENDING'
  return 'PROCESSING'
}

function statusLabel(status: string) {
  const normalized = normalizedDocStatus(status)
  const labels: Record<DocumentStatus, string> = {
    PENDING: 'PENDING',
    PROCESSING: 'PROCESSING',
    DONE: 'DONE',
    FAILED: 'FAILED',
  }
  return labels[normalized]
}

function statusType(status: string) {
  const normalized = normalizedDocStatus(status)
  if (normalized === 'DONE') return 'success'
  if (normalized === 'FAILED') return 'danger'
  if (normalized === 'PROCESSING') return 'primary'
  return 'info'
}

function isTerminal(status: string) {
  const normalized = normalizedDocStatus(status)
  return normalized === 'DONE' || normalized === 'FAILED'
}

function formatFileSize(size: number | null | undefined) {
  if (!size) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function formatDate(value: string | null | undefined) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

async function loadKnowledgeBase() {
  try {
    knowledgeBase.value = await getKnowledgeBase(kbId.value)
  } catch {
    knowledgeBase.value = null
  }
}

async function loadDocuments() {
  loading.value = true
  try {
    documents.value = await listKnowledgeDocuments(kbId.value)
    syncPolling()
  } catch {
    documents.value = []
    clearAllPolling()
  } finally {
    loading.value = false
  }
}

function upsertDocument(document: KnowledgeDocumentResponse) {
  const index = documents.value.findIndex((item) => item.id === document.id)
  if (index >= 0) {
    documents.value.splice(index, 1, document)
    return
  }
  documents.value = [document, ...documents.value]
}

function syncPolling() {
  const currentIds = new Set(documents.value.map((item) => item.id))
  for (const document of documents.value) {
    if (!isTerminal(document.processStatus)) {
      startDocumentPolling(document.id)
    }
  }
  for (const documentId of pollTimers.keys()) {
    if (!currentIds.has(documentId)) {
      clearDocumentPolling(documentId)
    }
  }
}

function startDocumentPolling(documentId: number) {
  if (pollTimers.has(documentId)) return
  const timer = window.setInterval(() => {
    pollDocument(documentId)
  }, 3000)
  pollTimers.set(documentId, timer)
}

async function pollDocument(documentId: number) {
  if (pollingRequests.has(documentId)) return
  pollingRequests.add(documentId)
  try {
    const document = await getKnowledgeDocument(documentId)
    upsertDocument(document)
    if (isTerminal(document.processStatus)) {
      clearDocumentPolling(documentId)
      await loadDocuments()
    }
  } catch {
    clearDocumentPolling(documentId)
  } finally {
    pollingRequests.delete(documentId)
  }
}

function clearDocumentPolling(documentId: number) {
  const timer = pollTimers.get(documentId)
  if (timer == null) return
  window.clearInterval(timer)
  pollTimers.delete(documentId)
}

function clearAllPolling() {
  for (const documentId of pollTimers.keys()) {
    clearDocumentPolling(documentId)
  }
  pollingRequests.clear()
}

const beforeUpload: UploadProps['beforeUpload'] = (rawFile) => {
  const extension = rawFile.name.split('.').pop()?.toLowerCase()
  const allowedTypes = new Set(['txt', 'md', 'pdf'])
  const isAllowedType = extension ? allowedTypes.has(extension) : false
  const isAllowedSize = rawFile.size <= 10 * 1024 * 1024
  if (!isAllowedType) {
    ElMessage.warning('仅支持 txt、md、pdf 文件')
    return false
  }
  if (!isAllowedSize) {
    ElMessage.warning('文件大小不能超过 10MB')
    return false
  }
  return true
}

async function handleUpload(options: UploadRequestOptions) {
  try {
    const document = await uploadKnowledgeDocument(kbId.value, options.file)
    upsertDocument(document)
    startDocumentPolling(document.id)
    options.onSuccess?.(document)
    ElMessage.success('文档已上传')
    uploadVisible.value = false
    uploadRef.value?.clearFiles()
  } catch (error: any) {
    options.onError?.(error)
    ElMessage.error(error.message || '上传失败')
  }
}

async function removeDocument(row: KnowledgeDocumentResponse) {
  if (normalizedDocStatus(row.processStatus) === 'PROCESSING') return
  await ElMessageBox.confirm(`确认删除文档「${row.fileName}」？`, '删除文档', { type: 'warning' })
  await deleteKnowledgeDocument(row.id)
  clearDocumentPolling(row.id)
  ElMessage.success('文档已删除')
  await loadDocuments()
}

async function openChunks(row: KnowledgeDocumentResponse) {
  currentDocument.value = row
  chunksVisible.value = true
  chunksLoading.value = true
  expandedChunkKeys.value = []
  try {
    chunks.value = await listKnowledgeDocumentChunks(row.id)
  } finally {
    chunksLoading.value = false
  }
}

function chunkKey(chunk: RetrievedChunkDto) {
  return chunk.chunkId || chunk.chunkIndex
}

function isChunkExpanded(chunk: RetrievedChunkDto) {
  return expandedChunkKeys.value.includes(chunkKey(chunk))
}

function toggleChunk(chunk: RetrievedChunkDto) {
  const key = chunkKey(chunk)
  if (expandedChunkKeys.value.includes(key)) {
    expandedChunkKeys.value = expandedChunkKeys.value.filter((item) => item !== key)
    return
  }
  expandedChunkKeys.value = [...expandedChunkKeys.value, key]
}

function displayChunkContent(chunk: RetrievedChunkDto) {
  if (isChunkExpanded(chunk) || chunk.content.length <= 200) {
    return chunk.content
  }
  return `${chunk.content.slice(0, 200)}...`
}

function goBack() {
  router.push('/knowledge')
}

onMounted(async () => {
  await Promise.all([loadKnowledgeBase(), loadDocuments()])
})

onUnmounted(() => {
  clearAllPolling()
})
</script>

<template>
  <div class="document-page-header">
    <el-button :icon="Back" @click="goBack">返回</el-button>
    <PageHeader :title="pageTitle" description="管理当前知识库的上传文档、处理状态和文本分块">
      <template #actions>
        <el-button type="primary" :icon="UploadFilled" @click="uploadVisible = true">上传文档</el-button>
      </template>
    </PageHeader>
  </div>

  <section class="panel">
    <el-table v-loading="loading" :data="documents" row-key="id">
      <el-table-column prop="fileName" label="文件名" min-width="220" show-overflow-tooltip />
      <el-table-column prop="fileType" label="文件类型" width="100" />
      <el-table-column label="文件大小" width="120">
        <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="chunkCount" label="分块数量" width="110" />
      <el-table-column label="处理状态" width="150">
        <template #default="{ row }">
          <el-tooltip
            v-if="normalizedDocStatus(row.processStatus) === 'FAILED' && row.errorMessage"
            :content="row.errorMessage"
            placement="top"
          >
            <el-tag :type="statusType(row.processStatus)" size="small">
              {{ statusLabel(row.processStatus) }}
            </el-tag>
          </el-tooltip>
          <el-tag v-else :type="statusType(row.processStatus)" size="small">
            <el-icon v-if="normalizedDocStatus(row.processStatus) === 'PROCESSING'" class="status-loading">
              <Loading />
            </el-icon>
            {{ statusLabel(row.processStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link :icon="View" @click="openChunks(row)">查看分块</el-button>
          <el-button
            link
            type="danger"
            :icon="Delete"
            :disabled="normalizedDocStatus(row.processStatus) === 'PROCESSING'"
            @click="removeDocument(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>

  <el-dialog v-model="uploadVisible" title="上传文档" width="520px" @closed="uploadRef?.clearFiles()">
    <el-upload
      ref="uploadRef"
      drag
      :limit="1"
      :http-request="handleUpload"
      :before-upload="beforeUpload"
      accept=".txt,.md,.pdf,text/plain,text/markdown,application/pdf"
    >
      <el-icon class="upload-icon"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖拽文件到此处，或点击选择</div>
      <template #tip>
        <div class="el-upload__tip">支持 txt、md、pdf，单个文件不超过 10MB</div>
      </template>
    </el-upload>
  </el-dialog>

  <el-dialog
    v-model="chunksVisible"
    :title="currentDocument ? `文档分块 - ${currentDocument.fileName}` : '文档分块'"
    width="760px"
  >
    <div v-loading="chunksLoading" class="chunk-list">
      <div v-for="chunk in chunks" :key="chunkKey(chunk)" class="chunk-item">
        <div class="chunk-index">#{{ chunk.chunkIndex + 1 }}</div>
        <div class="chunk-content">
          <p>{{ displayChunkContent(chunk) }}</p>
          <el-button
            v-if="chunk.content.length > 200"
            link
            type="primary"
            @click="toggleChunk(chunk)"
          >
            {{ isChunkExpanded(chunk) ? '收起' : '展开全文' }}
          </el-button>
        </div>
      </div>
      <el-empty v-if="!chunksLoading && chunks.length === 0" description="暂无分块" />
    </div>
  </el-dialog>
</template>

<style scoped>
.document-page-header {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: flex-start;
  gap: 12px;
}

.document-page-header :deep(.page-header) {
  margin-bottom: var(--section-gap);
}

.panel {
  min-width: 0;
  background: var(--bg-surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  padding: 16px;
}

.status-loading {
  margin-right: 4px;
  animation: status-rotate 1s linear infinite;
}

.upload-icon {
  color: var(--color-primary);
  font-size: 34px;
}

.chunk-list {
  min-height: 180px;
  max-height: 62vh;
  overflow: auto;
}

.chunk-item {
  display: grid;
  grid-template-columns: 56px 1fr;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--border-light);
}

.chunk-item:last-child {
  border-bottom: none;
}

.chunk-index {
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: var(--text-sm);
}

.chunk-content {
  min-width: 0;
}

.chunk-content p {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-primary);
  line-height: var(--leading-relaxed);
}

@keyframes status-rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 720px) {
  .document-page-header {
    grid-template-columns: 1fr;
  }

  .chunk-item {
    grid-template-columns: 1fr;
  }
}
</style>
