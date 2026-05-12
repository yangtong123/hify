<script setup lang="ts">
import { ref } from 'vue'
import { ElTag, ElButton, ElMessage, ElPopover, ElTable, ElTableColumn } from 'element-plus'
import { Edit, Delete, Connection } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import HifyTable from '@/components/HifyTable.vue'
import type { HifyColumn } from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import { useConfirm } from '@/composables/useConfirm'
import type { FormRules } from 'element-plus'
import {
  getProviderList,
  getProviderDetail,
  createProvider,
  updateProvider,
  deleteProvider,
  testConnection,
  type ProviderResponse,
  type ProviderRequest,
  type ModelConfigResponse,
} from '@/api/provider'

/* ============================================================
   Type tag mapping (keys are lowercase backend values)
   ============================================================ */
const typeColorMap: Record<string, { bg: string; text: string }> = {
  openai: { bg: '#10b981', text: '#ffffff' },
  openai_compatible: { bg: '#10b981', text: '#ffffff' },
  anthropic: { bg: '#f59e0b', text: '#ffffff' },
  ollama: { bg: '#6b7280', text: '#ffffff' },
}

const typeDisplayMap: Record<string, string> = {
  openai: 'OpenAI',
  openai_compatible: 'OpenAI Compatible',
  anthropic: 'Anthropic',
  ollama: 'Ollama',
}

/* ============================================================
   Table config
   ============================================================ */
const columns: HifyColumn[] = [
  { label: '名称', prop: 'name', minWidth: 140 },
  { label: '类型', prop: 'type', width: 130, slot: 'type' },
  { label: 'Base URL', prop: 'baseUrl', minWidth: 200 },
  { label: '健康状态', prop: 'healthStatus', width: 140, slot: 'health' },
  { label: '模型数', prop: 'modelCount', width: 90, slot: 'models' },
  { label: '状态', prop: 'enabled', width: 80, slot: 'status' },
  { label: '创建时间', prop: 'createdAt', width: 170 },
  { label: '操作', prop: 'actions', width: 230, slot: 'actions', minWidth: 230 },
]

const tableRef = ref<{ refresh: () => Promise<void> }>()

/* ============================================================
   Dialog & Form
   ============================================================ */
const dialogVisible = ref(false)
const dialogRef = ref<{ open: (data?: Record<string, unknown>) => void }>()
const isEditMode = ref(false)
const editingId = ref<number | null>(null)

interface ProviderForm {
  name: string
  type: string
  baseUrl: string
  apiKey: string
  enabled: number
}

