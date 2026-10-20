<!-- 工作台：问候、待办、今日增量、快捷入口、图表、最近队列。 -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  api,
  ApiError,
  REPORT_REASONS,
  type OpsOverview,
  type OpsPostRow,
  type OpsReport,
  type OpsSupport,
  type SupportKind,
} from '@onepage/api-client'
import type { EChartsCoreOption } from 'echarts/core'
import Icon from './Icon.vue'
import OpsChart from './OpsChart.vue'

const props = defineProps<{ nickname: string }>()
const emit = defineEmits<{ open: [string] }>()

const data = ref<OpsOverview | null>(null)
const note = ref('正在加载概览…')
const posts = ref<OpsPostRow[]>([])
const reports = ref<OpsReport[]>([])
const tickets = ref<OpsSupport[]>([])

const hello = computed(() => {
  const hour = new Date().getHours()
  if (hour < 11) return '早上好'
  if (hour < 13) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const todayText = computed(() =>
  new Date().toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }),
)

const n = (key: keyof OpsOverview) => data.value?.[key] ?? 0

const todoTotal = computed(
  () => n('postsPending') + n('reportsPending') + n('supportPending'),
)

const queues = computed(() => [
  {
    id: 'posts',
    title: '待审笔记',
    hint: '机审未过或未跑的公开笔记',
    count: n('postsPending'),
  },
  {
    id: 'reports',
    title: '待处理工单',
    hint: '举报笔记 / 评论 / 用户 / 直播',
    count: n('reportsPending'),
  },
  {
    id: 'support',
    title: '帮助申请',
    hint: '反馈、主播入驻、大 V 认证',
    count: n('supportPending'),
  },
])

const kpis = computed(() => [
  { id: 'users', title: '社区用户', value: n('communityUsers'), delta: n('usersToday'), deltaLabel: '今日新注册' },
  { id: 'posts', title: '在线笔记', value: n('postsLive'), delta: n('postsToday'), deltaLabel: '今日新笔记' },
  { id: 'comments', title: '可见评论', value: n('comments'), delta: n('commentsToday'), deltaLabel: '今日新评论' },
  { id: 'posts', title: '已下架', value: n('postsDown'), delta: n('postsRejected'), deltaLabel: '未通过' },
])

const shortcuts: { id: string; label: string; icon: string }[] = [
  { id: 'posts', label: '审笔记', icon: 'sticky-note' },
  { id: 'reports', label: '处理工单', icon: 'flag' },
  { id: 'support', label: '帮助申请', icon: 'message-square' },
  { id: 'im', label: '私信巡查', icon: 'message-square' },
  { id: 'users', label: '用户', icon: 'users' },
  { id: 'policy', label: '社区规则', icon: 'flag' },
]

const pieTooltip = {
  trigger: 'item' as const,
  formatter: '{b} {c}（{d}%）',
}

const todoOption = computed<EChartsCoreOption>(() => ({
  color: ['#ff2442', '#ff8a73', '#f2c4c4'],
  tooltip: pieTooltip,
  legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: '#8a8a8a', fontSize: 12 } },
  series: [
    {
      type: 'pie',
      radius: ['48%', '70%'],
      label: { show: false },
      data: [
        { name: '待审笔记', value: n('postsPending') },
        { name: '待处理工单', value: n('reportsPending') },
        { name: '帮助申请', value: n('supportPending') },
      ],
    },
  ],
}))

const postOption = computed<EChartsCoreOption>(() => ({
  color: ['#ff2442', '#f2c4c4', '#e8e8e8', '#d4d4d4'],
  tooltip: pieTooltip,
  legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: '#8a8a8a', fontSize: 12 } },
  series: [
    {
      type: 'pie',
      radius: ['48%', '70%'],
      label: { show: false },
      data: [
        { name: '在线', value: n('postsLive') },
        { name: '待审', value: n('postsPending') },
        { name: '未通过', value: n('postsRejected') },
        { name: '已下架', value: n('postsDown') },
      ],
    },
  ],
}))

