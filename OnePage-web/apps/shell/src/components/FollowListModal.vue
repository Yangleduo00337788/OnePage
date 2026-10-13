<!-- 关注 / 粉丝列表。GET /users/{pageId}/following|followers；行内可再关注。 -->
<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError, type UserBrief } from '@onepage/api-client'
import { useAuthModal } from '../stores/authModal'
import { useSession } from '../stores/session'
import Icon from './Icon.vue'
import EmptyState from './EmptyState.vue'

const props = defineProps<{
  open: boolean
  pageId: string
  kind: 'following' | 'followers'
}>()

const emit = defineEmits<{ close: [] }>()

const router = useRouter()
const session = useSession()
const auth = useAuthModal()
const items = ref<(UserBrief & { followedByMe?: boolean })[]>([])
const cursor = ref<string | null>(null)
const hasMore = ref(true)
const loading = ref(false)
const loadingMore = ref(false)
const failed = ref('')
const sentinel = ref<HTMLElement | null>(null)
const busyId = ref('')
let io: IntersectionObserver | null = null
let seq = 0

const title = () => (props.kind === 'following' ? '关注' : '粉丝')

function fetchPage(nextCursor?: string) {
  if (props.kind === 'following') return api.userFollowing(props.pageId, nextCursor, 20)
  return api.userFollowers(props.pageId, nextCursor, 20)
}

async function refresh() {
  if (!props.pageId) return
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

async function toggle(user: UserBrief) {
  if (session.me?.pageId === user.pageId) return
  if (!session.me) {
    auth.show('login', '/me')
    return
  }
  busyId.value = user.pageId
  try {
    if (user.followedByMe) {
      await api.unfollowUser(user.pageId)
      user.followedByMe = false
      session.bumpFollowing(-1)
    } else {
      await api.followUser(user.pageId)
      user.followedByMe = true
      session.bumpFollowing(1)
    }
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) auth.show('login')
  } finally {
    busyId.value = ''
  }
}

function openProfile(pageId: string) {
  emit('close')
  if (session.me?.pageId === pageId) void router.push('/me')
  else void router.push(`/u/${encodeURIComponent(pageId)}`)
}

watch(
  () => [props.open, props.pageId, props.kind] as const,
  ([open]) => {
    if (open) void refresh()
  },
)

watch([items, loading], async () => {
  await nextTick()
  if (!io || !sentinel.value) return
  io.disconnect()
  io.observe(sentinel.value)
})

watch(
  () => props.open,
  (open) => {
    if (open) {
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
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center bg-black/45 px-4" @click.self="emit('close')">
      <div class="flex max-h-[min(560px,80vh)] w-[400px] max-w-[calc(100vw-32px)] flex-col rounded-[20px] bg-white shadow-[0_20px_60px_rgba(0,0,0,0.16)]">
        <div class="relative border-b border-[#ececec] px-5 py-4">
          <h2 class="text-center text-[16px] font-semibold">{{ title() }}</h2>
          <button type="button" class="absolute top-3 right-3 flex h-8 w-8 items-center justify-center rounded-full hover:bg-[#f2f2f2]" aria-label="关闭" @click="emit('close')">
            <Icon name="x" :size="16" />
          </button>
        </div>
        <div class="min-h-0 flex-1 overflow-y-auto px-2 py-2">
          <p v-if="loading" class="py-10 text-center text-[14px] text-[#8a8a8a]">加载中…</p>
          <p v-else-if="failed" class="py-10 text-center text-[14px] text-[#8a8a8a]">{{ failed }}</p>
          <EmptyState v-else-if="!items.length" compact src="/empty-people.png" title="还没有人" />
          <button
            v-for="user in items"
            :key="user.userId"
            type="button"
            class="flex w-full items-center gap-3 rounded-[16px] px-3 py-2.5 text-left hover:bg-[#f7f7f7]"
            @click="openProfile(user.pageId)"
          >
            <img v-if="user.avatarUrl" :src="user.avatarUrl" alt="" class="h-11 w-11 rounded-full object-cover" />
            <span v-else class="flex h-11 w-11 items-center justify-center rounded-full bg-[#f2f2f2] text-[14px] text-[#8a8a8a]">
              {{ (user.nickname || '一').slice(0, 1) }}
            </span>
            <span class="min-w-0 flex-1">
              <span class="block truncate text-[15px] font-medium text-[#1f1f1f]">{{ user.nickname }}</span>
              <span class="mt-0.5 block truncate text-[12px] text-[#8a8a8a]">{{ user.pageId }}</span>
            </span>
            <span
              v-if="session.me && session.me.pageId !== user.pageId"
              class="follow-mini"
              :class="{ 'is-on': user.followedByMe }"
              @click.stop="toggle(user)"
            >
              {{ busyId === user.pageId ? '…' : user.followedByMe ? '已关注' : '关注' }}
            </span>
          </button>
          <div ref="sentinel" class="h-4" aria-hidden="true" />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.follow-mini {
  flex-shrink: 0;
  height: 28px;
  padding: 0 12px;
  border-radius: 20px;
  background: #ff2442;
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  line-height: 28px;
}
.follow-mini.is-on {
  background: #f2f2f2;
  color: #555;
}
</style>
