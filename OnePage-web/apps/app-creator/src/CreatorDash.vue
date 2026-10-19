<!-- 创作者首页：20px 白卡片工作台；总览接 /creator/stats 与 /live/stats。 -->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  api,
  type CreatorStats,
  type LiveCreatorStats,
  type StatsMetric,
  type StatsRange,
  type UserProfile,
} from '@onepage/api-client'
import TextNoteCover from './TextNoteCover.vue'
import { coverVideoSrc, isTextNote, isVideoNote, noteFromMine, type Note } from './notes'
import type { PublishKind } from './PublishComposer.vue'
import Icon from './Icon.vue'

const props = defineProps<{ me: UserProfile | null }>()
const emit = defineEmits<{
  publish: [PublishKind]
  'open-stats': []
  'open-notes': [tab?: string]
  'open-events': []
  'open-live': []
}>()

const dataTab = ref<'notes' | 'live'>('notes')
const range = ref<Extract<StatsRange, '1' | '30'>>('1')
const noteData = ref<CreatorStats | null>(null)
const liveData = ref<LiveCreatorStats | null>(null)

function textOf(n: number | null | undefined) {
  if (n == null) return '—'
  return String(n)
}

function deltaOf(metric: StatsMetric | undefined, fallback = '环比 -') {
  if (!metric || metric.delta == null) return fallback
  const pct = Math.round(metric.delta * 1000) / 10
  const sign = pct > 0 ? '+' : ''
  return `环比 ${sign}${pct}%`
}

const name = computed(() => props.me?.nickname || '未登录')
const initial = computed(() => name.value.slice(0, 1))
const following = computed(() => props.me?.stats.followingCount ?? 0)
const followers = computed(() => props.me?.stats.followerCount ?? 0)
const likes = computed(() => {
  const s = props.me?.stats
  if (!s) return 0
  return s.likeReceivedCount + s.favoriteReceivedCount
})
const pageId = computed(() => props.me?.pageId || '—')
const bio = computed(() => props.me?.bio || '还没有简介')

const noteStats = computed(() => {
  const a = noteData.value?.account
  return [
    { label: '曝光数', value: textOf(a?.impressions.value ?? 0), delta: deltaOf(a?.impressions) },
    { label: '观看数', value: textOf(a?.views.value ?? 0), delta: deltaOf(a?.views) },
    { label: '点赞数', value: textOf(a?.likes.value ?? 0), delta: deltaOf(a?.likes) },
    { label: '评论数', value: textOf(a?.comments.value ?? 0), delta: deltaOf(a?.comments) },
    { label: '净涨粉', value: textOf(a?.followerNet.value ?? 0), delta: deltaOf(a?.followerNet) },
    { label: '封面点击率', value: `${a?.coverCtr.value ?? 0}%`, delta: deltaOf(a?.coverCtr) },
    { label: '视频完播率', value: `${a?.videoCompleteRate.value ?? 0}%`, delta: deltaOf(a?.videoCompleteRate) },
    { label: '收藏数', value: textOf(a?.favorites.value ?? 0), delta: deltaOf(a?.favorites) },
    { label: '分享数', value: textOf(a?.shares.value ?? 0), delta: deltaOf(a?.shares) },
    { label: '新增关注', value: textOf(a?.followerNew.value ?? 0), delta: deltaOf(a?.followerNew) },
  ]
})

const liveStats = computed(() => {
  const l = liveData.value
  return [
    { label: '开播场次', value: textOf(l?.sessions.value ?? 0), delta: deltaOf(l?.sessions) },
    { label: '观看人次', value: textOf(l?.viewers.value ?? 0), delta: deltaOf(l?.viewers) },
    { label: '峰值在线', value: textOf(l?.peakOnline.value ?? 0), delta: deltaOf(l?.peakOnline) },
    { label: '新增关注', value: textOf(l?.followerNew.value ?? 0), delta: deltaOf(l?.followerNew) },
    { label: '送礼人数', value: textOf(l?.gifters.value ?? 0), delta: '礼物未做' },
  ]
})

