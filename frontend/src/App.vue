<template>
  <div class="app-shell">
    <router-view />
  </div>
</template>

<script setup lang="ts">
// 全局样式（重置、排版、滚动条、tokens）统一在 main.ts 引入，
// 组件级样式不放在这里，避免所有页面被迫共享同一份全局作用域。
</script>

<style scoped>
.app-shell {
  height: 100%;
}
</style>
