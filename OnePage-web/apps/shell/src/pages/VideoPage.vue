<!--
  视频页：与首页同一套 280×440 瀑布流，无分类 Tab。
  数据来自 GET /feeds/explore，只展示 postType=video。
-->
<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import Icon from '../components/Icon.vue'
import EmptyState from '../components/EmptyState.vue'
import NoteFeedCard from '../components/NoteFeedCard.vue'
import { noteFromPost, type NoteCard } from '../data/home'
import { api } from '@onepage/api-client'

const loading = ref(false)
const loadingMore = ref(false)
const notes = ref<NoteCard[]>([])
const cursor = ref<string | null>(null)
const hasMore = ref(true)
const failed = ref('')
const sentinel = ref<HTMLElement | null>(null)
let io: IntersectionObserver | null = null
let seq = 0

async function pullVideos() {
  let guard = 0
  const added: NoteCard[] = []
  while (guard++ < 8) {
    const page = await api.exploreFeed(cursor.value || undefined, 20)
    added.push(...page.items.filter((p) => p.postType === 'video').map(noteFromPost))
    cursor.value = page.nextCursor
    hasMore.value = page.hasMore
    if (added.length || !page.hasMore) break
  }
  return added
}

async function refreshFeed() {
  const token = ++seq
  loading.value = true
  loadingMore.value = false
  failed.value = ''
  cursor.value = null
  hasMore.value = true
  try {
    const added = await pullVideos()
    if (token !== seq) return
    notes.value = added
  } catch {
    if (token !== seq) return
    notes.value = []
    failed.value = '加载失败，点右下角刷新重试'
  } finally {
    if (token === seq) loading.value = false
  }
}

async function loadMore() {
  if (loading.value || loadingMore.value || !hasMore.value) return
  const token = seq
  loadingMore.value = true
  try {
    const added = await pullVideos()
    if (token !== seq) return
    notes.value = notes.value.concat(added)
  } catch {
    if (token === seq) hasMore.value = false
  } finally {
    if (token === seq) loadingMore.value = false
  }
}

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
    <div class="feed-refresh" :class="loading ? 'is-on' : ''" aria-hidden="true">
      <span v-show="loading" class="feed-spinner" />
    </div>

    <p v-if="!loading && failed" class="py-16 text-center text-[14px] text-[#8a8a8a]">{{ failed }}</p>
    <EmptyState v-else-if="!loading && !notes.length" src="/empty-video.png" title="还没有视频" />

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