const stats = computed(() => (dataTab.value === 'notes' ? noteStats.value : liveStats.value))

const starters = [
  { id: 'image' as PublishKind, title: '发布图文笔记', hint: '支持常见图片格式 jpg、png、webp', tone: 'peach', icon: 'image' },
  { id: 'video' as PublishKind, title: '发布视频笔记', hint: '支持 mp4、mov，不超过 200MB', tone: 'blue', icon: 'circle-play' },
  { id: 'article' as PublishKind, title: '写长文', hint: '以正文为主，可再配图', tone: 'mint', icon: 'pencil' },
]

const topics = [
  { tag: '美食', heat: '34.7 万人参与' },
  { tag: '时尚', heat: '21.2 万人参与' },
  { tag: '出行', heat: '16.3 万人参与' },
  { tag: '知识', heat: '12.8 万人参与' },
]

const notices = [
  { title: '一页公约', desc: '真诚分享，友好互动' },
]

const recent = ref<Note[]>([])
const campaigns = ref<{ id: string; title: string; time: string; cover: string }[]>([])
const pendingCount = computed(() => noteData.value?.pending.review ?? 0)
const draftCount = computed(() => noteData.value?.pending.draft ?? 0)

function campaignStatus(status: string) {
  if (status === 'live') return '进行中'
  if (status === 'upcoming') return '预告'
  if (status === 'ended') return '结束'
  return status
}

async function loadRecent() {
  if (!props.me) {
    recent.value = []
    return
  }
  try {
    const page = await api.myPosts(undefined, 5)
    recent.value = page.items.map(noteFromMine)
  } catch {
    recent.value = []
  }
}

async function loadCampaigns() {
  try {
    const data = await api.catalog()
    const rows = (data.campaigns || []).filter((row) => row.enabled && row.status !== 'ended')
    rows.sort((a, b) => {
      const rank = (s: string) => (s === 'live' ? 0 : s === 'upcoming' ? 1 : 2)
      return rank(a.status) - rank(b.status) || a.sortOrder - b.sortOrder
    })
    campaigns.value = rows.slice(0, 3).map((row) => ({
      id: row.eventId,
      title: row.title,
      time: campaignStatus(row.status),
      cover: row.coverUrl || '',
    }))
  } catch {
    campaigns.value = []
  }
}

async function loadOverview() {
  if (!props.me) {
    noteData.value = null
    liveData.value = null
    return
  }
  try {
    noteData.value = await api.creatorStats(range.value)
  } catch {
    noteData.value = null
  }
  try {
    liveData.value = await api.liveStats(range.value)
  } catch {
    liveData.value = null
  }
}

onMounted(() => {
  void loadRecent()
  void loadOverview()
  void loadCampaigns()
})

watch(
  () => props.me?.userId,
  () => {
    void loadRecent()
    void loadOverview()
  },
)

watch(range, () => {
  void loadOverview()
})

const period = computed(() => (range.value === '1' ? '昨日' : '近 30 日'))
</script>

