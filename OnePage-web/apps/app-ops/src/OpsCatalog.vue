<!-- 发布用分类与活动目录，与 content 发帖校验同一份。 -->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, ApiError, type OpsCatalog } from '@onepage/api-client'
import EmptyState from './EmptyState.vue'

const data = ref<OpsCatalog | null>(null)
const failed = ref('')
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    data.value = await api.opsCatalog()
  } catch (e) {
    failed.value = e instanceof ApiError ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page">
    <section class="card">
      <h1>目录</h1>
      <p class="lead">首页 Tab 与发帖分类、可关联活动。直播 / 搜索 / AI 还没有后端，不在这里占位。</p>
      <p v-if="failed" class="err">{{ failed }}</p>
      <p v-else-if="loading" class="muted">加载中…</p>
      <EmptyState
        v-else-if="!data"
        src="/empty-box.png"
        title="没有目录"
        desc="确认 content 与治理服务已启动。"
        compact
      />
      <template v-else>
        <h2>笔记分类</h2>
        <ul class="pills">
          <li v-for="item in data.categories" :key="item">{{ item }}</li>
        </ul>
        <h2>活动</h2>
        <p class="hint">与发帖关联活动对齐。创作者活动中心里未进目录的预告不会出现。</p>
        <ul v-if="data.campaigns.length" class="list">
          <li v-for="row in data.campaigns" :key="row.eventId" class="row">
            <span class="id">{{ row.eventId }}</span>
            <span class="name">{{ row.title }}</span>
          </li>
        </ul>
        <p v-else class="muted">还没有可关联活动。</p>
      </template>
    </section>
  </div>
</template>

<style scoped>
.page {
  flex: 1;
  min-height: 0;
  padding: 0 20px 20px 0;
}

.card {
  min-height: calc(100vh - 92px);
  padding: 24px 28px 28px;
  border-radius: 20px;
  background: #fff;
}

h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.lead,
.hint,
.muted,
.err {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #8a8a8a;
}

.err {
  color: #ff2442;
}

h2 {
  margin: 28px 0 0;
  font-size: 14px;
  font-weight: 600;
}

.pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.pills li {
  height: 32px;
  padding: 0 12px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
  color: #1f1f1f;
  font-size: 13px;
  line-height: 32px;
}

.list {
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.row {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
  font-size: 14px;
}

.id {
  width: 40px;
  color: #8a8a8a;
}

.name {
  color: #1f1f1f;
}
</style>
