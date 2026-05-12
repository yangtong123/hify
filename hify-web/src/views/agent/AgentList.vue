<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
</script>

<template>
  <PageHeader title="Agent 管理" description="配置和发布 AI Agent，绑定模型、知识库与工具">
    <template #actions>
      <button class="btn btn-primary">创建 Agent</button>
    </template>
  </PageHeader>

  <div class="card">
    <p style="color: var(--text-tertiary)">暂无 Agent，点击上方按钮创建第一个</p>
  </div>
</template>
