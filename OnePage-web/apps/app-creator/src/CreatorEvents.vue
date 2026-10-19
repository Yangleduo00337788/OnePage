<!-- 活动中心：状态 Tab + 活动卡。只读 GET /catalog，空则空态。 -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '@onepage/api-client'

type TabId = 'all' | '进行中' | '预告'

const tab = ref<TabId>('all')
const tabs: { id: TabId; label: string }[] = [
  { id: 'all', label: '全部' },
  { id: '进行中', label: '进行中' },
  { id: '预告', label: '预告' },
]

const items = ref<{ id: string; title: string; time: string; status: string; intro: string; cover: string }[]>([])

const list = computed(() => {
  if (tab.value === 'all') return items.value.filter((item) => item.status !== '结束')
  return items.value.filter((item) => item.status === tab.value)
})

function labelOf(status: string) {
  if (status === 'live') return '进行中'
  if (status === 'upcoming') return '预告'
  if (status === 'ended') return '结束'
  return status
}

onMounted(() => {
  void api
    .catalog()
    .then((data) => {
      const rows = (data.campaigns || []).filter((row) => row.enabled)
      items.value = rows.map((row) => ({
        id: row.eventId,
        title: row.title,
        time: row.intro || '',
        status: labelOf(row.status),
        intro: row.intro || '',
        cover: row.coverUrl || '',
      }))
    })
    .catch(() => {
      items.value = []
    })
})
</script>

<template>
  <div class="page">
    <div class="bar">
      <h1>活动中心</h1>
      <div class="tabs">
        <button
          v-for="item in tabs"
          :key="item.id"
          type="button"
          class="tab"
          :class="{ 'is-on': tab === item.id }"
          @click="tab = item.id"
        >
          {{ item.label }}
        </button>
      </div>
    </div>

    <div v-if="list.length" class="grid">
      <article v-for="item in list" :key="item.id" class="card">
        <div class="cover">
          <img v-if="item.cover" :src="item.cover" alt="" />
        </div>
        <div class="body">
          <p class="status" :class="item.status === '进行中' ? 'is-on' : ''">{{ item.status }}</p>
          <h2>{{ item.title }}</h2>
          <p class="time">{{ item.time }}</p>
          <p class="intro">{{ item.intro }}</p>
        </div>
      </article>
    </div>
    <div v-else class="empty">
      <img class="empty-art" src="/empty-box.png" alt="" width="160" height="160" />
      <p>暂时没有这类活动</p>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  margin: 0 20px 20px 0;
  padding: 20px 24px 24px;
  box-sizing: border-box;
  overflow: auto;
  border-radius: 20px;
  background: #fff;
}

.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.tabs {
  display: flex;
  gap: 8px;
}

.tab {
  height: 32px;
  padding: 0 12px;
  border-radius: 16px;
  color: #8a8a8a;
  font-size: 13px;
}

.tab.is-on {
  background: rgb(248, 248, 248);
  color: #1f1f1f;
  font-weight: 600;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 20px;
}

.card {
  display: flex;
  gap: 16px;
  padding: 12px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
}

.cover {
  width: 88px;
  height: 88px;
  overflow: hidden;
  flex-shrink: 0;
  border-radius: 12px;
  background: #eee;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.body {
  min-width: 0;
}

.status {
  margin: 0;
  font-size: 12px;
  color: #8a8a8a;
}

.status.is-on {
  color: #ff2442;
}

h2 {
  margin: 6px 0 0;
  font-size: 15px;
}

.time,
.intro {
  margin: 6px 0 0;
  color: #8a8a8a;
  font-size: 13px;
  line-height: 1.5;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 280px;
  color: #8a8a8a;
  font-size: 14px;
}

.empty-art {
  display: block;
  margin-bottom: 12px;
}
</style>

