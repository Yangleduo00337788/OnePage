<!-- 数据报表：账号概览 / 内容分析 / 粉丝数据。接 GET /creator/stats。 -->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api, type CreatorStats, type StatsMetric, type StatsRange } from '@onepage/api-client'
import Icon from './Icon.vue'
import type { PublishKind } from './PublishComposer.vue'

const props = defineProps<{ kind: 'account' | 'content' | 'fans' }>()
const emit = defineEmits<{ publish: [PublishKind] }>()

const range = ref<StatsRange>('7')
const query = ref('')
const sort = ref<'views' | 'likes' | 'comments'>('views')
const kindFilter = ref<'all' | 'image' | 'video'>('all')
const data = ref<CreatorStats | null>(null)

const ranges = [
  { id: '1' as const, label: '昨日' },
  { id: '7' as const, label: '近 7 日' },
  { id: '30' as const, label: '近 30 日' },
]

const titles = {
  account: '账号概览',
  content: '内容分析',
  fans: '粉丝数据',
}

const subs = {
  account: '看账号整体热度，不拆到单篇。曝光与观看同为详情打开次数（无独立曝光日志）。分享暂无落库。',
  content: '按每一篇已发布笔记看周期内观看和互动，用来找写得好的内容。',
  fans: '涨粉按关注时间统计。取关会删边，没有历史所以取关和 7 日留存暂空。',
}

function textOf(n: number | null | undefined) {
  if (n == null) return '—'
  return String(n)
}

function rateOf(n: number | null | undefined) {
  if (n == null) return '—'
  return `${Math.round(n * 1000) / 10}%`
}

function deltaOf(metric: StatsMetric | undefined, empty = '环比 -') {
  if (!metric || metric.delta == null) return empty
  const pct = Math.round(metric.delta * 1000) / 10
  const sign = pct > 0 ? '+' : ''
  return `环比 ${sign}${pct}%`
}

function cell(label: string, metric: StatsMetric | undefined, asRate = false) {
  return {
    label,
    value: asRate ? rateOf(metric?.value) : textOf(metric?.value ?? 0),
    delta: deltaOf(metric),
  }
}

const account = computed(() => data.value?.account)
const fans = computed(() => data.value?.fans)
const content = computed(() => data.value?.content)

const accountCells = computed(() => {
  const a = account.value
  return [
    cell('曝光数', a?.impressions),
    cell('观看数', a?.views),
    cell('点赞数', a?.likes),
    cell('评论数', a?.comments),
    cell('收藏数', a?.favorites),
    cell('分享数', a?.shares),
    cell('净涨粉', a?.followerNet),
    cell('新增关注', a?.followerNew),
  ]
})

const mix = computed(() => {
  const m = account.value?.mix
  const items = [
    { label: '图文', value: m?.image ?? 0 },
    { label: '视频', value: m?.video ?? 0 },
    { label: '长文', value: m?.article ?? 0 },
  ]
  const max = Math.max(1, ...items.map((item) => item.value))
  return items.map((item) => ({ ...item, width: Math.round((item.value / max) * 100) }))
})

const interactMix = computed(() => {
  const a = account.value
  const items = [
    { label: '点赞数', value: a?.likes.value ?? 0 },
    { label: '评论数', value: a?.comments.value ?? 0 },
    { label: '收藏数', value: a?.favorites.value ?? 0 },
    { label: '分享数', value: a?.shares.value ?? 0 },
  ]
  const max = Math.max(1, ...items.map((item) => item.value))
  return items.map((item) => ({ ...item, width: Math.round((item.value / max) * 100) }))
})

const fanCells = computed(() => {
  const f = fans.value
  return [
    cell('粉丝总数', f?.followerTotal),
    cell('净增粉丝', f?.followerNet),
    {
      label: '活跃粉丝',
      value: rateOf(f?.activeRate),
      delta: '周期内互动粉丝 / 当前粉丝',
    },
    { label: '取关数', value: '—', delta: '取关不留历史' },
    {
      label: '回关率',
      value: rateOf(f?.reciprocalRate),
      delta: '你也关注了对方的粉丝占比',
    },
    {
      label: '观看粉丝',
      value: textOf(f?.viewerFans ?? 0),
      delta: '周期内打开过你笔记的粉丝',
    },
  ]
})

