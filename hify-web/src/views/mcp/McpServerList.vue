<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormRules } from 'element-plus'
import { Connection, Delete, Edit, View } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import HifyTable from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import type { HifyColumn } from '@/components/HifyTable.vue'
import { useConfirm } from '@/composables/useConfirm'
import {
  createMcpServer,
  debugMcpTool,
  deleteMcpServer,
  getMcpServerDetail,
  getMcpServerList,
  testMcpServer,
  updateMcpServer,
  type McpConnectionTestResult,
  type McpServerDetailResponse,
  type McpServerRequest,
  type McpServerResponse,
  type McpToolDebugResponse,
  type McpToolResponse,
} from '@/api/mcp'

interface McpServerForm {
  name: string
  endpoint: string
  enabled: number
}

const columns: HifyColumn[] = [
  { label: '名称', prop: 'name', minWidth: 150 },
  { label: 'Endpoint', prop: 'endpoint', minWidth: 260, slot: 'endpoint' },
  { label: '工具数', prop: 'toolCount', width: 90, slot: 'tools' },
  { label: '状态', prop: 'enabled', width: 80, slot: 'status' },
  { label: '创建时间', prop: 'createdAt', width: 170 },
  { label: '操作', prop: 'actions', width: 260, slot: 'actions', minWidth: 260 },
]

const tableRef = ref<{ refresh: () => Promise<void> }>()
const dialogVisible = ref(false)
const dialogRef = ref<{ open: (data?: Record<string, unknown>, editMode?: boolean) => void }>()
const isEditMode = ref(false)
const editingId = ref<number | null>(null)

const detailVisible = ref(false)
const detailLoading = ref(false)
const currentDetail = ref<McpServerDetailResponse | null>(null)
const detailActiveTab = ref('info')

const selectedToolName = ref('')
const debugForm = ref<Record<string, string | number | null>>({})
const debugLoading = ref(false)
const debugResult = ref('')
const debugElapsedMs = ref<number | null>(null)
const debugHistory = ref<Array<{
  id: number
  toolName: string
  arguments: Record<string, unknown>
  result: string
  elapsedMs: number
  calledAt: string
}>>([])

const testingIds = ref<Set<number>>(new Set())
const { confirm } = useConfirm()

const formRules: FormRules = {
  name: [{ required: true, message: '请输入 MCP Server 名称', trigger: 'blur' }],
  endpoint: [{ required: true, message: '请输入 Endpoint', trigger: 'blur' }],
}

const detailTools = computed(() => currentDetail.value?.tools || [])
const selectedTool = computed(() => detailTools.value.find((tool) => tool.name === selectedToolName.value) || null)
const debugFields = computed(() => selectedTool.value ? getSchemaFields(selectedTool.value) : [])

function handleCreate() {
  isEditMode.value = false
  editingId.value = null
  dialogRef.value?.open({ name: '', endpoint: '', enabled: 1 }, false)
}

function handleEdit(row: McpServerResponse) {
  isEditMode.value = true
  editingId.value = row.id
  dialogRef.value?.open({
    name: row.name,
    endpoint: row.endpoint,
    enabled: row.enabled,
  }, true)
}

async function handleSubmit(form: Record<string, unknown>) {
  const f = form as unknown as McpServerForm
  const request: McpServerRequest = {
    name: f.name,
    endpoint: f.endpoint,
    enabled: f.enabled ?? 1,
  }

  try {
    if (isEditMode.value && editingId.value != null) {
      await updateMcpServer(editingId.value, request)
      ElMessage.success('MCP Server 已更新')
    } else {
      await createMcpServer(request)
      ElMessage.success('MCP Server 已创建')
    }
    tableRef.value?.refresh()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
    throw e
  }
}

async function handleDelete(row: McpServerResponse) {
  await confirm(
    `确认删除 MCP Server「${row.name}」？如果已有 Agent 绑定，后端会拒绝删除。`,
    () => deleteMcpServer(row.id),
    'MCP Server 已删除',
  )
  tableRef.value?.refresh()
}

