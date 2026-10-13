<!-- 全站空态：上方粘土插画 + 标题 + 可选说明。 -->
<script setup lang="ts">
withDefaults(
  defineProps<{
    src: string
    title: string
    desc?: string
    compact?: boolean
  }>(),
  { compact: false },
)
</script>

<template>
  <div class="me-empty" :class="{ 'is-compact': compact }">
    <img class="me-empty-art" :src="src" alt="" width="180" height="180" />
    <p class="me-empty-title">{{ title }}</p>
    <p v-if="desc" class="me-empty-desc">{{ desc }}</p>
    <slot />
  </div>
</template>