const fanMix = computed(() => {
  const c = fans.value?.change
  const items = [
    { label: '新增关注', value: c?.followNew ?? 0 },
    { label: '取消关注', value: c?.unfollow },
    { label: '净增', value: c?.net ?? 0 },
  ]
  const nums = items.map((item) => item.value).filter((n): n is number => n != null)
  const max = Math.max(1, ...nums, 0)
  return items.map((item) => ({
    label: item.label,
    value: item.value == null ? '—' : String(item.value),
    width: item.value == null ? 0 : Math.round((item.value / max) * 100),
  }))
})

const fanLive = computed(() => {
  const f = fans.value
  return [
    { label: '看过你的粉丝', value: textOf(f?.viewerFans ?? 0) },
    { label: '互动过的粉丝', value: textOf(f?.engagedFans ?? 0) },
    { label: '7 日留存', value: '—' },
  ]
})

type Row = {
  id: string
  title: string
  kind: string
  views: number
  likes: number
  comments: number
  fav: number
}

const rows = computed<Row[]>(() => {
  const list = content.value?.posts ?? []
  const sorted = [...list].sort((a, b) => b[sort.value] - a[sort.value])
  return sorted.map((item) => ({
    id: item.postId,
    title: item.title || '未命名笔记',
    kind: item.postType,
    views: item.views,
    likes: item.likes,
    comments: item.comments,
    fav: item.favorites,
  }))
})

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  return rows.value.filter((item) => {
    if (kindFilter.value === 'video' && item.kind !== 'video') return false
    if (kindFilter.value === 'image' && item.kind === 'video') return false
    if (q && !item.title.toLowerCase().includes(q)) return false
    return true
  })
})

const period = computed(() => ranges.find((item) => item.id === range.value)?.label ?? '')

function chartOf(points: { date: string; value: number }[] | undefined, rangeId: StatsRange) {
  const list = points ?? []
  const max = Math.max(1, ...list.map((item) => item.value), 0)
  return list.map((item, i) => ({
    label: rangeId === '1' ? '昨日' : String(i + 1),
    h: item.value <= 0 ? 6 : Math.max(8, Math.round((item.value / max) * 100)),
  }))
}

const days = computed(() => chartOf(account.value?.viewTrend, range.value))
const fanDays = computed(() => chartOf(fans.value?.trend, range.value))

async function load() {
  try {
    data.value = await api.creatorStats(range.value)
  } catch {
    data.value = null
  }
}

onMounted(() => {
  void load()
})

watch(range, () => {
  void load()
})
</script>