async function handleTest(row: McpServerResponse) {
  if (testingIds.value.has(row.id)) return
  testingIds.value = new Set([...testingIds.value, row.id])
  let testPassed = false
  try {
    const result = await testMcpServer(row.id)
    showTestResult(result)
    testPassed = result.success
    if (result.success) {
      row.toolCount = result.toolCount ?? 0
      if (currentDetail.value?.id === row.id) {
        currentDetail.value.toolCount = result.toolCount ?? 0
      }
    }
  } catch (e: any) {
    ElMessage.error(`连通性测试异常：${e.message}`)
  } finally {
    const next = new Set(testingIds.value)
    next.delete(row.id)
    testingIds.value = next
  }
  if (testPassed) {
    refreshAfterTest(row.id)
  }
}

function showTestResult(result: McpConnectionTestResult) {
  if (result.success) {
    ElMessage.success(`连通性测试通过，延迟 ${result.latencyMs}ms，发现 ${result.toolCount} 个工具`)
  } else {
    ElMessage.error(`连通性测试失败：${result.message}`)
  }
}

async function openDetail(row: McpServerResponse) {
  detailVisible.value = true
  detailActiveTab.value = 'info'
  await loadDetail(row.id)
}

async function loadDetail(id: number) {
  detailLoading.value = true
  try {
    currentDetail.value = await getMcpServerDetail(id)
    debugHistory.value = []
    const firstTool = currentDetail.value.tools?.[0]
    if (firstTool) {
      selectDebugTool(firstTool)
    } else {
      selectedToolName.value = ''
      debugForm.value = {}
      debugResult.value = ''
      debugElapsedMs.value = null
    }
  } catch (e: any) {
    ElMessage.error(e.message || '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function refreshAfterTest(id: number) {
  try {
    await tableRef.value?.refresh()
    if (currentDetail.value?.id === id) {
      await loadDetail(id)
    }
  } catch (e: any) {
    ElMessage.warning(e.message ? `工具已同步，但刷新显示失败：${e.message}` : '工具已同步，但刷新显示失败')
  }
}

function formatSchema(tool: McpToolResponse) {
  return JSON.stringify(tool.inputSchema || {}, null, 2)
}

function getSchemaFields(tool: McpToolResponse) {
  const schema = tool.inputSchema || {}
  const properties = isRecord(schema.properties) ? schema.properties : {}
  const required = Array.isArray(schema.required) ? schema.required.map(String) : []
  return Object.entries(properties).map(([name, config]) => {
    const field = isRecord(config) ? config : {}
    return {
      name,
      type: normalizeSchemaType(field.type),
      description: typeof field.description === 'string' ? field.description : '',
      required: required.includes(name),
    }
  })
}

function normalizeSchemaType(type: unknown) {
  const value = Array.isArray(type) ? type[0] : type
  return value === 'number' || value === 'integer' ? 'number' : 'string'
}

function isRecord(value: unknown): value is Record<string, any> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function selectDebugTool(tool: McpToolResponse) {
  selectedToolName.value = tool.name
  debugResult.value = ''
  debugElapsedMs.value = null
  const nextForm: Record<string, string | number | null> = {}
  getSchemaFields(tool).forEach((field) => {
    nextForm[field.name] = field.type === 'number' ? null : ''
  })
  debugForm.value = nextForm
}

async function invokeDebugTool() {
  if (!currentDetail.value || !selectedTool.value || debugLoading.value) return

  const argumentsPayload: Record<string, unknown> = {}
  for (const field of debugFields.value) {
    const value = debugForm.value[field.name]
    if (field.required && (value === null || value === undefined || value === '')) {
      ElMessage.warning(`请填写必填参数：${field.name}`)
      return
    }
    if (value !== null && value !== undefined && value !== '') {
      argumentsPayload[field.name] = field.type === 'number' ? Number(value) : value
    }
  }

  debugLoading.value = true
  try {
    const response: McpToolDebugResponse = await debugMcpTool(currentDetail.value.id, {
      toolName: selectedTool.value.name,
      arguments: argumentsPayload,
    })
    debugResult.value = response.result || ''
    debugElapsedMs.value = response.elapsedMs
    debugHistory.value = [
      {
        id: Date.now(),
        toolName: selectedTool.value.name,
        arguments: argumentsPayload,
        result: response.result || '',
        elapsedMs: response.elapsedMs,
        calledAt: new Date().toLocaleTimeString(),
      },
      ...debugHistory.value,
    ].slice(0, 5)
  } catch (e: any) {
    ElMessage.error(e.message || '工具调用失败')
  } finally {
    debugLoading.value = false
  }
}

function formatArguments(args: Record<string, unknown>) {
  return JSON.stringify(args, null, 2)
}
</script>

<template>
  <PageHeader
    title="MCP Server 管理"
    description="管理 Agent 可调用的 MCP 工具服务和工具发现结果"
  >
    <template #actions>
      <button class="btn btn-primary" @click="handleCreate">新增 MCP Server</button>
    </template>
  </PageHeader>

  <HifyTable
    ref="tableRef"
    :columns="columns"
    :api="getMcpServerList"
    :page-size="10"
  >
    <template #endpoint="{ row }">
      <span class="endpoint-text">{{ row.endpoint || '-' }}</span>
    </template>

    <template #tools="{ row }">
      <el-button link type="primary" size="small" @click="openDetail(row)">
        {{ row.toolCount ?? 0 }}
      </el-button>
    </template>

    <template #status="{ row }">
      <el-tag
        :style="{
          backgroundColor: row.enabled ? '#dcfce7' : '#fef3c7',
          color: row.enabled ? '#15803d' : '#d97706',
          borderColor: row.enabled ? '#dcfce7' : '#fef3c7',
          fontWeight: '500',
        }"
        effect="plain"
        size="small"
        disable-transitions
      >
        {{ row.enabled ? '启用' : '禁用' }}
      </el-tag>
    </template>

    <template #actions="{ row }">
      <el-button type="primary" link size="small" :icon="View" @click="openDetail(row)">
        详情
      </el-button>
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
        @click="handleTest(row)"
      >
        测试
      </el-button>
    </template>
  </HifyTable>

  <HifyFormDialog
    ref="dialogRef"
    v-model="dialogVisible"
    title="MCP Server"
    :rules="formRules"
    :on-submit="handleSubmit"
  >
    <template #default="{ data }">
      <el-form-item label="名称" prop="name">
        <el-input v-model="data.name" placeholder="如 Internal CRM MCP" maxlength="100" />
      </el-form-item>
      <el-form-item label="Endpoint" prop="endpoint">
        <el-input v-model="data.endpoint" placeholder="http://localhost:3001/mcp" maxlength="500" />
      </el-form-item>
      <el-form-item label="启用" prop="enabled">
        <el-select v-model="data.enabled" style="width: 100%">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </el-form-item>
    </template>
  </HifyFormDialog>

  <el-drawer
    v-model="detailVisible"
    title="MCP Server 详情"
    size="920px"
    destroy-on-close
  >
    <div v-loading="detailLoading" class="detail-body">
      <template v-if="currentDetail">
        <el-tabs v-model="detailActiveTab" class="detail-tabs">
          <el-tab-pane label="基础信息" name="info">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="名称">{{ currentDetail.name }}</el-descriptions-item>
              <el-descriptions-item label="Endpoint">
                <span class="endpoint-text">{{ currentDetail.endpoint }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="currentDetail.enabled ? 'success' : 'warning'" size="small">
                  {{ currentDetail.enabled ? '启用' : '禁用' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="工具数">{{ currentDetail.toolCount ?? 0 }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <el-tab-pane label="工具列表" name="tools">
            <div class="tools-header">
              <h2>工具列表</h2>
              <el-button
                type="warning"
                size="small"
                :icon="Connection"
                :loading="testingIds.has(currentDetail.id)"
                @click="handleTest(currentDetail)"
              >
                重新测试
              </el-button>
            </div>

            <el-table
              v-if="detailTools.length > 0"
              :data="detailTools"
              row-key="id"
              size="small"
              border
            >
              <el-table-column prop="name" label="名称" min-width="160" />
              <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.description || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="Schema" width="100">
                <template #default="{ row }">
                  <el-popover placement="left" trigger="click" :width="420">
                    <template #reference>
                      <el-button link type="primary" size="small">查看</el-button>
                    </template>
                    <pre class="schema-code">{{ formatSchema(row) }}</pre>
                  </el-popover>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无工具，请先测试连通性" />
          </el-tab-pane>

          <el-tab-pane label="调试" name="debug">
            <div v-if="detailTools.length > 0" class="debug-layout">
              <aside class="debug-tool-list">
                <button
                  v-for="tool in detailTools"
                  :key="tool.id"
                  class="debug-tool-item"
                  :class="{ active: selectedToolName === tool.name }"
                  type="button"
                  @click="selectDebugTool(tool)"
                >
                  <span class="debug-tool-name">{{ tool.name }}</span>
                  <span class="debug-tool-desc">{{ tool.description || '暂无描述' }}</span>
                </button>
              </aside>

              <section class="debug-panel">
                <template v-if="selectedTool">
                  <div class="tool-description">
                    {{ selectedTool.description || '暂无描述' }}
                  </div>

                  <el-form label-position="top" class="debug-form">
                    <el-form-item v-for="field in debugFields" :key="field.name">
                      <template #label>
                        <span class="field-label">
                          {{ field.name }}
                          <span v-if="field.required" class="required-star">*</span>
                        </span>
                        <span v-if="field.description" class="field-description">{{ field.description }}</span>
                      </template>
                      <el-input
                        v-if="field.type === 'number'"
                        v-model.number="debugForm[field.name]"
                        type="number"
                        :placeholder="field.required ? '必填' : '可选'"
                      />
                      <el-input
                        v-else
                        v-model="debugForm[field.name]"
                        :placeholder="field.required ? '必填' : '可选'"
                      />
                    </el-form-item>
                  </el-form>

                  <div class="debug-actions">
                    <el-button
                      type="primary"
                      :loading="debugLoading"
                      :disabled="debugLoading"
                      @click="invokeDebugTool"
                    >
                      调用
                    </el-button>
                    <span v-if="debugElapsedMs !== null" class="elapsed-text">耗时 {{ debugElapsedMs }}ms</span>
                  </div>

                  <div class="debug-result">
                    <div class="debug-section-title">结果</div>
                    <pre class="result-code">{{ debugResult || '暂无调用结果' }}</pre>
                  </div>

                  <div class="debug-history">
                    <div class="debug-section-title">最近调用</div>
                    <el-empty v-if="debugHistory.length === 0" description="暂无调用记录" :image-size="72" />
                    <div v-for="record in debugHistory" :key="record.id" class="history-item">
                      <div class="history-meta">
                        <span>{{ record.calledAt }}</span>
                        <span>{{ record.toolName }}</span>
                        <span>{{ record.elapsedMs }}ms</span>
                      </div>
                      <pre class="history-code">参数：{{ formatArguments(record.arguments) }}
结果：{{ record.result }}</pre>
                    </div>
                  </div>
                </template>
              </section>
            </div>
            <el-empty v-else description="暂无工具，请先测试连通性" />
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
.endpoint-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  color: var(--text-secondary);
}

.detail-body {
  min-height: 320px;
}

.detail-tabs {
  min-height: 320px;
}

.tools-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 20px 0 12px;
}

.tools-header h2 {
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--text-primary);
}

.schema-code {
  max-height: 360px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-primary);
}

.debug-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 16px;
  min-height: 520px;
}

