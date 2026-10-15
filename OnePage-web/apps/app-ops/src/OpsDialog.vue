<!-- 运营 CRUD / 表单弹层。 -->
<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'

defineProps<{ title: string }>()
const emit = defineEmits<{ close: [] }>()

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}

onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="mask" @click.self="emit('close')">
    <section class="sheet" role="dialog" aria-modal="true">
      <header class="head">
        <h2>{{ title }}</h2>
        <button type="button" class="x" aria-label="关闭" @click="emit('close')">×</button>
      </header>
      <div class="body">
        <slot />
      </div>
      <footer class="foot">
        <slot name="footer" />
      </footer>
    </section>
  </div>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgb(0 0 0 / 45%);
}

.sheet {
  display: flex;
  flex-direction: column;
  width: min(480px, calc(100vw - 40px));
  max-height: min(88vh, 720px);
  overflow: hidden;
  padding: 20px 24px 16px;
  border-radius: 20px;
  background: #fff;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.x {
  width: 32px;
  height: 32px;
  border-radius: 16px;
  color: #8a8a8a;
  font-size: 22px;
  line-height: 1;
}

.x:hover {
  background: #f2f2f2;
}

.body {
  flex: 1;
  min-height: 0;
  margin-top: 16px;
  overflow: auto;
}

.foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
}

.foot :deep(button) {
  height: 36px;
  padding: 0 16px;
  border-radius: 18px;
  font-size: 14px;
}

.foot :deep(.ghost) {
  background: rgb(248, 248, 248);
  color: #555;
}

.foot :deep(.go) {
  background: #ff2442;
  color: #fff;
  font-weight: 600;
}

.foot :deep(.go:disabled) {
  opacity: 0.5;
}
</style>
