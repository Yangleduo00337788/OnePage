<!-- 主页笔记 / 「我的」收藏点赞历史：卡片同首页瀑布流。 -->
<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { api, ApiError, type CursorPage, type PostBrief } from '@onepage/api-client'
import { noteFromPost, type NoteCard } from '../data/home'
import NoteFeedCard from './NoteFeedCard.vue'

export type ProfileNoteSource = 'user-posts' | 'favorites' | 'likes' | 'history'

const props = withDefaults(
  defineProps<{
    source?: ProfileNoteSource
    pageId?: string
    emptyTitle: string
    emptyDesc: string
    emptyPrimary?: boolean
  }>(),
  { source: 'user-posts', emptyPrimary: true },
)

const emit = defineEmits<{ emptyAction: [] }>()

const notes = ref<NoteCard[]>([])
const cursor = ref<string | null>(null)
const hasMore = ref(true)
const loading = ref(false)
const loadingMore = ref(false)
const failed = ref('')
const sentinel = ref<HTMLElement | null>(null)
let io: IntersectionObserver | null = null
let seq = 0

function fetchPage(nextCursor?: string) {
  if (props.source === 'favorites') return api.myFavorites(nextCursor, 20)
  if (props.source === 'likes') return api.myLikes(nextCursor, 20)
  if (props.source === 'history') return api.myHistory(nextCursor, 20)
  if (!props.pageId) {
    return Promise.resolve({ items: [], nextCursor: null, hasMore: false } as CursorPage<PostBrief>)
  }
  return api.userPosts(props.pageId, nextCursor, 20)
}

async function refresh() {
  if (props.source === 'user-posts' && !props.pageId) return
  const token = ++seq
  loading.value = true
  loadingMore.value = false
  failed.value = ''
  cursor.value = null
  hasMore.value = true
  try {
    const page = await fetchPage(undefined)
    if (token !== seq) return
    notes.value = page.items.map(noteFromPost)
    cursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } catch (e) {
    if (token !== seq) return
    notes.value = []
    failed.value = e instanceof ApiError ? e.message : '加载失败'
  } finally {
    if (token === seq) loading.value = false
  }
}

async function loadMore() {
  if (loading.value || loadingMore.value || !hasMore.value) return
  if (props.source === 'user-posts' && !props.pageId) return
  const token = seq
  loadingMore.value = true
  try {
    const page = await fetchPage(cursor.value || undefined)
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

watch(
  () => [props.pageId, props.source] as const,
  () => {
    void refresh()
  },
)

watch([notes, loading], async () => {
  await nextTick()
  if (!io || !sentinel.value) return
  io.disconnect()
  io.observe(sentinel.value)
})

onMounted(() => {
  io = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) void loadMore()
    },
    { root: null, rootMargin: '480px 0px' },
  )
  void refresh()
})

onUnmounted(() => {
  io?.disconnect()
})
</script>

<template>
  <div class="py-6">
    <p v-if="loading" class="py-10 text-center text-[14px] text-[#8a8a8a]">加载中…</p>
    <p v-else-if="failed" class="py-10 text-center text-[14px] text-[#8a8a8a]">{{ failed }}</p>
    <div v-else-if="!notes.length" class="me-empty">
      <img class="me-empty-art" src="/empty-me.png" alt="" width="180" height="180" />
      <p class="me-empty-title">{{ emptyTitle }}</p>
      <p class="me-empty-desc">{{ emptyDesc }}</p>
      <button
        v-if="$slots.action"
        type="button"
        class="me-empty-go"
        :class="{ 'is-primary': emptyPrimary }"
        @click="emit('emptyAction')"
      >
        <slot name="action" />
      </button>
    </div>
    <template v-else>
      <div class="feed-grid flex flex-wrap justify-center gap-4">
        <NoteFeedCard v-for="note in notes" :key="note.id" :note="note" />
      </div>
      <div ref="sentinel" class="feed-more" aria-hidden="true">
        <span v-show="loadingMore" class="feed-spinner is-more" />
      </div>
    </template>
  </div>
</template>
