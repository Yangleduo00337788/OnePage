<!--
  视频页：与首页同一套 280×440 瀑布流，无分类 Tab。
  卡片一律当视频（播放钮 + 时长），P0 仍用 picsum 占位。
-->
<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import Icon from '../components/Icon.vue'
import { NOTES, type NoteCard } from '../data/home'
import { useNoteDetail } from '../stores/noteDetail'

const noteDetail = useNoteDetail()

const loading = ref(false)
const loadingMore = ref(false)
const sentinel = ref<HTMLElement | null>(null)
let loadTimer = 0
let moreTimer = 0
let page = 1
let refreshSalt = 0
let io: IntersectionObserver | null = null

function shuffleFor(seed: string): NoteCard[] {
  const copy = NOTES.map((n) => ({ ...n }))
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

function pack(salt: number, p: number): NoteCard[] {
  return shuffleFor(`视频-${salt}-${p}`).map((n, i) => ({
    ...n,
    id: `video-${salt}-${p}-${n.id}-${i}`,
    image: `https://picsum.photos/seed/op-video-${salt}-${p}-${n.id}/640/800`,
    video: true,
  }))
}

const notes = ref(pack(refreshSalt, 1))

function refreshFeed() {
  loading.value = true
  window.clearTimeout(loadTimer)
  window.clearTimeout(moreTimer)
  loadingMore.value = false
  loadTimer = window.setTimeout(() => {
    refreshSalt += 1
    page = 1
    notes.value = pack(refreshSalt, 1)
    loading.value = false
  }, 520)
}

function loadMore() {
  if (loading.value || loadingMore.value) return
  loadingMore.value = true
  window.clearTimeout(moreTimer)
  moreTimer = window.setTimeout(() => {
    page += 1
    notes.value = notes.value.concat(pack(refreshSalt, page))
    loadingMore.value = false
  }, 360)
}

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
          <span class="absolute top-2.5 right-2.5 z-[1] text-white drop-shadow">
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
      <button type="button" class="feed-fab" :class="loading ? 'is-spin' : ''" @click="refreshFeed">
        <Icon name="refresh" :size="18" />
        <span class="feed-tip">刷新</span>
      </button>
    </div>
  </section>
</template>
