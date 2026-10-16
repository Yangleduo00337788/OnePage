<!--
  通知中心。Tab 对应 GET /notifications?group=comment|reaction|follow|review。
  未登录弹登录。点关注类进主页；有 postId 的打开笔记详情。
-->
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError, type NotificationItem } from '@onepage/api-client'
import { useAuthModal } from '../stores/authModal'
import { useNoteDetail } from '../stores/noteDetail'
import { useSession } from '../stores/session'
import { noteFromPost } from '../data/home'
import EmptyState from '../components/EmptyState.vue'

const NOTIFY_TABS = ['评论和@', '点赞和收藏', '新增关注', '审核'] as const
type NotifyTab = (typeof NOTIFY_TABS)[number]

const GROUP: Record<NotifyTab, string> = {
  '评论和@': 'comment',
  点赞和收藏: 'reaction',
  新增关注: 'follow',
  审核: 'review',
}

function tabUnread(item: NotifyTab) {
  const g = GROUP[item]
  if (g === 'comment') return session.unreadNotifyGroups.comment
  if (g === 'reaction') return session.unreadNotifyGroups.reaction
  if (g === 'review') return session.unreadNotifyGroups.review
  return session.unreadNotifyGroups.follow
}

const ACTION: Record<string, string> = {
  comment: '评论了你的笔记',
  reply: '回复了你的评论',
  mention: '提到了你',
  like_post: '赞了你的笔记',
  favorite_post: '收藏了你的笔记',
  like_comment: '赞了你的评论',
  follow: '关注了你',
  review_reject: '未通过你的笔记',
}

const route = useRoute()
const router = useRouter()
const session = useSession()
const auth = useAuthModal()
const detail = useNoteDetail()

const items = ref<NotificationItem[]>([])
const cursor = ref<string | null>(null)
const hasMore = ref(true)
const loading = ref(false)
const loadingMore = ref(false)
const failed = ref('')
const sentinel = ref<HTMLElement | null>(null)
let io: IntersectionObserver | null = null
let seq = 0

const tab = computed({
  get: () => {
    const t = route.query.tab
    if (typeof t === 'string' && (NOTIFY_TABS as readonly string[]).includes(t)) return t as NotifyTab
    return '评论和@'
  },
  set: (value: NotifyTab) => {
    router.replace({ query: value === '评论和@' ? {} : { tab: value } })
  },
})

function timeText(raw: string) {
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw
  return d.toLocaleString()
}

async function refresh() {
  const token = ++seq
  loading.value = true
  loadingMore.value = false
  failed.value = ''
  cursor.value = null
  hasMore.value = true
  try {
    const page = await api.notifications(GROUP[tab.value], undefined, 20)
    if (token !== seq) return
    items.value = page.items
    cursor.value = page.nextCursor
    hasMore.value = page.hasMore
    const ids = page.items.filter((n) => !n.read).map((n) => n.id)
    if (ids.length) {
      await api.markNotificationsRead({ ids })
      if (token !== seq) return
      items.value = items.value.map((n) => (ids.includes(n.id) ? { ...n, read: true } : n))
      await session.refreshUnread()
    }
  } catch (e) {
    if (token !== seq) return
    items.value = []
    if (e instanceof ApiError && e.status === 401) {
      auth.show('login', '/notify')
      failed.value = ''
      return
    }
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
    const page = await api.notifications(GROUP[tab.value], cursor.value || undefined, 20)
    if (token !== seq) return
    items.value = items.value.concat(page.items)
    cursor.value = page.nextCursor
    hasMore.value = page.hasMore
    const ids = page.items.filter((n) => !n.read).map((n) => n.id)
    if (ids.length) {
      await api.markNotificationsRead({ ids })
      if (token !== seq) return
      items.value = items.value.map((n) => (ids.includes(n.id) ? { ...n, read: true } : n))
      await session.refreshUnread()
    }
  } catch {
    if (token === seq) hasMore.value = false
  } finally {
    if (token === seq) loadingMore.value = false
  }
}

async function openItem(row: NotificationItem) {
  if (row.postId) {
    try {
      const post = await api.post(row.postId)
      detail.show(noteFromPost(post))
      return
    } catch {
      /* 帖子不可见则退到主页 */
    }
  }
  if (row.actor?.pageId) {
    await router.push(`/u/${encodeURIComponent(row.actor.pageId)}`)
  }
}

onMounted(async () => {
  await session.refresh()
  if (!session.me) {
    auth.show('login', '/notify')
    await router.replace('/')
    return
  }
  io = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) void loadMore()
    },
    { rootMargin: '240px 0px' },
  )
  await refresh()
})

onUnmounted(() => {
  io?.disconnect()
  io = null
})

watch(tab, () => {
  void refresh()
})

watch([items, loading], async () => {
  await nextTick()
  if (!io || !sentinel.value) return
  io.disconnect()
  io.observe(sentinel.value)
})
</script>

<template>
  <section class="mx-auto max-w-[720px]">
    <div class="mb-6 flex flex-wrap items-center justify-center gap-x-10 gap-y-2">
      <button
        v-for="item in NOTIFY_TABS"
        :key="item"
        type="button"
        class="feed-tab"
        :class="tab === item ? 'is-on' : ''"
        @click="tab = item"
      >
        <span class="inline-flex items-center gap-1.5">
          {{ item }}
          <span
            v-if="tabUnread(item) > 0"
            class="min-w-[16px] rounded-full bg-[#ff2442] px-1 py-0.5 text-center text-[10px] font-medium leading-none text-white"
          >
            {{ tabUnread(item) > 99 ? '99+' : tabUnread(item) }}
          </span>
        </span>
      </button>
    </div>
    <p v-if="loading" class="py-20 text-center text-[13px] text-[#8a8a8a]">加载中…</p>
    <p v-else-if="failed" class="py-20 text-center text-[13px] text-[#8a8a8a]">{{ failed }}</p>
    <EmptyState v-else-if="!items.length" src="/empty-notify.png" title="暂时还没有这类通知" />
    <div v-else class="space-y-1">
      <button
        v-for="row in items"
        :key="row.id"
        type="button"
        class="flex w-full items-center gap-3 rounded-[16px] px-2 py-2.5 text-left hover:bg-[#f7f7f7]"
        @click="openItem(row)"
      >
        <span class="relative shrink-0">
          <img
            v-if="row.actor?.avatarUrl"
            :src="row.actor.avatarUrl"
            alt=""
            class="h-11 w-11 rounded-full object-cover"
          />
          <span
            v-else
            class="flex h-11 w-11 items-center justify-center rounded-full bg-[#f2f2f2] text-[14px] text-[#8a8a8a]"
          >
            {{ (row.actor?.nickname || '一').slice(0, 1) }}
          </span>
          <span v-if="!row.read" class="absolute top-0 right-0 h-2 w-2 rounded-full bg-[#ff2442]" />
        </span>
        <span class="min-w-0 flex-1">
          <span class="block truncate text-[15px] font-medium text-[#1f1f1f]">
            {{ row.actor?.nickname || '用户' }}
            <span class="ml-1 font-normal text-[#8a8a8a]">{{ ACTION[row.type] || row.type }}</span>
          </span>
          <span v-if="row.preview" class="mt-0.5 block truncate text-[13px] text-[#333]">{{ row.preview }}</span>
          <span class="mt-0.5 block truncate text-[12px] text-[#8a8a8a]">{{ timeText(row.createdAt) }}</span>
        </span>
      </button>
      <div ref="sentinel" class="h-4" aria-hidden="true" />
    </div>
  </section>
</template>
