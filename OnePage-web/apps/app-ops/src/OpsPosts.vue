<!-- 全站笔记：审稿、预览、下架、恢复。已下架也能看。 -->
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { api, ApiError, type OpsPostRow, type OpsPostView } from '@onepage/api-client'
import EmptyState from './EmptyState.vue'
import Icon from './Icon.vue'
import OpsDialog from './OpsDialog.vue'
import OpsPager from './OpsPager.vue'
import OpsTargetPreview from './OpsTargetPreview.vue'
import { useCursorPages } from './useCursorPages'

type Taken = '' | 'pending' | 'live' | 'rejected' | 'down'

const taken = ref<Taken>('pending')
const query = ref('')
const items = ref<OpsPostRow[]>([])
const { page, pageSize, hasMore, resetCursors, cursorFor, applyResult } = useCursorPages()
const loading = ref(false)
const failed = ref('')
const toast = ref('')
const busyId = ref('')
const previewOpen = ref(false)
const previewPost = ref<OpsPostView | null>(null)
const previewLoading = ref(false)
const previewError = ref('')
const rejectRow = ref<OpsPostRow | null>(null)
const rejectReason = ref('')

const tabs: { id: Taken; label: string }[] = [
  { id: '', label: '全部' },
  { id: 'pending', label: '待审' },
  { id: 'live', label: '在线' },
  { id: 'rejected', label: '未通过' },
  { id: 'down', label: '已下架' },
]

function flash(text: string) {
  toast.value = text
  window.setTimeout(() => {
    if (toast.value === text) toast.value = ''
  }, 1800)
}

function clock(iso: string) {
  if (!iso) return '—'
  return iso.replace('T', ' ').slice(0, 16)
}

