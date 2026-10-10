<!-- 活动中心：状态 Tab + 活动卡。P0 占位。 -->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { EVENTS } from './events'

type TabId = 'all' | '进行中' | '预告'

const tab = ref<TabId>('all')
const tabs: { id: TabId; label: string }[] = [
  { id: 'all', label: '全部' },
  { id: '进行中', label: '进行中' },
  { id: '预告', label: '预告' },
]

const list = computed(() => {
  if (tab.value === 'all') return EVENTS
  return EVENTS.filter((item) => item.status === tab.value)
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
          <img :src="item.cover" alt="" />
        </div>
        <div class="body">
          <p class="status" :class="item.status === '进行中' ? 'is-on' : ''">{{ item.status }}</p>
          <h2>{{ item.title }}</h2>
          <p class="time">{{ item.time }}</p>
          <p class="intro">{{ item.intro }}</p>
        </div>
      </article>
    </div>
    <p v-else class="empty">暂时没有这类活动</p>
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
  font-size: 16px;
  font-weight: 600;
}

.tabs {
  display: flex;
  gap: 8px;
}

.tab {
  height: 32px;
  padding: 0 14px;
  border-radius: 20px;
  color: #555;
  font-size: 14px;
}

.tab:hover,
.tab.is-on {
  color: #1f1f1f;
  font-weight: 600;
  background: rgb(248, 248, 248);
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 20px;
}

.card {
  display: flex;
  gap: 14px;
  padding: 14px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
}

.cover {
  overflow: hidden;
  flex-shrink: 0;
  width: 72px;
  height: 72px;
  border-radius: 12px;
  background: #eee;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.status {
  margin: 0;
  color: #8a8a8a;
  font-size: 12px;
}

.status.is-on {
  color: #ff2442;
}

.body {
  min-width: 0;
  padding: 0;
}

h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.time {
  margin: 8px 0 0;
  color: #8a8a8a;
  font-size: 12px;
}

.intro {
  margin: 10px 0 0;
  color: #555;
  font-size: 13px;
  line-height: 1.6;
}

.empty {
  margin: 80px 0 0;
  color: #8a8a8a;
  font-size: 14px;
  text-align: center;
}

@media (max-width: 1100px) {
  .grid {
    grid-template-columns: 1fr;
  }
}

</style>
