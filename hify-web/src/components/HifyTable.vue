<script setup lang="ts" generic="T">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElPagination, ElEmpty } from 'element-plus'

export interface HifyColumn {
  label: string
  prop: string
  width?: string | number
  minWidth?: string | number
  slot?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

const props = withDefaults(
  defineProps<{
    columns: HifyColumn[]
    api: (params: { page: number; size: number }) => Promise<PageResult<T>>
    showPagination?: boolean
    pageSize?: number
    pageSizes?: number[]
  }>(),
  {
    showPagination: true,
    pageSize: 10,
    pageSizes: () => [10, 20, 50],
  },
)

const loading = ref(false)
const tableData = ref<T[]>([])
const total = ref(0)
const currentPage = ref(1)
const currentSize = ref(props.pageSize)

async function fetchData() {
  loading.value = true
  try {
    const result = await props.api({ page: currentPage.value, size: currentSize.value })
    tableData.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  currentPage.value = page
  fetchData()
}

function handleSizeChange(size: number) {
  currentSize.value = size
  currentPage.value = 1
  fetchData()
}

defineExpose({ refresh: fetchData })

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div class="hify-table" v-loading="loading">
    <el-table :data="tableData" row-key="id" :border="false" stripe>
      <el-table-column
        v-for="col in columns"
        :key="col.prop"
        :label="col.label"
        :prop="col.prop"
        :width="col.width"
        :min-width="col.minWidth"
      >
        <template v-if="col.slot" #default="scope">
          <slot :name="col.slot" :row="scope.row" :$index="scope.$index" />
        </template>
      </el-table-column>

      <template #empty>
        <el-empty description="暂无数据" />
      </template>
    </el-table>

    <div v-if="showPagination && total > 0" class="hify-table-pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="currentSize"
        :page-sizes="pageSizes"
        :total="total"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<style scoped>
.hify-table {
  background: var(--bg-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: var(--card-padding);
}

.hify-table-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--section-gap);
}
</style>
