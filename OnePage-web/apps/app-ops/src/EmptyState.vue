<!-- 全站空态：粘土插画 + 标题。 -->
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

<style scoped>
.me-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56px 0 32px;
  text-align: center;
}

.me-empty-art {
  display: block;
  width: 180px;
  height: auto;
  pointer-events: none;
  user-select: none;
}

.is-compact {
  padding: 28px 0 16px;
}

.is-compact .me-empty-art {
  width: 120px;
}

.me-empty-title {
  margin-top: 16px;
  font-size: 16px;
  font-weight: 500;
  color: #1f1f1f;
}

.me-empty-desc {
  margin-top: 8px;
  max-width: 280px;
  font-size: 13px;
  line-height: 1.6;
  color: #8a8a8a;
}
</style>