<template>
  <div class="dash">
    <div class="col">
      <article class="card profile">
        <span class="avatar">
          <img v-if="props.me?.avatarUrl" :src="props.me.avatarUrl" alt="" />
          <span v-else>{{ initial }}</span>
        </span>
        <div class="profile-body">
          <h2>{{ name }}</h2>
          <p class="counts">
            <span>{{ following }} 关注</span>
            <span>{{ followers }} 粉丝</span>
            <span>{{ likes }} 获赞与收藏</span>
          </p>
          <p class="meta">一页号 {{ pageId }}　{{ bio }}</p>
        </div>
      </article>

      <article class="card">
        <h3>新的创作</h3>
        <div class="starts">
          <button v-for="item in starters" :key="item.id" type="button" class="start" :class="item.tone" @click="emit('publish', item.id)">
            <span class="start-ico"><Icon :name="item.icon" :size="22" /></span>
            <span>
              <strong>{{ item.title }}</strong>
              <small>{{ item.hint }}</small>
            </span>
          </button>
          <button type="button" class="start pink" @click="emit('open-live')">
            <span class="start-ico"><Icon name="radio" :size="22" /></span>
            <strong>去开播</strong>
          </button>
        </div>
      </article>

      <article class="card">
        <div class="data-head">
          <div class="tabs">
            <button type="button" class="tab" :class="{ 'is-on': dataTab === 'notes' }" @click="dataTab = 'notes'">笔记数据总览</button>
            <button type="button" class="tab" :class="{ 'is-on': dataTab === 'live' }" @click="dataTab = 'live'">直播数据总览</button>
          </div>
          <button type="button" class="link" @click="emit('open-stats')">去看详情</button>
        </div>
        <div class="range-row">
          <p>统计周期 {{ period }}</p>
          <div class="range">
            <button type="button" :class="{ 'is-on': range === '1' }" @click="range = '1'">昨日</button>
            <button type="button" :class="{ 'is-on': range === '30' }" @click="range = '30'">近 30 日</button>
          </div>
        </div>
        <div class="grid">
          <div v-for="item in stats" :key="item.label" class="cell">
            <p class="cell-label">{{ item.label }}</p>
            <p class="cell-value">{{ item.value }}</p>
            <p class="cell-delta">{{ item.delta }}</p>
          </div>
        </div>
      </article>

      <article class="card">
        <h3>创作话题</h3>
        <ul class="topics">
          <li v-for="item in topics" :key="item.tag">
            <strong>#{{ item.tag }}</strong>
            <span>{{ item.heat }}</span>
          </li>
        </ul>
      </article>
    </div>

    <aside class="rail">
      <article class="card">
        <h3>待处理</h3>
        <div class="todo">
          <button type="button" @click="emit('open-notes', '草稿')">
            草稿
            <b>{{ draftCount }}</b>
          </button>
          <button type="button" @click="emit('open-notes', '审核中')">
            审核中
            <b>{{ pendingCount }}</b>
          </button>
        </div>
      </article>
      <article class="card">
        <div class="card-head">
          <h3>最近笔记</h3>
          <button type="button" class="link" @click="emit('open-notes')">全部</button>
        </div>
        <ul v-if="recent.length" class="acts">
          <li v-for="item in recent" :key="item.id">
            <span class="thumb">
              <video
                v-if="item.cover && isVideoNote(item)"
                :src="coverVideoSrc(item.cover)"
                muted
                playsinline
                preload="metadata"
              />
              <img v-else-if="item.cover && !isTextNote(item)" :src="item.cover" alt="" />
              <TextNoteCover v-else size="thumb" :title="item.title" />
            </span>
            <div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.status }} · 浏览 {{ item.views }}</p>
            </div>
          </li>
        </ul>
        <div v-else class="empty-recent">
          <img class="empty-art" src="/empty-me.png" alt="" width="120" height="120" />
          <p>还没有笔记</p>
        </div>
      </article>
      <article class="card">
        <h3>平台公告</h3>
        <div v-for="item in notices" :key="item.title" class="notice">
          <strong>{{ item.title }}</strong>
          <p>{{ item.desc }}</p>
        </div>
      </article>
      <article class="card">
        <div class="card-head">
          <h3>热门活动</h3>
          <button type="button" class="link" @click="emit('open-events')">全部</button>
        </div>
        <ul v-if="campaigns.length" class="acts">
          <li v-for="item in campaigns" :key="item.id">
            <button type="button" class="act" @click="emit('open-events')">
              <span class="thumb"><img v-if="item.cover" :src="item.cover" alt="" /></span>
              <span>
                <strong>{{ item.title }}</strong>
                <small>{{ item.time }}</small>
              </span>
            </button>
          </li>
        </ul>
        <p v-else class="empty-hint">还没有进行中的活动</p>
      </article>
    </aside>
  </div>
</template>

<style scoped>
.dash {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 16px;
  flex: 1;
  min-height: 0;
  margin: 0 20px 20px 0;
  overflow: auto;
  align-items: start;
}

