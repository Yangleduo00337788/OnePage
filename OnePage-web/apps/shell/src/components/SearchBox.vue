<!--
  顶栏搜索。首页/搜索页出完整输入条；其它页先出图标，点开再输入。
  空关键词：正在直播走 /live/sessions，话题走目录；有字走 GET /search?type=all。
  提交后跳 /search?q=，历史存在 localStorage。
-->
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, type LiveSession, type SearchTopicHit } from '@onepage/api-client'
import Icon from './Icon.vue'

const props = defineProps<{
  /** 非首页：只出搜索图标，点开才展开输入条 */
  compact?: boolean
  /** 「我的」/他人主页：与编辑、退出登录同一套白字胶囊 */
  hero?: boolean
}>()

const shellClass = computed(() => {
  if (props.hero && props.compact && !expanded.value) return ''
  if (props.hero && props.compact && expanded.value) {
    return 'absolute right-0 top-0 z-40 w-[min(560px,calc(100vw-292px))]'
  }
  if (props.compact && !expanded.value) return 'w-9'
  return 'w-full max-w-[720px]'
})

const HISTORY_KEY = 'onepage.search.history'
const router = useRouter()
const route = useRoute()
const open = ref(false)
const expanded = ref(false)
const keyword = ref('')
const history = ref<string[]>([])
const root = ref<HTMLElement | null>(null)
const inputEl = ref<HTMLInputElement | null>(null)
const liveHits = ref<LiveSession[]>([])
const topicHits = ref<SearchTopicHit[]>([])
let suggestTimer = 0
let suggestSeq = 0

function loadHistory() {
  try {
    const raw = localStorage.getItem(HISTORY_KEY)
    const parsed = raw ? (JSON.parse(raw) as unknown) : null
    history.value = Array.isArray(parsed) ? parsed.map(String) : []
  } catch {
    history.value = []
  }
}

function saveHistory() {
  localStorage.setItem(HISTORY_KEY, JSON.stringify(history.value))
}

function remember(term: string) {
  const t = term.trim()
  if (!t) return
  history.value = [t, ...history.value.filter((item) => item !== t)].slice(0, 8)
  saveHistory()
}

function search(term = keyword.value) {
  const t = term.trim()
  if (!t) return
  keyword.value = t
  remember(t)
  open.value = false
  expanded.value = false
  router.push({ path: '/search', query: { q: t } })
}

async function expand() {
  expanded.value = true
  open.value = true
  await nextTick()
  inputEl.value?.focus()
}

function collapse() {
  open.value = false
  if (props.compact) expanded.value = false
}

function goLive(sessionId: string) {
  open.value = false
  expanded.value = false
  router.push(`/live/${encodeURIComponent(sessionId)}`)
}

async function loadSuggest() {
  const token = ++suggestSeq
  const q = keyword.value.trim()
  try {
    if (!q) {
      const [livePage, topics] = await Promise.all([api.liveSessions(), api.suggestTopics()])
      if (token !== suggestSeq) return
      liveHits.value = (livePage.items || []).slice(0, 5)
      topicHits.value = (topics || []).slice(0, 8).map((item) => ({
        topicId: item.topicId,
        name: item.name,
        official: item.official,
        sortOrder: item.sortOrder,
      }))
      return
    }
    const page = await api.search(q, 'all', undefined, 8)
    if (token !== suggestSeq) return
    liveHits.value = (page.lives || []).slice(0, 5)
    topicHits.value = (page.topics || []).slice(0, 8)
  } catch {
    if (token !== suggestSeq) return
    liveHits.value = []
    topicHits.value = []
  }
}

function scheduleSuggest() {
  window.clearTimeout(suggestTimer)
  suggestTimer = window.setTimeout(() => {
    void loadSuggest()
  }, 280)
}

watch(
  () => route.query.q,
  (q) => {
    if (typeof q === 'string') keyword.value = q
  },
  { immediate: true },
)

watch(
  () => [route.path, props.compact] as const,
  () => {
    if (props.compact) {
      open.value = false
      expanded.value = false
    }
  },
)

watch(
  () => [keyword.value, open.value] as const,
  ([, isOpen]) => {
    if (isOpen) scheduleSuggest()
  },
)

function removeHistory(term: string) {
  history.value = history.value.filter((item) => item !== term)
  saveHistory()
}

function clearHistory() {
  history.value = []
  localStorage.setItem(HISTORY_KEY, '[]')
}

