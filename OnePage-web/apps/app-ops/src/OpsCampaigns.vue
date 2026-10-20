<!-- 官方活动。新增/编辑走弹窗。 -->
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { api, ApiError, type OpsCampaign } from '@onepage/api-client'
import ImageCropDialog from '@onepage/image-crop'
import EmptyState from './EmptyState.vue'
import OpsDialog from './OpsDialog.vue'
import OpsPager from './OpsPager.vue'

const items = ref<OpsCampaign[]>([])
const failed = ref('')
const loading = ref(false)
const toast = ref('')
const busyId = ref('')
const dialogOpen = ref(false)
const creating = ref(true)
const eventId = ref('')
const title = ref('')
const intro = ref('')
const coverUrl = ref('')
const status = ref('live')
const sortOrder = ref(0)
const page = ref(1)
const pageSize = ref(10)
const cropSrc = ref('')
const cropName = ref('')
const uploading = ref(false)
const previewUrl = ref('')
const coverPreview = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

const statuses: { id: string; label: string }[] = [
  { id: 'live', label: '进行中' },
  { id: 'upcoming', label: '预告' },
  { id: 'ended', label: '结束' },
]

const rows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return items.value.slice(start, start + pageSize.value)
})
const hasMore = computed(() => page.value * pageSize.value < items.value.length)

watch(items, () => {
  const max = Math.max(1, Math.ceil(items.value.length / pageSize.value) || 1)
  if (page.value > max) page.value = max
})

function statusLabel(id: string) {
  return statuses.find((item) => item.id === id)?.label || id
}

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
    items.value = data.campaigns
  } catch (e) {
    failed.value = e instanceof ApiError ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  creating.value = true
  eventId.value = ''
  title.value = ''
  intro.value = ''
  coverUrl.value = ''
  setCoverPreview('')
  status.value = 'live'
  sortOrder.value = items.value.length + 1
  dialogOpen.value = true
}

function openEdit(row: OpsCampaign) {
  creating.value = false
  eventId.value = row.eventId
  title.value = row.title
  intro.value = row.intro
  coverUrl.value = row.coverUrl
  setCoverPreview(row.coverUrl)
  status.value = row.status || 'live'
  sortOrder.value = row.sortOrder
  dialogOpen.value = true
}

