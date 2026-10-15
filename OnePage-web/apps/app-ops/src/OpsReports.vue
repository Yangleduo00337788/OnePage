<!-- 举报工单：待处理 / 已处理。接口未上线时空态，不造假数据。 -->
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { api, ApiError, type OpsReport } from '@onepage/api-client'
import EmptyState from './EmptyState.vue'
import Icon from './Icon.vue'

type Tab = 'pending' | 'done'

const tab = ref<Tab>('pending')
const query = ref('')
const items = ref<OpsReport[]>([])
const loading = ref(false)
const failed = ref('')
const toast = ref('')
const busyId = ref('')

const tabs: { id: Tab; label: string }[] = [
  { id: 'pending', label: '待处理' },
  { id: 'done', label: '已处理' },
]

function flash(text: string) {
  toast.value = text
  window.setTimeout(() => {
    if (toast.value === text) toast.value = ''
  }, 1800)
}

function targetLabel(item: OpsReport) {
  return item.targetType === 'post' ? `笔记 ${item.targetId}` : `用户 ${item.targetId}`
}

async function load() {
  loading.value = true
  failed.value = ''
  items.value = []
  try {
    const page = await api.opsReports(tab.value)
    const q = query.value.trim().toLowerCase()
    items.value = q
      ? page.items.filter(
          (item) =>
            item.reasonCode.toLowerCase().includes(q) ||
            (item.detail || '').toLowerCase().includes(q) ||
            item.targetId.toLowerCase().includes(q),
        )
      : page.items
  } catch (e) {
    if (e instanceof ApiError && (e.status === 404 || e.status === 501)) {
      failed.value = ''
      items.value = []
    } else {
      failed.value = e instanceof ApiError ? e.message : '加载失败'
    }
  } finally {
    loading.value = false
  }
}

async function resolve(item: OpsReport, action: 'dismiss' | 'takedown' | 'ban') {
  busyId.value = item.reportId
  try {
    await api.opsResolveReport(item.reportId, { action })
    flash(action === 'dismiss' ? '已忽略' : action === 'ban' ? '已封禁' : '已下架')
    await load()
  } catch (e) {
    flash(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    busyId.value = ''
  }
}

onMounted(load)
watch(tab, load)
</script>

<template>
  <div class="page">
    <section class="card">
      <div class="head">
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
        <label class="search">
          <Icon name="search" :size="16" />
          <input v-model="query" placeholder="搜原因、说明、对象 ID" @keydown.enter="load" />
        </label>
      </div>

      <p v-if="toast" class="toast">{{ toast }}</p>
      <p v-if="failed" class="err">{{ failed }}</p>
      <p v-else-if="loading" class="muted">加载中…</p>

      <EmptyState
        v-else-if="!items.length"
        src="/empty-box.png"
        title="没有工单"
        desc="治理服务未接入，或当前状态下没有举报。"
        compact
      />

      <table v-else class="table">
        <thead>
          <tr>
            <th>对象</th>
            <th>原因</th>
            <th>说明</th>
            <th>举报人</th>
            <th>时间</th>
            <th v-if="tab === 'pending'" />
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.reportId">
            <td>{{ targetLabel(item) }}</td>
            <td>{{ item.reasonCode }}</td>
            <td class="detail">{{ item.detail || '—' }}</td>
            <td>{{ item.reporter?.nickname || item.reporter?.pageId || '—' }}</td>
            <td>{{ item.createdAt }}</td>
            <td v-if="tab === 'pending'" class="acts">
              <button type="button" :disabled="busyId === item.reportId" @click="resolve(item, 'dismiss')">
                忽略
              </button>
              <button
                v-if="item.targetType === 'post'"
                type="button"
                class="danger"
                :disabled="busyId === item.reportId"
                @click="resolve(item, 'takedown')"
              >
                下架
              </button>
              <button
                v-if="item.targetType === 'user'"
                type="button"
                class="danger"
                :disabled="busyId === item.reportId"
                @click="resolve(item, 'ban')"
              >
                封禁
              </button>
            </td>
          </tr>
        </tbody>
      </table>
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
  padding: 20px 24px 28px;
  border-radius: 20px;
  background: #fff;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.tabs {
  display: flex;
  gap: 8px;
}

.tab {
  height: 36px;
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

.search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 12px;
  border-radius: 18px;
  background: rgb(248, 248, 248);
  color: #8a8a8a;
}

.search input {
  width: 220px;
  border: 0;
  background: transparent;
  color: #1f1f1f;
  font: inherit;
  outline: none;
}

.toast,
.err,
.muted {
  margin: 12px 0 0;
  font-size: 13px;
}

.err {
  color: #ff2442;
}

.muted,
.toast {
  color: #8a8a8a;
}

.table {
  width: 100%;
  margin-top: 16px;
  border-collapse: collapse;
  font-size: 13px;
}

th,
td {
  padding: 12px 8px;
  border-bottom: 1px solid #f0f0f0;
  text-align: left;
  color: #1f1f1f;
}

th {
  color: #8a8a8a;
  font-weight: 500;
}

.detail {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #555;
}

.acts {
  white-space: nowrap;
}

.acts button {
  height: 32px;
  margin-left: 6px;
  padding: 0 12px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
  color: #555;
  font-size: 13px;
}

.acts button:hover:not(:disabled) {
  background: #eee;
  color: #1f1f1f;
}

.acts .danger {
  color: #ff2442;
}

.acts button:disabled {
  opacity: 0.5;
}
</style>
