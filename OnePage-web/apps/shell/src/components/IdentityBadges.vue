<!--
  贴法同「我的」头像左下相机：right/bottom 0 贴在圆的右下角。
  大 V = 红 V；主播 = 蓝 V。两枚都有时红 V 贴右下角，蓝 V 略向左叠。
-->
<script setup lang="ts">
withDefaults(
  defineProps<{
    liveHost?: boolean
    verified?: boolean
    size?: 'lg' | 'md' | 'sm'
    inline?: boolean
  }>(),
  { size: 'md' },
)
</script>

<template>
  <span
    v-if="liveHost || verified"
    class="seals"
    :class="[`is-${size}`, { 'is-pair': liveHost && verified, 'is-inline': inline }]"
  >
    <span v-if="liveHost" class="seal is-host" title="主播">
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="12" r="12" fill="#4a90d9" />
        <path
          d="M8.05 7.15c.38-.16.82.02 1 .38L12 14.7l2.95-7.17c.18-.36.62-.54 1-.38.4.16.6.6.44 1L12.86 17.1c-.22.54-.86.54-1.08 0L7.61 8.15c-.16-.4.04-.84.44-1Z"
          fill="#fff"
        />
      </svg>
    </span>
    <span v-if="verified" class="seal" title="大 V 认证">
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="12" r="12" fill="#ff2442" />
        <path
          d="M8.05 7.15c.38-.16.82.02 1 .38L12 14.7l2.95-7.17c.18-.36.62-.54 1-.38.4.16.6.6.44 1L12.86 17.1c-.22.54-.86.54-1.08 0L7.61 8.15c-.16-.4.04-.84.44-1Z"
          fill="#fff"
        />
      </svg>
    </span>
  </span>
</template>

<style scoped>
.seals {
  position: absolute;
  right: 0;
  bottom: 0;
  z-index: 3;
  display: flex;
  align-items: flex-end;
  pointer-events: none;
}
.seal {
  display: block;
  overflow: hidden;
  border-radius: 50%;
  background: #ff2442;
  box-shadow: 0 0 0 1.5px var(--panel, #fff);
}
.seal.is-host {
  background: #4a90d9;
}
.seal svg {
  display: block;
  width: 100%;
  height: 100%;
}
.is-lg .seal {
  width: 20px;
  height: 20px;
}
.is-md .seal {
  width: 14px;
  height: 14px;
}
.is-sm .seal {
  width: 12px;
  height: 12px;
}
.is-pair .seal + .seal {
  margin-left: -6px;
}
.is-lg.is-pair .seal + .seal {
  margin-left: -8px;
}
.is-sm.is-pair .seal + .seal {
  margin-left: -4px;
}
.seals.is-inline {
  position: static;
  right: auto;
  bottom: auto;
  z-index: auto;
  pointer-events: none;
  flex-shrink: 0;
}
</style>
