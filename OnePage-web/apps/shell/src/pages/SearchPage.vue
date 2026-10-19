<!--
  搜索结果。笔记/用户/话题/直播走 GET /search（ES）。空关键词不请求。
-->
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError, type LiveSession, type SearchTopicHit, type SearchUserHit } from '@onepage/api-client'
import { noteFromPost, type NoteCard } from '../data/home'
import Icon from '../components/Icon.vue'
import EmptyState from '../components/EmptyState.vue'
import NoteFeedCard from '../components/NoteFeedCard.vue'
import LiveFeedCard from '../components/LiveFeedCard.vue'
import IdentityBadges from '../components/IdentityBadges.vue'

const route = useRoute()
const router = useRouter()
const filterOpen = ref(false)

const SORTS = ['最新', '最热', '最多点赞', '最多评论', '最多收藏'] as const
const TYPES = ['图文', '视频'] as const
const TIMES = ['不限', '周内', '月内', '半年内'] as const
const SCOPES = ['不限', '关注', '已看', '未看'] as const

const filters = reactive({
  sort: '最新' as (typeof SORTS)[number],
  types: [] as (typeof TYPES)[number][],
  time: '不限' as (typeof TIMES)[number],
  scope: '不限' as (typeof SCOPES)[number],
})

function resetFilters() {
  filters.sort = '最新'
  filters.types = []
  filters.time = '不限'
  filters.scope = '不限'
  closeFilter()
}

function toggleType(item: (typeof TYPES)[number]) {
  const i = filters.types.indexOf(item)
  if (i >= 0) filters.types.splice(i, 1)
  else filters.types.push(item)
}

const q = computed(() => (typeof route.query.q === 'string' && route.query.q.trim() ? route.query.q.trim() : ''))
const tab = computed({
  get: () => (typeof route.query.tab === 'string' ? route.query.tab : '笔记'),
  set: (value: string) => {
    router.replace({ query: { ...route.query, tab: value } })
  },
})

const TABS = ['笔记', '用户', '话题', '直播'] as const

const notes = ref<NoteCard[]>([])
const users = ref<SearchUserHit[]>([])
const topics = ref<SearchTopicHit[]>([])
const lives = ref<LiveSession[]>([])
const cursor = ref<string | null>(null)
const hasMore = ref(false)
const loading = ref(false)
const loadingMore = ref(false)
const failed = ref('')
const sentinel = ref<HTMLElement | null>(null)
let io: IntersectionObserver | null = null
let seq = 0

function searchType() {
  if (tab.value === '用户') return 'user' as const
  if (tab.value === '笔记') return 'post' as const
  if (tab.value === '话题') return 'topic' as const
  if (tab.value === '直播') return 'live' as const
  return null
}

function extra() {
  const types: string[] = []
  if (filters.types.includes('图文')) types.push('image')
  if (filters.types.includes('视频')) types.push('video')
  const sortMap: Record<(typeof SORTS)[number], string> = {
    最新: 'latest',
    最热: 'hot',
    最多点赞: 'likes',
    最多评论: 'comments',
    最多收藏: 'favorites',
  }
  const timeMap: Record<(typeof TIMES)[number], string> = {
    不限: '',
    周内: 'week',
    月内: 'month',
    半年内: 'half',
  }
  const scopeMap: Record<(typeof SCOPES)[number], string> = {
    不限: '',
    关注: 'following',
    已看: 'seen',
    未看: 'unseen',
  }
  return {
    postType: types.join(',') || undefined,
    sort: sortMap[filters.sort],
    time: timeMap[filters.time] || undefined,
    scope: scopeMap[filters.scope] || undefined,
  }
}

async function refresh() {
  const token = ++seq
  notes.value = []
  users.value = []
  topics.value = []
  lives.value = []
  cursor.value = null
  hasMore.value = false
  failed.value = ''
  if (!q.value) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const kind = searchType()
    if (!kind) return
    const page = await api.search(q.value, kind, undefined, 20, extra())
    if (token !== seq) return
    notes.value = (page.posts || []).map(noteFromPost)
    users.value = page.users || []
    topics.value = page.topics || []
    lives.value = page.lives || []
    cursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } catch (e) {
    if (token !== seq) return
    failed.value = e instanceof ApiError ? e.message : '搜索失败'
  } finally {
    if (token === seq) loading.value = false
  }
}

