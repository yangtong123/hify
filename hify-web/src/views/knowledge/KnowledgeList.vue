<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, FolderOpened, UploadFilled } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { getAvailableModels, type ModelConfigDto } from '@/api/provider'
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  deleteKnowledgeDocument,
  getKnowledgeBaseList,
  listKnowledgeDocuments,
  updateKnowledgeBase,
  uploadKnowledgeDocument,
  type KnowledgeBaseRequest,
  type KnowledgeBaseResponse,
  type KnowledgeDocumentResponse,
} from '@/api/knowledge'

const router = useRouter()
const loading = ref(false)
const bases = ref<KnowledgeBaseResponse[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const models = ref<ModelConfigDto[]>([])
const selectedBase = ref<KnowledgeBaseResponse | null>(null)
const documents = ref<KnowledgeDocumentResponse[]>([])
const documentsLoading = ref(false)
const pollTimer = ref<number | null>(null)

const processingStatuses = new Set(['PENDING', 'PARSING', 'CHUNKING', 'EMBEDDING'])

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<KnowledgeBaseRequest>({
  name: '',
  description: '',
  embeddingModelConfigId: 0,
  embeddingDimension: 1024,
  chunkSize: 1000,
  chunkOverlap: 150,
  topK: 5,
  similarityThreshold: 0.7,
  status: 'ACTIVE',
})

const dialogTitle = computed(() => (editingId.value ? '编辑知识库' : '新增知识库'))

async function loadBases() {
  loading.value = true
  try {
    const res = await getKnowledgeBaseList({ page: page.value, size: size.value })
    bases.value = res.records
    total.value = res.total
    if (!selectedBase.value && bases.value.length > 0) {
      await selectBase(bases.value[0])
    }
  } finally {
    loading.value = false
  }
}

async function loadModels() {
  models.value = await getAvailableModels()
  const qwen = models.value.find((item) => item.modelId.includes('qwen3-embedding'))
  if (!form.embeddingModelConfigId && qwen) {
    form.embeddingModelConfigId = qwen.id
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    name: '',
    description: '',
    embeddingModelConfigId: form.embeddingModelConfigId || models.value[0]?.id || 0,
    embeddingDimension: 1024,
    chunkSize: 1000,
    chunkOverlap: 150,
    topK: 5,
    similarityThreshold: 0.7,
    status: 'ACTIVE',
  })
  dialogVisible.value = true
}

function openEdit(row: KnowledgeBaseResponse) {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    description: row.description,
    embeddingModelConfigId: row.embeddingModelConfigId,
    embeddingDimension: row.embeddingDimension,
    chunkSize: row.chunkSize,
    chunkOverlap: row.chunkOverlap,
    topK: row.topK,
    similarityThreshold: row.similarityThreshold,
    status: row.status,
  })
  dialogVisible.value = true
}

function openDocuments(row: KnowledgeBaseResponse) {
  router.push(`/knowledge-bases/${row.id}/documents`)
}

async function submitBase() {
  if (!form.name || !form.embeddingModelConfigId) {
    ElMessage.warning('请填写名称并选择 Embedding 模型')
    return
  }
  if (editingId.value) {
    await updateKnowledgeBase(editingId.value, form)
    ElMessage.success('知识库已更新')
  } else {
    await createKnowledgeBase(form)
    ElMessage.success('知识库已创建')
  }
  dialogVisible.value = false
  await loadBases()
}

async function removeBase(row: KnowledgeBaseResponse) {
  await ElMessageBox.confirm(`确认删除知识库「${row.name}」？`, '删除知识库', { type: 'warning' })
  await deleteKnowledgeBase(row.id)
  ElMessage.success('知识库已删除')
  selectedBase.value = null
  documents.value = []
  await loadBases()
}

async function selectBase(row: KnowledgeBaseResponse) {
  selectedBase.value = row
  await refreshDocuments(false)
}

async function refreshDocuments(silent = true) {
  if (!selectedBase.value) return
  if (!silent) {
    documentsLoading.value = true
  }
  const base = selectedBase.value
  try {
    documents.value = await listKnowledgeDocuments(base.id)
    updatePolling()
  } finally {
    if (!silent) {
      documentsLoading.value = false
    }
  }
}

function upsertDocument(document: KnowledgeDocumentResponse) {
  const index = documents.value.findIndex((item) => item.id === document.id)
  if (index >= 0) {
    documents.value.splice(index, 1, document)
  } else {
    documents.value = [document, ...documents.value]
  }
}

function updatePolling() {
  const hasProcessing = documents.value.some((item) => isProcessing(item.processStatus))
  if (hasProcessing) {
    startPolling()
  } else {
    stopPolling()
  }
}

function startPolling() {
  if (pollTimer.value != null) return
  pollTimer.value = window.setInterval(() => {
    refreshDocuments(true)
  }, 1500)
}

function stopPolling() {
  if (pollTimer.value == null) return
  window.clearInterval(pollTimer.value)
  pollTimer.value = null
}

function isProcessing(status: string) {
  return processingStatuses.has(status)
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    PENDING: '等待处理',
    PARSING: '解析中',
    CHUNKING: '分块中',
    EMBEDDING: '向量化中',
    COMPLETED: '已完成',
    FAILED: '失败',
  }
  return labels[status] || status
}

function statusProgress(status: string) {
  const progress: Record<string, number> = {
    PENDING: 10,
    PARSING: 30,
    CHUNKING: 55,
    EMBEDDING: 80,
    COMPLETED: 100,
    FAILED: 100,
  }
  return progress[status] || 0
}

