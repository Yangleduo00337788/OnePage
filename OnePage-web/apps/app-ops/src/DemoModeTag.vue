<script setup lang="ts">
import { isDemoMode } from '@onepage/api-client'
</script>

<template>
  <span v-if="isDemoMode()" class="demo-mode">
    <span class="demo-mode-label">当前模式：</span><span class="demo-mode-value">演示模式</span>
  </span>
</template>

<style scoped>
.demo-mode {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  white-space: nowrap;
}

.demo-mode-label {
  color: #8a8a8a;
}

.demo-mode-value {
  color: #ff2442;
}
</style>
