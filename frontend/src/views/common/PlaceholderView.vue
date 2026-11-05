<template>
  <div class="placeholder">
    <div class="placeholder-inner">
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
      <router-link v-if="actionText" class="action" :to="actionTo">{{ actionText }}</router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 未实现页面的占位。
 *
 * 按设计系统 §6.3 的空状态规范：必须有一句说明"这里会有什么"和一个可执行动作，
 * 不允许只有"暂无数据"——用户看到空白页面时无法判断是加载失败还是功能没做。
 */
withDefaults(
  defineProps<{
    title: string
    description: string
    actionText?: string
    actionTo?: string
  }>(),
  {
    // 给 actionTo 默认值而不是留 undefined：router-link 的 to 属性不接受 undefined，
    // 类型层面挡住比运行时才报错更早发现问题
    actionText: '',
    actionTo: '/console'
  }
)
</script>

<style scoped>
.placeholder {
  display: grid;
  place-items: center;
  height: 100%;
  padding: var(--space-6);
}

.placeholder-inner {
  max-width: 400px;
  padding: var(--space-6);
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  text-align: center;
}

.placeholder-inner h2 {
  margin-bottom: var(--space-2);
  font-size: var(--text-lg);
}

.placeholder-inner p {
  margin-bottom: var(--space-4);
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
}

.action {
  display: inline-flex;
  align-items: center;
  height: 34px;
  padding: 0 var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: var(--color-text-inverse);
  font-size: var(--text-sm);
  font-weight: 500;
}

.action:hover {
  background: var(--color-primary-hover);
}
</style>
