<!-- 私信巡查：会话列表，点开看消息，可隐藏/恢复。 -->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  api,
  ApiError,
  type OpsConversationRow,
  type OpsImMessageRow,
  type OpsPostView,
} from '@onepage/api-client'
import EmptyState from './EmptyState.vue'
import Icon from './Icon.vue'
import OpsPager from './OpsPager.vue'
import OpsTargetPreview from './OpsTargetPreview.vue'
import { useCursorPages } from './useCursorPages'

const query = ref('')
const items = ref<OpsConversationRow[]>([])
const { page, pageSize, hasMore, resetCursors, cursorFor, applyResult } = useCursorPages()
const loading = ref(false)
const failed = ref('')
const toast = ref('')
const busyId = ref('')
const open = ref<OpsConversationRow | null>(null)
const messages = ref<OpsImMessageRow[]>([])
const msgLoading = ref(false)
const msgFailed = ref('')
const previewOpen = ref(false)
const previewPost = ref<OpsPostView | null>(null)
const previewLoading = ref(false)
const previewError = ref('')

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

function nameOf(row: OpsConversationRow) {
  const left = row.left?.nickname || row.left?.pageId || row.left?.userId || '用户'
  const right = row.right?.nickname || row.right?.pageId || row.right?.userId || '用户'
  return `${left} · ${right}`
}

async function load(index = 1, reset = false) {
  if (reset) resetCursors()
  loading.value = true
  failed.value = ''
  try {
    const result = await api.opsConversations({
      q: query.value.trim() || undefined,
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

async function openThread(row: OpsConversationRow) {
  open.value = row
  messages.value = []
  msgFailed.value = ''
  msgLoading.value = true
  try {
    const result = await api.opsConversationMessages(row.conversationId, { limit: 50 })
    messages.value = result.items
  } catch (e) {
    msgFailed.value = e instanceof ApiError ? e.message : '加载失败'
  } finally {
    msgLoading.value = false
  }
}

async function act(row: OpsImMessageRow, kind: 'hide' | 'restore') {
  busyId.value = row.messageId
  try {
    if (kind === 'hide') await api.opsHideImMessage(row.messageId)
    else await api.opsRestoreImMessage(row.messageId)
    flash(kind === 'hide' ? '已对双方隐藏' : '已恢复')
    if (open.value) await openThread(open.value)
  } catch (e) {
    flash(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    busyId.value = ''
  }
}

async function freeze(userId: string) {
  if (!userId) return
  busyId.value = userId
  try {
    await api.opsFreezeUser(userId)
    flash('已冻结发送人')
  } catch (e) {
    flash(e instanceof ApiError ? e.message : '冻结失败')
  } finally {
    busyId.value = ''
  }
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

onMounted(() => load(1, true))
</script>

<template>
  <div class="page">
    <section class="card">
      <div class="head">
        <div>
          <h1>私信</h1>
          <p class="lead">巡查单聊。隐藏后双方会话里不再出现该条。搜用户 ID 或会话 ID。</p>
        </div>
        <div class="tools">
          <label class="search">
            <Icon name="search" :size="16" />
            <input v-model="query" placeholder="用户 ID 或会话 ID" @keydown.enter="load(1, true)" />
          </label>
          <button type="button" class="go" :disabled="loading" @click="load(1, true)">
            {{ loading ? '筛选中…' : '筛选' }}
          </button>
        </div>
      </div>
      <p v-if="toast" class="toast">{{ toast }}</p>
      <p v-if="failed" class="err">{{ failed }}</p>
      <p v-else-if="loading && !items.length" class="muted">加载中…</p>
      <EmptyState
        v-else-if="!items.length"
        src="/empty-box.png"
        title="没有会话"
        desc="社区单聊会出现在这里。须执行 002_ops_hide.sql。"
        compact
      />
      <table v-else class="table">
        <thead>
          <tr>
            <th>双方</th>
            <th>最近一条</th>
            <th>时间</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.conversationId">
            <td>{{ nameOf(row) }}</td>
            <td class="detail">{{ row.preview || '—' }}</td>
            <td>{{ clock(row.updatedAt) }}</td>
            <td class="acts">
              <button type="button" @click="openThread(row)">查看</button>
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
    <div v-if="open" class="mask" @click.self="open = null">
      <section class="sheet">
        <header class="sheet-head">
          <h2>{{ nameOf(open) }}</h2>
          <button type="button" class="x" aria-label="关闭" @click="open = null">×</button>
        </header>
        <p v-if="msgFailed" class="err">{{ msgFailed }}</p>
        <p v-else-if="msgLoading && !messages.length" class="muted">加载中…</p>
        <p v-else-if="!messages.length" class="muted">没有消息</p>
        <ul v-else class="msgs">
          <li v-for="row in messages" :key="row.messageId" :class="{ 'is-hidden': row.hidden }">
            <div class="msg-meta">
              <span>{{ row.sender?.nickname || row.sender?.pageId || '用户' }}</span>
              <span>{{ clock(row.createdAt) }}</span>
              <span v-if="row.hidden" class="tag">已隐藏</span>
            </div>
            <p v-if="row.msgType === 'image' && row.imageUrl" class="msg-body">
              <img :src="row.imageUrl" alt="" referrerpolicy="no-referrer" />
            </p>
            <p v-else class="msg-body">{{ row.preview || row.msgType }}</p>
            <div class="acts">
              <button v-if="row.postId" type="button" @click="openPost(row.postId)">预览笔记</button>
              <button
                v-if="row.sender?.userId"
                type="button"
                :disabled="busyId === row.sender.userId"
                @click="freeze(row.sender.userId)"
              >
                冻结发送人
              </button>
              <button
                v-if="!row.hidden"
                type="button"
                class="danger"
                :disabled="busyId === row.messageId"
                @click="act(row, 'hide')"
              >
                隐藏
              </button>
              <button
                v-else
                type="button"
                :disabled="busyId === row.messageId"
                @click="act(row, 'restore')"
              >
                恢复
              </button>
            </div>
          </li>
        </ul>
      </section>
    </div>
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
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.go {
  height: 36px;
  padding: 0 16px;
  border-radius: 18px;
  background: rgb(248, 248, 248);
  color: #555;
  font-size: 13px;
}

h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.lead,
.toast,
.err,
.muted {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #8a8a8a;
}

.err {
  color: #ff2442;
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
  width: 200px;
  border: 0;
  background: transparent;
  color: #1f1f1f;
  font: inherit;
  outline: none;
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

.mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgb(0 0 0 / 45%);
}

.sheet {
  width: min(640px, calc(100vw - 40px));
  max-height: min(88vh, 720px);
  overflow: auto;
  padding: 20px 24px 24px;
  border-radius: 20px;
  background: #fff;
}

.sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.x {
  width: 32px;
  height: 32px;
  border-radius: 16px;
  color: #8a8a8a;
  font-size: 22px;
  line-height: 1;
}

.msgs {
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
}

.msgs li {
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.msgs li.is-hidden {
  opacity: 0.55;
}

.msg-meta {
  display: flex;
  gap: 10px;
  font-size: 12px;
  color: #8a8a8a;
}

.msg-body {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.5;
}

.msg-body img {
  display: block;
  max-width: 240px;
  max-height: 160px;
  object-fit: cover;
  border-radius: 12px;
}

.tag {
  color: #ff2442;
}
</style>
