<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = ref(route.path)
</script>

<template>
  <el-container class="app-layout">
    <el-aside width="220px" class="app-aside">
      <div class="logo">Hify</div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <el-menu-item index="/models">
          <span>模型管理</span>
        </el-menu-item>
        <el-menu-item index="/agents">
          <span>Agent 管理</span>
        </el-menu-item>
        <el-menu-item index="/conversations">
          <span>对话</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style>
html, body, #app {
  height: 100%;
  margin: 0;
}
</style>

<style scoped>
.app-layout {
  height: 100%;
}
.app-aside {
  background-color: #304156;
  overflow-y: auto;
}
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-size: 20px;
  font-weight: 700;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}
.app-main {
  background-color: var(--el-bg-color-page);
  padding: 20px;
}
</style>
