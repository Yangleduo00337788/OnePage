<!--
  搜索结果占位页。q/tab 都在 query，筛选只在前端过滤假数据。
  「问耶耶」把当前 q 带到 /yehyeh。
-->
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '../components/Icon.vue'
import { LIVES, NOTES, SEARCH_USERS, TOPICS } from '../data/home'
import { useNoteDetail } from '../stores/noteDetail'

const noteDetail = useNoteDetail()

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

const notes = computed(() => {
  let list = NOTES
  if (q.value) {
    const k = q.value.replace(/^#/, '').toLowerCase()
    const hit = NOTES.filter((n) => n.title.includes(k) || n.author.includes(k))
    list = hit.length ? hit : NOTES.slice(0, 8)
  }
  if (filters.types.length === 1) {
    const wantVideo = filters.types[0] === '视频'
    list = list.filter((n) => Boolean(n.video) === wantVideo)
  }
  return list
})

const followUps = computed(() => {
  const k = q.value || '生活'
  return [`${k} 怎么入门`, `和 ${k} 相关的穿搭`, `附近有哪些 ${k}`]
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
</script>

<template>
  <section>
    <div class="relative z-20 mb-8 flex items-center gap-8 overflow-visible text-[16px] text-[#8a8a8a]">
      <button
        v-for="item in TABS"
        :key="item"
        type="button"
        class="pb-1.5"
        :class="tab === item ? 'text-[18px] font-semibold text-[#1f1f1f] shadow-[inset_0_-2px_0_#ff2442]' : 'hover:text-[#333]'"
        @click="tab = item"
      >
        {{ item }}
      </button>
      <button type="button" class="inline-flex items-center gap-1 pb-1.5 hover:text-[#333]" @click="openYehyeh()">
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
        <div v-if="tab === '笔记'" class="grid grid-cols-3 gap-4 xl:grid-cols-4">
          <article
            v-for="note in notes"
            :key="note.id"
            :data-note-id="note.id"
            class="cursor-pointer"
            @click="noteDetail.show(note, $event.currentTarget)"
          >
            <div class="group relative overflow-hidden rounded-xl bg-[#ececec]">
              <img :src="note.image" :alt="note.title" class="aspect-[3/4] w-full object-cover" />
              <span class="pointer-events-none absolute inset-0 bg-black/0 group-hover:bg-black/25" />
              <span
                v-if="note.video"
                class="absolute top-2.5 right-2.5 z-[1] text-white drop-shadow"
              >
                <svg viewBox="0 0 24 24" width="28" height="28" fill="currentColor" aria-hidden="true">
                  <path d="M8 5.5v13l11-6.5z" />
                </svg>
              </span>
            </div>
            <h3 class="mt-2 line-clamp-2 text-[15px] leading-5">{{ note.title }}</h3>
            <p class="mt-1.5 text-[13px] text-[#8a8a8a]">{{ note.author }} · {{ note.likes }}</p>
          </article>
        </div>

        <ul v-else-if="tab === '用户'" class="divide-y divide-[#f2f2f2] rounded-2xl ring-1 ring-[#f2f2f2]">
          <li v-for="user in SEARCH_USERS" :key="user.id" class="flex items-center gap-3 px-4 py-3">
            <span class="h-12 w-12 shrink-0 rounded-full bg-[#ececec]" />
            <div class="min-w-0 flex-1">
              <p class="truncate text-[16px] font-medium">{{ user.name }}</p>
              <p class="truncate text-[13px] text-[#8a8a8a]">@{{ user.pageId }} · {{ user.bio }}</p>
            </div>
            <span class="text-[13px] text-[#bbb]">{{ user.fans }} 粉丝</span>
            <button type="button" class="rounded-[20px] bg-[#f7f7f7] px-4 py-1.5 text-[13px]">关注</button>
          </li>
        </ul>

        <ol v-else-if="tab === '话题'" class="space-y-3">
          <li
            v-for="item in TOPICS"
            :key="item.rank"
            class="flex cursor-pointer items-center gap-3 rounded-2xl px-3 py-2 hover:bg-[#fafafa]"
            @click="router.push({ path: '/search', query: { q: item.name, tab: '话题' } })"
          >
            <span class="w-5 font-semibold" :class="item.rank <= 3 ? 'text-[#ff6a00]' : 'text-[#bbb]'">{{ item.rank }}</span>
            <span class="flex-1">{{ item.name }}</span>
            <span class="text-[12px] text-[#b0b0b0]">{{ item.heat }}</span>
          </li>
        </ol>

        <ul v-else class="space-y-3">
          <li v-for="item in LIVES" :key="item.title" class="flex gap-3 rounded-2xl p-2 hover:bg-[#fafafa]">
            <div class="relative h-16 w-16 overflow-hidden rounded-xl bg-[#eee]">
              <img :src="item.image" alt="" class="h-full w-full object-cover" />
              <span class="absolute top-1 left-1 rounded px-1 text-[9px] text-white" style="background:#ff4d4f">LIVE</span>
            </div>
            <div>
              <p class="text-[14px] font-medium">{{ item.title }}</p>
              <p class="mt-1 text-[12px] text-[#8a8a8a]">{{ item.author }} · {{ item.viewers }} 人在线</p>
            </div>
          </li>
        </ul>
      </div>

      <aside class="sticky top-[84px] hidden h-[calc(100vh-108px)] w-[400px] shrink-0 flex-col lg:flex">
        <div class="flex min-h-0 flex-1 flex-col overflow-y-auto rounded-2xl bg-[#fafafa] p-5">
          <div class="flex items-center gap-2">
            <span class="flex h-7 w-7 items-center justify-center rounded-full bg-[#ff2442] text-white">
              <Icon name="spark" :size="14" :stroke="2" />
            </span>
            <span class="text-[15px] font-semibold">耶耶</span>
            <span class="rounded-full bg-white px-2 py-0.5 text-[11px] text-[#8a8a8a] ring-1 ring-[#eee]">AI</span>
          </div>
          <p class="mt-3 text-[14px] leading-6 text-[#333]">
            关于「{{ q || '你感兴趣的内容' }}」，耶耶根据站内笔记整理了这些看法：
          </p>
          <ul class="mt-2 list-disc space-y-1 pl-5 text-[13px] leading-6 text-[#555]">
            <li>先看真实体验贴，再决定要不要跟风。</li>
            <li>把预算和场景说清楚，能缩成更可执行的清单。</li>
            <li>相关直播和话题可以对照着刷，避免只看一面之词。</li>
          </ul>
          <div class="mt-4 flex flex-col gap-2">
            <button
              v-for="item in followUps"
              :key="item"
              type="button"
              class="rounded-full bg-white px-3 py-1.5 text-left text-[12px] text-[#555] ring-1 ring-[#eee] hover:text-[#ff2442]"
              @click="openYehyeh(item)"
            >
              {{ item }}
            </button>
          </div>
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