.col,
.rail {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.rail {
  position: sticky;
  top: 0;
}

.card {
  padding: 18px 20px 20px;
  border-radius: 20px;
  background: #fff;
}

.card h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.profile {
  display: flex;
  align-items: center;
  gap: 16px;
}

.avatar {
  overflow: hidden;
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: rgb(248, 248, 248);
  color: #555;
  font-size: 22px;
  font-weight: 600;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-body h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.counts {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 8px 0 0;
  color: #8a8a8a;
  font-size: 13px;
}

.meta {
  margin: 8px 0 0;
  color: #8a8a8a;
  font-size: 12px;
}

.starts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.start {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 84px;
  padding: 14px;
  border-radius: 16px;
  text-align: left;
}

.start strong {
  display: block;
  font-size: 14px;
}

.start small {
  display: block;
  margin-top: 4px;
  color: #8a8a8a;
  font-size: 12px;
}

.start-ico {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: #fff;
}

.peach {
  background: #fff1e6;
  color: #c45a12;
}

.blue {
  background: #e8f2ff;
  color: #2a6fdb;
}

.mint {
  background: #e8f8f0;
  color: #1a8a5a;
}

.mint {
  background: #e8f6ee;
  color: #1f8a54;
}

.pink {
  background: #ffe8ee;
  color: #e24b6a;
}

.data-head,
.card-head,
.range-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-head {
  margin-bottom: 12px;
}

.card-head span,
.link,
.range-row p {
  color: #8a8a8a;
  font-size: 12px;
}

.link:hover {
  color: #1f1f1f;
}

.tabs {
  display: flex;
  gap: 8px;
}

.tab {
  height: 32px;
  padding: 0 12px;
  border-radius: 20px;
  color: #8a8a8a;
  font-size: 14px;
}

.tab.is-on {
  color: #1f1f1f;
  font-weight: 600;
  background: rgb(248, 248, 248);
}

.range-row {
  margin: 8px 0 4px;
}

.range {
  display: flex;
  gap: 4px;
  padding: 3px;
  border-radius: 20px;
  background: rgb(248, 248, 248);
}

.range button {
  height: 26px;
  padding: 0 12px;
  border-radius: 16px;
  color: #8a8a8a;
  font-size: 12px;
}

.range button.is-on {
  color: #1f1f1f;
  background: #fff;
  font-weight: 500;
}

.grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px 12px;
  margin-top: 8px;
}

.cell {
  padding: 12px 4px;
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

.topics,
.acts {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.topics li,
.acts li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f2f2f2;
}

.topics li:last-child,
.acts li:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.topics strong,
.acts strong,
.notice strong {
  display: block;
  font-size: 13px;
  font-weight: 500;
}

.topics span,
.acts p,
.notice p {
  margin: 4px 0 0;
  color: #8a8a8a;
  font-size: 12px;
}

.notice {
  margin-top: 12px;
}

.todo {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 12px;
}

.todo button {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  border-radius: 12px;
  background: rgb(248, 248, 248);
  color: #8a8a8a;
  font-size: 12px;
  text-align: left;
}

.todo b {
  color: #1f1f1f;
  font-size: 20px;
  font-weight: 600;
}

.act {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 0;
  text-align: left;
}

.act small {
  display: block;
  margin-top: 4px;
  color: #8a8a8a;
  font-size: 12px;
}

.thumb {
  overflow: hidden;
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 10px;
  background: #eee;
}

.thumb img,
.thumb video,
.thumb :deep(.tnc) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.empty-recent {
  margin: 8px 0 0;
  color: #8a8a8a;
  font-size: 13px;
  text-align: center;
}

.empty-hint {
  margin: 8px 0 0;
  color: #8a8a8a;
  font-size: 13px;
}

.empty-art {
  display: block;
  width: 120px;
  height: auto;
  margin: 8px auto 8px;
}

@media (max-width: 1100px) {
  .dash {
    grid-template-columns: 1fr;
  }

  .rail {
    position: static;
  }

  .starts,
  .grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 720px) {
  .starts,
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
