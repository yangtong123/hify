<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElButton, ElMessage, ElTag } from 'element-plus'
import { Delete, Edit, Tools } from '@element-plus/icons-vue'
import type { FormRules } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import HifyTable from '@/components/HifyTable.vue'
import type { HifyColumn } from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import { useConfirm } from '@/composables/useConfirm'
import {
  createAgent,
  deleteAgent,
  getAgentDetail,
  getAgentList,
  updateAgent,
  type AgentListResponse,
  type AgentRequest,
} from '@/api/agent'
import {
  getAvailableModels,
  type ModelConfigDto,
} from '@/api/provider'
import { getMcpServers, type McpServerResponse } from '@/api/mcp'

interface ModelOption {
  id: number
  label: string
  providerName: string
  modelName: string
  contextSize: number
}

interface AgentForm {
  name: string
  description: string
  systemPrompt: string
  modelConfigId: number | null
  temperature: number
  maxTokens: number
  topP: number
  maxContextTurns: number
  openingMessage: string
  suggestedQuestionsText: string
  maxIterations: number
  enabled: number
  mcpServerIds: number[]
}

const columns: HifyColumn[] = [
  { label: '名称', prop: 'name', minWidth: 150 },
  { label: '模型', prop: 'modelName', minWidth: 180, slot: 'model' },
  { label: '参数', prop: 'temperature', width: 180, slot: 'params' },
  { label: '工具', prop: 'mcpServerCount', width: 90, slot: 'tools' },
  { label: '状态', prop: 'enabled', width: 80, slot: 'status' },
  { label: '创建时间', prop: 'createdAt', width: 170 },
  { label: '操作', prop: 'actions', width: 170, slot: 'actions', minWidth: 170 },
]

const tableRef = ref<{ refresh: () => Promise<void> }>()
const dialogVisible = ref(false)
const dialogRef = ref<{ open: (data?: Record<string, unknown>, editMode?: boolean) => void }>()
const isEditMode = ref(false)
const editingId = ref<number | null>(null)
const modelOptions = ref<ModelOption[]>([])
const mcpServers = ref<McpServerResponse[]>([])
const loadingOptions = ref(false)

const formRules: FormRules = {
  name: [{ required: true, message: '请输入 Agent 名称', trigger: 'blur' }],
  modelConfigId: [{ required: true, message: '请选择模型', trigger: 'change' }],
  temperature: [{ required: true, message: '请输入 temperature', trigger: 'blur' }],
  maxTokens: [{ required: true, message: '请输入 maxTokens', trigger: 'blur' }],
  topP: [{ required: true, message: '请输入 topP', trigger: 'blur' }],
  maxContextTurns: [{ required: true, message: '请输入上下文轮数', trigger: 'blur' }],
}

const mcpOptions = computed(() =>
  mcpServers.value.map((server) => ({
    label: `${server.name} · ${server.serverType}`,
    value: server.id,
  })),
)

function defaultForm(): AgentForm {
  return {
    name: '',
    description: '',
    systemPrompt: '',
    modelConfigId: modelOptions.value[0]?.id ?? null,
    temperature: 0.7,
    maxTokens: 4096,
    topP: 1,
    maxContextTurns: 10,
    openingMessage: '',
    suggestedQuestionsText: '',
    maxIterations: 20,
    enabled: 1,
    mcpServerIds: [],
  }
}

async function loadOptions() {
  loadingOptions.value = true
  try {
    const [models, servers] = await Promise.all([
      getAvailableModels(),
      getMcpServers({ enabled: 1 }),
    ])

    modelOptions.value = models.map(toModelOption)
    mcpServers.value = servers
  } finally {
    loadingOptions.value = false
  }
}

function toModelOption(model: ModelConfigDto): ModelOption {
  const context = model.contextSize
    ? model.contextSize >= 1000
      ? `${Math.round(model.contextSize / 1000)}K`
      : `${model.contextSize}`
    : '-'
  return {
    id: model.id,
    label: `${model.providerName} / ${model.name} · ${context}`,
    providerName: model.providerName,
    modelName: model.name,
    contextSize: model.contextSize ?? 0,
  }
}

async function handleCreate() {
  isEditMode.value = false
  editingId.value = null
  await loadOptions()
  dialogRef.value?.open(defaultForm() as unknown as Record<string, unknown>, false)
}

async function handleEdit(row: AgentListResponse) {
  isEditMode.value = true
  editingId.value = row.id
  await loadOptions()
  const detail = await getAgentDetail(row.id)
  dialogRef.value?.open({
    name: detail.name,
    description: detail.description,
    systemPrompt: detail.systemPrompt,
    modelConfigId: detail.modelConfigId,
    temperature: detail.temperature,
    maxTokens: detail.maxTokens,
    topP: detail.topP,
    maxContextTurns: detail.maxContextTurns,
    openingMessage: detail.configJson?.openingMessage || '',
    suggestedQuestionsText: (detail.configJson?.suggestedQuestions || []).join('\n'),
    maxIterations: detail.configJson?.maxIterations || 20,
    enabled: detail.enabled,
    mcpServerIds: (detail.mcpServers || []).map((server) => server.id),
  }, true)
}

