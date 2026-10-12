<!--
  顶栏搜索。展开面板只展示本机历史；直播/话题无接口时为空。
  提交后跳 /search?q=，历史存在 localStorage。
-->
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from './Icon.vue'

const HISTORY_KEY = 'onepage.search.history'
const router = useRouter()
const route = useRoute()
const open = ref(false)
const keyword = ref('')
const history = ref<string[]>([])
const root = ref<HTMLElement | null>(null)

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
  router.push({ path: '/search', query: { q: t } })
}

watch(
  () => route.query.q,
  (q) => {
    if (typeof q === 'string') keyword.value = q
  },
  { immediate: true },
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
  if (!open.value || !root.value) return
  if (!root.value.contains(e.target as Node)) open.value = false
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') open.value = false
}

onMounted(() => {
  loadHistory()
  document.addEventListener('pointerdown', onDocPointer)
  document.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocPointer)
  document.removeEventListener('keydown', onKey)
})
</script>

<template>
  <div ref="root" class="relative w-full max-w-[720px]">
    <label
      class="flex h-11 w-full items-center rounded-full bg-[#fafafa] px-4 text-sm ring-1"
      :class="open ? 'ring-[#ddd] bg-white' : 'ring-[#eee]'"
    >
      <Icon name="search" :size="16" class="text-[#bbb]" />
      <input
        v-model="keyword"
        class="ml-2 h-full w-full bg-transparent outline-none placeholder:text-[#c0c0c0]"
        placeholder="搜索你感兴趣的内容、用户或话题..."
        @focus="open = true"
        @keydown.enter.prevent="search()"
      />
    </label>

    <div
      v-if="open"
      class="absolute top-[calc(100%+8px)] right-0 left-0 z-30 overflow-hidden rounded-2xl bg-white p-5 shadow-[0_8px_32px_rgba(0,0,0,0.08)] ring-1 ring-[#eee]"
    >
      <div class="mb-3 flex items-center justify-between">
        <h3 class="text-[15px] font-semibold">历史记录</h3>
        <button v-if="history.length" type="button" class="text-[12px] text-[#8a8a8a] hover:text-[#333]" @click="clearHistory">
          清空
        </button>
      </div>
      <ul v-if="history.length" class="flex flex-wrap gap-2">
        <li v-for="item in history" :key="item">
          <span class="inline-flex items-center gap-1.5 rounded-full bg-[#fafafa] py-1 pr-2 pl-2.5 text-[13px] text-[#333]">
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
          <p class="text-[13px] text-[#bbb]">暂无直播</p>
        </section>

        <section class="flex min-h-0 flex-col">
          <div class="mb-3 flex items-center justify-between">
            <h3 class="text-[15px] font-semibold">热门话题</h3>
          </div>
          <p class="text-[13px] text-[#bbb]">暂无热门话题</p>
        </section>
      </div>
    </div>
  </div>
</template>