<template>
  <div class="stats">
    <div class="stats-top">
      <div class="head">
        <h1>{{ titles[props.kind] }}</h1>
        <p class="sub">{{ subs[props.kind] }}</p>
      </div>
      <div class="range">
        <button
          v-for="item in ranges"
          :key="item.id"
          type="button"
          :class="{ 'is-on': range === item.id }"
          @click="range = item.id"
        >
          {{ item.label }}
        </button>
      </div>
    </div>

    <template v-if="props.kind === 'account'">
      <p class="hint">统计周期 {{ period }}</p>
      <div class="grid">
        <div v-for="item in accountCells" :key="item.label" class="cell">
          <p class="cell-label">{{ item.label }}</p>
          <p class="cell-value">{{ item.value }}</p>
          <p class="cell-delta">{{ item.delta }}</p>
        </div>
      </div>

      <section class="panel">
        <h2>观看趋势</h2>
        <div class="chart">
          <div v-for="d in days" :key="d.label" class="col">
            <span class="chart-bar" :style="{ height: d.h + '%' }" />
            <em>{{ d.label }}</em>
          </div>
        </div>
      </section>

      <div class="split">
        <section class="panel">
          <h2>体裁构成</h2>
          <ul class="mix">
            <li v-for="item in mix" :key="item.label">
              <span>{{ item.label }}</span>
              <b>{{ item.value }}</b>
              <i :style="{ width: item.width + '%' }" />
            </li>
          </ul>
        </section>
        <section class="panel">
          <h2>互动构成</h2>
          <ul class="mix">
            <li v-for="item in interactMix" :key="item.label">
              <span>{{ item.label }}</span>
              <b>{{ item.value }}</b>
              <i :style="{ width: item.width + '%' }" />
            </li>
          </ul>
        </section>
      </div>
    </template>

    <template v-else-if="props.kind === 'fans'">
      <p class="hint">统计周期 {{ period }}</p>
      <div class="grid fans">
        <div v-for="item in fanCells" :key="item.label" class="cell">
          <p class="cell-label">{{ item.label }}</p>
          <p class="cell-value">{{ item.value }}</p>
          <p class="cell-delta">{{ item.delta }}</p>
        </div>
      </div>

      <section class="panel">
        <h2>粉丝趋势</h2>
        <div class="chart">
          <div v-for="d in fanDays" :key="d.label" class="col">
            <span class="chart-bar" :style="{ height: d.h + '%' }" />
            <em>{{ d.label }}</em>
          </div>
        </div>
      </section>

      <div class="split">
        <section class="panel">
          <h2>增减构成</h2>
          <ul class="mix">
            <li v-for="item in fanMix" :key="item.label">
              <span>{{ item.label }}</span>
              <b>{{ item.value }}</b>
              <i :style="{ width: item.width + '%' }" />
            </li>
          </ul>
        </section>
        <section class="panel">
          <h2>活跃情况</h2>
          <ul class="mix">
            <li v-for="item in fanLive" :key="item.label">
              <span>{{ item.label }}</span>
              <b>{{ item.value }}</b>
              <i />
            </li>
          </ul>
        </section>
      </div>
    </template>

    <template v-else>
      <div class="toolbar">
        <div class="tabs">
          <button type="button" class="tab" :class="{ 'is-on': kindFilter === 'all' }" @click="kindFilter = 'all'">全部</button>
          <button type="button" class="tab" :class="{ 'is-on': kindFilter === 'image' }" @click="kindFilter = 'image'">图文</button>
          <button type="button" class="tab" :class="{ 'is-on': kindFilter === 'video' }" @click="kindFilter = 'video'">视频</button>
        </div>
        <div class="tabs">
          <button type="button" class="tab" :class="{ 'is-on': sort === 'views' }" @click="sort = 'views'">按观看</button>
          <button type="button" class="tab" :class="{ 'is-on': sort === 'likes' }" @click="sort = 'likes'">按点赞</button>
          <button type="button" class="tab" :class="{ 'is-on': sort === 'comments' }" @click="sort = 'comments'">按评论</button>
        </div>
        <label class="search">
          <Icon name="search" :size="16" />
          <input v-model="query" type="search" placeholder="搜索笔记" />
        </label>
      </div>

      <div class="grid">
        <div class="cell">
          <p class="cell-label">平均观看</p>
          <p class="cell-value">{{ content?.avgViews ?? 0 }}</p>
          <p class="cell-delta">{{ period }}</p>
        </div>
        <div class="cell">
          <p class="cell-label">平均点赞</p>
          <p class="cell-value">{{ content?.avgLikes ?? 0 }}</p>
          <p class="cell-delta">{{ period }}</p>
        </div>
        <div class="cell">
          <p class="cell-label">平均评论</p>
          <p class="cell-value">{{ content?.avgComments ?? 0 }}</p>
          <p class="cell-delta">{{ period }}</p>
        </div>
        <div class="cell">
          <p class="cell-label">已分析笔记</p>
          <p class="cell-value">{{ content?.postCount ?? 0 }}</p>
          <p class="cell-delta">{{ period }}</p>
        </div>
      </div>

      <section class="panel table-wrap">
        <h2>单篇表现</h2>
        <div class="table">
          <div class="thead">
            <span>笔记</span>
            <span>观看</span>
            <span>点赞</span>
            <span>评论</span>
            <span>收藏</span>
          </div>
          <div v-for="item in filtered" :key="item.id" class="trow">
            <span>{{ item.title }}</span>
            <span>{{ item.views }}</span>
            <span>{{ item.likes }}</span>
            <span>{{ item.comments }}</span>
            <span>{{ item.fav }}</span>
          </div>
        </div>
        <div v-if="!filtered.length" class="empty">
          <img class="empty-art" src="/empty-me.png" alt="" width="160" height="160" />
          <p>还没有可分析的笔记。发布后会出现在这里。</p>
          <button type="button" class="cta" @click="emit('publish', 'image')">去发布</button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.stats {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  margin: 0 20px 20px 0;
  padding: 20px 24px 28px;
  box-sizing: border-box;
  overflow: auto;
  border-radius: 20px;
  background: #fff;
}

