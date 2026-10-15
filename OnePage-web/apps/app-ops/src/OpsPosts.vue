<!-- 全站笔记：预览、下架、恢复。已下架也能看。 -->
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { api, ApiError, type OpsPostRow, type OpsPostView } from '@onepage/api-client'
import EmptyState from './EmptyState.vue'
import Icon from './Icon.vue'
import OpsTargetPreview from './OpsTargetPreview.vue'

type Taken = '' | 'live' | 'down'

const taken = ref<Taken>('')
const query = ref('')
const items = ref<OpsPostRow[]>([])
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const loading = ref(false)
const failed = ref('')
const toast = ref('')
const busyId = ref('')
const previewOpen = ref(false)
const previewPost = ref<OpsPostView | null>(null)
const previewLoading = ref(false)
const previewError = ref('')

const tabs: { id: Taken; label: string }[] = [
  { id: '', label: '全部' },
  { id: 'live', label: '在线' },
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

async function load(reset: boolean) {
  if (reset) {
    items.value = []
    nextCursor.value = null
    hasMore.value = false
  }
  loading.value = true
  failed.value = ''
  try {
    const page = await api.opsPosts({
      q: query.value.trim() || undefined,
      taken: taken.value || undefined,
      cursor: reset ? undefined : nextCursor.value || undefined,
    })
    items.value = reset ? page.items : items.value.concat(page.items)
    nextCursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } catch (e) {
    failed.value = e instanceof ApiError ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
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

async function act(row: OpsPostRow, kind: 'takedown' | 'restore') {
  busyId.value = row.postId
  try {
    if (kind === 'takedown') await api.opsTakedownPost(row.postId)
    else await api.opsRestorePost(row.postId)
    flash(kind === 'takedown' ? '已下架' : '已恢复')
    previewOpen.value = false
    await load(true)
  } catch (e) {
    flash(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    busyId.value = ''
  }
}

onMounted(() => load(true))
watch(taken, () => load(true))
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
          <input v-model="query" placeholder="搜标题或笔记 ID" @keydown.enter="load(true)" />
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
            </td>
            <td>{{ row.author?.nickname || row.author?.pageId || '—' }}</td>
            <td>{{ row.category || '—' }}</td>
            <td>{{ clock(row.publishedAt) }}</td>
            <td class="acts">
              <button
                v-if="!row.takenDown"
                type="button"
                class="danger"
                :disabled="busyId === row.postId"
                @click="act(row, 'takedown')"
              >
                下架
              </button>
              <button
                v-else
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
      <button v-if="hasMore" type="button" class="more" :disabled="loading" @click="load(false)">
        {{ loading ? '加载中…' : '加载更多' }}
      </button>
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
</style>
