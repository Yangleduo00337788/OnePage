<!-- 功能引导：目标脉冲 + 白卡片 Popover（小红书/抖音类常见样式） -->
<script setup lang="ts">
import { ref } from 'vue'

withDefaults(
  defineProps<{
    title: string
    description: string
    placement?: 'right' | 'left'
  }>(),
  { placement: 'right' },
)

const emit = defineEmits<{ acknowledge: [] }>()

/** 仅当前页有效；刷新后会再次出现 */
const visible = ref(true)

function dismiss() {
  visible.value = false
}

function onAck() {
  dismiss()
  emit('acknowledge')
}

defineExpose({ dismiss, visible })
</script>

<template>
  <div class="hint-anchor">
    <div class="hint-target" :class="{ 'is-pulse': visible }">
      <slot />
    </div>
    <Transition name="hint">
      <div
        v-if="visible"
        class="hint-pop"
        :class="`hint-pop--${placement}`"
        role="dialog"
        aria-live="polite"
      >
        <p class="hint-title">{{ title }}</p>
        <p class="hint-desc">{{ description }}</p>
        <div class="hint-actions">
          <button type="button" class="hint-btn" @click.stop="onAck">知道了</button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.hint-anchor {
  position: relative;
  display: flex;
  justify-content: center;
  overflow: visible;
  width: 100%;
}

.hint-target {
  position: relative;
  display: flex;
  justify-content: center;
  border-radius: 999px;
}

.hint-target.is-pulse::after {
  content: '';
  position: absolute;
  inset: -3px;
  border-radius: inherit;
  pointer-events: none;
  animation: hint-ring 2s ease-out infinite;
}

@keyframes hint-ring {
  0% {
    box-shadow: 0 0 0 0 rgba(255, 36, 66, 0.38);
  }
  65% {
    box-shadow: 0 0 0 10px rgba(255, 36, 66, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(255, 36, 66, 0);
  }
}

.hint-pop {
  position: absolute;
  z-index: 50;
  width: 272px;
  padding: 14px 16px 12px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow:
    0 16px 48px rgba(0, 0, 0, 0.12),
    0 2px 8px rgba(0, 0, 0, 0.04);
  pointer-events: auto;
}

.hint-pop--right {
  left: calc(100% + 14px);
  top: 50%;
  transform: translateY(-50%);
}

.hint-pop--right::before {
  content: '';
  position: absolute;
  left: -7px;
  top: 50%;
  width: 12px;
  height: 12px;
  background: #fff;
  border-left: 1px solid rgba(0, 0, 0, 0.06);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  transform: translateY(-50%) rotate(45deg);
}

.hint-pop--left {
  right: calc(100% + 14px);
  top: 50%;
  transform: translateY(-50%);
}

.hint-pop--left::before {
  content: '';
  position: absolute;
  right: -7px;
  top: 50%;
  width: 12px;
  height: 12px;
  background: #fff;
  border-right: 1px solid rgba(0, 0, 0, 0.06);
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  transform: translateY(-50%) rotate(45deg);
}

.hint-title {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.35;
  color: #1f1f1f;
}

.hint-desc {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.55;
  color: #666;
}

.hint-actions {
  display: flex;
  justify-content: flex-end;
}

.hint-btn {
  height: 32px;
  padding: 0 16px;
  border-radius: 16px;
  background: #ff2442;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}

.hint-btn:hover {
  filter: brightness(0.96);
}

.hint-enter-active,
.hint-leave-active {
  transition:
    opacity 200ms ease,
    transform 200ms ease;
}

.hint-pop--right.hint-enter-from,
.hint-pop--right.hint-leave-to {
  opacity: 0;
  transform: translate(6px, -50%);
}

.hint-pop--left.hint-enter-from,
.hint-pop--left.hint-leave-to {
  opacity: 0;
  transform: translate(-6px, -50%);
}
</style>
