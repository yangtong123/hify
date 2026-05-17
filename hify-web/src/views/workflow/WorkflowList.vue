<script setup lang="ts">
import { ref } from 'vue'
import { ElButton, ElTag } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import HifyTable from '@/components/HifyTable.vue'
import type { HifyColumn } from '@/components/HifyTable.vue'
import { useConfirm } from '@/composables/useConfirm'
import {
  deleteWorkflow,
  getWorkflowList,
  type WorkflowListResponse,
} from '@/api/workflow'

const router = useRouter()
const tableRef = ref<{ refresh: () => Promise<void> }>()
const { confirm } = useConfirm()

const columns: HifyColumn[] = [
  { label: '名称', prop: 'name', minWidth: 180 },
  { label: '状态', prop: 'status', width: 130, slot: 'status' },
  { label: '创建时间', prop: 'createdAt', width: 180 },
  { label: '操作', prop: 'actions', width: 110, slot: 'actions' },
]

function statusType(status: string) {
  const value = status?.toUpperCase()
  if (value === 'PUBLISHED') return 'success'
  if (value === 'DRAFT') return 'info'
  return 'warning'
}

async function handleDelete(row: WorkflowListResponse) {
  await confirm(
    `确认删除工作流「${row.name}」？删除后不可恢复。`,
    () => deleteWorkflow(row.id),
    '工作流已删除',
  )
  tableRef.value?.refresh()
}

function goCreate() {
  router.push('/workflows/create')
}
</script>

<template>
  <PageHeader title="工作流管理" description="管理客服分流、知识检索和自动回复流程">
    <template #actions>
      <button class="btn btn-primary" @click="goCreate">
        <Plus :size="16" />
        新建工作流
      </button>
    </template>
  </PageHeader>

  <HifyTable
    ref="tableRef"
    :columns="columns"
    :api="getWorkflowList"
    :page-size="10"
  >
    <template #status="{ row }">
      <el-tag :type="statusType(row.status)" size="small" effect="plain">
        {{ row.status }}
      </el-tag>
    </template>

    <template #actions="{ row }">
      <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">
        删除
      </el-button>
    </template>
  </HifyTable>
</template>
