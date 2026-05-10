<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getHealth } from '@/api/health'

const connected = ref<boolean | null>(null)

onMounted(async () => {
  try {
    await getHealth()
    connected.value = true
  } catch {
    connected.value = false
  }
})
</script>

<template>
  <div>
    <p>模型提供商管理</p>
    <p v-if="connected === null" style="color: #909399">正在检测后端连接...</p>
    <p v-else-if="connected" style="color: #67c23a">后端已连接：Hify is running</p>
    <p v-else style="color: #f56c6c">后端未连接</p>
  </div>
</template>