.stats-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
}

.head {
  flex: 1;
  min-width: 0;
}

h1 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.sub {
  margin: 6px 0 0;
  color: #8a8a8a;
  font-size: 13px;
  line-height: 1.5;
}

.range {
  display: flex;
  flex-shrink: 0;
  flex-wrap: nowrap;
  gap: 4px;
  padding: 3px;
  border-radius: 20px;
  background: rgb(248, 248, 248);
}

.range button {
  flex-shrink: 0;
  height: 28px;
  padding: 0 12px;
  border-radius: 16px;
  color: #8a8a8a;
  font-size: 13px;
  white-space: nowrap;
}

.range button.is-on {
  color: #1f1f1f;
  background: #fff;
  font-weight: 500;
}

.hint {
  margin: 16px 0 0;
  color: #8a8a8a;
  font-size: 12px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}

.tabs {
  display: flex;
  gap: 6px;
}

.tab {
  height: 32px;
  padding: 0 12px;
  border-radius: 20px;
  color: #8a8a8a;
  font-size: 13px;
}

.tab.is-on {
  color: #1f1f1f;
  font-weight: 600;
  background: rgb(248, 248, 248);
}

.search {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  width: 220px;
  height: 36px;
  padding: 0 14px;
  border-radius: 20px;
  background: rgb(248, 248, 248);
  color: #bbb;
}

.search input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  color: #1f1f1f;
  font: inherit;
  font-size: 14px;
  outline: none;
}

.search input::placeholder {
  color: #bbb;
}

.grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 12px;
  margin-top: 8px;
}

.grid.fans {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.cell {
  padding: 16px 8px;
}

.cell-label {
  margin: 0;
  color: #8a8a8a;
  font-size: 12px;
}

.cell-value {
  margin: 8px 0 0;
  font-size: 22px;
  font-weight: 600;
}

.cell-delta {
  margin: 6px 0 0;
  color: #bbb;
  font-size: 12px;
}

.panel {
  margin-top: 20px;
  padding: 16px 18px 18px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
}

.panel h2 {
  margin: 0 0 14px;
  font-size: 14px;
  font-weight: 600;
}

.chart {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 140px;
  padding-top: 8px;
}

.col {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  height: 100%;
  gap: 8px;
}

.chart-bar {
  display: block;
  width: 100%;
  max-width: 28px;
  min-height: 8px;
  border-radius: 6px 6px 2px 2px;
  background: #e4e4e4;
}

.col em {
  color: #bbb;
  font-size: 11px;
  font-style: normal;
}

.split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.mix {
  margin: 0;
  padding: 0;
  list-style: none;
}

.mix li {
  display: grid;
  grid-template-columns: 64px 32px 1fr;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  font-size: 13px;
}

.mix li:first-child {
  margin-top: 0;
}

.mix b {
  font-weight: 600;
}

.mix i {
  display: block;
  height: 6px;
  max-width: 100%;
  border-radius: 3px;
  background: #e8e8e8;
}

.table-wrap {
  background: rgb(248, 248, 248);
}

.table {
  margin-top: 0;
}

.thead,
.trow {
  display: grid;
  grid-template-columns: minmax(160px, 2fr) 1fr 1fr 1fr 1fr;
  gap: 8px;
  padding: 12px 4px;
  font-size: 13px;
}

.thead {
  color: #8a8a8a;
  border-bottom: 1px solid #ececec;
}

.trow {
  border-bottom: 1px solid #ececec;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 36px 12px 20px;
  color: #8a8a8a;
}

.empty-art {
  width: 160px;
  height: auto;
  margin-bottom: 12px;
}

.empty p {
  margin: 0;
  font-size: 14px;
}

.cta {
  height: 36px;
  margin-top: 16px;
  padding: 0 18px;
  border-radius: 18px;
  background: #ff2442;
  color: #fff;
  font-size: 14px;
}

@media (max-width: 840px) {
  .grid,
  .grid.fans,
  .split {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