const formRules: FormRules = {
  name: [{ required: true, message: '请输入提供商名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
}

function handleCreate() {
  isEditMode.value = false
  editingId.value = null
  dialogRef.value?.open({ enabled: 1, name: '', type: '', baseUrl: '', apiKey: '' })
}

function handleEdit(row: ProviderResponse) {
  isEditMode.value = true
  editingId.value = row.id
  dialogRef.value?.open({
    name: row.name,
    type: row.type,
    baseUrl: row.baseUrl,
    apiKey: row.authConfig?.apiKey || '',
    enabled: row.enabled,
  })
}

async function handleSubmit(form: Record<string, unknown>) {
  const f = form as unknown as ProviderForm
  const request: ProviderRequest = {
    name: f.name,
    type: f.type,
    baseUrl: f.baseUrl,
    enabled: f.enabled ?? 1,
    authConfig: f.apiKey
      ? { authType: 'bearer', apiKey: f.apiKey }
      : null,
  }

  try {
    if (isEditMode.value && editingId.value != null) {
      await updateProvider(editingId.value, request)
      ElMessage.success('提供商已更新')
    } else {
      await createProvider(request)
      ElMessage.success('提供商已创建')
    }
    tableRef.value?.refresh()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
    throw e
  }
}

/* ============================================================
   Delete
   ============================================================ */
const { confirm } = useConfirm()

async function handleDelete(row: ProviderResponse) {
  await confirm(
    `确认删除提供商「${row.name}」？删除后不可恢复。`,
    () => deleteProvider(row.id),
    '提供商已删除',
  )
  tableRef.value?.refresh()
}

/* ============================================================
   Test connection
   ============================================================ */
const testingIds = ref<Set<number>>(new Set())

async function handleTestConnection(row: ProviderResponse) {
  if (testingIds.value.has(row.id)) return
  testingIds.value = new Set([...testingIds.value, row.id])
  try {
    const result = await testConnection(row.id)
    if (result.success) {
      ElMessage.success(
        `连通性测试通过 — 延迟 ${result.latencyMs}ms，发现 ${result.modelCount} 个模型`,
      )
    } else {
      ElMessage.error(`连通性测试失败：${result.errorMessage}`)
    }
    tableRef.value?.refresh()
  } catch (e: any) {
    ElMessage.error(`连通性测试异常：${e.message}`)
  } finally {
    const next = new Set(testingIds.value)
    next.delete(row.id)
    testingIds.value = next
  }
}

/* ============================================================
   Model expansion (popover)
   ============================================================ */
const modelCache = ref<Map<number, ModelConfigResponse[]>>(new Map())
const loadingModels = ref<Set<number>>(new Set())

async function fetchModels(id: number) {
  if (modelCache.value.has(id) || loadingModels.value.has(id)) return
  loadingModels.value = new Set([...loadingModels.value, id])
  try {
    const detail = await getProviderDetail(id)
    const next = new Map(modelCache.value)
    next.set(id, detail.models)
    modelCache.value = next
  } finally {
    const next = new Set(loadingModels.value)
    next.delete(id)
    loadingModels.value = next
  }
}

/* ============================================================
   Health tag helpers
   ============================================================ */
function healthTagType(status: string) {
  const s = status?.toUpperCase()
  if (s === 'UP') return 'success'
  if (s === 'DOWN') return 'danger'
  if (s === 'DEGRADED') return 'warning'
  return 'info'
}

function healthLabel(status: string) {
  const s = status?.toUpperCase()
  if (s === 'UP') return '正常'
  if (s === 'DOWN') return '异常'
  if (s === 'DEGRADED') return '降级'
  return '未知'
}
</script>

<template>
  <PageHeader
    title="模型提供商管理"
    description="管理 OpenAI、Claude、Ollama 等 LLM 提供商的 API 配置"
  >
    <template #actions>
      <button class="btn btn-primary" @click="handleCreate">新增提供商</button>
    </template>
  </PageHeader>

  <HifyTable
    ref="tableRef"
    :columns="columns"
    :api="getProviderList"
    :page-size="10"
  >
    <!-- 类型 -->
    <template #type="{ row }">
      <el-tag
        :style="{
          backgroundColor: typeColorMap[row.type]?.bg || '#6b7280',
          color: typeColorMap[row.type]?.text || '#ffffff',
          borderColor: typeColorMap[row.type]?.bg || '#6b7280',
        }"
        effect="plain"
        size="small"
        disable-transitions
      >
        {{ typeDisplayMap[row.type] || row.type }}
      </el-tag>
    </template>

    <!-- 健康状态 -->
    <template #health="{ row }">
      <div style="display: flex; align-items: center; gap: 6px">
        <el-tag :type="healthTagType(row.healthStatus)" size="small" effect="plain">
          {{ healthLabel(row.healthStatus) }}
        </el-tag>
        <span v-if="row.healthLatencyMs != null" style="font-size: 12px; color: #909399">
          {{ row.healthLatencyMs }}ms
        </span>
      </div>
    </template>

    <!-- 模型数 -->
    <template #models="{ row }">
      <ElPopover
        trigger="click"
        placement="right"
        :width="280"
        @show="fetchModels(row.id)"
      >
        <template #reference>
          <el-button
            link
            type="primary"
            size="small"
            :loading="loadingModels.has(row.id)"
          >
            {{ row.modelCount ?? 0 }}
          </el-button>
        </template>

        <div v-if="loadingModels.has(row.id)" style="text-align: center; padding: 12px">
          加载中...
        </div>
        <div v-else>
          <el-table
            v-if="(modelCache.get(row.id) || []).length > 0"
            :data="modelCache.get(row.id) || []"
            size="small"
            max-height="300"
          >
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="modelId" label="模型 ID" />
            <el-table-column prop="contextSize" label="上下文" width="90">
              <template #default="{ row: m }">
                {{ m.contextSize ? (m.contextSize >= 1000 ? `${(m.contextSize / 1000).toFixed(0)}K` : m.contextSize) : '-' }}
              </template>
            </el-table-column>
          </el-table>
          <div v-else style="text-align: center; padding: 12px; color: #909399">
            暂无已启用模型
          </div>
        </div>
      </ElPopover>
    </template>

    <!-- 状态 -->
    <template #status="{ row }">
      <el-tag
        :style="{
          backgroundColor: row.enabled ? '#dbeafe' : '#fef3c7',
          color: row.enabled ? '#0369a1' : '#d97706',
          borderColor: row.enabled ? '#dbeafe' : '#fef3c7',
          fontWeight: '500',
        }"
        effect="plain"
        size="small"
        disable-transitions
      >
        {{ row.enabled ? '启用' : '禁用' }}
      </el-tag>
    </template>

    <!-- 操作 -->
    <template #actions="{ row }">
      <el-button type="primary" link size="small" :icon="Edit" @click="handleEdit(row)">
        编辑
      </el-button>
      <el-button type="danger" link size="small" :icon="Delete" @click="handleDelete(row)">
        删除
      </el-button>
      <el-button
        type="warning"
        link
        size="small"
        :icon="Connection"
        :loading="testingIds.has(row.id)"
        @click="handleTestConnection(row)"
      >
        测试
      </el-button>
    </template>
  </HifyTable>

  <HifyFormDialog
    ref="dialogRef"
    v-model="dialogVisible"
    title="提供商"
    :rules="formRules"
    :on-submit="handleSubmit"
  >
    <template #default="{ data }">
      <el-form-item label="名称" prop="name">
        <el-input v-model="data.name" placeholder="如 OpenAI、Claude" maxlength="50" />
      </el-form-item>
      <el-form-item label="类型" prop="type">
        <el-select v-model="data.type" placeholder="请选择提供商类型" style="width: 100%">
          <el-option label="OpenAI" value="openai" />
          <el-option label="Anthropic" value="anthropic" />
          <el-option label="Ollama" value="ollama" />
          <el-option label="OpenAI Compatible" value="openai_compatible" />
        </el-select>
      </el-form-item>
      <el-form-item label="Base URL" prop="baseUrl">
        <el-input v-model="data.baseUrl" placeholder="https://api.openai.com" />
      </el-form-item>
      <el-form-item label="API Key" prop="apiKey">
        <el-input v-model="data.apiKey" type="password" show-password placeholder="请输入 API Key" />
      </el-form-item>
      <el-form-item label="启用" prop="enabled">
        <el-select v-model="data.enabled" style="width: 100%">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </el-form-item>
    </template>
  </HifyFormDialog>
</template>
