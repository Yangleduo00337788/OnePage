<!-- 举报工单：待处理 / 已处理。翻页、备注、对象标题。 -->
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { api, ApiError, REPORT_REASONS, type OpsPostView, type OpsReport, type OpsResolveAction, type OpsUser } from '@onepage/api-client'
import EmptyState from './EmptyState.vue'
import Icon from './Icon.vue'
import OpsTargetPreview from './OpsTargetPreview.vue'

type Tab = 'pending' | 'done'

const tab = ref<Tab>('pending')
const query = ref('')
const note = ref('')
const items = ref<OpsReport[]>([])
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const loading = ref(false)
const failed = ref('')
const toast = ref('')
const busyId = ref('')
const previewOpen = ref(false)
const previewKind = ref<'post' | 'user'>('post')
const previewPost = ref<OpsPostView | null>(null)
const previewUser = ref<OpsUser | null>(null)
const previewLoading = ref(false)
const previewError = ref('')

const tabs: { id: Tab; label: string }[] = [
  { id: 'pending', label: '待处理' },
  { id: 'done', label: '已处理' },
]

const reasonLabel: Record<string, string> = Object.fromEntries(
  REPORT_REASONS.map((item) => [item.code, item.label]),
)

const actionLabel: Record<string, string> = {
  dismiss: '忽略',
  takedown: '下架',
  restore: '恢复',
  ban: '封禁',
  unban: '解冻',
}

function flash(text: string) {
  toast.value = text
  window.setTimeout(() => {
    if (toast.value === text) toast.value = ''
  }, 1800)
}

function targetLabel(item: OpsReport) {
  if (item.targetTitle) return item.targetTitle
  return item.targetType === 'post' ? `笔记 ${item.targetId}` : `用户 ${item.targetId}`
}

async function openTarget(item: OpsReport) {
  previewOpen.value = true
  previewKind.value = item.targetType
  previewPost.value = null
  previewUser.value = null
  previewError.value = ''
  previewLoading.value = true
  try {
    if (item.targetType === 'post') {
      previewPost.value = await api.opsPost(item.targetId)
    } else {
      previewUser.value = await api.opsUser(item.targetId)
    }
  } catch (e) {
    previewError.value = e instanceof ApiError ? e.message : '预览失败'
  } finally {
    previewLoading.value = false
  }
}

function closePreview() {
  previewOpen.value = false
}

function clock(iso: string) {
  if (!iso) return '—'
  return iso.replace('T', ' ').slice(0, 16)
}

function matches(item: OpsReport, q: string) {
  if (!q) return true
  return (
    item.reasonCode.toLowerCase().includes(q) ||
    (item.detail || '').toLowerCase().includes(q) ||
    item.targetId.toLowerCase().includes(q) ||
    (item.targetTitle || '').toLowerCase().includes(q) ||
    (item.targetPageId || '').toLowerCase().includes(q)
  )
}

async function load(reset: boolean) {
  if (reset) {
    items.value = []
    nextCursor.value = null
    hasMore.value = false
  }
  loading.value = true
  failed.value = ''
  try {
    const page = await api.opsReports(tab.value, reset ? undefined : nextCursor.value || undefined)
    const q = query.value.trim().toLowerCase()
    const batch = q ? page.items.filter((item) => matches(item, q)) : page.items
    items.value = reset ? batch : items.value.concat(batch)
    nextCursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } catch (e) {
    if (reset && e instanceof ApiError && (e.status === 404 || e.status === 501)) {
      failed.value = ''
      items.value = []
    } else {
      failed.value = e instanceof ApiError ? e.message : '加载失败'
    }
  } finally {
    loading.value = false
  }
}

