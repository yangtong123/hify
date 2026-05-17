import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/providers',
    },
    {
      path: '/providers',
      name: 'providers',
      component: () => import('@/views/provider/ProviderList.vue'),
    },
    {
      path: '/agents',
      name: 'agents',
      component: () => import('@/views/agent/AgentList.vue'),
    },
    {
      path: '/knowledge',
      name: 'knowledge',
      component: () => import('@/views/knowledge/KnowledgeList.vue'),
    },
    {
      path: '/knowledge-bases/:kbId/documents',
      name: 'knowledge-documents',
      component: () => import('@/views/knowledge/KnowledgeDocuments.vue'),
    },
    {
      path: '/workflows',
      name: 'workflows',
      component: () => import('@/views/workflow/WorkflowList.vue'),
    },
    {
      path: '/workflows/create',
      name: 'workflow-create',
      component: () => import('@/views/workflow/WorkflowCreate.vue'),
    },
    {
      path: '/chat',
      name: 'chat',
      component: () => import('@/views/chat/ChatList.vue'),
    },
  ],
})

export default router
