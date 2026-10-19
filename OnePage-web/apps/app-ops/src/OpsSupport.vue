<!-- 帮助反馈 / 主播入驻 / 大 V 申请。备注后结案，不自动开通权限。 -->
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { api, ApiError, type OpsSupport, type SupportKind } from '@onepage/api-client'
import EmptyState from './EmptyState.vue'
import OpsPager from './OpsPager.vue'
import { useCursorPages } from './useCursorPages'

type StatusTab = 'pending' | 'done'
type KindTab = 'all' | SupportKind

const status = ref<StatusTab>('pending')
const kind = ref<KindTab>('all')
const items = ref<OpsSupport[]>([])
const dialog = ref<{ item: OpsSupport; action: 'reply' | 'approve' | 'reject' } | null>(null)
const draft = ref('')
const { page, pageSize, hasMore, resetCursors, cursorFor, applyResult } = useCursorPages()
const loading = ref(false)
const failed = ref('')
const toast = ref('')
const busyId = ref('')

const statusTabs: { id: StatusTab; label: string }[] = [
  { id: 'pending', label: '待处理' },
  { id: 'done', label: '已处理' },
]
const kindTabs: { id: KindTab; label: string }[] = [
  { id: 'all', label: '全部' },
  { id: 'feedback', label: '帮助反馈' },
  { id: 'live', label: '主播入驻' },
  { id: 'verify', label: '大 V 认证' },
]

