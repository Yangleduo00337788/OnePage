<!--
  搜索结果。筛选 UI 保留；笔记/用户/话题/直播尚无搜索服务，空态不再灌假数据。
-->
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '../components/Icon.vue'
import EmptyState from '../components/EmptyState.vue'

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

const emptyHint = computed(() => {
  if (tab.value === '用户') return '还没有匹配的用户'
  if (tab.value === '话题') return '还没有热门话题'
  if (tab.value === '直播') return '还没有直播'
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
        <EmptyState :src="emptyArt" :title="emptyHint" />
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
          <p class="mt-3 text-[14px] leading-6 text-[#555]">
            耶耶尚未接入，无法根据「{{ q || '你感兴趣的内容' }}」整理看法。
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