function progressStatus(status: string) {
  if (status === 'FAILED') return 'exception'
  if (status === 'COMPLETED') return 'success'
  return undefined
}

async function reloadDocumentsForSelectedBase() {
  if (!selectedBase.value) return
  documentsLoading.value = true
  try {
    await refreshDocuments(true)
  } finally {
    documentsLoading.value = false
  }
}

async function handleUpload(options: any) {
  if (!selectedBase.value) return
  try {
    const document = await uploadKnowledgeDocument(selectedBase.value.id, options.file)
    upsertDocument(document)
    updatePolling()
    ElMessage.success('文档已上传，后台正在解析和向量化')
    await loadBases()
  } catch (e: any) {
    ElMessage.error(e.message || '上传失败')
  }
}

async function removeDocument(row: KnowledgeDocumentResponse) {
  await ElMessageBox.confirm(`确认删除文档「${row.fileName}」？`, '删除文档', { type: 'warning' })
  await deleteKnowledgeDocument(row.id)
  ElMessage.success('文档已删除')
  if (selectedBase.value) {
    await reloadDocumentsForSelectedBase()
  }
}

function statusType(status: string) {
  if (status === 'ACTIVE' || status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'DISABLED') return 'info'
  return 'warning'
}

onMounted(async () => {
  await loadModels()
  await loadBases()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <PageHeader title="知识库管理" description="上传公司文档，自动分块、向量化并用于 Agent 对话检索">
    <template #actions>
      <button class="btn btn-primary" @click="openCreate">新增知识库</button>
    </template>
  </PageHeader>

  <div class="knowledge-layout">
    <section class="panel">
      <el-table
        v-loading="loading"
        :data="bases"
        highlight-current-row
        @row-click="selectBase"
      >
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="embeddingModelName" label="Embedding 模型" min-width="180" />
        <el-table-column prop="documentCount" label="文档" width="80" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link :icon="FolderOpened" @click.stop="openDocuments(row)">文档</el-button>
            <el-button link :icon="Edit" @click.stop="openEdit(row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click.stop="removeBase(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        class="pager"
        background
        layout="prev, pager, next"
        :total="total"
        @current-change="loadBases"
      />
    </section>

    <section class="panel document-panel">
      <div class="panel-head">
        <div>
          <h3>{{ selectedBase?.name || '请选择知识库' }}</h3>
          <p v-if="selectedBase">
            chunk {{ selectedBase.chunkSize }} / overlap {{ selectedBase.chunkOverlap }} / topK {{ selectedBase.topK }}
          </p>
        </div>
        <el-upload
          v-if="selectedBase"
          :show-file-list="false"
          :http-request="handleUpload"
          accept=".txt,.md,.markdown,.html,.htm,.csv,.json"
        >
          <el-button type="primary" :icon="UploadFilled">上传文档</el-button>
        </el-upload>
      </div>

      <el-table v-loading="documentsLoading" :data="documents">
        <el-table-column prop="fileName" label="文件名" min-width="180" />
        <el-table-column prop="fileType" label="类型" width="80" />
        <el-table-column prop="chunkCount" label="分块" width="80" />
        <el-table-column prop="processStatus" label="处理状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.processStatus)" size="small">
              {{ statusLabel(row.processStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" min-width="160">
          <template #default="{ row }">
            <el-progress
              :percentage="statusProgress(row.processStatus)"
              :status="progressStatus(row.processStatus)"
              :indeterminate="isProcessing(row.processStatus)"
              :duration="1.2"
            />
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="失败原因" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="danger" :icon="Delete" @click="removeDocument(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
    <el-form label-width="130px">
      <el-form-item label="名称">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item label="Embedding 模型">
        <el-select v-model="form.embeddingModelConfigId" filterable>
          <el-option
            v-for="model in models"
            :key="model.id"
            :label="`${model.providerName} / ${model.name}`"
            :value="model.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="向量维度">
        <el-input-number v-model="form.embeddingDimension" :min="1" :max="8192" />
      </el-form-item>
      <el-form-item label="分块大小">
        <el-input-number v-model="form.chunkSize" :min="100" :max="4000" />
      </el-form-item>
      <el-form-item label="分块重叠">
        <el-input-number v-model="form.chunkOverlap" :min="0" :max="1000" />
      </el-form-item>
      <el-form-item label="召回数量">
        <el-input-number v-model="form.topK" :min="1" :max="20" />
      </el-form-item>
      <el-form-item label="相似度阈值">
        <el-input-number v-model="form.similarityThreshold" :min="0" :max="1" :step="0.05" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="submitBase">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.knowledge-layout {
  display: grid;
  grid-template-columns: minmax(420px, 1fr) minmax(420px, 1.2fr);
  gap: 16px;
}

.panel {
  min-width: 0;
  background: var(--hify-color-surface, #fff);
  border: 1px solid var(--hify-color-border, #e5e7eb);
  border-radius: 8px;
  padding: 16px;
}

.document-panel {
  min-height: 420px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.panel-head h3 {
  margin: 0;
  font-size: 16px;
}

.panel-head p {
  margin: 4px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.pager {
  justify-content: flex-end;
  margin-top: 12px;
}

@media (max-width: 1100px) {
  .knowledge-layout {
    grid-template-columns: 1fr;
  }
}
</style>
