<template>
  <div class="conversation-item" :class="{ active }" @click="emit('select')">
    <div class="title-row">
      <span class="title" :title="title">{{ title }}</span>
      <span v-if="badge" class="badge" :class="badgeType">{{ badge }}</span>
    </div>
    <div v-if="meta" class="meta">{{ meta }}</div>
    <button
      v-if="deletable"
      class="del"
      type="button"
      title="删除会话"
      aria-label="删除会话"
      @click.stop="emit('delete')"
    >
      <el-icon><Delete /></el-icon>
    </button>
  </div>
</template>

<script setup lang="ts">
/**
 * 会话列表项。
 *
 * <p>调试台的"我的会话"和坐席工作台的"待接入/进行中"都用它——
 * 两处只是数据来源不同，条目本身的标题、状态徽标、删除入口完全一致。
 * 差异通过 props 表达，而不是复制一份样式。</p>
 */
withDefaults(
  defineProps<{
    title: string
    active?: boolean
    /** 次要信息，如访客短码、最后活跃时间 */
    meta?: string
    /** 状态徽标文案，如"待接入" */
    badge?: string
    /** 徽标配色：等待用 warning，进行中用 primary */
    badgeType?: 'warning' | 'primary' | 'info'
    /** 是否显示删除入口（访客会话不允许坐席删除，只有自己的调试会话可以） */
    deletable?: boolean
  }>(),
  { badgeType: 'info' }
)

const emit = defineEmits<{
  (e: 'select'): void
  (e: 'delete'): void
}>()
</script>

<style scoped>
.conversation-item {
  position: relative;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-out);
}

.conversation-item:hover {
  background: var(--color-bg);
}

.conversation-item.active {
  background: var(--color-primary-soft);
}

.title-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.title {
  flex: 1;
  overflow: hidden;
  color: var(--color-text);
  font-size: var(--text-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.badge {
  flex: none;
  padding: 0 6px;
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  line-height: 16px;
}

.badge.warning {
  background: var(--color-warning-soft);
  color: var(--color-warning);
}

.badge.primary {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.badge.info {
  background: var(--color-bg);
  color: var(--color-text-secondary);
}

.meta {
  margin-top: 2px;
  overflow: hidden;
  color: var(--color-text-tertiary);
  font-size: var(--text-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.del {
  position: absolute;
  top: 50%;
  right: var(--space-2);
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-tertiary);
  cursor: pointer;
  opacity: 0;
  transform: translateY(-50%);
  transition: opacity var(--duration-fast) var(--ease-out);
}

.conversation-item:hover .del {
  opacity: 1;
}

.del:hover {
  color: var(--color-danger);
}
</style>
