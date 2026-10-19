<!-- 昵称后：小尺寸章 + 一页官方认证号 / 官方主播认证 / 官方认证。 -->
<script setup lang="ts">
import IdentityBadges from './IdentityBadges.vue'

defineProps<{ liveHost?: boolean; verified?: boolean; official?: boolean; light?: boolean }>()
</script>

<template>
  <span
    v-if="liveHost || verified || official"
    class="inline-flex min-w-0 shrink-0 flex-wrap items-center gap-x-1.5 gap-y-0.5 text-[12px] font-normal leading-none"
    :class="light ? 'text-white/90' : 'text-[#8a8a8a]'"
  >
    <span v-if="official" class="inline-flex items-center gap-1">
      <IdentityBadges inline size="sm" official />
      <span>一页官方认证号</span>
    </span>
    <span v-if="liveHost" class="inline-flex items-center gap-1">
      <IdentityBadges inline size="sm" live-host />
      <span>官方主播认证</span>
    </span>
    <span v-if="verified" class="inline-flex items-center gap-1">
      <IdentityBadges inline size="sm" verified />
      <span>官方认证</span>
    </span>
  </span>
</template>
