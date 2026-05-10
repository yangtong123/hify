import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/models',
    },
    {
      path: '/models',
      name: 'models',
      component: () => import('@/views/model/ProviderList.vue'),
    },
    {
      path: '/agents',
      name: 'agents',
      component: () => import('@/views/agent/AgentList.vue'),
    },
    {
      path: '/conversations',
      name: 'conversations',
      component: () => import('@/views/conversation/ConversationList.vue'),
    },
  ],
})

export default router