const todayOption = computed<EChartsCoreOption>(() => ({
  color: ['#ff2442'],
  tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 12, top: 16, bottom: 28 },
  xAxis: {
    type: 'category',
    data: ['新用户', '新笔记', '新评论', '新工单'],
    axisLine: { lineStyle: { color: '#eee' } },
    axisLabel: { color: '#8a8a8a' },
    axisTick: { show: false },
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    splitLine: { lineStyle: { color: '#f2f2f2' } },
    axisLabel: { color: '#8a8a8a' },
  },
  series: [
    {
      type: 'bar',
      barWidth: 22,
      itemStyle: { borderRadius: [8, 8, 0, 0] },
      data: [n('usersToday'), n('postsToday'), n('commentsToday'), n('reportsToday')],
    },
  ],
}))

const reasonLabel = Object.fromEntries(REPORT_REASONS.map((item) => [item.code, item.label]))
const kindLabel: Record<SupportKind, string> = {
  feedback: '帮助反馈',
  live: '主播入驻',
  verify: '大 V 认证',
}
const targetKind: Record<string, string> = {
  post: '笔记',
  comment: '评论',
  user: '用户',
  live: '直播',
}

function clock(iso: string) {
  if (!iso) return '—'
  return iso.replace('T', ' ').slice(0, 16)
}

onMounted(async () => {
  try {
    const [overview, postPage, reportPage, supportPage] = await Promise.all([
      api.opsOverview(),
      api.opsPosts({ taken: 'pending', limit: 6 }),
      api.opsReports('pending', undefined, 6),
      api.opsSupport({ status: 'pending', limit: 6 }),
    ])
    data.value = overview
    posts.value = postPage.items
    reports.value = reportPage.items
    tickets.value = supportPage.items
    const total = overview.postsPending + overview.reportsPending + overview.supportPending
    note.value = total > 0 ? `还有 ${total} 件待办，优先处理审稿和工单。` : '当前没有积压待办。'
  } catch (e) {
    data.value = null
    if (e instanceof ApiError && e.status === 403) {
      note.value = '当前账号没有运营权限。'
    } else {
      note.value = e instanceof ApiError ? e.message : '概览不可用'
    }
  }
})
</script>

