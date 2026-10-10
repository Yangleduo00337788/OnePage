<!--
  直播页：分类 Tab 样式同首页，卡片同 280×440 瀑布流。
  P0 占位封面；右上「直播中」动效，头像带在播环，右侧人数。
-->
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '../components/Icon.vue'
import { LIVE_ROOMS, LIVE_TABS, type LiveRoom } from '../data/home'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const loadingMore = ref(false)
const sentinel = ref<HTMLElement | null>(null)
let loadTimer = 0
let moreTimer = 0
let page = 1
let refreshSalt = 0
let io: IntersectionObserver | null = null

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

function pool(cat: string): LiveRoom[] {
  return cat === '全部' ? LIVE_ROOMS : LIVE_ROOMS.filter((r) => r.cat === cat)
}

function shuffleFor(seed: string, list: LiveRoom[]): LiveRoom[] {
  const copy = list.map((n) => ({ ...n }))
  let s = [...seed].reduce((a, c) => a + c.charCodeAt(0), 0) || 1
  for (let i = copy.length - 1; i > 0; i--) {
    s = (s * 9301 + 49297) % 233280
    const j = s % (i + 1)
    const t = copy[i]
    copy[i] = copy[j]
    copy[j] = t
  }
  return copy
}

function pack(cat: string, salt: number, p: number): LiveRoom[] {
  const base = pool(cat)
  const src = base.length ? base : LIVE_ROOMS
  return shuffleFor(`${cat}-${salt}-${p}`, src).map((n, i) => ({
    ...n,
    id: `${cat}-${salt}-${p}-${n.id}-${i}`,
    cover: `https://picsum.photos/seed/op-live-${encodeURIComponent(cat)}-${salt}-${p}-${n.id}/640/800`,
  }))
}

const rooms = ref(pack(tab.value, refreshSalt, 1))

function refreshFeed(cat = tab.value) {
  loading.value = true
  window.clearTimeout(loadTimer)
  window.clearTimeout(moreTimer)
  loadingMore.value = false
  loadTimer = window.setTimeout(() => {
    refreshSalt += 1
    page = 1
    rooms.value = pack(cat, refreshSalt, 1)
    loading.value = false
  }, 520)
}

function loadMore() {
  if (loading.value || loadingMore.value) return
  loadingMore.value = true
  window.clearTimeout(moreTimer)
  moreTimer = window.setTimeout(() => {
    page += 1
    rooms.value = rooms.value.concat(pack(tab.value, refreshSalt, page))
    loadingMore.value = false
  }, 360)
}

watch(tab, (next, prev) => {
  if (next === prev) return
  refreshFeed(next)
})

function toTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  io = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) loadMore()
    },
    { root: null, rootMargin: '560px 0px' },
  )
  nextTick(() => {
    if (sentinel.value) io?.observe(sentinel.value)
  })
})

onUnmounted(() => {
  window.clearTimeout(loadTimer)
  window.clearTimeout(moreTimer)
  io?.disconnect()
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

    <div class="feed-grid flex flex-wrap justify-center gap-4" :class="loading ? 'is-refreshing' : ''">
      <article v-for="room in rooms" :key="room.id" class="flex h-[440px] w-[280px] flex-col">
        <div class="group relative min-h-0 flex-1 overflow-hidden rounded-2xl bg-[#ececec]">
          <img :src="room.cover" :alt="room.title" class="h-full w-full object-cover" />
          <span class="pointer-events-none absolute inset-0 bg-black/0 group-hover:bg-black/25" />
          <span class="live-onair">
            <span class="live-waves" aria-hidden="true">
              <span /><span /><span />
            </span>
            直播中
          </span>
        </div>
        <h2 class="mt-2 shrink-0 line-clamp-2 text-[14px] leading-5">{{ room.title }}</h2>
        <div class="mt-2 flex shrink-0 items-center justify-between text-[12px] text-[#8a8a8a]">
          <span class="flex min-w-0 items-center gap-1.5">
            <span class="live-avatar">
              <img :src="room.avatar" :alt="room.author" />
              <span class="live-avatar-dot" />
            </span>
            <span class="truncate">{{ room.author }}</span>
          </span>
          <span class="flex items-center gap-1">
            <Icon name="users" :size="13" />
            {{ room.viewers }}
          </span>
        </div>
      </article>
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
