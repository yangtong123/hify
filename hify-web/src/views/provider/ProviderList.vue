<script setup lang="ts">
import { ref } from 'vue'
import { ElTag, ElButton, ElInput, ElSelect, ElOption } from 'element-plus'
import { Edit, Delete } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import HifyTable from '@/components/HifyTable.vue'
import type { HifyColumn, PageResult } from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import { useConfirm } from '@/composables/useConfirm'
import type { FormRules } from 'element-plus'

/* ============================================================
   Types
   ============================================================ */
interface Provider {
  id: string
  name: string
  type: 'OpenAI' | 'Claude' | 'Gemini' | 'Ollama'
  apiKey: string
  baseUrl: string
  enabled: boolean
  createdAt: string
}

interface ProviderForm {
  name: string
  type: string
  apiKey: string
  baseUrl: string
}

/* ============================================================
   Type tag mapping
   ============================================================ */
const typeColorMap: Record<string, { bg: string; text: string }> = {
  OpenAI: { bg: '#10b981', text: '#ffffff' },
  Claude: { bg: '#f59e0b', text: '#ffffff' },
  Gemini: { bg: '#8b5cf6', text: '#ffffff' },
  Ollama: { bg: '#6b7280', text: '#ffffff' },
}

/* ============================================================
   Mock data
   ============================================================ */
const providers = ref<Provider[]>([
  {
    id: '1',
    name: 'OpenAI GPT-4',
    type: 'OpenAI',
    apiKey: 'sk-****',
    baseUrl: 'https://api.openai.com/v1',
    enabled: true,
    createdAt: '2026-04-15 10:30:00',
  },
  {
    id: '2',
    name: 'Claude Sonnet',
    type: 'Claude',
    apiKey: 'sk-ant-****',
    baseUrl: 'https://api.anthropic.com',
    enabled: true,
    createdAt: '2026-04-20 14:22:00',
  },
  {
    id: '3',
    name: 'Gemini Pro',
    type: 'Gemini',
    apiKey: 'AIza****',
    baseUrl: 'https://generativelanguage.googleapis.com',
    enabled: false,
    createdAt: '2026-05-01 09:15:00',
  },
  {
    id: '4',
    name: '本地 Ollama',
    type: 'Ollama',
    apiKey: '',
    baseUrl: 'http://localhost:11434',
    enabled: true,
    createdAt: '2026-05-05 16:48:00',
  },
  {
    id: '5',
    name: 'DeepSeek V3',
    type: 'OpenAI',
    apiKey: 'sk-****',
    baseUrl: 'https://api.deepseek.com/v1',
    enabled: true,
    createdAt: '2026-05-10 11:00:00',
  },
])

/* ============================================================
   Table config
   ============================================================ */
const columns: HifyColumn[] = [
  { label: '名称', prop: 'name', minWidth: 140 },
  { label: '类型', prop: 'type', width: 100, slot: 'type' },
  { label: 'Base URL', prop: 'baseUrl', minWidth: 220 },
  { label: '状态', prop: 'enabled', width: 80, slot: 'status' },
  { label: '创建时间', prop: 'createdAt', width: 170 },
  { label: '操作', prop: 'actions', width: 150, slot: 'actions' },
]

const tableRef = ref<InstanceType<typeof HifyTable>>()

async function fetchProviders(params: { page: number; size: number }): Promise<PageResult<Provider>> {
  const start = (params.page - 1) * params.size
  const records = providers.value.slice(start, start + params.size)
  return {
    records,
    total: providers.value.length,
    page: params.page,
    size: params.size,
  }
}

/* ============================================================
   Dialog
   ============================================================ */
const dialogVisible = ref(false)
const dialogRef = ref<InstanceType<typeof HifyFormDialog>>()

const formRules: FormRules = {
  name: [{ required: true, message: '请输入提供商名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  apiKey: [{ required: true, message: '请输入 API Key', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
}

function handleCreate() {
  dialogRef.value?.open()
}

function handleEdit(row: Provider) {
  dialogRef.value?.open({
    name: row.name,
    type: row.type,
    apiKey: row.apiKey,
    baseUrl: row.baseUrl,
  })
}

function handleSubmit(form: ProviderForm) {
  // Simple edit detection: check if a provider with same name exists
  const existing = providers.value.find((p) => p.name === form.name)
  if (existing) {
    Object.assign(existing, { ...form, apiKey: form.apiKey || existing.apiKey })
  } else {
    providers.value.unshift({
      id: String(Date.now()),
      name: form.name,
      type: form.type as Provider['type'],
      apiKey: form.apiKey,
      baseUrl: form.baseUrl,
      enabled: true,
      createdAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
    })
  }
  dialogVisible.value = false
  tableRef.value?.refresh()
}

/* ============================================================
   Delete
   ============================================================ */
const { confirm } = useConfirm()

async function handleDelete(row: Provider) {
  await confirm(
    `确认删除提供商「${row.name}」？删除后不可恢复。`,
    async () => {
      const idx = providers.value.findIndex((p) => p.id === row.id)
      if (idx > -1) providers.value.splice(idx, 1)
    },
    '提供商已删除',
  )
  tableRef.value?.refresh()
}
</script>

<template>
  <PageHeader title="模型提供商管理" description="管理 OpenAI、Claude、Gemini、Ollama 等 LLM 提供商的 API 配置">
    <template #actions>
      <button class="btn btn-primary" @click="handleCreate">新增提供商</button>
    </template>
  </PageHeader>

  <HifyTable
    ref="tableRef"
    :columns="columns"
    :api="fetchProviders"
    :page-size="10"
  >
    <!-- 类型 -->
    <template #type="{ row }">
      <el-tag
        :style="{
          backgroundColor: typeColorMap[row.type].bg,
          color: typeColorMap[row.type].text,
          borderColor: typeColorMap[row.type].bg,
        }"
        effect="plain"
        size="small"
        disable-transitions
      >
        {{ row.type }}
      </el-tag>
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
    </template>
  </HifyTable>

  <HifyFormDialog
    ref="dialogRef"
    v-model="dialogVisible"
    title="提供商"
    :rules="formRules"
    @submit="handleSubmit"
  >
    <template #default="{ data }">
      <el-form-item label="名称" prop="name">
        <el-input v-model="data.name" placeholder="如 OpenAI、Claude" maxlength="50" />
      </el-form-item>
      <el-form-item label="类型" prop="type">
        <el-select v-model="data.type" placeholder="请选择提供商类型" style="width: 100%">
          <el-option label="OpenAI" value="OpenAI" />
          <el-option label="Claude" value="Claude" />
          <el-option label="Gemini" value="Gemini" />
          <el-option label="Ollama" value="Ollama" />
        </el-select>
      </el-form-item>
      <el-form-item label="API Key" prop="apiKey">
        <el-input v-model="data.apiKey" type="password" show-password placeholder="请输入 API Key" />
      </el-form-item>
      <el-form-item label="Base URL" prop="baseUrl">
        <el-input v-model="data.baseUrl" placeholder="https://api.openai.com/v1" />
      </el-form-item>
    </template>
  </HifyFormDialog>
</template>
