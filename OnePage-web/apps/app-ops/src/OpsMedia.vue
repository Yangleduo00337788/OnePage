<!-- 媒体库：巡查 media_asset，可隐藏/恢复、冻结上传人。不删 MinIO。 -->
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import {
  api,
  ApiError,
  type OpsMediaRow,
  type OpsPostView,
} from '@onepage/api-client'
import EmptyState from './EmptyState.vue'
import Icon from './Icon.vue'
import OpsPager from './OpsPager.vue'
import OpsTargetPreview from './OpsTargetPreview.vue'
import { useCursorPages } from './useCursorPages'

type Purpose = '' | 'avatar' | 'cover' | 'post_image' | 'post_video' | 'im_image'
type Hidden = '' | 'no' | 'yes'

const purpose = ref<Purpose>('')
const hidden = ref<Hidden>('')
const query = ref('')
const items = ref<OpsMediaRow[]>([])
const { page, pageSize, hasMore, resetCursors, cursorFor, applyResult } = useCursorPages()
const loading = ref(false)
const failed = ref('')
const toast = ref('')
const busyId = ref('')
const open = ref<OpsMediaRow | null>(null)
const previewOpen = ref(false)
const previewPost = ref<OpsPostView | null>(null)
const previewLoading = ref(false)
const previewError = ref('')

const purposes: { id: Purpose; label: string }[] = [
  { id: '', label: '全部' },
  { id: 'post_image', label: '笔记图' },
  { id: 'post_video', label: '笔记视频' },
  { id: 'avatar', label: '头像' },
  { id: 'cover', label: '封面' },
  { id: 'im_image', label: '私信图' },
]

const visibilities: { id: Hidden; label: string }[] = [
  { id: '', label: '全部' },
  { id: 'no', label: '可见' },
  { id: 'yes', label: '已隐藏' },
]

const purposeLabel: Record<string, string> = {
  avatar: '头像',
  cover: '封面',
  post_image: '笔记图',
  post_video: '笔记视频',
  im_image: '私信图',
}

const statusLabel: Record<string, string> = {
  ready: '已就绪',
  pending: '上传中',
  fail: '失败',
}

function clock(iso: string) {
  if (!iso) return '—'
  return iso.replace('T', ' ').slice(0, 16)
}

