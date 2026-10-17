<!-- 社区直播列表（只看，不开播）。GET /live/sessions。开播在创作者中心。 -->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError, type LiveSession } from '@onepage/api-client'
import Icon from '../components/Icon.vue'
import EmptyState from '../components/EmptyState.vue'
import LiveFeedCard from '../components/LiveFeedCard.vue'
import { LIVE_TABS } from '../data/home'
import { openCreatorSite } from '../creatorSite'

const route = useRoute()
const router = useRouter()

const tab = computed({
  get: () => {
    const c = route.query.cat
    if (typeof c === 'string' && (LIVE_TABS as readonly string[]).includes(c)) return c
    return '全部'
  },
  set: (value: string) => {
    const next = { ...route.query }
    if (value === '全部') delete next.cat
    else next.cat = value
    void router.replace({ query: next })
  },
})

const items = ref<LiveSession[]>([])
const loading = ref(false)
const failed = ref('')

async function loadList() {
  loading.value = true
  failed.value = ''
  try {
    const page = await api.liveSessions({ category: tab.value === '全部' ? undefined : tab.value })
    items.value = page.items || []
  } catch (e) {
    items.value = []
    failed.value = e instanceof ApiError ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function openWatch(id: string) {
  void router.push(`/live/${encodeURIComponent(id)}`)
}

function toTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

watch(
  () => tab.value,
  () => {
    void loadList()
  },
)

onMounted(() => {
  const s = route.query.s
  if (typeof s === 'string' && s) {
    void router.replace(`/live/${encodeURIComponent(s)}`)
    return
  }
  void loadList()
})
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

    <div class="feed-refresh" :class="loading ? 'is-on' : ''" aria-hidden="true">
      <span v-show="loading" class="feed-spinner" />
    </div>

    <p v-if="!loading && failed" class="mb-4 text-center text-[13px] text-[#8a8a8a]">{{ failed }}</p>

    <div v-if="items.length" class="feed-grid flex flex-wrap justify-center gap-4" :class="loading ? 'is-refreshing' : ''">
      <LiveFeedCard
        v-for="item in items"
        :key="item.sessionId"
        :item="item"
        @click="openWatch(item.sessionId)"
      />
    </div>
    <EmptyState
      v-else-if="!loading"
      src="/empty-live.png"
      title="还没有人开播"
      desc="主播请到创作者中心开播，这里只看正在直播的房间。"
    >
      <button type="button" class="me-empty-go is-primary" @click="openCreatorSite()">去创作者中心开播</button>
    </EmptyState>

    <div class="feed-fabs">
      <button type="button" class="feed-fab" @click="toTop">
        <Icon name="chevron-up" :size="20" />
        <span class="feed-tip">返回顶部</span>
      </button>
      <button type="button" class="feed-fab" :class="loading ? 'is-spin' : ''" @click="loadList()">
        <Icon name="refresh" :size="18" />
        <span class="feed-tip">刷新</span>
      </button>
    </div>
  </section>
</template>