async function load(index = 1, reset = false) {
  if (reset) resetCursors()
  loading.value = true
  failed.value = ''
  try {
    const result = await api.opsPosts({
      q: query.value.trim() || undefined,
      taken: taken.value || undefined,
      cursor: cursorFor(index),
      limit: pageSize.value,
    })
    items.value = result.items
    applyResult(index, result.nextCursor, result.hasMore)
  } catch (e) {
    failed.value = e instanceof ApiError ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function setSize(size: number) {
  pageSize.value = size
  void load(1, true)
}

async function openPost(id: string) {
  previewOpen.value = true
  previewPost.value = null
  previewError.value = ''
  previewLoading.value = true
  try {
    previewPost.value = await api.opsPost(id)
  } catch (e) {
    previewError.value = e instanceof ApiError ? e.message : '预览失败'
  } finally {
    previewLoading.value = false
  }
}

function reviewLabel(status?: string) {
  if (status === 'pending') return '待审'
  if (status === 'rejected') return '未通过'
  if (status === 'published') return ''
  return ''
}

function aiLabel(row: OpsPostRow) {
  if (row.reviewStatus !== 'pending') {
    return row.aiReviewScore != null ? `风险 ${row.aiReviewScore}` : ''
  }
  const score = row.aiReviewScore != null ? `风险 ${row.aiReviewScore}` : ''
  if (row.aiReview === 'flag') return score ? `进人审 · ${score}` : '进人审'
  if (row.aiReview === 'skip') return '待人审'
  if (!row.aiReview) return '机审中'
  return score
}

function openReject(row: OpsPostRow) {
  rejectRow.value = row
  rejectReason.value = ''
}

function closeReject() {
  rejectRow.value = null
  rejectReason.value = ''
}

async function confirmReject() {
  const row = rejectRow.value
  const reason = rejectReason.value.trim()
  if (!row) return
  if (!reason) {
    flash('请填写回复信息')
    return
  }
  await act(row, 'reject', reason)
}

async function act(row: OpsPostRow, kind: 'approve' | 'reject' | 'takedown' | 'restore', reason?: string) {
  busyId.value = row.postId
  try {
    if (kind === 'approve') await api.opsApprovePost(row.postId)
    else if (kind === 'reject') await api.opsRejectPost(row.postId, reason)
    else if (kind === 'takedown') await api.opsTakedownPost(row.postId)
    else await api.opsRestorePost(row.postId)
    const msg =
      kind === 'approve' ? '已通过' : kind === 'reject' ? '已驳回' : kind === 'takedown' ? '已下架' : '已恢复'
    flash(msg)
    previewOpen.value = false
    closeReject()
    await load(page.value)
  } catch (e) {
    flash(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    busyId.value = ''
  }
}

onMounted(() => load(1, true))
watch(taken, () => load(1, true))
</script>

<template>
  <div class="page">
    <section class="card">
      <div class="head">
        <div class="tabs">
          <button
            v-for="item in tabs"
            :key="item.id || 'all'"
            type="button"
            class="tab"
            :class="{ 'is-on': taken === item.id }"
            @click="taken = item.id"
          >
            {{ item.label }}
          </button>
        </div>
        <label class="search">
          <Icon name="search" :size="16" />
          <input v-model="query" placeholder="搜标题或笔记 ID" @keydown.enter="load(1, true)" />
        </label>
      </div>
      <p v-if="toast" class="toast">{{ toast }}</p>
      <p v-if="failed" class="err">{{ failed }}</p>
      <p v-else-if="loading && !items.length" class="muted">加载中…</p>
      <EmptyState
        v-else-if="!items.length"
        src="/empty-box.png"
        title="没有笔记"
        desc="社区发布后会出现在这里。"
        compact
      />
      <table v-else class="table">
        <thead>
          <tr>
            <th>标题</th>
            <th>作者</th>
            <th>分类</th>
            <th>机审</th>
            <th>时间</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.postId">
            <td>
              <button type="button" class="link" @click="openPost(row.postId)">
                {{ row.title || `笔记 ${row.postId}` }}
              </button>
              <span v-if="row.takenDown" class="tag">已下架</span>
              <span v-else-if="reviewLabel(row.reviewStatus)" class="tag">{{ reviewLabel(row.reviewStatus) }}</span>
            </td>
            <td>{{ row.author?.nickname || row.author?.pageId || '—' }}</td>
            <td>{{ row.category || '—' }}</td>
            <td>
              <span v-if="aiLabel(row)" class="tag">{{ aiLabel(row) }}</span>
              <span v-else>—</span>
            </td>
            <td>{{ clock(row.publishedAt) }}</td>
            <td class="acts">
              <button
                v-if="row.reviewStatus === 'pending' || row.reviewStatus === 'rejected'"
                type="button"
                :disabled="busyId === row.postId"
                @click="act(row, 'approve')"
              >
                通过
              </button>
              <button
                v-if="row.reviewStatus === 'pending'"
                type="button"
                class="danger"
                :disabled="busyId === row.postId"
                @click="openReject(row)"
              >
                驳回
              </button>
              <button
                v-else-if="!row.takenDown && row.reviewStatus === 'published'"
                type="button"
                class="danger"
                :disabled="busyId === row.postId"
                @click="act(row, 'takedown')"
              >
                下架
              </button>
              <button
                v-else-if="row.takenDown"
                type="button"
                :disabled="busyId === row.postId"
                @click="act(row, 'restore')"
              >
                恢复
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
      kind="post"
      :post="previewPost"
      :user="null"
      :loading="previewLoading"
      :error="previewError"
      @close="previewOpen = false"
    />
    <OpsDialog v-if="rejectRow" title="回复作者" @close="closeReject">
      <label class="form">
        回复信息
        <textarea v-model="rejectReason" maxlength="200" rows="5" placeholder="说明未通过原因，作者会在创作者中心看到" />
      </label>
      <p class="muted">{{ rejectReason.length }}/200</p>
      <template #footer>
        <button type="button" class="ghost" @click="closeReject">取消</button>
        <button
          type="button"
          class="go"
          :disabled="busyId === rejectRow.postId || !rejectReason.trim()"
          @click="confirmReject"
        >
          确定驳回
        </button>
      </template>
    </OpsDialog>
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

.link {
  padding: 0;
  color: #1f1f1f;
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

.acts button {
  height: 32px;
  margin-left: 6px;
  padding: 0 12px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
  color: #555;
  font-size: 13px;
}

.acts .danger {
  color: #ff2442;
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

.form {
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: #555;
  font-size: 13px;
}

.form textarea {
  width: 100%;
  padding: 10px 12px;
  border: 0;
  border-radius: 12px;
  background: rgb(248, 248, 248);
  color: #1f1f1f;
  font: inherit;
  resize: vertical;
  outline: none;
}
</style>