function sizeOf(bytes: number) {
  if (!bytes) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function ownerOf(row: OpsMediaRow) {
  return row.owner?.nickname || row.owner?.pageId || row.owner?.userId || '用户'
}

function isVideo(row: OpsMediaRow) {
  return row.mimeType.startsWith('video/') || row.purpose === 'post_video'
}

function flash(text: string) {
  toast.value = text
  window.setTimeout(() => {
    if (toast.value === text) toast.value = ''
  }, 1800)
}

async function load(index = 1, reset = false) {
  if (reset) resetCursors()
  loading.value = true
  failed.value = ''
  try {
    const result = await api.opsMedia({
      q: query.value.trim() || undefined,
      purpose: purpose.value || undefined,
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

async function act(row: OpsMediaRow, kind: 'hide' | 'restore') {
  busyId.value = row.mediaId
  try {
    if (kind === 'hide') await api.opsHideMedia(row.mediaId)
    else await api.opsRestoreMedia(row.mediaId)
    flash(kind === 'hide' ? '已对用户隐藏' : '已恢复')
    if (open.value?.mediaId === row.mediaId) open.value = null
    await load(page.value)
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
    flash('已冻结上传人')
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
watch([purpose, hidden], () => load(1, true))
</script>

<template>
  <div class="page">
    <section class="card">
      <div class="head">
        <div class="tabs">
          <button
            v-for="item in purposes"
            :key="item.id || 'all'"
            type="button"
            class="tab"
            :class="{ 'is-on': purpose === item.id }"
            @click="purpose = item.id"
          >
            {{ item.label }}
          </button>
        </div>
        <div class="tools">
          <div class="tabs">
            <button
              v-for="item in visibilities"
              :key="item.id || 'vis'"
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
            <input v-model="query" placeholder="媒体 ID 或上传人 ID" @keydown.enter="load(1, true)" />
          </label>
        </div>
      </div>
      <p class="lead">隐藏后笔记不再出这张图；头像/封面会从资料拿掉。已发出的私信图请到私信页隐藏该条。不删 MinIO。</p>
      <p v-if="toast" class="toast">{{ toast }}</p>
      <p v-if="failed" class="err">{{ failed }}</p>
      <p v-else-if="loading && !items.length" class="muted">加载中…</p>
      <EmptyState
        v-else-if="!items.length"
        src="/empty-box.png"
        title="没有媒体"
        desc="头像、封面、笔记和私信上传会出现在这里。"
        compact
      />
      <table v-else class="table">
        <thead>
          <tr>
            <th>预览</th>
            <th>用途</th>
            <th>上传人</th>
            <th>大小</th>
            <th>状态</th>
            <th>时间</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.mediaId">
            <td>
              <button type="button" class="thumb" :disabled="!row.url" @click="open = row">
                <img v-if="row.url && !isVideo(row)" :src="row.url" alt="" referrerpolicy="no-referrer" />
                <span v-else-if="row.url">视频</span>
                <span v-else>—</span>
              </button>
            </td>
            <td>
              {{ purposeLabel[row.purpose] || row.purpose || '—' }}
              <span v-if="row.hidden" class="tag">已隐藏</span>
            </td>
            <td>{{ ownerOf(row) }}</td>
            <td>{{ sizeOf(row.sizeBytes) }}</td>
            <td>{{ statusLabel[row.status] || row.status }}</td>
            <td>{{ clock(row.createdAt) }}</td>
            <td class="acts">
              <button v-if="row.postId" type="button" @click="openPost(row.postId)">预览笔记</button>
              <button
                v-if="row.owner?.userId"
                type="button"
                :disabled="busyId === row.owner.userId"
                @click="freeze(row.owner.userId)"
              >
                冻结上传人
              </button>
              <button
                v-if="!row.hidden"
                type="button"
                class="danger"
                :disabled="busyId === row.mediaId"
                @click="act(row, 'hide')"
              >
                隐藏
              </button>
              <button
                v-else
                type="button"
                :disabled="busyId === row.mediaId"
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
    <div v-if="open" class="mask" @click.self="open = null">
      <section class="sheet">
        <header class="sheet-head">
          <h2>{{ purposeLabel[open.purpose] || '媒体' }} · {{ ownerOf(open) }}</h2>
          <button type="button" class="x" aria-label="关闭" @click="open = null">×</button>
        </header>
        <p class="muted">{{ clock(open.createdAt) }} · {{ sizeOf(open.sizeBytes) }}</p>
        <video v-if="open.url && isVideo(open)" class="preview" :src="open.url" controls playsinline />
        <img
          v-else-if="open.url"
          class="preview"
          :src="open.url"
          alt=""
          referrerpolicy="no-referrer"
        />
        <p v-else class="muted">尚未上传完成，没有可预览地址。</p>
        <div class="acts sheet-acts">
          <button v-if="open.postId" type="button" @click="openPost(open.postId)">预览笔记</button>
          <button
            v-if="open.owner?.userId"
            type="button"
            :disabled="busyId === open.owner.userId"
            @click="freeze(open.owner.userId)"
          >
            冻结上传人
          </button>
          <button
            v-if="!open.hidden"
            type="button"
            class="danger"
            :disabled="busyId === open.mediaId"
            @click="act(open, 'hide')"
          >
            隐藏
          </button>
          <button
            v-else
            type="button"
            :disabled="busyId === open.mediaId"
            @click="act(open, 'restore')"
          >
            恢复
          </button>
        </div>
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
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.tabs {
  display: flex;
  flex-wrap: wrap;
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
  width: 200px;
  border: 0;
  background: transparent;
  color: #1f1f1f;
  font: inherit;
  outline: none;
}

.lead,
.toast,
.err,
.muted {
  margin: 12px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #8a8a8a;
}

.err {
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

.sheet-acts {
  margin-top: 16px;
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

.thumb {
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  padding: 0;
  border-radius: 12px;
  background: rgb(248, 248, 248);
  color: #8a8a8a;
  font-size: 12px;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb:disabled {
  cursor: default;
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
  width: min(720px, calc(100vw - 40px));
  max-height: min(88vh, 760px);
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

.preview {
  display: block;
  width: 100%;
  max-height: 520px;
  margin-top: 12px;
  object-fit: contain;
  border-radius: 12px;
  background: rgb(248, 248, 248);
}
</style>
