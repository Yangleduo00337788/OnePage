<!--
  直播页：分类 Tab 样式同首页。live 服务未建，不再用假房间演示。
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '../components/Icon.vue'
import EmptyState from '../components/EmptyState.vue'
import { LIVE_TABS } from '../data/home'

const route = useRoute()
const router = useRouter()

const tab = computed({
  get: () => {
    const c = route.query.cat
    if (typeof c === 'string' && (LIVE_TABS as readonly string[]).includes(c)) return c
    return '全部'
  },
  set: (value: string) => {
    router.replace({ query: value === '全部' ? {} : { cat: value } })
  },
})

function toTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<template>
  <section>
    <div class="mb-5 flex flex-wrap items-center justify-center gap-x-7 gap-y-2">
      <button
        v-for="item in LIVE_TABS"
        :key="item"
        type="button"
        class="feed-tab"
        :class="tab === item ? 'is-on' : ''"
        @click="tab = item"
      >
        {{ item }}
      </button>
    </div>

    <EmptyState
      src="/empty-live.png"
      title="还没有直播"
      desc="开播能力尚未接入，这里不再用假房间占位。"
    />

    <div class="feed-fabs">
      <button type="button" class="feed-fab" @click="toTop">
        <Icon name="chevron-up" :size="20" />
        <span class="feed-tip">返回顶部</span>
      </button>
    </div>
  </section>
</template>
