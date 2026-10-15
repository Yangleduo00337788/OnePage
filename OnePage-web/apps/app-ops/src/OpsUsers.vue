<!-- 全站用户表。空关键字默认列出全部。 -->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, ApiError, type OpsUser } from '@onepage/api-client'
import EmptyState from './EmptyState.vue'
import Icon from './Icon.vue'
import OpsPager from './OpsPager.vue'
import OpsTargetPreview from './OpsTargetPreview.vue'
import { useCursorPages } from './useCursorPages'

const query = ref('')
const items = ref<OpsUser[]>([])
const { page, pageSize, hasMore, resetCursors, cursorFor, applyResult } = useCursorPages()
const loading = ref(false)
const failed = ref('')
const toast = ref('')
const busyId = ref('')
const previewOpen = ref(false)
const previewUser = ref<OpsUser | null>(null)
const previewLoading = ref(false)
const previewError = ref('')

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
    const result = await api.opsUsers({
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

function filterUsers() {
  void load(1, true)
}

async function openUser(id: string) {
  previewOpen.value = true
  previewUser.value = null
  previewError.value = ''
  previewLoading.value = true
  try {
    previewUser.value = await api.opsUser(id)
  } catch (e) {
    previewError.value = e instanceof ApiError ? e.message : '预览失败'
  } finally {
    previewLoading.value = false
  }
}

async function toggle(row: OpsUser) {
  busyId.value = row.userId
  try {
    const next = row.frozen ? await api.opsUnfreezeUser(row.userId) : await api.opsFreezeUser(row.userId)
    items.value = items.value.map((item) => (item.userId === next.userId ? next : item))
    flash(next.frozen ? '已冻结' : '已解冻')
    previewOpen.value = false
  } catch (e) {
    flash(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    busyId.value = ''
  }
}

onMounted(() => load(1, true))
</script>

<template>
  <div class="page">
    <section class="card">
      <form class="head" @submit.prevent="filterUsers">
        <label class="search">
          <Icon name="search" :size="16" />
          <input v-model="query" placeholder="筛一页号或昵称，空则列出全部" @keydown.enter.prevent="filterUsers" />
        </label>
        <button type="button" class="go" :disabled="loading" @click="filterUsers">
          {{ loading ? '筛选中…' : '筛选' }}
        </button>
      </form>
      <p v-if="toast" class="toast">{{ toast }}</p>
      <p v-if="failed" class="err">{{ failed }}</p>
      <p v-else-if="loading && !items.length" class="muted">加载中…</p>
      <EmptyState
        v-else-if="!items.length"
        src="/empty-people.png"
        title="没有用户"
        desc="社区注册后会出现在这里。"
        compact
      />
      <table v-else class="table">
        <thead>
          <tr>
            <th>用户</th>
            <th>一页号</th>
            <th>状态</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.userId">
            <td>
              <button type="button" class="link" @click="openUser(row.userId)">
                {{ row.nickname || '未命名' }}
              </button>
            </td>
            <td>{{ row.pageId || row.userId }}</td>
            <td>{{ row.frozen ? '已冻结' : '正常' }}</td>
            <td class="acts">
              <button
                type="button"
                :class="{ danger: !row.frozen }"
                :disabled="busyId === row.userId"
                @click="toggle(row)"
              >
                {{ row.frozen ? '解冻' : '冻结' }}
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
      kind="user"
      :post="null"
      :user="previewUser"
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
  gap: 10px;
  align-items: center;
}

.search {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 12px;
  border-radius: 18px;
  background: rgb(248, 248, 248);
  color: #8a8a8a;
}

.search input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  color: #1f1f1f;
  font: inherit;
  outline: none;
}

.go {
  height: 36px;
  padding: 0 16px;
  border-radius: 18px;
  background: #ff2442;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
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

.acts button {
  height: 32px;
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
