<!-- 全站评论：隐藏后社区不再展示。 -->
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { api, ApiError, type OpsCommentRow, type OpsPostView } from '@onepage/api-client'
import EmptyState from './EmptyState.vue'
import Icon from './Icon.vue'
import OpsPager from './OpsPager.vue'
import OpsTargetPreview from './OpsTargetPreview.vue'
import { useCursorPages } from './useCursorPages'

type Hidden = '' | 'no' | 'yes'

const hidden = ref<Hidden>('')
const query = ref('')
const items = ref<OpsCommentRow[]>([])
const { page, pageSize, hasMore, resetCursors, cursorFor, applyResult } = useCursorPages()
const loading = ref(false)
const failed = ref('')
const toast = ref('')
const busyId = ref('')
const previewOpen = ref(false)
const previewPost = ref<OpsPostView | null>(null)
const previewLoading = ref(false)
const previewError = ref('')

const tabs: { id: Hidden; label: string }[] = [
  { id: '', label: '全部' },
  { id: 'no', label: '可见' },
  { id: 'yes', label: '已隐藏' },
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
    const result = await api.opsComments({
      q: query.value.trim() || undefined,
      hidden: hidden.value || undefined,
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
  if (!id) return
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

async function act(row: OpsCommentRow, kind: 'hide' | 'restore') {
  busyId.value = row.commentId
  try {
    if (kind === 'hide') await api.opsHideComment(row.commentId)
    else await api.opsRestoreComment(row.commentId)
    flash(kind === 'hide' ? '已隐藏' : '已恢复')
    await load(page.value)
  } catch (e) {
    flash(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    busyId.value = ''
  }
}

onMounted(() => load(1, true))
watch(hidden, () => load(1, true))
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
            :class="{ 'is-on': hidden === item.id }"
            @click="hidden = item.id"
          >
            {{ item.label }}
          </button>
        </div>
        <label class="search">
          <Icon name="search" :size="16" />
          <input v-model="query" placeholder="搜正文、评论或笔记 ID" @keydown.enter="load(1, true)" />
        </label>
      </div>
      <p v-if="toast" class="toast">{{ toast }}</p>
      <p v-if="failed" class="err">{{ failed }}</p>
      <p v-else-if="loading && !items.length" class="muted">加载中…</p>
      <EmptyState
        v-else-if="!items.length"
        src="/empty-box.png"
        title="没有评论"
        desc="社区评论会出现在这里。"
        compact
      />
      <table v-else class="table">
        <thead>
          <tr>
            <th>内容</th>
            <th>作者</th>
            <th>笔记</th>
            <th>时间</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.commentId">
            <td class="detail">
              {{ row.content }}
              <span v-if="row.hidden" class="tag">已隐藏</span>
            </td>
            <td>{{ row.author?.nickname || row.author?.pageId || '—' }}</td>
            <td>
              <button type="button" class="link" @click="openPost(row.postId)">预览笔记</button>
            </td>
            <td>{{ clock(row.createdAt) }}</td>
            <td class="acts">
              <button
                v-if="!row.hidden"
                type="button"
                class="danger"
                :disabled="busyId === row.commentId"
                @click="act(row, 'hide')"
              >
                隐藏
              </button>
              <button
                v-else
                type="button"
                :disabled="busyId === row.commentId"
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
  width: 240px;
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
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
</style>