async function handleSubmit(form: Record<string, unknown>) {
  const f = form as unknown as AgentForm
  if (f.modelConfigId == null) {
    throw new Error('请选择模型')
  }
  const request: AgentRequest = {
    name: f.name,
    description: f.description || '',
    systemPrompt: f.systemPrompt || '',
    modelConfigId: f.modelConfigId,
    temperature: Number(f.temperature),
    maxTokens: Number(f.maxTokens),
    topP: Number(f.topP),
    maxContextTurns: Number(f.maxContextTurns),
    enabled: f.enabled ?? 1,
    mcpServerIds: f.mcpServerIds || [],
    configJson: {
      openingMessage: f.openingMessage || undefined,
      suggestedQuestions: splitLines(f.suggestedQuestionsText),
      maxIterations: Number(f.maxIterations || 20),
    },
  }

  try {
    if (isEditMode.value && editingId.value != null) {
      await updateAgent(editingId.value, request)
      ElMessage.success('Agent 已更新')
    } else {
      await createAgent(request)
      ElMessage.success('Agent 已创建')
    }
    tableRef.value?.refresh()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
    throw e
  }
}

function splitLines(text: string) {
  return (text || '')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
}

const { confirm } = useConfirm()

async function handleDelete(row: AgentListResponse) {
  await confirm(`确认删除 Agent「${row.name}」？`, () => deleteAgent(row.id), 'Agent 已删除')
  tableRef.value?.refresh()
}

function modelLabel(row: AgentListResponse) {
  if (row.providerName && row.modelName) {
    return `${row.providerName} / ${row.modelName}`
  }
  return row.modelName || '-'
}

onMounted(() => {
  loadOptions()
})
</script>

<template>
  <PageHeader title="Agent 管理" description="配置模型、提示词、参数与 MCP 工具">
    <template #actions>
      <button class="btn btn-primary" @click="handleCreate">创建 Agent</button>
    </template>
  </PageHeader>

  <HifyTable ref="tableRef" :columns="columns" :api="getAgentList" :page-size="10">
    <template #model="{ row }">
      <div class="model-cell">
        <span>{{ modelLabel(row) }}</span>
        <span class="muted">#{{ row.modelConfigId }}</span>
      </div>
    </template>

    <template #params="{ row }">
      <div class="param-cell">
        <span>T {{ row.temperature }}</span>
        <span>TopP {{ row.topP }}</span>
        <span>{{ row.maxTokens }}</span>
      </div>
    </template>

    <template #tools="{ row }">
      <el-tag size="small" effect="plain" type="info">
        <el-icon><Tools /></el-icon>
        {{ row.mcpServerCount || 0 }}
      </el-tag>
    </template>

    <template #status="{ row }">
      <el-tag :type="row.enabled ? 'success' : 'warning'" size="small" effect="plain">
        {{ row.enabled ? '启用' : '禁用' }}
      </el-tag>
    </template>

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
    title="Agent"
    width="720px"
    :rules="formRules"
    :on-submit="handleSubmit"
  >
    <template #default="{ data }">
      <el-form-item label="名称" prop="name">
        <el-input v-model="data.name" placeholder="如 数据分析助手" maxlength="100" />
      </el-form-item>

      <el-form-item label="描述" prop="description">
        <el-input v-model="data.description" placeholder="用于列表识别的简短描述" maxlength="500" />
      </el-form-item>

      <el-form-item label="模型" prop="modelConfigId">
        <el-select
          v-model="data.modelConfigId"
          :loading="loadingOptions"
          placeholder="请选择模型"
          no-data-text="暂无可用模型，请先在提供商页面测试连接同步模型"
          style="width: 100%"
          filterable
        >
          <el-option
            v-for="model in modelOptions"
            :key="model.id"
            :label="model.label"
            :value="model.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="系统提示词" prop="systemPrompt">
        <el-input
          v-model="data.systemPrompt"
          type="textarea"
          :rows="5"
          placeholder="定义 Agent 的角色、边界和回答风格"
        />
      </el-form-item>

      <div class="form-grid">
        <el-form-item label="Temperature" prop="temperature">
          <el-input-number v-model="data.temperature" :min="0" :max="2" :step="0.1" />
        </el-form-item>
        <el-form-item label="Top P" prop="topP">
          <el-input-number v-model="data.topP" :min="0" :max="1" :step="0.05" />
        </el-form-item>
        <el-form-item label="Max Tokens" prop="maxTokens">
          <el-input-number v-model="data.maxTokens" :min="1" :max="200000" :step="512" />
        </el-form-item>
        <el-form-item label="上下文轮数" prop="maxContextTurns">
          <el-input-number v-model="data.maxContextTurns" :min="1" :max="100" />
        </el-form-item>
      </div>

      <el-form-item label="MCP 工具" prop="mcpServerIds">
        <el-select
          v-model="data.mcpServerIds"
          :loading="loadingOptions"
          placeholder="请选择可用 MCP Server"
          style="width: 100%"
          multiple
          collapse-tags
          filterable
        >
          <el-option
            v-for="server in mcpOptions"
            :key="server.value"
            :label="server.label"
            :value="server.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="开场白" prop="openingMessage">
        <el-input v-model="data.openingMessage" placeholder="会话开始时展示给用户的话" />
      </el-form-item>

      <el-form-item label="建议问题" prop="suggestedQuestionsText">
        <el-input
          v-model="data.suggestedQuestionsText"
          type="textarea"
          :rows="3"
          placeholder="每行一个建议问题"
        />
      </el-form-item>

      <div class="form-grid">
        <el-form-item label="最大迭代" prop="maxIterations">
          <el-input-number v-model="data.maxIterations" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-select v-model="data.enabled" style="width: 100%">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
      </div>
    </template>
  </HifyFormDialog>
</template>

<style scoped>
.model-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.35;
}

.muted {
  color: #909399;
  font-size: 12px;
}

.param-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  color: #606266;
  font-size: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  column-gap: 12px;
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