<template>
  <div class="home">
    <section class="hero card">
      <div>
        <h1>{{ hello }}，{{ props.nickname || '运营' }}</h1>
        <p class="lead">{{ todayText }} · {{ note }}</p>
      </div>
      <div v-if="data" class="hero-stat">
        <span class="hero-n">{{ todoTotal }}</span>
        <span class="hero-l">待办合计</span>
      </div>
    </section>

    <div v-if="data" class="queues">
      <button
        v-for="item in queues"
        :key="item.id"
        type="button"
        class="queue"
        :class="{ 'is-hot': item.count > 0 }"
        @click="emit('open', item.id)"
      >
        <span class="queue-n">{{ item.count }}</span>
        <span>
          <span class="queue-t">{{ item.title }}</span>
          <span class="queue-h">{{ item.hint }}</span>
        </span>
      </button>
    </div>

    <div v-if="data" class="kpis">
      <button
        v-for="item in kpis"
        :key="item.title"
        type="button"
        class="kpi"
        @click="emit('open', item.id)"
      >
        <span class="kpi-t">{{ item.title }}</span>
        <span class="kpi-n">{{ item.value }}</span>
        <span class="kpi-d">{{ item.deltaLabel }} {{ item.delta }}</span>
      </button>
    </div>

    <section v-if="data" class="card">
      <h2>快捷入口</h2>
      <div class="shortcuts">
        <button
          v-for="item in shortcuts"
          :key="item.id + item.label"
          type="button"
          class="shortcut"
          @click="emit('open', item.id)"
        >
          <Icon :name="item.icon" :size="18" />
          {{ item.label }}
        </button>
      </div>
    </section>

    <div v-if="data" class="charts">
      <section class="card">
        <h2>待办构成</h2>
        <OpsChart :option="todoOption" />
      </section>
      <section class="card">
        <h2>笔记状态</h2>
        <OpsChart :option="postOption" />
      </section>
      <section class="card">
        <h2>今日新增</h2>
        <OpsChart :option="todayOption" />
      </section>
    </div>

    <div v-if="data" class="lists">
      <section class="card">
        <div class="list-head">
          <h2>最新待审</h2>
          <button type="button" class="more" @click="emit('open', 'posts')">全部</button>
        </div>
        <p v-if="!posts.length" class="empty">暂无待审笔记</p>
        <button
          v-for="row in posts"
          :key="row.postId"
          type="button"
          class="row"
          @click="emit('open', 'posts')"
        >
          <span class="row-t">{{ row.title || '无标题' }}</span>
          <span class="row-m">{{ row.author?.nickname || '作者' }} · {{ clock(row.publishedAt) }}</span>
        </button>
      </section>
      <section class="card">
        <div class="list-head">
          <h2>最新工单</h2>
          <button type="button" class="more" @click="emit('open', 'reports')">全部</button>
        </div>
        <p v-if="!reports.length" class="empty">暂无待处理工单</p>
        <button
          v-for="row in reports"
          :key="row.reportId"
          type="button"
          class="row"
          @click="emit('open', 'reports')"
        >
          <span class="row-t">{{ targetKind[row.targetType] || row.targetType }} · {{ reasonLabel[row.reasonCode] || row.reasonCode }}</span>
          <span class="row-m">{{ row.targetTitle || row.reporter?.nickname || row.targetId }} · {{ clock(row.createdAt) }}</span>
        </button>
      </section>
      <section class="card">
        <div class="list-head">
          <h2>最新申请</h2>
          <button type="button" class="more" @click="emit('open', 'support')">全部</button>
        </div>
        <p v-if="!tickets.length" class="empty">暂无帮助申请</p>
        <button
          v-for="row in tickets"
          :key="row.ticketId"
          type="button"
          class="row"
          @click="emit('open', 'support')"
        >
          <span class="row-t">{{ kindLabel[row.kind] }} · {{ row.title || '未填名称' }}</span>
          <span class="row-m">{{ row.user?.nickname || '用户' }} · {{ clock(row.createdAt) }}</span>
        </button>
      </section>
    </div>
  </div>
</template>

<style scoped>
.home {
  flex: 1;
  min-height: 0;
  padding: 0 20px 20px 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card {
  padding: 24px 28px 28px;
  border-radius: 20px;
  background: #fff;
}

.hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 650;
}

h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.lead {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #8a8a8a;
}

.hero-stat {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.hero-n {
  font-size: 32px;
  font-weight: 700;
  color: #ff2442;
  line-height: 1;
}

.hero-l {
  font-size: 12px;
  color: #8a8a8a;
}

.queues {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.queue {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 22px;
  border-radius: 20px;
  background: #fff;
  text-align: left;
}

.queue:hover,
.kpi:hover {
  background: #fafafa;
}

.queue.is-hot .queue-n {
  color: #ff2442;
}

.queue-n {
  font-size: 28px;
  font-weight: 700;
  min-width: 36px;
  line-height: 1;
}

.queue-t,
.kpi-t {
  display: block;
  font-size: 14px;
  font-weight: 600;
}

.queue-h,
.kpi-d {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #8a8a8a;
}

.kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.kpi {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 20px 22px;
  border-radius: 20px;
  background: #fff;
  text-align: left;
}

.kpi-n {
  margin-top: 10px;
  font-size: 24px;
  font-weight: 650;
}

.shortcuts {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.shortcut {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 14px;
  border-radius: 20px;
  background: rgb(248, 248, 248);
  font-size: 13px;
  color: #333;
}

.shortcut:hover {
  background: #eee;
}

.charts,
.lists {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.more {
  font-size: 13px;
  color: #8a8a8a;
}

.more:hover {
  color: #1f1f1f;
}

.row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  padding: 12px 0;
  border-top: 1px solid #f2f2f2;
  text-align: left;
}

.row-t {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-m,
.empty {
  font-size: 12px;
  color: #8a8a8a;
}

.empty {
  margin: 16px 0 0;
}

@media (max-width: 1100px) {
  .queues,
  .kpis,
  .charts,
  .lists {
    grid-template-columns: 1fr;
  }
}
</style>
