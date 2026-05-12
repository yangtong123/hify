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
      path: '/chat',
      name: 'chat',
      component: () => import('@/views/chat/ChatList.vue'),
    },
  ],
})

export default router