async function loadMore() {
  const kind = searchType()
  if (!kind || loading.value || loadingMore.value || !hasMore.value || !q.value) return
  const token = seq
  loadingMore.value = true
  try {
    const page = await api.search(q.value, kind, cursor.value || undefined, 20, extra())
    if (token !== seq) return
    notes.value = notes.value.concat((page.posts || []).map(noteFromPost))
    users.value = users.value.concat(page.users || [])
    topics.value = topics.value.concat(page.topics || [])
    lives.value = lives.value.concat(page.lives || [])
    cursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } catch {
    if (token === seq) hasMore.value = false
  } finally {
    if (token === seq) loadingMore.value = false
  }
}

watch(
  () => [q.value, tab.value, filters.sort, filters.types.join('|'), filters.time, filters.scope] as const,
  () => {
    void refresh()
  },
)

function openTopic(name: string) {
  void router.replace({ query: { ...route.query, q: name, tab: '笔记' } })
}

function openLive(sessionId: string) {
  void router.push(`/live/${encodeURIComponent(sessionId)}`)
}

const emptyHint = computed(() => {
  if (tab.value === '用户') return '还没有匹配的用户'
  if (tab.value === '话题') return q.value ? `没有找到与「${q.value}」相关的话题` : '输入关键词开始搜索'
  if (tab.value === '直播') return q.value ? `没有找到与「${q.value}」相关的直播` : '输入关键词开始搜索'
  return q.value ? `没有找到与「${q.value}」相关的笔记` : '输入关键词开始搜索'
})

const emptyArt = computed(() => {
  if (tab.value === '用户') return '/empty-people.png'
  if (tab.value === '直播') return '/empty-live.png'
  return '/empty-search.png'
})

function openYehyeh(topic = q.value) {
  router.push({ path: '/yehyeh', query: topic ? { q: topic } : {} })
}

let filterLeaveTimer = 0

function onFilterEnter() {
  window.clearTimeout(filterLeaveTimer)
  filterOpen.value = true
}

function onFilterLeave() {
  filterLeaveTimer = window.setTimeout(() => {
    filterOpen.value = false
  }, 120)
}

function closeFilter() {
  window.clearTimeout(filterLeaveTimer)
  filterOpen.value = false
}

function openUser(pageId: string) {
  void router.push(`/u/${encodeURIComponent(pageId)}`)
}

