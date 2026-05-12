<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Setting, User, ChatDotRound, Fold, Expand } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const isCollapsed = ref(false)

const menuItems = [
  { path: '/providers', icon: Setting, label: '提供商管理' },
  { path: '/agents', icon: User, label: 'Agent 管理' },
  { path: '/chat', icon: ChatDotRound, label: '对话' },
]

/* ---------- breadcrumb ---------- */
const breadcrumbs = computed(() => {
  const items: { label: string; path?: string }[] = [
    { label: '首页', path: '/' },
  ]
  const current = menuItems.find((m) => m.path === route.path)
  if (current) {
    items.push({ label: current.label })
  }
  return items
})

/* ---------- methods ---------- */
function isActive(path: string): boolean {
  return route.path === path
}

function navigateTo(path: string) {
  router.push(path)
}

function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
}
</script>

<template>
  <div class="app-layout">
    <!-- ========== 侧边栏 ========== -->
    <aside class="sidebar" :class="{ collapsed: isCollapsed }">
      <div class="sidebar-brand">
        <div class="brand-logo">
          <span class="logo-text">Hify</span>
          <span class="logo-dot"></span>
        </div>
        <p class="brand-sub">AI Agent Platform</p>
      </div>

      <nav class="sidebar-nav">
        <a
          v-for="item in menuItems"
          :key="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
          @click="navigateTo(item.path)"
        >
          <span class="nav-icon">
            <component :is="item.icon" :size="18" />
          </span>
          <span class="nav-label">{{ item.label }}</span>
        </a>
      </nav>

      <div class="sidebar-footer">
        <button class="footer-btn" @click="toggleCollapse">
          <span class="footer-btn-icon">
            <Expand v-if="isCollapsed" :size="16" />
            <Fold v-else :size="16" />
          </span>
          <span class="footer-btn-text">收起</span>
        </button>
        <p class="footer-version">v0.1.0</p>
      </div>
    </aside>

    <!-- ========== 右侧主体 ========== -->
    <div class="app-body">
      <!-- 顶栏 -->
      <header class="topbar">
        <nav class="topbar-breadcrumb">
          <template v-for="(item, i) in breadcrumbs" :key="i">
            <span v-if="i > 0" class="breadcrumb-sep">/</span>
            <a
              v-if="item.path"
              class="breadcrumb-link"
              @click="navigateTo(item.path)"
            >{{ item.label }}</a>
            <span v-else class="breadcrumb-current">{{ item.label }}</span>
          </template>
        </nav>

        <div class="topbar-user">
          <span class="user-avatar">Y</span>
          <span class="user-name">yangtong</span>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="app-main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style>
html, body, #app {
  height: 100%;
  margin: 0;
}
</style>

<style scoped>
/* ============================================================
   Layout
   ============================================================ */
.app-layout {
  display: flex;
  height: 100%;
}

.app-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* ============================================================
   Sidebar
   ============================================================ */
.sidebar {
  display: flex;
  flex-direction: column;
  width: var(--sidebar-width);
  min-width: var(--sidebar-width);
  height: 100%;
  background: var(--sidebar-bg);
  border-right: 1px solid var(--sidebar-border);
  transition: width var(--transition-normal), min-width var(--transition-normal);
  overflow: hidden;
  user-select: none;
}

.sidebar.collapsed {
  width: var(--sidebar-collapsed-width);
  min-width: var(--sidebar-collapsed-width);
}

/* Brand */
.sidebar-brand {
  padding: 20px 20px 16px;
  border-bottom: 1px solid var(--sidebar-border);
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 6px;
}

.logo-text {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.02em;
  background: linear-gradient(135deg, var(--hify-color-primary-300), var(--hify-color-accent-300));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.logo-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--hify-color-accent-400);
  box-shadow: 0 0 8px rgba(34, 211, 238, 0.5);
  margin-top: -8px;
}

