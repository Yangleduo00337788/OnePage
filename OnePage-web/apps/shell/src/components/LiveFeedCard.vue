<!-- 直播列表卡：右上灰「直播中」带动效点；底栏头像+昵称，右侧 icon+人数。 -->
<script setup lang="ts">
import type { LiveSession } from '@onepage/api-client'
import Icon from './Icon.vue'
import IdentityBadges from './IdentityBadges.vue'
import { formatCount } from '../data/home'

defineProps<{ item: LiveSession }>()
const emit = defineEmits<{ click: [] }>()
</script>

<template>
  <article class="flex w-[280px] cursor-pointer flex-col text-left" @click="emit('click')">
    <div class="group relative h-[360px] overflow-hidden rounded-2xl bg-[#1a1a1a]">
      <img
        v-if="item.coverUrl"
        :src="item.coverUrl"
        alt=""
        class="pointer-events-none h-full w-full object-cover"
      />
      <div v-else class="flex h-full items-center justify-center px-5">
        <p class="line-clamp-4 text-center text-[26px] font-extrabold leading-[1.28] text-white">
          {{ item.title || '直播' }}
        </p>
      </div>
      <span class="pointer-events-none absolute inset-0 bg-black/0 group-hover:bg-black/25" />
      <span
        class="absolute top-2.5 right-2.5 z-[1] flex items-center gap-1 rounded bg-[#5c5c5c]/80 px-1.5 py-0.5 text-[11px] text-white"
      >
        <i class="live-dot" aria-hidden="true" />
        直播中
      </span>
    </div>
    <h2 class="mt-2 shrink-0 line-clamp-2 text-[14px] leading-5">{{ item.title }}</h2>
    <div class="mt-2 flex shrink-0 items-center justify-between gap-2 text-[12px] text-[#8a8a8a]">
      <span class="flex min-w-0 items-center gap-1.5">
        <span class="live-ava-ring">
          <img
            v-if="item.owner?.avatarUrl"
            :src="item.owner.avatarUrl"
            alt=""
            class="h-full w-full rounded-full object-cover"
          />
          <span
            v-else
            class="flex h-full w-full items-center justify-center rounded-full bg-[#d9d9d9] text-[10px] text-[var(--muted)]"
          >
            {{ (item.owner?.nickname || '主').slice(0, 1) }}
          </span>
          <IdentityBadges
            size="sm"
            :live-host="item.owner?.liveHost"
            :verified="item.owner?.verified"
          />
        </span>
        <span class="truncate">{{ item.owner?.nickname || '主播' }}</span>
      </span>
      <span class="flex shrink-0 items-center gap-1">
        <Icon name="users" :size="13" />
        {{ formatCount(item.viewerCount) }}
      </span>
    </div>
  </article>
</template>

<style scoped>
.live-dot {
  display: block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 0 0 rgba(255, 255, 255, 0.7);
  animation: live-dot 1.4s ease-out infinite;
}

.live-ava-ring {
  position: relative;
  display: flex;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  overflow: visible;
  padding: 1px;
  border-radius: 50%;
  background: var(--panel);
  box-shadow: 0 0 0 0 rgba(255, 36, 66, 0.55);
  animation: live-ring 1.6s ease-out infinite;
}

.live-ava-ring::before {
  content: '';
  position: absolute;
  inset: -2px;
  border-radius: 50%;
  border: 1.5px solid #ff2442;
  pointer-events: none;
}

@keyframes live-dot {
  0% {
    box-shadow: 0 0 0 0 rgba(255, 255, 255, 0.7);
    opacity: 1;
  }
  70% {
    box-shadow: 0 0 0 5px rgba(255, 255, 255, 0);
    opacity: 0.85;
  }
  100% {
    box-shadow: 0 0 0 0 rgba(255, 255, 255, 0);
    opacity: 1;
  }
}

@keyframes live-ring {
  0% {
    box-shadow: 0 0 0 0 rgba(255, 36, 66, 0.55);
  }
  70% {
    box-shadow: 0 0 0 6px rgba(255, 36, 66, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(255, 36, 66, 0);
  }
}
</style>
