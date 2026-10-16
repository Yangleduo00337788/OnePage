<!-- 主页笔记 / 「我的」收藏点赞历史：卡片同首页瀑布流。 -->
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
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
const busy = ref('')
const picking = ref(false)
const selected = ref<string[]>([])
const allOn = computed(
  () => notes.value.length > 0 && notes.value.every((item) => selected.value.includes(item.id)),
)

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
    picking.value = false
    selected.value = []
    void refresh()
  },
)

watch([notes, loading], async () => {
  await nextTick()
  if (!io || !sentinel.value) return
  io.disconnect()
  io.observe(sentinel.value)
})

function startPick() {
  picking.value = true
  selected.value = []
}

function cancelPick() {
  picking.value = false
  selected.value = []
}

function toggleOne(id: string) {
  if (selected.value.includes(id)) selected.value = selected.value.filter((x) => x !== id)
  else selected.value = [...selected.value, id]
}

function toggleAll() {
  if (allOn.value) selected.value = []
  else selected.value = notes.value.map((item) => item.id)
}

async function deleteSelected() {
  if (busy.value || !selected.value.length) return
  busy.value = 'del'
  try {
    const ids = [...selected.value]
    for (const id of ids) {
      await api.deleteHistory(id)
    }
    const gone = new Set(ids)
    notes.value = notes.value.filter((item) => !gone.has(item.id))
    selected.value = []
    picking.value = false
  } catch (e) {
    failed.value = e instanceof ApiError ? e.message : '删除失败'
  } finally {
    busy.value = ''
  }
}

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
      <div v-if="props.source === 'history'" class="mb-3 flex items-center justify-end gap-3 text-[13px]">
        <template v-if="!picking">
          <button type="button" class="text-[#8a8a8a] hover:text-[#1f1f1f]" @click="startPick">清空浏览记录</button>
        </template>
        <template v-else>
          <button type="button" class="text-[#8a8a8a] hover:text-[#1f1f1f]" @click="cancelPick">取消</button>
          <button type="button" class="text-[#8a8a8a] hover:text-[#1f1f1f]" @click="toggleAll">
            {{ allOn ? '取消全选' : '全选' }}
          </button>
          <button
            type="button"
            class="text-[#ff2442]"
            :disabled="!selected.length || Boolean(busy)"
            @click="deleteSelected"
          >
            {{ busy === 'del' ? '删除中…' : `删除${selected.length ? ` ${selected.length}` : ''}` }}
          </button>
        </template>
      </div>
      <div class="feed-grid flex flex-wrap justify-center gap-4">
        <div v-for="note in notes" :key="note.id" class="relative">
          <div :class="picking ? 'pointer-events-none' : ''">
            <NoteFeedCard :note="note" />
          </div>
          <button
            v-if="picking"
            type="button"
            class="absolute inset-0 z-10 cursor-pointer rounded-2xl"
            :aria-pressed="selected.includes(note.id)"
            :aria-label="selected.includes(note.id) ? '取消选择' : '选择'"
            @click.stop="toggleOne(note.id)"
          >
            <span
              class="absolute top-2.5 left-2.5 flex h-6 w-6 items-center justify-center rounded-full border-2 shadow"
              :class="
                selected.includes(note.id)
                  ? 'border-[#ff2442] bg-[#ff2442] text-white'
                  : 'border-white bg-white/90'
              "
            >
              <svg v-if="selected.includes(note.id)" viewBox="0 0 16 16" width="12" height="12" fill="none" aria-hidden="true">
                <path d="M3.5 8.2 6.4 11l6.1-7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </span>
          </button>
        </div>
      </div>
      <div ref="sentinel" class="feed-more" aria-hidden="true">
        <span v-show="loadingMore" class="feed-spinner is-more" />
      </div>
    </template>
  </div>
</template>
