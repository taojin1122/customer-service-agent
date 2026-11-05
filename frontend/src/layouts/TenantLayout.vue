<template>
  <div class="tenant-layout">
    <!-- 左侧导航：菜单项完全由 constants/menu.ts 的白名单决定 -->
    <aside class="sidebar">
      <div class="brand">
        <el-icon :size="20"><ChatDotRound /></el-icon>
        <span class="brand-name">智能客服 Agent</span>
      </div>

      <nav class="menu" aria-label="租户端主菜单">
        <template v-for="item in menus" :key="item.key">
          <router-link
            v-if="!item.disabled"
            :to="item.path"
            class="menu-item"
            :class="{ active: isActive(item) }"
          >
            <el-icon :size="18"><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </router-link>

          <!--
            未实现的功能仍然占位显示并置灰，而不是直接从菜单里删掉。
            删掉的后果是用户不知道"有没有这个功能"，会反复来问；
            置灰后加一个"开发中"标签，用户自己就能判断。
          -->
          <div v-else class="menu-item disabled" :title="`${item.label}（功能开发中）`">
            <el-icon :size="18"><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
            <span class="menu-badge">开发中</span>
          </div>
        </template>
      </nav>

      <div class="sidebar-footer">
        <UserMenu :name="auth.displayName" :role="auth.user?.role" @logout="logout" />
      </div>
    </aside>

    <!-- 右侧：顶栏 + 页面内容 -->
    <section class="main">
      <header class="topbar">
        <span class="page-title">{{ pageTitle }}</span>
        <el-tag size="small" type="info" effect="plain">{{ roleText(auth.user?.role) }}</el-tag>
      </header>

      <div class="content">
        <router-view />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import UserMenu from '@/components/UserMenu.vue'
import { useLogout } from '@/composables/useLogout'
import { useAuthStore } from '@/stores/auth'
import { roleText } from '@/constants/role'
import { menusFor, type MenuItem } from '@/constants/menu'

const route = useRoute()
const auth = useAuthStore()
const { logout } = useLogout()

/**
 * 菜单按角色过滤，判断逻辑统一收在 menusFor 里。
 * 这里刻意不写 `if (role === 'ADMIN')`：一旦散落在组件里，
 * 将来新增角色时必然漏改一处，漏掉的那处就是越权入口。
 */
const menus = computed<MenuItem[]>(() => menusFor(auth.user?.role))

/** 顶栏标题取路由 meta，避免每个页面自己再画一遍标题 */
const pageTitle = computed(() => (route.meta.title as string | undefined) ?? '管理后台')

/**
 * 高亮判定用前缀匹配而不是全等：
 * 将来 `/tenant/knowledge/123` 这类详情页也要让"知识库"保持高亮。
 * 但必须带上 `/` 再比较，否则 `/console` 会误匹配 `/console-xxx`。
 */
function isActive(item: MenuItem): boolean {
  return route.path === item.path || route.path.startsWith(item.path + '/')
}
</script>

<style scoped>
.tenant-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--color-bg);
}

.sidebar {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  width: var(--sidebar-width);
  background: var(--color-sidebar);
  /* 不能用 --color-text-inverse：它在深色主题下会反转成深色，压在深色侧栏上看不见 */
  color: var(--color-sidebar-text);
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: var(--header-height);
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--color-sidebar-divider);
}

.brand-name {
  font-size: var(--text-base);
  font-weight: 600;
  letter-spacing: 0.2px;
}

.menu {
  flex: 1;
  padding: var(--space-3) var(--space-2);
  overflow-y: auto;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  height: 40px;
  margin-bottom: var(--space-1);
  padding: 0 var(--space-3);
  border-radius: var(--radius-md);
  color: var(--color-sidebar-text-secondary);
  font-size: var(--text-base);
  transition:
    background var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.menu-item:hover {
  background: var(--color-sidebar-hover);
  color: var(--color-sidebar-text);
}

.menu-item.active {
  background: var(--color-sidebar-active);
  color: var(--color-sidebar-text);
  font-weight: 500;
}

/* 置灰项不可点：不给 hover 反馈、光标改成禁用态，否则用户会以为点了没反应是卡顿 */
.menu-item.disabled {
  color: rgba(255, 255, 255, 0.34);
  cursor: not-allowed;
}

.menu-item.disabled:hover {
  background: transparent;
  color: rgba(255, 255, 255, 0.34);
}

.menu-badge {
  margin-left: auto;
  padding: 0 6px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  line-height: 16px;
}

/* 用户信息块（含退出登录）由 components/UserMenu.vue 提供，
   这里只负责它外层的间距与分隔线 */
.sidebar-footer {
  padding: var(--space-3) var(--space-4);
  border-top: 1px solid var(--color-sidebar-divider);
}

.main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  height: var(--header-height);
  flex-shrink: 0;
  padding: 0 var(--space-6);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
}

.page-title {
  font-size: var(--text-lg);
  font-weight: 600;
  color: var(--color-text);
}

/* 用 flex 列容器而不是 block：占位页靠 height: 100% 居中，
   block 父容器高度是 auto，那个 100% 会失效，占位块会贴在顶部 */
.content {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: var(--space-6);
  overflow-y: auto;
}

/*
  子页面作为 flex 项目默认会被拉伸到容器高度，但知识库这类内容页需要按内容高度排列，
  否则卡片之间会被撑出大片空白。
  作用到子组件根节点不需要 :deep——根节点会继承父组件的 scoped 标记。
*/
.content > * {
  flex: 0 0 auto;
}
</style>