onMounted(() => {
  void refresh()
  io = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) void loadMore()
    },
    { root: null, rootMargin: '480px 0px' },
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
    <div class="relative z-20 mb-8 flex items-center gap-8 overflow-visible text-[16px] text-[#8a8a8a]">
      <button
        v-for="item in TABS"
        :key="item"
        type="button"
        class="pb-1.5"
        :class="tab === item ? 'text-[18px] font-semibold text-[var(--ink)] shadow-[inset_0_-2px_0_#ff2442]' : 'hover:text-[var(--ink)]'"
        @click="tab = item"
      >
        {{ item }}
      </button>
      <button type="button" class="inline-flex items-center gap-1 pb-1.5 hover:text-[var(--ink)]" @click="openYehyeh()">
        问耶耶
        <span class="text-[11px] font-medium text-[#ff2442]">AI</span>
      </button>
      <div class="relative ml-auto shrink-0" @mouseenter="onFilterEnter" @mouseleave="onFilterLeave">
        <button type="button" class="filter-pill" :class="filterOpen ? 'is-on' : ''">
          <Icon name="sliders" :size="16" />
          筛选
        </button>
        <div v-show="filterOpen" class="filter-flyout">
          <div class="filter-panel">
          <section class="mb-5">
            <h3 class="filter-label">排序规则</h3>
            <div class="grid grid-cols-3 gap-2">
              <button
                v-for="item in SORTS"
                :key="item"
                type="button"
                class="filter-chip"
                :class="filters.sort === item ? 'is-on' : ''"
                @click="filters.sort = item"
              >
                {{ item }}
              </button>
            </div>
          </section>
          <section class="mb-5">
            <h3 class="filter-label">笔记类型</h3>
            <div class="grid grid-cols-2 gap-2">
              <button
                v-for="item in TYPES"
                :key="item"
                type="button"
                class="filter-chip"
                :class="filters.types.includes(item) ? 'is-on' : ''"
                @click="toggleType(item)"
              >
                {{ item }}
              </button>
            </div>
          </section>
          <section class="mb-5">
            <h3 class="filter-label">发布时间</h3>
            <div class="grid grid-cols-4 gap-2">
              <button
                v-for="item in TIMES"
                :key="item"
                type="button"
                class="filter-chip"
                :class="filters.time === item ? 'is-on' : ''"
                @click="filters.time = item"
              >
                {{ item }}
              </button>
            </div>
          </section>
          <section class="mb-5">
            <h3 class="filter-label">搜索范围</h3>
            <div class="grid grid-cols-4 gap-2">
              <button
                v-for="item in SCOPES"
                :key="item"
                type="button"
                class="filter-chip"
                :class="filters.scope === item ? 'is-on' : ''"
                @click="filters.scope = item"
              >
                {{ item }}
              </button>
            </div>
          </section>
          <div class="grid grid-cols-2 gap-3">
            <button type="button" class="filter-chip is-wide" @click="resetFilters">重置</button>
            <button type="button" class="filter-chip is-wide" @click="closeFilter">收起</button>
          </div>
          </div>
        </div>
      </div>
    </div>

    <div class="flex items-start gap-8">
      <div class="min-w-0 flex-1">
        <p v-if="loading" class="py-16 text-center text-[14px] text-[#8a8a8a]">搜索中…</p>
        <p v-else-if="failed" class="py-16 text-center text-[14px] text-[#8a8a8a]">{{ failed }}</p>
        <div v-else-if="tab === '用户' && users.length" class="divide-y divide-[var(--line)]">
          <button
            v-for="user in users"
            :key="user.userId"
            type="button"
            class="flex w-full items-center gap-3 py-3 text-left"
            @click="openUser(user.pageId)"
          >
            <span class="relative h-12 w-12 shrink-0">
              <img
                v-if="user.avatarUrl"
                :src="user.avatarUrl"
                alt=""
                class="h-12 w-12 rounded-full object-cover"
              />
              <span v-else class="block h-12 w-12 rounded-full bg-[var(--fill)]" />
              <IdentityBadges size="md" :live-host="user.liveHost" :verified="user.verified" />
            </span>
            <span class="min-w-0 flex-1">
              <span class="block truncate text-[15px] font-medium text-[var(--ink)]">{{ user.nickname }}</span>
              <span class="mt-0.5 block truncate text-[13px] text-[#8a8a8a]">
                {{ user.bio?.trim() || `一页号 ${user.pageId}` }}
              </span>
            </span>
          </button>
        </div>
        <div v-else-if="tab === '笔记' && notes.length" class="feed-grid flex flex-wrap justify-center gap-4">
          <NoteFeedCard v-for="note in notes" :key="note.id" :note="note" />
        </div>
        <div v-else-if="tab === '话题' && topics.length" class="flex flex-wrap gap-2 py-2">
          <button
            v-for="item in topics"
            :key="item.topicId"
            type="button"
            class="rounded-[20px] bg-[var(--fill)] px-4 py-2 text-[14px] text-[var(--ink)] hover:bg-[var(--hover)]"
            @click="openTopic(item.name)"
          >
            #{{ item.name }}
          </button>
        </div>
        <div v-else-if="tab === '直播' && lives.length" class="feed-grid flex flex-wrap justify-center gap-4">
          <LiveFeedCard
            v-for="item in lives"
            :key="item.sessionId"
            :item="item"
            @click="openLive(item.sessionId)"
          />
        </div>
        <EmptyState v-else :src="emptyArt" :title="emptyHint" />
        <div ref="sentinel" class="feed-more" aria-hidden="true">
          <span v-show="loadingMore" class="feed-spinner is-more" />
        </div>
      </div>

      <aside class="sticky top-[84px] hidden h-[calc(100vh-108px)] w-[400px] shrink-0 flex-col lg:flex">
        <div class="flex min-h-0 flex-1 flex-col overflow-y-auto rounded-2xl bg-[var(--fill)] p-5">
          <div class="flex items-center gap-2">
            <span class="flex h-7 w-7 items-center justify-center rounded-full bg-[#ff2442] text-white">
              <Icon name="spark" :size="14" :stroke="2" />
            </span>
            <span class="text-[15px] font-semibold">耶耶</span>
            <span class="rounded-full bg-[var(--panel)] px-2 py-0.5 text-[11px] text-[var(--muted)] ring-1 ring-[var(--line)]">AI</span>
          </div>
          <p class="mt-3 text-[14px] leading-6 text-[var(--muted)]">
            根据「{{ q || '你感兴趣的内容' }}」去问耶耶，耶耶会先检索站内笔记再回答。
          </p>
        </div>
        <button
          type="button"
          class="mt-4 flex w-full items-center justify-center gap-2 rounded-[20px] bg-[#ff2442] py-2.5 text-[14px] text-white"
          @click="openYehyeh()"
        >
          <Icon name="spark" :size="16" />
          问耶耶
          <span class="rounded-full bg-white/20 px-1.5 py-px text-[10px]">AI</span>
        </button>
      </aside>
    </div>
  </section>
</template>
