import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { hasAnyToken } from '@/utils/token'

/**
 * 路由结构对应 AGENTS.md §9.1 的四个入口区。
 *
 * 当前阶段只实现 `/login` 与 `/console`，`/tenant/*`、`/platform/*` 先给占位页。
 * 占位而不是删除的原因：直接访问不存在的路径会落到空白页，无法区分"没实现"和"出错了"。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      // 默认落点给坐席工作台：租户账号里 AGENT 占多数，管理员登录后也会先看会话
      redirect: '/console'
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: { public: true, title: '登录' }
    },
    {
      path: '/platform/login',
      name: 'PlatformLogin',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: { public: true, title: '平台运营登录' }
    },
    {
      path: '/console',
      name: 'Console',
      // 暂时复用现有聊天页，步骤 B 再物理迁移到 views/console
      component: () => import('@/views/chat/index.vue'),
      meta: { title: '坐席工作台' }
    },
    {
      path: '/chat',
      redirect: '/console'
    },
    {
      path: '/tenant/overview',
      name: 'TenantOverview',
      component: () => import('@/views/common/PlaceholderView.vue'),
      props: {
        title: '租户概览',
        description: '这里会展示本租户的会话量、知识库文档数与坐席在线情况。',
        actionText: '去坐席工作台',
        actionTo: '/console'
      },
      meta: { title: '租户概览', roles: ['ADMIN'] }
    },
    {
      path: '/tenant/knowledge',
      name: 'TenantKnowledge',
      component: () => import('@/views/knowledge/index.vue'),
      meta: { title: '知识库管理', roles: ['ADMIN'] }
    },
    {
      path: '/knowledge',
      redirect: '/tenant/knowledge'
    },
    {
      path: '/platform/tenants',
      name: 'PlatformTenants',
      component: () => import('@/views/common/PlaceholderView.vue'),
      props: {
        title: '租户管理',
        description: '平台运营端可在此开通租户、调整席位配额、查看全局用量。当前为占位页。',
        actionText: '返回坐席工作台',
        actionTo: '/console'
      },
      meta: { title: '租户管理', roles: ['PLATFORM_ADMIN'] }
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: () => import('@/views/common/PlaceholderView.vue'),
      props: {
        title: '页面不存在',
        description: '你访问的地址没有对应页面，可能是链接已失效。',
        actionText: '回到工作台',
        actionTo: '/console'
      },
      meta: { public: true, title: '页面不存在' }
    }
  ]
})

/**
 * 只在首次导航时尝试恢复登录态。
 *
 * 不放在每个守卫里执行：每次路由切换都请求 /auth/me 会让页面切换多一次往返，
 * 而且标签页长时间挂着时会产生无意义的请求。
 */
let restored = false

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (!restored) {
    restored = true
    if (!auth.isLoggedIn && hasAnyToken()) {
      await auth.restore()
    }
  }

  const isPublic = to.meta.public === true
  const toPlatformArea = to.path.startsWith('/platform')

  // 未登录访问受保护页面：带上原始地址，登录后能回到目标页而不是一律回首页
  if (!isPublic && !auth.isLoggedIn) {
    return {
      path: toPlatformArea ? '/platform/login' : '/login',
      query: { redirect: to.fullPath }
    }
  }

  // 已登录又访问登录页：直接送进各自的首页，避免出现"登录了还能看到登录框"
  if (isPublic && auth.isLoggedIn && to.path.includes('login')) {
    return auth.isPlatform ? '/platform/tenants' : '/console'
  }

  if (auth.isLoggedIn && !isPublic) {
    // 平台账号与租户账号的登录体系完全独立，跨端一律弹回各自的落点。
    // 前端只做体验层拦截，真正的边界在后端（平台账号没有 tenantId，调租户接口会 403）
    if (auth.isPlatform !== toPlatformArea) {
      return auth.isPlatform ? '/platform/tenants' : '/console'
    }

    // 菜单隐藏之外的第二道：直接输 URL 也进不去。
    // 这一步仍然不是安全边界——后端 @RequireRole 才是
    const allowedRoles = to.meta.roles as string[] | undefined
    if (allowedRoles && auth.user && !allowedRoles.includes(auth.user.role)) {
      return '/console'
    }
  }

  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · 智能客服 Agent` : '智能客服 Agent'
})

export default router
