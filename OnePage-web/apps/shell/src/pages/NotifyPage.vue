<!--
  通知中心。三个居中 Tab：评论和@ / 点赞和收藏 / 新增关注。
  P0 占位列表，未接 notification-service。
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NOTICES, NOTIFY_TABS, type NotifyTab } from '../data/notify'

const route = useRoute()
const router = useRouter()

const tab = computed({
  get: () => {
    const t = route.query.tab
    if (typeof t === 'string' && (NOTIFY_TABS as readonly string[]).includes(t)) return t as NotifyTab
    return '评论和@'
  },
  set: (value: NotifyTab) => {
    router.replace({ query: value === '评论和@' ? {} : { tab: value } })
  },
})

const list = computed(() => NOTICES.filter((n) => n.tab === tab.value))
</script>

<template>
  <section class="mx-auto max-w-[720px]">
    <div class="mb-6 flex flex-wrap items-center justify-center gap-x-10 gap-y-2">
      <button
        v-for="item in NOTIFY_TABS"
        :key="item"
        type="button"
        class="feed-tab"
        :class="tab === item ? 'is-on' : ''"
        @click="tab = item"
      >
        {{ item }}
      </button>
    </div>

    <ul class="divide-y divide-[#f2f2f2]">
      <li v-for="item in list" :key="item.id" class="notify-row">
        <span class="notify-avatar">
          <img :src="item.avatar" :alt="item.name" />
          <span v-if="item.unread" class="notify-unread" />
        </span>
        <div class="min-w-0 flex-1">
          <p class="text-[14px] leading-6 text-[#1f1f1f]">
            <span class="font-medium">{{ item.name }}</span>
            <span class="text-[#555]"> {{ item.action }}</span>
          </p>
          <p v-if="item.snippet" class="mt-0.5 truncate text-[13px] text-[#8a8a8a]">{{ item.snippet }}</p>
          <p class="mt-1 text-[12px] text-[#bbb]">{{ item.time }}</p>
        </div>
        <img v-if="item.thumb" :src="item.thumb" alt="" class="notify-thumb" />
      </li>
    </ul>
    <p v-if="!list.length" class="py-20 text-center text-[13px] text-[#8a8a8a]">暂时还没有这类通知</p>
  </section>
</template>
