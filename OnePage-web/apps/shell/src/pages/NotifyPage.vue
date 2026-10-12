<!--
  通知中心。三个居中 Tab：评论和@ / 点赞和收藏 / 新增关注。
  通知服务未建，列表为空，不再用假数据演示。
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const NOTIFY_TABS = ['评论和@', '点赞和收藏', '新增关注'] as const
type NotifyTab = (typeof NOTIFY_TABS)[number]

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
    <p class="py-20 text-center text-[13px] text-[#8a8a8a]">暂时还没有这类通知</p>
  </section>
</template>
