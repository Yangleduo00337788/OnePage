<!-- 纯文字封面：对齐小红书/抖音图文，封面只放大加粗标题，居中铺满。 -->
<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    title: string
    body?: string
    size?: 'feed' | 'card' | 'thumb'
  }>(),
  { body: '', size: 'card' },
)

const headline = computed(() => {
  const title = props.title.trim()
  if (title) return title
  const line = props.body.trim().split(/\n/)[0] || ''
  return line.trim() || '笔记'
})
</script>

<template>
  <div class="tnc" :class="'is-' + size">
    <p class="tnc-title">{{ headline }}</p>
  </div>
</template>

<style scoped>
.tnc {
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 28px 20px;
  background: var(--fill);
  color: var(--ink);
}

.tnc-title {
  margin: 0;
  width: 100%;
  overflow: hidden;
  font-size: 26px;
  font-weight: 800;
  line-height: 1.28;
  letter-spacing: 0.02em;
  text-align: center;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
  word-break: break-word;
}

.is-card {
  padding: 20px 14px;
}

.is-card .tnc-title {
  font-size: 20px;
  -webkit-line-clamp: 3;
}

.is-thumb {
  padding: 6px 4px;
}

.is-thumb .tnc-title {
  font-size: 11px;
  font-weight: 800;
  line-height: 1.2;
  -webkit-line-clamp: 3;
}
</style>
