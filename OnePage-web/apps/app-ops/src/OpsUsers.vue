<!-- 用户处置：搜一页号/昵称，冻结或解冻。走 /ops/users。 -->
<script setup lang="ts">
import { ref } from 'vue'
import { api, ApiError, type OpsUser } from '@onepage/api-client'
import EmptyState from './EmptyState.vue'
import Icon from './Icon.vue'

const query = ref('')
const items = ref<OpsUser[]>([])
const loading = ref(false)
const failed = ref('')
const toast = ref('')
const busyId = ref('')
const searched = ref(false)

function flash(text: string) {
  toast.value = text
  window.setTimeout(() => {
    if (toast.value === text) toast.value = ''
  }, 1800)
}

async function search() {
  const q = query.value.trim()
  if (!q) {
    failed.value = '请输入一页号或昵称'
    return
  }
  loading.value = true
  failed.value = ''
  searched.value = true
  try {
    items.value = await api.opsUsers(q)
  } catch (e) {
    items.value = []
    failed.value = e instanceof ApiError ? e.message : '搜索失败'
  } finally {
    loading.value = false
  }
}

async function toggle(row: OpsUser) {
  busyId.value = row.userId
  try {
    const next = row.frozen ? await api.opsUnfreezeUser(row.userId) : await api.opsFreezeUser(row.userId)
    items.value = items.value.map((item) => (item.userId === next.userId ? next : item))
    flash(next.frozen ? '已冻结' : '已解冻')
  } catch (e) {
    flash(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    busyId.value = ''
  }
}
</script>

<template>
  <div class="page">
    <section class="card">
      <form class="head" @submit.prevent="search">
        <label class="search">
          <Icon name="search" :size="16" />
          <input v-model="query" placeholder="搜一页号或昵称" />
        </label>
        <button class="go" :disabled="loading">{{ loading ? '搜索中…' : '搜索' }}</button>
      </form>
      <p v-if="toast" class="toast">{{ toast }}</p>
      <p v-if="failed" class="err">{{ failed }}</p>
      <EmptyState
        v-else-if="!searched"
        src="/empty-people.png"
        title="搜索用户"
        desc="冻结账号后对方不能再登录。不能处置运营员工或自己。"
        compact
      />
      <EmptyState
        v-else-if="!loading && !items.length"
        src="/empty-search.png"
        title="没有这个人"
        desc="换一页号或昵称再试。"
        compact
      />
      <ul v-else class="list">
        <li v-for="row in items" :key="row.userId" class="row">
          <span class="avatar">
            <img v-if="row.avatarUrl" :src="row.avatarUrl" alt="" />
            <span v-else>{{ (row.nickname || '一').slice(0, 1) }}</span>
          </span>
          <span class="meta">
            <span class="name">{{ row.nickname || '未命名' }}</span>
            <span class="id">{{ row.pageId || row.userId }}{{ row.frozen ? ' · 已冻结' : '' }}</span>
          </span>
          <button
            type="button"
            class="act"
            :class="{ danger: !row.frozen }"
            :disabled="busyId === row.userId"
            @click="toggle(row)"
          >
            {{ row.frozen ? '解冻' : '冻结' }}
          </button>
        </li>
      </ul>
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
.err {
  margin: 12px 0 0;
  font-size: 13px;
}

.err {
  color: #ff2442;
}

.toast {
  color: #8a8a8a;
}

.list {
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
}

.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.avatar {
  display: flex;
  width: 40px;
  height: 40px;
  overflow: hidden;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #f2f2f2;
  color: #8a8a8a;
  font-size: 15px;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.meta {
  min-width: 0;
  flex: 1;
}

.name {
  display: block;
  font-size: 14px;
  font-weight: 600;
}

.id {
  display: block;
  margin-top: 2px;
  color: #8a8a8a;
  font-size: 12px;
}

.act {
  height: 32px;
  padding: 0 14px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
  color: #555;
  font-size: 13px;
}

.act.danger {
  color: #ff2442;
}
</style>