.debug-tool-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: auto;
  padding-right: 4px;
}

.debug-tool-item {
  width: 100%;
  padding: 10px 12px;
  text-align: left;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
}

.debug-tool-item.active {
  border-color: var(--primary-color);
  background: #eff6ff;
}

.debug-tool-name {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  color: var(--text-primary);
}

.debug-tool-desc {
  display: block;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--text-secondary);
}

.debug-panel {
  min-width: 0;
}

.tool-description {
  padding: 10px 12px;
  margin-bottom: 14px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  line-height: 1.6;
  color: var(--text-primary);
  background: #f8fafc;
}

.debug-form {
  max-width: 560px;
}

.field-label {
  font-weight: 600;
  color: var(--text-primary);
}

.required-star {
  color: #dc2626;
}

.field-description {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--text-secondary);
}

.debug-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 8px 0 16px;
}

.elapsed-text {
  font-size: 13px;
  color: var(--text-secondary);
}

.debug-result,
.debug-history {
  margin-top: 16px;
}

.debug-section-title {
  margin-bottom: 8px;
  font-weight: 600;
  color: var(--text-primary);
}

.result-code,
.history-code {
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-primary);
}

.result-code {
  min-height: 120px;
  max-height: 260px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: #0f172a;
  color: #e5e7eb;
}

.history-item {
  padding: 10px 0;
  border-top: 1px solid var(--border-color);
}

.history-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}

.history-code {
  max-height: 160px;
}

@media (max-width: 768px) {
  .debug-layout {
    grid-template-columns: 1fr;
  }
}
</style>