async function save() {
  const id = eventId.value.trim()
  const name = title.value.trim()
  if (!id || !name) {
    flash('活动 id 和标题必填')
    return
  }
  if (uploading.value) {
    flash('封面还在上传')
    return
  }
  busyId.value = id
  try {
    if (creating.value) {
      await api.opsCreateCampaign({
        eventId: id,
        title: name,
        intro: intro.value.trim(),
        coverUrl: persistCoverUrl(coverUrl.value),
        status: status.value,
        sortOrder: sortOrder.value,
        enabled: true,
      })
    } else {
      await api.opsPatchCampaign(id, {
        title: name,
        intro: intro.value.trim(),
        coverUrl: persistCoverUrl(coverUrl.value),
        status: status.value,
        sortOrder: sortOrder.value,
      })
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

async function toggle(row: OpsCampaign) {
  busyId.value = row.eventId
  try {
    await api.opsCampaignEnabled(row.eventId, !row.enabled)
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

function stopCrop() {
  if (cropSrc.value) URL.revokeObjectURL(cropSrc.value)
  cropSrc.value = ''
  cropName.value = ''
}

function persistCoverUrl(raw: string) {
  const text = raw.trim()
  if (!text || text.startsWith('blob:')) return ''
  try {
    const u = new URL(text)
    u.search = ''
    u.hash = ''
    return u.toString()
  } catch {
    return text
  }
}

function setCoverPreview(next: string) {
  if (coverPreview.value.startsWith('blob:')) URL.revokeObjectURL(coverPreview.value)
  coverPreview.value = next
}

function pickCover(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (file.size > 10 * 1024 * 1024) {
    flash('图片不能超过 10MB')
    return
  }
  stopCrop()
  cropName.value = file.name
  cropSrc.value = URL.createObjectURL(file)
}

async function onCropped(file: File) {
  stopCrop()
  setCoverPreview(URL.createObjectURL(file))
  uploading.value = true
  try {
    const mimeType = file.type || 'image/jpeg'
    const ticket = await api.presign(mimeType, file.size, 'cover')
    await api.putObject(ticket.uploadUrl, file, mimeType)
    const done = await api.completeUpload(ticket.mediaId)
    coverUrl.value = persistCoverUrl(done.url || '')
    if (!coverUrl.value) flash('已上传，但没有拿到封面地址，保存前无法写入活动')
    else flash('封面已上传')
  } catch (e) {
    setCoverPreview('')
    flash(e instanceof ApiError ? e.message : '上传失败')
  } finally {
    uploading.value = false
  }
}

function clearCover() {
  coverUrl.value = ''
  setCoverPreview('')
}

onMounted(() => load())
onUnmounted(() => {
  stopCrop()
  setCoverPreview('')
})
</script>

<template>
  <div class="page">
    <section class="card">
      <div class="bar">
        <div>
          <h1>活动</h1>
          <p class="lead">启用且进行中的活动可在发帖时关联。创作者活动中心按状态展示。</p>
        </div>
        <button type="button" class="go" @click="openCreate">新增</button>
      </div>
      <p v-if="toast" class="toast">{{ toast }}</p>
      <p v-if="failed" class="err">{{ failed }}</p>
      <p v-else-if="loading && !items.length" class="muted">加载中…</p>
      <EmptyState
        v-else-if="!items.length"
        src="/empty-box.png"
        title="没有活动"
        desc="点右上角新增，或执行 005_ops_catalog.sql。"
        compact
      />
      <table v-else class="table">
        <thead>
          <tr>
            <th>id</th>
            <th>标题</th>
            <th>状态</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.eventId">
            <td>{{ row.eventId }}</td>
            <td>
              <button type="button" class="title-cell" @click="row.coverUrl ? (previewUrl = row.coverUrl) : openEdit(row)">
                <img v-if="row.coverUrl" class="thumb" :src="row.coverUrl" alt="" referrerpolicy="no-referrer" />
                <span v-else class="thumb is-empty" />
                <span>{{ row.title }}{{ row.enabled ? '' : ' · 停用' }}</span>
              </button>
            </td>
            <td>{{ statusLabel(row.status) }}</td>
            <td class="acts">
              <button type="button" @click="openEdit(row)">编辑</button>
              <button type="button" :disabled="busyId === row.eventId" @click="toggle(row)">
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
    <OpsDialog v-if="dialogOpen" :title="creating ? '新增活动' : '编辑活动'" @close="dialogOpen = false">
      <form class="form" @submit.prevent="save">
        <label>
          活动 id
          <input v-model="eventId" maxlength="32" placeholder="如 e3" :disabled="!creating" />
        </label>
        <label>
          标题
          <input v-model="title" maxlength="64" />
        </label>
        <label>
          简介
          <input v-model="intro" maxlength="200" />
        </label>
        <div class="cover-field">
          <span>封面</span>
          <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/webp" hidden @change="pickCover" />
          <button type="button" class="cover-pick" :disabled="uploading" @click="fileInput?.click()">
            <img v-if="coverPreview || coverUrl" :src="coverPreview || coverUrl" alt="" referrerpolicy="no-referrer" />
            <span v-else>{{ uploading ? '上传中…' : '点击上传并裁剪' }}</span>
          </button>
          <div class="cover-acts">
            <button v-if="coverPreview || coverUrl" type="button" class="ghost" @click="previewUrl = coverPreview || coverUrl">预览</button>
            <button v-if="coverPreview || coverUrl" type="button" class="ghost" @click="clearCover">去掉</button>
          </div>
        </div>
        <label>
          状态
          <select v-model="status">
            <option v-for="item in statuses" :key="item.id" :value="item.id">{{ item.label }}</option>
          </select>
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
    <ImageCropDialog
      v-if="cropSrc"
      :src="cropSrc"
      title="裁剪活动封面"
      :aspect="1"
      :file-name="cropName"
      :output-width="800"
      @confirm="onCropped"
      @cancel="stopCrop"
    />
    <div v-if="previewUrl" class="mask" @click.self="previewUrl = ''">
      <img class="preview" :src="previewUrl" alt="" />
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

.form input,
.form select {
  height: 36px;
  padding: 0 12px;
  border: 0;
  border-radius: 12px;
  background: rgb(248, 248, 248);
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

.acts button {
  height: 32px;
  margin-left: 6px;
  padding: 0 12px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
  color: #555;
  font-size: 13px;
}

.title-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0;
  text-align: left;
  color: #1f1f1f;
}

.thumb {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  object-fit: cover;
  border-radius: 8px;
  background: rgb(248, 248, 248);
}

.thumb.is-empty {
  display: inline-block;
}

.cover-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
  color: #8a8a8a;
}

.cover-pick {
  display: flex;
  overflow: hidden;
  align-items: center;
  justify-content: center;
  width: 160px;
  height: 160px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
  color: #8a8a8a;
  font-size: 13px;
}

.cover-pick img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-acts {
  display: flex;
  gap: 8px;
}

.ghost {
  height: 32px;
  padding: 0 12px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
  color: #555;
  font-size: 13px;
}

.mask {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgb(0 0 0 / 45%);
}

.preview {
  max-width: min(720px, calc(100vw - 48px));
  max-height: calc(100vh - 48px);
  border-radius: 16px;
  object-fit: contain;
}
</style>
