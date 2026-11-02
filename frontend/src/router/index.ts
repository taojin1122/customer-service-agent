import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/chat'
    },
    {
      path: '/chat',
      name: 'Chat',
      component: () => import('@/views/chat/index.vue'),
      meta: { title: '智能客服对话' }
    },
    {
      path: '/knowledge',
      name: 'Knowledge',
      component: () => import('@/views/knowledge/index.vue'),
      meta: { title: '知识库管理' }
    }
  ]
})

export default router
