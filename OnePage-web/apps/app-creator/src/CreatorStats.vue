<!-- 数据报表：账号概览 / 内容分析 / 粉丝数据。P0 占位。 -->
<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from './Icon.vue'
import type { PublishKind } from './PublishComposer.vue'

const props = defineProps<{ kind: 'account' | 'content' | 'fans' }>()
const emit = defineEmits<{ publish: [PublishKind] }>()

const range = ref<'1' | '7' | '30'>('7')
const query = ref('')
const sort = ref<'views' | 'likes' | 'comments'>('views')
const kindFilter = ref<'all' | 'image' | 'video'>('all')

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
  account: '看账号整体热度，不拆到单篇。',
  content: '按每一篇笔记看观看和互动，用来找写得好的内容。',
  fans: '看涨粉、取关和活跃，判断账号有没有留住人。',
}

const accountCells = [
  { label: '曝光数', value: '0' },
  { label: '观看数', value: '0' },
  { label: '点赞数', value: '0' },
  { label: '评论数', value: '0' },
  { label: '收藏数', value: '0' },
  { label: '分享数', value: '0' },
  { label: '净涨粉', value: '0' },
  { label: '新增关注', value: '0' },
]

const mix = [
  { label: '图文', value: '0' },
  { label: '视频', value: '0' },
  { label: '长文', value: '0' },
]

const fanCells = [
  { label: '粉丝总数', value: '0' },
  { label: '净增粉丝', value: '0' },
  { label: '活跃粉丝', value: '0%' },
  { label: '取关数', value: '0' },
  { label: '回关率', value: '0%' },
  { label: '观看粉丝', value: '0' },
]

const fanMix = [
  { label: '新增关注', value: '0' },
  { label: '取消关注', value: '0' },
  { label: '净增', value: '0' },
]

const fanLive = [
  { label: '看过你的粉丝', value: '0' },
  { label: '互动过的粉丝', value: '0' },
  { label: '7 日留存', value: '0%' },
]

type Row = { id: string; title: string; kind: 'image' | 'video'; views: string; likes: string; comments: string; fav: string }

/** P0 无内容接口。 */
const rows = ref<Row[]>([])

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  return rows.value.filter((item) => {
    if (kindFilter.value !== 'all' && item.kind !== kindFilter.value) return false
    if (q && !item.title.toLowerCase().includes(q)) return false
    return true
  })
})

const period = computed(() => ranges.find((item) => item.id === range.value)?.label ?? '')

const days = computed(() => {
  if (range.value === '1') return [{ label: '昨日', h: 6 }]
  const n = range.value === '7' ? 7 : 10
  return Array.from({ length: n }, (_, i) => ({ label: String(i + 1), h: 6 }))
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
          <p class="cell-delta">环比 -</p>
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
              <i />
            </li>
          </ul>
        </section>
        <section class="panel">
          <h2>互动构成</h2>
          <ul class="mix">
            <li v-for="item in accountCells.slice(2, 6)" :key="item.label">
              <span>{{ item.label }}</span>
              <b>{{ item.value }}</b>
              <i />
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
          <p class="cell-delta">环比 -</p>
        </div>
      </div>

      <section class="panel">
        <h2>粉丝趋势</h2>
        <div class="chart">
          <div v-for="d in days" :key="d.label" class="col">
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
              <i />
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
          <p class="cell-value">0</p>
          <p class="cell-delta">{{ period }}</p>
        </div>
        <div class="cell">
          <p class="cell-label">平均点赞</p>
          <p class="cell-value">0</p>
          <p class="cell-delta">{{ period }}</p>
        </div>
        <div class="cell">
          <p class="cell-label">平均评论</p>
          <p class="cell-value">0</p>
          <p class="cell-delta">{{ period }}</p>
        </div>
        <div class="cell">
          <p class="cell-label">已分析笔记</p>
          <p class="cell-value">0</p>
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
