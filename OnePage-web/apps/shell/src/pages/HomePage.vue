<!--
  PC 首页瀑布流。推荐走 GET /feeds/explore，关注走 /feeds/following（P1 同一数据）。
  分类 Tab 按 post.category 筛发现流。点卡片打开详情弹窗。
-->
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { TABS, noteFromPost, type NoteCard } from '../data/home'
import Icon from '../components/Icon.vue'
import NoteFeedCard from '../components/NoteFeedCard.vue'
import { api, ApiError, FEED_CATEGORIES } from '@onepage/api-client'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const loadingMore = ref(false)
const notes = ref<NoteCard[]>([])
const cursor = ref<string | null>(null)
const hasMore = ref(true)
const failed = ref('')
const sentinel = ref<HTMLElement | null>(null)
let io: IntersectionObserver | null = null
let seq = 0

const tab = computed({
  get: () => {
    const c = route.query.cat
    if (typeof c === 'string' && (TABS as readonly string[]).includes(c)) return c
    return '推荐'
  },
  set: (value: string) => {
    router.replace({ query: value === '推荐' ? {} : { cat: value } })
  },
})

function pull(nextCursor?: string) {
  if (tab.value === '关注') return api.followingFeed(nextCursor, 20)
  const cat = (FEED_CATEGORIES as readonly string[]).includes(tab.value) ? tab.value : undefined
  return api.exploreFeed(nextCursor, 20, cat)
}

async function refreshFeed() {
  const token = ++seq
  loading.value = true
  loadingMore.value = false
  failed.value = ''
  cursor.value = null
  hasMore.value = true
  try {
    const page = await pull()
    if (token !== seq) return
    notes.value = page.items.map(noteFromPost)
    cursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } catch (e) {
    if (token !== seq) return
    notes.value = []
    failed.value = e instanceof ApiError ? e.message : '加载失败，点右下角刷新重试'
  } finally {
    if (token === seq) loading.value = false
  }
}

async function loadMore() {
  if (loading.value || loadingMore.value || !hasMore.value) return
  const token = seq
  loadingMore.value = true
  try {
    const page = await pull(cursor.value || undefined)
    if (token !== seq) return
    notes.value = notes.value.concat(page.items.map(noteFromPost))
    cursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } catch {
    if (token === seq) hasMore.value = false
  } finally {
    if (token === seq) loadingMore.value = false
  }
}

watch(tab, (next, prev) => {
  if (next === prev) return
  void refreshFeed()
})

function toTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  void refreshFeed()
  io = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) void loadMore()
    },
    { root: null, rootMargin: '560px 0px' },
  )
  nextTick(() => {
    if (sentinel.value) io?.observe(sentinel.value)
  })
})

onUnmounted(() => {
  io?.disconnect()
})
</script>

<template>
  <section>
    <div class="mb-5 flex flex-wrap items-center justify-center gap-x-7 gap-y-2">
      <button
        v-for="item in TABS"
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

    <p v-if="!loading && failed" class="py-16 text-center text-[14px] text-[#8a8a8a]">{{ failed }}</p>
    <p v-else-if="!loading && !notes.length" class="py-16 text-center text-[14px] text-[#8a8a8a]">还没有笔记</p>

    <div v-else class="feed-grid flex flex-wrap justify-center gap-4" :class="loading ? 'is-refreshing' : ''">
      <NoteFeedCard v-for="note in notes" :key="note.id" :note="note" />
    </div>
    <div ref="sentinel" class="feed-more" aria-hidden="true">
      <span v-show="loadingMore" class="feed-spinner is-more" />
    </div>

    <div class="feed-fabs">
      <button type="button" class="feed-fab" @click="toTop">
        <Icon name="chevron-up" :size="20" />
        <span class="feed-tip">返回顶部</span>
      </button>
      <button type="button" class="feed-fab" :class="loading ? 'is-spin' : ''" @click="refreshFeed()">
        <Icon name="refresh" :size="18" />
        <span class="feed-tip">刷新</span>
      </button>
    </div>
  </section>
</template>
