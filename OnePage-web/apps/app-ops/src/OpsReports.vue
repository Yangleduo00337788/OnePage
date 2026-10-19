<!-- 举报工单：待处理 / 已处理。翻页、备注、对象标题。 -->
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { api, ApiError, REPORT_REASONS, type OpsCommentRow, type OpsLiveView, type OpsPostView, type OpsReport, type OpsResolveAction, type OpsUser } from '@onepage/api-client'
import EmptyState from './EmptyState.vue'
import Icon from './Icon.vue'
import OpsPager from './OpsPager.vue'
import OpsTargetPreview from './OpsTargetPreview.vue'
import { useCursorPages } from './useCursorPages'

type Tab = 'pending' | 'done'

const tab = ref<Tab>('pending')
const query = ref('')
const note = ref('')
const items = ref<OpsReport[]>([])
const { page, pageSize, hasMore, resetCursors, cursorFor, applyResult } = useCursorPages()
const loading = ref(false)
const failed = ref('')
const toast = ref('')
const busyId = ref('')
const previewOpen = ref(false)
const previewKind = ref<'post' | 'user' | 'comment' | 'live'>('post')
const previewPost = ref<OpsPostView | null>(null)
const previewUser = ref<OpsUser | null>(null)
const previewComment = ref<OpsCommentRow | null>(null)
const previewLive = ref<OpsLiveView | null>(null)
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

function actionName(item: OpsReport, action: string) {
  if (item.targetType === 'comment' && action === 'takedown') return '删除评论'
  if (item.targetType === 'comment' && action === 'ban') return '封禁用户'
  if (item.targetType === 'comment' && action === 'unban') return '解冻用户'
  if (item.targetType === 'live' && action === 'takedown') return '关播'
  if (item.targetType === 'live' && action === 'ban') return '封禁主播'
  if (item.targetType === 'live' && action === 'unban') return '解冻主播'
  if (item.targetType === 'post' && action === 'ban') return '封禁作者'
  if (item.targetType === 'post' && action === 'unban') return '解冻作者'
  return actionLabel[action] || action
}

function flash(text: string) {
  toast.value = text
  window.setTimeout(() => {
    if (toast.value === text) toast.value = ''
  }, 1800)
}

function targetLabel(item: OpsReport) {
  if (item.targetTitle) {
    if (item.targetType === 'comment') return `评论：${item.targetTitle}`
    if (item.targetType === 'live') return `直播：${item.targetTitle}`
    return item.targetTitle
  }
  if (item.targetType === 'post') return `笔记 ${item.targetId}`
  if (item.targetType === 'comment') return `评论 ${item.targetId}`
  if (item.targetType === 'live') return `直播 ${item.targetId}`
  return `用户 ${item.targetId}`
}

async function openTarget(item: OpsReport) {
  previewOpen.value = true
  previewKind.value = item.targetType
  previewPost.value = null
  previewUser.value = null
  previewComment.value = null
  previewLive.value = null
  previewError.value = ''
  previewLoading.value = true
  try {
    if (item.targetType === 'post') {
      previewPost.value = await api.opsPost(item.targetId)
    } else if (item.targetType === 'comment') {
      const page = await api.opsComments({ q: item.targetId, limit: 1 })
      previewComment.value = page.items.find((row) => row.commentId === item.targetId) || page.items[0] || null
      if (!previewComment.value) previewError.value = '评论不存在或已删除'
    } else if (item.targetType === 'live') {
      previewLive.value = await api.opsLive(item.targetId)
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

async function load(index = 1, reset = false) {
  if (reset) resetCursors()
  loading.value = true
  failed.value = ''
  try {
    const result = await api.opsReports(tab.value, cursorFor(index), pageSize.value)
    const q = query.value.trim().toLowerCase()
    items.value = q ? result.items.filter((item) => matches(item, q)) : result.items
    applyResult(index, result.nextCursor, result.hasMore)
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

function setSize(size: number) {
  pageSize.value = size
  void load(1, true)
}

async function resolve(item: OpsReport, action: OpsResolveAction) {
  busyId.value = item.reportId
  try {
    await api.opsResolveReport(item.reportId, { action, note: note.value.trim() || undefined })
    flash(`已${actionName(item, action)}`)
    closePreview()
    await load(1, true)
  } catch (e) {
    flash(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    busyId.value = ''
  }
}

onMounted(() => load(1, true))
watch(tab, () => load(1, true))
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
          <input v-model="query" placeholder="搜原因、说明、对象" @keydown.enter="load(1, true)" />
        </label>
      </div>
      <input
        v-model="note"
        class="note"
        maxlength="500"
        placeholder="处理备注（可选，随忽略/下架或删评/恢复/封禁/解冻一起提交）"
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
              <span v-else-if="item.targetType === 'comment' && item.takenDown" class="tag">已删除</span>
              <span v-else-if="item.targetType === 'live' && item.takenDown" class="tag">已关播</span>
            </td>
            <td>{{ reasonLabel[item.reasonCode] || item.reasonCode }}</td>
            <td class="detail">{{ item.detail || '—' }}</td>
            <td>{{ item.reporter?.nickname || item.reporter?.pageId || '—' }}</td>
            <td>{{ clock(item.createdAt) }}</td>
            <td v-if="tab === 'done'" class="detail">
              {{ actionName(item, item.handleAction || '') || '—' }}
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
                v-if="item.targetType === 'comment' && !item.takenDown"
                type="button"
                class="danger"
                :disabled="busyId === item.reportId"
                @click="resolve(item, 'takedown')"
              >
                删除评论
              </button>
              <button
                v-if="item.targetType === 'live' && !item.takenDown"
                type="button"
                class="danger"
                :disabled="busyId === item.reportId"
                @click="resolve(item, 'takedown')"
              >
                关播
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
                v-if="item.targetType === 'comment' && item.takenDown"
                type="button"
                :disabled="busyId === item.reportId"
                @click="resolve(item, 'restore')"
              >
                恢复评论
              </button>
              <button
                type="button"
                class="danger"
                :disabled="busyId === item.reportId"
                @click="resolve(item, 'ban')"
              >
                {{
                  item.targetType === 'user'
                    ? '封禁'
                    : item.targetType === 'comment'
                      ? '封禁用户'
                      : item.targetType === 'live'
                        ? '封禁主播'
                        : '封禁作者'
                }}
              </button>
              <button type="button" :disabled="busyId === item.reportId" @click="resolve(item, 'unban')">
                {{
                  item.targetType === 'user'
                    ? '解冻'
                    : item.targetType === 'comment'
                      ? '解冻用户'
                      : item.targetType === 'live'
                        ? '解冻主播'
                        : '解冻作者'
                }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <OpsPager
        v-if="items.length || page > 1"
        :page="page"
        :page-size="pageSize"
        :has-more="hasMore"
        :loading="loading"
        @prev="load(page - 1)"
        @next="load(page + 1)"
        @update:page-size="setSize"
      />
    </section>
    <OpsTargetPreview
      v-if="previewOpen"
      :kind="previewKind"
      :post="previewPost"
      :user="previewUser"
      :comment="previewComment"
      :live="previewLive"
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
