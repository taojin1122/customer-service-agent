import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { hasAnyToken } from '@/utils/token'
import { canAccess, defaultPathFor } from '@/constants/menu'

/**
 * 路由结构对应 AGENTS.md §9.1 的四个入口区。
 *
 * `/tenant/*` 挂在 TenantLayout 下（侧边菜单 + 顶栏），
 * `/console` 不挂布局——坐席工作台自己就是"会话列表 + 对话区"的两栏全屏结构，
 * 再套一层侧边栏会出现双菜单，且对话区被挤压。
 *
 * 未实现的页面用占位组件而不是直接不注册：直接访问不存在的路径会落到 404，
 * 用户无法区分"功能没做"和"地址写错了"。
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
      component: () => import('@/views/console/index.vue'),
      meta: { title: '坐席工作台', menuKey: 'console' }
    },
    {
      path: '/chat',
      redirect: '/console'
    },
    {
      path: '/tenant',
      component: () => import('@/layouts/TenantLayout.vue'),
      redirect: '/tenant/overview',
      children: [
        {
          path: 'overview',
          name: 'TenantOverview',
          component: () => import('@/views/common/PlaceholderView.vue'),
          props: {
            title: '租户概览',
            description: '这里会展示本租户的会话量、知识库文档数与坐席在线情况。',
            actionText: '去坐席工作台',
            actionTo: '/console'
          },
          meta: { title: '概览', menuKey: 'overview' }
        },
        {
          path: 'knowledge',
          name: 'TenantKnowledge',
          component: () => import('@/views/knowledge/index.vue'),
          meta: { title: '知识库', menuKey: 'knowledge' }
        },
        {
          // 调试台放在租户后台：它是管理员验证配置的工具，不是对客入口。
          // 会话渠道是 internal，不计入对客统计。
          path: 'playground',
          name: 'TenantPlayground',
          component: () => import('@/views/playground/index.vue'),
          meta: { title: 'AI 调试台', menuKey: 'playground' }
        },
        {
          path: 'agents',
          name: 'TenantAgents',
          component: () => import('@/views/common/PlaceholderView.vue'),
          props: {
            title: '坐席管理',
            description: '开通坐席账号、分配席位、停用离职人员。功能开发中。',
            actionText: '返回概览',
            actionTo: '/tenant/overview'
          },
          meta: { title: '坐席管理', menuKey: 'agents' }
        },
        {
          path: 'persona',
          name: 'TenantPersona',
          component: () => import('@/views/common/PlaceholderView.vue'),
          props: {
            title: '人设与话术',
            description: '配置机器人自称、公司名、开场白与业务边界。功能开发中。',
            actionText: '返回概览',
            actionTo: '/tenant/overview'
          },
          meta: { title: '人设与话术', menuKey: 'persona' }
        }
      ]
    },
    {
      path: '/knowledge',
      redirect: '/tenant/knowledge'
    },
    {
      // 用户侧客服窗：嵌在客户网站里的匿名入口，不走登录、不挂租户布局。
      // 路由参数用租户编码而不是内部 id：编码可公开，内部 id 泄露出去没有任何好处。
      path: '/widget/:tenantCode',
      name: 'Widget',
      component: () => import('@/views/widget/index.vue'),
      meta: { public: true, title: '在线客服' }
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
    return auth.isPlatform ? '/platform/tenants' : defaultPathFor(auth.user?.role)
  }

  if (auth.isLoggedIn && !isPublic) {
    // 平台账号与租户账号的登录体系完全独立，跨端一律弹回各自的落点。
    // 前端只做体验层拦截，真正的边界在后端（平台账号没有 tenantId，调租户接口会 403）
    if (auth.isPlatform !== toPlatformArea) {
      return auth.isPlatform ? '/platform/tenants' : defaultPathFor(auth.user?.role)
    }

    // 菜单隐藏之外的第二道：直接输 URL 也进不去。
    // 权限判断只认 menuKey + canAccess，不在路由表里另写一份 roles 数组——
    // 两份清单迟早会不一致，而不一致的那一端就是越权入口。
    // 这一步仍然不是安全边界，后端 @RequireRole 才是。
    const menuKey = to.meta.menuKey as string | undefined
    if (menuKey && auth.user && !canAccess(auth.user.role, menuKey)) {
      return defaultPathFor(auth.user.role)
    }

    // 平台端暂未接入菜单常量，仍用路由自带的 roles（只有 PLATFORM_ADMIN 一项）
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