function onDocPointer(e: PointerEvent) {
  if (!root.value) return
  if (root.value.contains(e.target as Node)) return
  collapse()
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') collapse()
}

onMounted(() => {
  loadHistory()
  document.addEventListener('pointerdown', onDocPointer)
  document.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  window.clearTimeout(suggestTimer)
  document.removeEventListener('pointerdown', onDocPointer)
  document.removeEventListener('keydown', onKey)
})
</script>

<template>
  <div
    ref="root"
    class="relative"
    :class="shellClass"
    @click.stop
  >
    <button
      v-if="props.compact && !expanded"
      type="button"
      :class="props.hero ? 'me-action' : 'search-fab'"
      aria-label="搜索"
      @click="expand"
    >
      <Icon name="search" :size="props.hero ? 14 : 18" :stroke="1.8" />
      <span v-if="props.hero">搜索</span>
    </button>
    <label
      v-else
      class="search-field flex h-11 w-full items-center rounded-full px-4 text-sm"
      :class="open ? 'is-open' : ''"
    >
      <Icon name="search" :size="16" class="text-[#bbb]" />
      <input
        ref="inputEl"
        v-model="keyword"
        class="ml-2 h-full w-full bg-transparent outline-none placeholder:text-[#c0c0c0]"
        placeholder="搜索你感兴趣的内容、用户或话题..."
        @focus="open = true"
        @keydown.enter.prevent="search()"
      />
    </label>

    <div
      v-if="open"
      class="search-panel absolute top-[calc(100%+8px)] right-0 left-0 z-30 overflow-hidden rounded-2xl p-5"
    >
      <div class="mb-3 flex items-center justify-between">
        <h3 class="text-[15px] font-semibold">历史记录</h3>
        <button v-if="history.length" type="button" class="text-[12px] text-[#8a8a8a] hover:text-[var(--ink)]" @click="clearHistory">
          清空
        </button>
      </div>
      <ul v-if="history.length" class="flex flex-wrap gap-2">
        <li v-for="item in history" :key="item">
          <span class="inline-flex items-center gap-1.5 rounded-full bg-[var(--fill)] py-1 pr-2 pl-2.5 text-[13px] text-[var(--ink)]">
            <Icon name="clock" :size="13" class="text-[#bbb]" />
            <button type="button" class="max-w-[180px] truncate" @click="search(item)">{{ item }}</button>
            <button type="button" class="text-[#ccc] hover:text-[#666]" aria-label="删除" @click="removeHistory(item)">
              <Icon name="x" :size="12" />
            </button>
          </span>
        </li>
      </ul>
      <p v-else class="text-[13px] text-[#bbb]">暂无搜索记录</p>

      <div class="mt-5 grid grid-cols-2 items-stretch gap-6">
        <section class="flex min-h-0 flex-col">
          <div class="mb-3 flex items-center justify-between">
            <h3 class="text-[15px] font-semibold">正在直播</h3>
            <button type="button" class="flex items-center text-[12px] text-[#8a8a8a]" @click="open = false; router.push('/live')">
              查看更多 <Icon name="chevron-right" :size="14" />
            </button>
          </div>
          <ul v-if="liveHits.length" class="space-y-2">
            <li v-for="item in liveHits" :key="item.sessionId">
              <button
                type="button"
                class="flex w-full items-center gap-2 text-left text-[13px] text-[var(--ink)] hover:text-[#ff2442]"
                @click="goLive(item.sessionId)"
              >
                <img
                  v-if="item.coverUrl"
                  :src="item.coverUrl"
                  alt=""
                  class="h-8 w-8 shrink-0 rounded object-cover"
                />
                <span class="truncate">{{ item.title }}</span>
              </button>
            </li>
          </ul>
          <p v-else class="text-[13px] text-[#bbb]">暂无直播</p>
        </section>

        <section class="flex min-h-0 flex-col">
          <div class="mb-3 flex items-center justify-between">
            <h3 class="text-[15px] font-semibold">热门话题</h3>
          </div>
          <ul v-if="topicHits.length" class="flex flex-wrap gap-2">
            <li v-for="item in topicHits" :key="item.topicId">
              <button
                type="button"
                class="rounded-full bg-[var(--fill)] px-2.5 py-1 text-[13px] text-[var(--ink)] hover:bg-[var(--hover)]"
                @click="search(item.name)"
              >
                {{ item.name }}
              </button>
            </li>
          </ul>
          <p v-else class="text-[13px] text-[#bbb]">暂无热门话题</p>
        </section>
      </div>
    </div>
  </div>
</template>
