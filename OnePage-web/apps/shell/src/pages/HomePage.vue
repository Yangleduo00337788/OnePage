<!--
  PC 首页瀑布流。P0 用 picsum 占位图，按分类名打乱，不是真实 Feed API。
  分类写在 URL ?cat=，刷新不会丢。IntersectionObserver 盯底部哨兵做无限加载。
-->
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '../components/Icon.vue'
import { NOTES, TABS, type NoteCard } from '../data/home'
import { useNoteDetail } from '../stores/noteDetail'

const noteDetail = useNoteDetail()

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
    if (typeof c === 'string' && (TABS as readonly string[]).includes(c)) return c
    return '推荐'
  },
  set: (value: string) => {
    router.replace({ query: value === '推荐' ? {} : { cat: value } })
  },
})

function shuffleFor(cat: string): NoteCard[] {
  const copy = NOTES.map((n) => ({ ...n }))
  let s = [...cat].reduce((a, c) => a + c.charCodeAt(0), 0) || 1
  for (let i = copy.length - 1; i > 0; i--) {
    s = (s * 9301 + 49297) % 233280
    const j = s % (i + 1)
    const t = copy[i]
    copy[i] = copy[j]
    copy[j] = t
  }
  return copy
}

function pack(cat: string, salt: number, p: number): NoteCard[] {
  return shuffleFor(`${cat}-${salt}-${p}`).map((n, i) => ({
    ...n,
    id: `${cat}-${salt}-${p}-${n.id}-${i}`,
    image: `https://picsum.photos/seed/op-${encodeURIComponent(cat)}-${salt}-${p}-${n.id}/640/800`,
  }))
}

const notes = ref(pack(tab.value, refreshSalt, 1))

function refreshFeed(cat = tab.value) {
  loading.value = true
  window.clearTimeout(loadTimer)
  window.clearTimeout(moreTimer)
  loadingMore.value = false
  loadTimer = window.setTimeout(() => {
    refreshSalt += 1
    page = 1
    notes.value = pack(cat, refreshSalt, 1)
    loading.value = false
  }, 520)
}

function loadMore() {
  if (loading.value || loadingMore.value) return
  loadingMore.value = true
  window.clearTimeout(moreTimer)
  moreTimer = window.setTimeout(() => {
    page += 1
    notes.value = notes.value.concat(pack(tab.value, refreshSalt, page))
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

    <div class="feed-grid flex flex-wrap justify-center gap-4" :class="loading ? 'is-refreshing' : ''">
      <article
        v-for="note in notes"
        :key="note.id"
        :data-note-id="note.id"
        class="flex h-[440px] w-[280px] cursor-pointer flex-col"
        @click="noteDetail.show(note, $event.currentTarget)"
      >
        <div class="group relative min-h-0 flex-1 overflow-hidden rounded-2xl bg-[#ececec]">
          <img :src="note.image" :alt="note.title" class="h-full w-full object-cover" />
          <span class="pointer-events-none absolute inset-0 bg-black/0 group-hover:bg-black/25" />
          <span
            v-if="note.video"
            class="absolute top-2.5 right-2.5 z-[1] text-white drop-shadow"
          >
            <svg viewBox="0 0 24 24" width="28" height="28" fill="currentColor" aria-hidden="true">
              <path d="M8 5.5v13l11-6.5z" />
            </svg>
          </span>
        </div>
        <h2 class="mt-2 shrink-0 line-clamp-2 text-[14px] leading-5">{{ note.title }}</h2>
        <div class="mt-2 flex shrink-0 items-center justify-between text-[12px] text-[#8a8a8a]">
          <span class="flex min-w-0 items-center gap-1.5">
            <span class="h-5 w-5 shrink-0 rounded-full bg-[#d9d9d9]" />
            <span class="truncate">{{ note.author }}</span>
          </span>
          <span class="flex items-center gap-1">
            <Icon name="heart" :size="13" />
            {{ note.likes }}
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
