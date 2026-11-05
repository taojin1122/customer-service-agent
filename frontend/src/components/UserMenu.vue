<template>
  <div class="user-menu">
    <div class="user-meta">
      <span class="user-name" :title="name">{{ name || '未登录' }}</span>
      <span class="user-role">{{ roleText(role) }}</span>
    </div>
    <button class="logout-btn" type="button" title="退出登录" aria-label="退出登录" @click="emit('logout')">
      <el-icon><SwitchButton /></el-icon>
    </button>
  </div>
</template>

<script setup lang="ts">
import { roleText } from '@/constants/role'

/**
 * 侧边栏底部的"当前用户 + 退出登录"。
 *
 * 刻意做成纯展示组件：只接收 name / role，退出动作通过 emit 交回父组件。
 * 组件里直接读 store 或直接跳路由的话，它就只能挂在"已经初始化好登录态"的页面里，
 * 换个上下文（比如登录后引导页）又得重写一遍——那等于没抽（AGENTS.md §2.4）。
 * 退出逻辑本身在 composables/useLogout.ts，父组件调它即可。
 *
 * 样式按"深色侧边栏内"设计，两种主题下侧栏都是深底，所以用 --color-sidebar-* 令牌。
 */
defineProps<{
  /** 展示用的用户名，已由父组件从登录态取出 */
  name: string
  /** 角色原始值（ADMIN / AGENT / PLATFORM_ADMIN），文案由 roleText 统一映射 */
  role?: string
}>()

const emit = defineEmits<{
  (e: 'logout'): void
}>()
</script>

<style scoped>
.user-menu {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.user-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.35;
}

.user-name {
  overflow: hidden;
  color: var(--color-sidebar-text);
  font-size: var(--text-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  color: var(--color-sidebar-text-secondary);
  font-size: var(--text-xs);
}

.logout-btn {
  display: grid;
  flex: none;
  place-items: center;
  width: 30px;
  height: 30px;
  margin-left: auto;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-sidebar-text-secondary);
  font-size: 15px;
  cursor: pointer;
  transition:
    background var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.logout-btn:hover {
  background: var(--color-sidebar-hover);
  color: var(--color-sidebar-text);
}

/* 键盘用户需要看得见焦点落点，深色底上默认描边几乎不可见 */
.logout-btn:focus-visible {
  outline: 2px solid var(--color-primary-hover);
  outline-offset: 1px;
}
</style>