const kindLabel: Record<SupportKind, string> = {
  feedback: '帮助反馈',
  live: '主播入驻',
  verify: '大 V 认证',
}

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
    const result = await api.opsSupport({
      kind: kind.value === 'all' ? undefined : kind.value,
      status: status.value,
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

function openResolve(item: OpsSupport, action: 'reply' | 'approve' | 'reject') {
  dialog.value = { item, action }
  draft.value = ''
}

async function confirmResolve() {
  const current = dialog.value
  const text = draft.value.trim()
  if (!current) return
  if (!text) {
    flash('请填写回复内容')
    return
  }
  busyId.value = current.item.ticketId
  try {
    await api.opsResolveSupport(current.item.ticketId, { action: current.action, note: text })
    flash(current.action === 'approve' ? '已通过' : current.action === 'reject' ? '已驳回' : '已回复')
    dialog.value = null
    draft.value = ''
    await load(1, true)
  } catch (e) {
    flash(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    busyId.value = ''
  }
}

function dialogTitle(action: 'reply' | 'approve' | 'reject') {
  if (action === 'approve') return '通过申请'
  if (action === 'reject') return '驳回申请'
  return '回复结案'
}

onMounted(() => load(1, true))
watch([status, kind], () => load(1, true))
</script>

<template>
  <div class="page">
    <section class="card">
      <div class="head">
        <div class="tabs">
          <button
            v-for="item in statusTabs"
            :key="item.id"
            type="button"
            class="tab"
            :class="{ 'is-on': status === item.id }"
            @click="status = item.id"
          >
            {{ item.label }}
          </button>
        </div>
        <div class="tabs">
          <button
            v-for="item in kindTabs"
            :key="item.id"
            type="button"
            class="tab"
            :class="{ 'is-on': kind === item.id }"
            @click="kind = item.id"
          >
            {{ item.label }}
          </button>
        </div>
      </div>
      <p v-if="toast" class="toast">{{ toast }}</p>
      <p v-if="failed" class="err">{{ failed }}</p>
      <p v-else-if="loading && !items.length" class="muted">加载中…</p>
      <EmptyState
        v-else-if="!items.length"
        src="/empty-box.png"
        title="没有申请"
        desc="社区帮助反馈或入驻认证提交后会出现在这里。"
        compact
      />
      <table v-else class="table">
        <thead>
          <tr>
            <th>类型</th>
            <th>内容</th>
            <th>联系</th>
            <th>用户</th>
            <th>时间</th>
            <th v-if="status === 'done'">处理</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.ticketId">
            <td>{{ kindLabel[item.kind] }}</td>
            <td class="detail">
              <div class="content">
                <div class="copy">
                  <strong v-if="item.title">{{ item.title }}</strong>
                  <span>{{ item.body || '—' }}</span>
                </div>
                <img v-if="item.mediaUrls?.[0]" class="cover" :src="item.mediaUrls[0]" alt="" />
              </div>
            </td>
            <td>{{ item.contact || '—' }}</td>
            <td>{{ item.user?.nickname || item.user?.pageId || '—' }}</td>
            <td>{{ clock(item.createdAt) }}</td>
            <td v-if="status === 'done'" class="detail">
              <span v-if="item.handler?.nickname">{{ item.handler.nickname }}</span>
              <span v-if="item.handleNote"> · {{ item.handleNote }}</span>
              <span v-if="!item.handler?.nickname && !item.handleNote">—</span>
            </td>
            <td class="acts">
              <button
                v-if="status === 'pending' && item.kind === 'feedback'"
                type="button"
                :disabled="busyId === item.ticketId"
                @click="openResolve(item, 'reply')"
              >
                回复结案
              </button>
              <template v-else-if="status === 'pending'">
                <button type="button" :disabled="busyId === item.ticketId" @click="openResolve(item, 'approve')">
                  通过
                </button>
                <button
                  type="button"
                  class="danger"
                  :disabled="busyId === item.ticketId"
                  @click="openResolve(item, 'reject')"
                >
                  驳回
                </button>
              </template>
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
    <div v-if="dialog" class="mask" @click.self="dialog = null">
      <section class="sheet" role="dialog" aria-modal="true">
        <h2>{{ dialogTitle(dialog.action) }}</h2>
        <p class="hint">回复会出现在对方通知里，必填。</p>
        <textarea v-model="draft" maxlength="500" rows="5" placeholder="填写回复内容" />
        <div class="sheet-acts">
          <button type="button" @click="dialog = null">取消</button>
          <button type="button" class="primary" :disabled="busyId === dialog.item.ticketId" @click="confirmResolve">
            确定
          </button>
        </div>
      </section>
    </div>
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
.content {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.copy {
  min-width: 0;
  flex: 1;
}
.cover {
  width: 72px;
  height: 72px;
  flex-shrink: 0;
  object-fit: cover;
  border-radius: 10px;
  background: #f5f5f5;
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
  vertical-align: top;
}
th {
  color: #8a8a8a;
  font-weight: 500;
}
.detail {
  max-width: 360px;
  color: #555;
}
.detail strong {
  display: block;
  margin-bottom: 4px;
  color: #1f1f1f;
}
.acts {
  display: flex;
  gap: 6px;
}
.acts button {
  height: 32px;
  padding: 0 12px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
  color: #555;
  font-size: 13px;
}
.acts button.danger {
  background: #fff1f0;
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
  width: min(440px, calc(100vw - 40px));
  padding: 20px 24px 16px;
  border-radius: 20px;
  background: #fff;
}
.sheet h2 {
  margin: 0;
  font-size: 16px;
}
.hint {
  margin: 8px 0 12px;
  color: #8a8a8a;
  font-size: 13px;
}
.sheet textarea {
  display: block;
  box-sizing: border-box;
  width: 100%;
  min-height: 120px;
  padding: 12px 14px;
  border: 0;
  border-radius: 12px;
  background: rgb(248, 248, 248);
  color: #1f1f1f;
  font: inherit;
  resize: vertical;
  outline: none;
}
.sheet-acts {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
.sheet-acts button {
  height: 36px;
  padding: 0 16px;
  border-radius: 18px;
  background: rgb(248, 248, 248);
  color: #555;
  font-size: 13px;
}
.sheet-acts .primary {
  background: #1f1f1f;
  color: #fff;
}
</style>