.brand-sub {
  margin-top: 2px;
  font-size: var(--text-xs);
  font-weight: 400;
  color: var(--sidebar-text);
  letter-spacing: 0.04em;
  white-space: nowrap;
}

/* Nav */
.sidebar-nav {
  flex: 1;
  padding: 8px 8px 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 38px;
  padding: 0 12px;
  border-radius: var(--radius-md);
  color: var(--sidebar-text);
  font-size: var(--text-base);
  font-weight: 450;
  text-decoration: none;
  cursor: pointer;
  transition: color var(--transition-fast), background-color var(--transition-fast);
}

.nav-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--sidebar-accent-line);
  transform: scaleY(0);
  transition: transform var(--transition-fast);
}

.nav-item:hover {
  color: var(--sidebar-text-hover);
  background: var(--sidebar-bg-hover);
}

.nav-item.active {
  color: var(--sidebar-text-active);
  background: var(--sidebar-bg-active);
  font-weight: 500;
}

.nav-item.active::before {
  transform: scaleY(1);
}

.nav-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

.nav-label {
  white-space: nowrap;
  overflow: hidden;
}

/* Footer */
.sidebar-footer {
  padding: 4px 12px 4px;
  border-top: 1px solid var(--sidebar-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.footer-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: auto;
  min-width: 40px;
  height: 22px;
  padding: 0 8px;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--sidebar-text);
  font-size: var(--text-xs);
  font-family: inherit;
  cursor: pointer;
  transition: color var(--transition-fast), background-color var(--transition-fast);
}

.footer-btn-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
}

.footer-btn-icon svg {
  display: block;
  width: 16px;
  height: 16px;
}

.footer-btn:hover {
  color: var(--sidebar-text-hover);
  background: var(--sidebar-bg-hover);
}

.footer-btn-text { white-space: nowrap; }

.footer-version {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.18);
  letter-spacing: 0.04em;
  font-family: var(--font-mono);
  white-space: nowrap;
}

/* Sidebar collapsed */
.sidebar.collapsed .sidebar-brand {
  padding: 20px 0 16px;
  display: flex;
  justify-content: center;
}
.sidebar.collapsed .brand-sub,
.sidebar.collapsed .logo-dot { display: none; }

.sidebar.collapsed .sidebar-nav {
  padding: 8px 0 0;
  align-items: center;
}
.sidebar.collapsed .nav-item {
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
}
.sidebar.collapsed .nav-label { display: none; }
.sidebar.collapsed .nav-item::before { left: 2px; }

.sidebar.collapsed .sidebar-footer {
  padding: 4px 0 4px;
  align-items: center;
  justify-content: center;
}
.sidebar.collapsed .footer-btn {
  justify-content: center;
  width: 40px;
  padding: 0;
}
.sidebar.collapsed .footer-btn-text,
.sidebar.collapsed .footer-version { display: none; }

/* ============================================================
   Topbar
   ============================================================ */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  min-height: 48px;
  padding: 0 var(--page-padding);
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-light);
}

/* Breadcrumb */
.topbar-breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-sm);
}

.breadcrumb-link {
  color: var(--text-secondary);
  text-decoration: none;
  cursor: pointer;
  transition: color var(--transition-fast);
}
.breadcrumb-link:hover {
  color: var(--color-primary);
}

.breadcrumb-current {
  color: var(--text-primary);
  font-weight: 500;
}

.breadcrumb-sep {
  color: var(--text-tertiary);
  font-size: var(--text-xs);
}

/* User */
.topbar-user {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: var(--radius-full);
  background: linear-gradient(135deg, var(--hify-color-primary-500), var(--hify-color-primary-700));
  color: #fff;
  font-size: var(--text-xs);
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-name {
  font-size: var(--text-sm);
  color: var(--text-primary);
  font-weight: 450;
}

/* ============================================================
   Main content
   ============================================================ */
.app-main {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  background: var(--color-bg-secondary);
  padding: var(--page-padding);
}
</style>
