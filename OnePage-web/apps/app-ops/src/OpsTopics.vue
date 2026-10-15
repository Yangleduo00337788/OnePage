<!-- 话题。新增/编辑走弹窗。 -->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api, ApiError, type OpsTopic } from '@onepage/api-client'
import EmptyState from './EmptyState.vue'
import OpsDialog from './OpsDialog.vue'
import OpsPager from './OpsPager.vue'

const items = ref<OpsTopic[]>([])
const failed = ref('')
const loading = ref(false)
const toast = ref('')
const busyId = ref('')
const dialogOpen = ref(false)
const editId = ref('')
const name = ref('')
const official = ref(true)
const sortOrder = ref(0)
const page = ref(1)
const pageSize = ref(10)

const rows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return items.value.slice(start, start + pageSize.value)
})
const hasMore = computed(() => page.value * pageSize.value < items.value.length)

watch(items, () => {
  const max = Math.max(1, Math.ceil(items.value.length / pageSize.value) || 1)
  if (page.value > max) page.value = max
})

function flash(text: string) {
  toast.value = text
  window.setTimeout(() => {
    if (toast.value === text) toast.value = ''
  }, 1800)
}

async function load() {
  loading.value = true
  failed.value = ''
  try {
    const data = await api.opsCatalog()
    items.value = data.topics
  } catch (e) {
    failed.value = e instanceof ApiError ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editId.value = ''
  name.value = ''
  official.value = true
  sortOrder.value = items.value.length + 1
  dialogOpen.value = true
}

function openEdit(row: OpsTopic) {
  editId.value = row.topicId
  name.value = row.name
  official.value = row.official
  sortOrder.value = row.sortOrder
  dialogOpen.value = true
}

async function save() {
  const title = name.value.trim().replace(/^#/, '')
  if (!title) {
    flash('话题名必填')
    return
  }
  busyId.value = editId.value || 'new'
  try {
    if (editId.value) {
      await api.opsPatchTopic(editId.value, { name: title, official: official.value, sortOrder: sortOrder.value })
    } else {
      await api.opsCreateTopic({ name: title, official: official.value, sortOrder: sortOrder.value, enabled: true })
    }
    flash('已保存')
    dialogOpen.value = false
    await load()
  } catch (e) {
    flash(e instanceof ApiError ? e.message : '保存失败')
  } finally {
    busyId.value = ''
  }
}

async function toggle(row: OpsTopic) {
  busyId.value = row.topicId
  try {
    await api.opsTopicEnabled(row.topicId, !row.enabled)
    flash(row.enabled ? '已停用' : '已启用')
    await load()
  } catch (e) {
    flash(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    busyId.value = ''
  }
}

function setSize(size: number) {
  pageSize.value = size
  page.value = 1
}

onMounted(() => load())
</script>

<template>
  <div class="page">
    <section class="card">
      <div class="bar">
        <div>
          <h1>话题</h1>
          <p class="lead">官方话题可在发帖联想。用户笔记里的 #标签 也会入库，可在此停用。</p>
        </div>
        <button type="button" class="go" @click="openCreate">新增</button>
      </div>
      <p v-if="toast" class="toast">{{ toast }}</p>
      <p v-if="failed" class="err">{{ failed }}</p>
      <p v-else-if="loading && !items.length" class="muted">加载中…</p>
      <EmptyState
        v-else-if="!items.length"
        src="/empty-box.png"
        title="没有话题"
        desc="点右上角新增，或执行 005_ops_catalog.sql。"
        compact
      />
      <table v-else class="table">
        <thead>
          <tr>
            <th>话题</th>
            <th>来源</th>
            <th>状态</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.topicId">
            <td>#{{ row.name }}</td>
            <td>{{ row.official ? '官方' : '用户' }}</td>
            <td>{{ row.enabled ? '启用' : '停用' }}</td>
            <td class="acts">
              <button type="button" @click="openEdit(row)">编辑</button>
              <button type="button" :disabled="busyId === row.topicId" @click="toggle(row)">
                {{ row.enabled ? '停用' : '启用' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <OpsPager
        v-if="items.length"
        :page="page"
        :page-size="pageSize"
        :has-more="hasMore"
        :total="items.length"
        @prev="page -= 1"
        @next="page += 1"
        @update:page-size="setSize"
      />
    </section>
    <OpsDialog v-if="dialogOpen" :title="editId ? '编辑话题' : '新增话题'" @close="dialogOpen = false">
      <form class="form" @submit.prevent="save">
        <label>
          话题名
          <input v-model="name" maxlength="32" placeholder="不要 #" />
        </label>
        <label class="chk">
          <input v-model="official" type="checkbox" />
          官方
        </label>
        <label>
          顺序
          <input v-model.number="sortOrder" type="number" />
        </label>
      </form>
      <template #footer>
        <button type="button" class="ghost" @click="dialogOpen = false">取消</button>
        <button type="button" class="go" :disabled="Boolean(busyId)" @click="save">保存</button>
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

.bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
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

.go {
  height: 36px;
  padding: 0 16px;
  border-radius: 18px;
  background: #ff2442;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: #8a8a8a;
}

.form input:not([type='checkbox']) {
  height: 36px;
  padding: 0 12px;
  border: 0;
  border-radius: 12px;
  background: rgb(248, 248, 248);
  color: #1f1f1f;
  font: inherit;
  outline: none;
}

.chk {
  flex-direction: row !important;
  align-items: center;
  color: #555 !important;
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

.acts button {
  height: 32px;
  margin-left: 6px;
  padding: 0 12px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
  color: #555;
  font-size: 13px;
}
</style>
