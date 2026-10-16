<!-- 工作台：数字卡片 + ECharts。 -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, ApiError, type OpsOverview } from '@onepage/api-client'
import type { EChartsCoreOption } from 'echarts/core'
import OpsChart from './OpsChart.vue'

const emit = defineEmits<{ open: [string] }>()

const data = ref<OpsOverview | null>(null)
const note = ref('正在加载概览…')

onMounted(async () => {
  try {
    data.value = await api.opsOverview()
    note.value = '社区用户、笔记、评论与工单；分类 / 活动 / 话题来自内容目录。AI 在左侧按模型 / 耶耶 / 发帖分开。'
  } catch (e) {
    data.value = null
    if (e instanceof ApiError && e.status === 403) {
      note.value = '当前账号没有运营权限。'
    } else {
      note.value = e instanceof ApiError ? e.message : '概览不可用'
    }
  }
})

const cards: { id: string; title: string; key: keyof OpsOverview }[] = [
  { id: 'users', title: '社区用户', key: 'communityUsers' },
  { id: 'posts', title: '在线笔记', key: 'postsLive' },
  { id: 'posts', title: '已下架', key: 'postsDown' },
  { id: 'comments', title: '评论', key: 'comments' },
  { id: 'reports', title: '待处理工单', key: 'reportsPending' },
  { id: 'reports', title: '已处理工单', key: 'reportsDone' },
  { id: 'categories', title: '分类', key: 'categories' },
  { id: 'campaigns', title: '活动', key: 'campaigns' },
  { id: 'topics', title: '话题', key: 'topics' },
]

const pieTooltip = {
  trigger: 'item' as const,
  formatter: '{b} {c}（{d}%）',
}

const postOption = computed<EChartsCoreOption>(() => {
  const live = data.value?.postsLive ?? 0
  const down = data.value?.postsDown ?? 0
  return {
    color: ['#ff2442', '#e8e8e8'],
    tooltip: pieTooltip,
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: '#8a8a8a', fontSize: 12 } },
    series: [
      {
        type: 'pie',
        radius: ['48%', '70%'],
        avoidLabelOverlap: true,
        label: { show: false },
        data: [
          { name: '在线', value: live },
          { name: '已下架', value: down },
        ],
      },
    ],
  }
})

const reportOption = computed<EChartsCoreOption>(() => {
  const pending = data.value?.reportsPending ?? 0
  const done = data.value?.reportsDone ?? 0
  return {
    color: ['#ff2442', '#e8e8e8'],
    tooltip: pieTooltip,
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: '#8a8a8a', fontSize: 12 } },
    series: [
      {
        type: 'pie',
        radius: ['48%', '70%'],
        label: { show: false },
        data: [
          { name: '待处理', value: pending },
          { name: '已处理', value: done },
        ],
      },
    ],
  }
})

const catalogOption = computed<EChartsCoreOption>(() => ({
  color: ['#ff2442'],
  tooltip: { trigger: 'axis' },
  grid: { left: 48, right: 16, top: 16, bottom: 28 },
  xAxis: {
    type: 'category',
    data: ['分类', '活动', '话题'],
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
      data: [data.value?.categories ?? 0, data.value?.campaigns ?? 0, data.value?.topics ?? 0],
    },
  ],
}))
</script>

<template>
  <div class="home">
    <section class="card">
      <h1>工作台</h1>
      <p class="lead">{{ note }}</p>
      <div v-if="data" class="stats">
        <button
          v-for="item in cards"
          :key="item.key"
          type="button"
          class="stat"
          @click="emit('open', item.id)"
        >
          <span class="n">{{ data[item.key] }}</span>
          <span class="l">{{ item.title }}</span>
        </button>
      </div>
    </section>
    <div v-if="data" class="charts">
      <section class="card">
        <h2>笔记状态</h2>
        <OpsChart :option="postOption" />
      </section>
      <section class="card">
        <h2>工单处理</h2>
        <OpsChart :option="reportOption" />
      </section>
      <section class="card">
        <h2>内容目录</h2>
        <OpsChart :option="catalogOption" />
      </section>
    </div>
  </div>
</template>

<style scoped>
.home {
  flex: 1;
  min-height: 0;
  padding: 0 20px 20px 0;
}

.card {
  padding: 24px 28px 28px;
  border-radius: 20px;
  background: #fff;
}

h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

h2 {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 600;
}

.lead {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #8a8a8a;
}

.stats {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 24px;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  min-width: 140px;
  padding: 16px 18px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
  text-align: left;
}

.stat:hover {
  background: #eee;
}

.n {
  font-size: 22px;
  font-weight: 650;
  color: #1f1f1f;
}

.l {
  font-size: 13px;
  color: #8a8a8a;
}

.charts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

@media (max-width: 1100px) {
  .charts {
    grid-template-columns: 1fr;
  }
}
</style>
