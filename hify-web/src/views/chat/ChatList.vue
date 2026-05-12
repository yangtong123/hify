<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
</script>

<template>
  <PageHeader title="对话" description="查看和管理所有 AI 对话历史记录">
    <template #actions>
      <button class="btn btn-secondary">导出记录</button>
      <button class="btn btn-primary">新建对话</button>
    </template>
  </PageHeader>

  <div class="card">
    <p style="color: var(--text-tertiary)">暂无对话记录</p>
  </div>
</template>