async function resolve(item: OpsReport, action: OpsResolveAction) {
  busyId.value = item.reportId
  try {
    await api.opsResolveReport(item.reportId, { action, note: note.value.trim() || undefined })
    flash(actionLabel[action] ? `已${actionLabel[action]}` : '已处理')
    closePreview()
    await load(true)
  } catch (e) {
    flash(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    busyId.value = ''
  }
}

onMounted(() => load(true))
watch(tab, () => load(true))
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
          <input v-model="query" placeholder="搜原因、说明、对象" @keydown.enter="load(true)" />
        </label>
      </div>
      <input
        v-model="note"
        class="note"
        maxlength="500"
        placeholder="处理备注（可选，随忽略/下架/恢复/封禁/解冻一起提交）"
      />

      <p v-if="toast" class="toast">{{ toast }}</p>
      <p v-if="failed" class="err">{{ failed }}</p>
      <p v-else-if="loading && !items.length" class="muted">加载中…</p>

      <EmptyState
        v-else-if="!items.length"
        src="/empty-box.png"
        title="没有工单"
        desc="社区提交举报后会出现在这里。"
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
            <th v-if="tab === 'done'">处理</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.reportId">
            <td>
              <button type="button" class="link" @click="openTarget(item)">{{ targetLabel(item) }}</button>
              <span v-if="item.targetType === 'post' && item.takenDown" class="tag">已下架</span>
            </td>
            <td>{{ reasonLabel[item.reasonCode] || item.reasonCode }}</td>
            <td class="detail">{{ item.detail || '—' }}</td>
            <td>{{ item.reporter?.nickname || item.reporter?.pageId || '—' }}</td>
            <td>{{ clock(item.createdAt) }}</td>
            <td v-if="tab === 'done'" class="detail">
              {{ actionLabel[item.handleAction || ''] || item.handleAction || '—' }}
              <span v-if="item.handler?.nickname"> · {{ item.handler.nickname }}</span>
              <span v-if="item.handleNote"> · {{ item.handleNote }}</span>
            </td>
            <td class="acts">
              <button
                v-if="tab === 'pending'"
                type="button"
                :disabled="busyId === item.reportId"
                @click="resolve(item, 'dismiss')"
              >
                忽略
              </button>
              <button
                v-if="item.targetType === 'post' && !item.takenDown"
                type="button"
                class="danger"
                :disabled="busyId === item.reportId"
                @click="resolve(item, 'takedown')"
              >
                下架
              </button>
              <button
                v-if="item.targetType === 'post' && item.takenDown"
                type="button"
                :disabled="busyId === item.reportId"
                @click="resolve(item, 'restore')"
              >
                恢复
              </button>
              <button
                type="button"
                class="danger"
                :disabled="busyId === item.reportId"
                @click="resolve(item, 'ban')"
              >
                {{ item.targetType === 'post' ? '封禁作者' : '封禁' }}
              </button>
              <button type="button" :disabled="busyId === item.reportId" @click="resolve(item, 'unban')">
                {{ item.targetType === 'post' ? '解冻作者' : '解冻' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <button v-if="hasMore" type="button" class="more" :disabled="loading" @click="load(false)">
        {{ loading ? '加载中…' : '加载更多' }}
      </button>
    </section>
    <OpsTargetPreview
      v-if="previewOpen"
      :kind="previewKind"
      :post="previewPost"
      :user="previewUser"
      :loading="previewLoading"
      :error="previewError"
      @close="closePreview"
    />
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

.note {
  display: block;
  box-sizing: border-box;
  width: 100%;
  height: 36px;
  margin-top: 12px;
  padding: 0 14px;
  border: 0;
  border-radius: 12px;
  background: rgb(248, 248, 248);
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

.link {
  padding: 0;
  color: #1f1f1f;
  text-decoration: none;
  background: none;
}

.link:hover {
  color: #ff2442;
}

.tag {
  margin-left: 8px;
  color: #ff2442;
  font-size: 12px;
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

.more {
  display: block;
  height: 36px;
  margin: 16px auto 0;
  padding: 0 18px;
  border-radius: 18px;
  background: rgb(248, 248, 248);
  color: #555;
  font-size: 13px;
}
</style>
