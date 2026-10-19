<!-- 获赞 / 被收藏。GET /users/me/likes-received | favorites-received。仅作者。 -->
<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError, type ReceivedItem } from '@onepage/api-client'
import { noteFromPost } from '../data/home'
import { useNoteDetail } from '../stores/noteDetail'
import { useSession } from '../stores/session'
import Icon from './Icon.vue'
import IdentityBadges from './IdentityBadges.vue'
import EmptyState from './EmptyState.vue'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{ close: [] }>()

const TABS = ['获赞', '被收藏'] as const
type Tab = (typeof TABS)[number]

const router = useRouter()
const session = useSession()
const detail = useNoteDetail()
const tab = ref<Tab>('获赞')
const items = ref<ReceivedItem[]>([])
const cursor = ref<string | null>(null)
const hasMore = ref(true)
const loading = ref(false)
const loadingMore = ref(false)
const failed = ref('')
const sentinel = ref<HTMLElement | null>(null)
let io: IntersectionObserver | null = null
let seq = 0

function fetchPage(nextCursor?: string) {
  if (tab.value === '获赞') return api.likesReceived(nextCursor, 20)
  return api.favoritesReceived(nextCursor, 20)
}

async function refresh() {
  const token = ++seq
  loading.value = true
  loadingMore.value = false
  failed.value = ''
  cursor.value = null
  hasMore.value = true
  try {
    const page = await fetchPage()
    if (token !== seq) return
    items.value = page.items
    cursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } catch (e) {
    if (token !== seq) return
    items.value = []
    failed.value = e instanceof ApiError ? e.message : '加载失败'
  } finally {
    if (token === seq) loading.value = false
  }
}

async function loadMore() {
  if (loading.value || loadingMore.value || !hasMore.value) return
  const token = seq
  loadingMore.value = true
  try {
    const page = await fetchPage(cursor.value || undefined)
    if (token !== seq) return
    items.value = items.value.concat(page.items)
    cursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } catch {
    if (token === seq) hasMore.value = false
  } finally {
    if (token === seq) loadingMore.value = false
  }
}

function openProfile(pageId: string) {
  emit('close')
  if (session.me?.pageId === pageId) void router.push('/me')
  else void router.push(`/u/${encodeURIComponent(pageId)}`)
}

function openPost(item: ReceivedItem) {
  if (!item.postId) return
  emit('close')
  detail.show(
    noteFromPost({
      postId: item.postId,
      title: item.postTitle || '笔记',
      coverUrl: '',
      postType: 'image',
      likeCount: 0,
      author: {
        nickname: item.actor.nickname,
        pageId: item.actor.pageId,
        avatarUrl: item.actor.avatarUrl,
      },
    }),
  )
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      tab.value = '获赞'
      void refresh()
      io = new IntersectionObserver(
        (entries) => {
          if (entries.some((e) => e.isIntersecting)) void loadMore()
        },
        { rootMargin: '240px 0px' },
      )
    } else {
      io?.disconnect()
      io = null
    }
  },
)

watch(tab, () => {
  if (props.open) void refresh()
})

watch([items, loading], async () => {
  await nextTick()
  if (!io || !sentinel.value) return
  io.disconnect()
  io.observe(sentinel.value)
})
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center bg-black/45 px-4" @click.self="emit('close')">
      <div class="flex max-h-[min(560px,80vh)] w-[400px] max-w-[calc(100vw-32px)] flex-col rounded-[20px] bg-[var(--panel)] shadow-[0_20px_60px_rgba(0,0,0,0.16)]">
        <div class="relative border-b border-[#ececec] px-5 py-4">
          <h2 class="text-center text-[16px] font-semibold">获赞与收藏</h2>
          <button type="button" class="absolute top-3 right-3 flex h-8 w-8 items-center justify-center rounded-full hover:bg-[var(--hover)]" aria-label="关闭" @click="emit('close')">
            <Icon name="x" :size="16" />
          </button>
          <div class="mt-3 flex justify-center gap-2">
            <button
              v-for="item in TABS"
              :key="item"
              type="button"
              class="me-tab"
              :class="{ 'is-on': tab === item }"
              @click="tab = item"
            >
              {{ item }}
            </button>
          </div>
        </div>
        <div class="min-h-0 flex-1 overflow-y-auto px-2 py-2">
          <p v-if="loading" class="py-10 text-center text-[14px] text-[#8a8a8a]">加载中…</p>
          <p v-else-if="failed" class="py-10 text-center text-[14px] text-[#8a8a8a]">{{ failed }}</p>
          <EmptyState
            v-else-if="!items.length"
            compact
            src="/empty-people.png"
            :title="tab === '获赞' ? '还没有获赞' : '还没有被收藏'"
          />
          <div
            v-for="item in items"
            :key="`${item.actor.userId}-${item.postId}-${item.createdAt}`"
            class="flex w-full items-center gap-3 rounded-[16px] px-3 py-2.5"
          >
            <button type="button" class="flex min-w-0 flex-1 items-center gap-3 text-left" @click="openProfile(item.actor.pageId)">
              <span class="relative h-11 w-11 shrink-0">
                <img v-if="item.actor.avatarUrl" :src="item.actor.avatarUrl" alt="" class="h-11 w-11 rounded-full object-cover" />
                <span v-else class="flex h-11 w-11 items-center justify-center rounded-full bg-[var(--hover)] text-[14px] text-[#8a8a8a]">
                  {{ (item.actor.nickname || '一').slice(0, 1) }}
                </span>
                <IdentityBadges size="md" :live-host="item.actor.liveHost" :verified="item.actor.verified" />
              </span>
              <span class="min-w-0 flex-1">
                <span class="block truncate text-[15px] font-medium text-[var(--ink)]">{{ item.actor.nickname }}</span>
                <span class="mt-0.5 block truncate text-[12px] text-[#8a8a8a]">{{ item.actor.pageId }}</span>
              </span>
            </button>
            <button
              type="button"
              class="max-w-[120px] shrink-0 truncate text-right text-[12px] text-[#8a8a8a] hover:text-[#ff2442]"
              @click="openPost(item)"
            >
              {{ item.postTitle || '笔记' }}
            </button>
          </div>
          <div ref="sentinel" class="h-4" aria-hidden="true" />
        </div>
      </div>
    </div>
  </Teleport>
</template>
