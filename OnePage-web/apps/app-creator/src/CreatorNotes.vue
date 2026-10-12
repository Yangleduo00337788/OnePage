<!-- 笔记管理：卡片/列表、筛选、批量。列表走 GET /posts/mine。 -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, ApiError, type PostDetail } from '@onepage/api-client'
import Icon from './Icon.vue'
import CreatorNotePreview from './CreatorNotePreview.vue'
import TextNoteCover from './TextNoteCover.vue'
import { coverVideoSrc, isTextNote, isVideoNote, noteFromMine, type Note, type NoteStatus } from './notes'

type TabId = 'all' | NoteStatus
type View = 'card' | 'list'

const tab = ref<TabId>('all')
const query = ref('')
const view = ref<View>('card')
const selected = ref<string[]>([])
const notes = ref<Note[]>([])
const toast = ref('')
const loading = ref(false)
const failed = ref('')
const busy = ref(false)
const previewOpen = ref(false)
const previewDetail = ref<PostDetail | null>(null)
const previewLoading = ref(false)
const previewError = ref('')
const emit = defineEmits<{ edit: [string] }>()

const tabs: { id: TabId; label: string }[] = [
  { id: 'all', label: '全部' },
  { id: '已发布', label: '已发布' },
  { id: '审核中', label: '审核中' },
  { id: '未通过', label: '未通过' },
]

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  return notes.value.filter((item) => {
    if (tab.value !== 'all' && item.status !== tab.value) return false
    if (q && !item.title.toLowerCase().includes(q)) return false
    return true
  })
})

const allOn = computed(
  () => filtered.value.length > 0 && filtered.value.every((item) => selected.value.includes(item.id)),
)

function toggleOne(id: string) {
  if (selected.value.includes(id)) selected.value = selected.value.filter((x) => x !== id)
  else selected.value = [...selected.value, id]
}

function toggleAll() {
  if (allOn.value) {
    const ids = new Set(filtered.value.map((item) => item.id))
    selected.value = selected.value.filter((id) => !ids.has(id))
    return
  }
  const extra = filtered.value.map((item) => item.id)
  selected.value = [...new Set([...selected.value, ...extra])]
}

function flash(text: string) {
  toast.value = text
  window.setTimeout(() => {
    if (toast.value === text) toast.value = ''
  }, 1800)
}

function chipsOf(item: Note) {
  const chips: string[] = []
  if (item.category) chips.push(item.category)
  if (item.visibility === 'self') chips.push('仅自己可见')
  return chips
}

async function loadMine() {
  loading.value = true
  failed.value = ''
  notes.value = []
  selected.value = []
  try {
    const collected: Note[] = []
    let cursor: string | undefined
    for (let i = 0; i < 20; i++) {
      const page = await api.myPosts(cursor, 50)
      collected.push(...page.items.map(noteFromMine))
      if (!page.hasMore || !page.nextCursor) break
      cursor = page.nextCursor
    }
    notes.value = collected
  } catch (e) {
    failed.value = e instanceof ApiError ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function batchDelete() {
  if (!selected.value.length || busy.value) return
  busy.value = true
  try {
    const ids = [...selected.value]
    for (const id of ids) {
      await api.deletePost(id)
    }
    const gone = new Set(ids)
    notes.value = notes.value.filter((item) => !gone.has(item.id))
    selected.value = []
    flash('已删除所选笔记')
  } catch (e) {
    flash(e instanceof ApiError ? e.message : '删除失败')
  } finally {
    busy.value = false
  }
}

async function batchHide() {
  if (!selected.value.length || busy.value) return
  busy.value = true
  try {
    const ids = [...selected.value]
    for (const id of ids) {
      const brief = await api.patchPostVisibility(id, 'self')
      const next = noteFromMine(brief)
      notes.value = notes.value.map((item) => (item.id === id ? next : item))
    }
    selected.value = []
    flash('已对所选设为仅自己可见')
  } catch (e) {
    flash(e instanceof ApiError ? e.message : '设置失败')
  } finally {
    busy.value = false
  }
}

async function previewNote(item: Note) {
  previewOpen.value = true
  previewDetail.value = null
  previewError.value = ''
  previewLoading.value = true
  try {
    previewDetail.value = await api.post(item.id)
  } catch (e) {
    previewError.value = e instanceof ApiError ? e.message : '预览失败'
  } finally {
    previewLoading.value = false
  }
}

function closePreview() {
  previewOpen.value = false
  previewDetail.value = null
  previewError.value = ''
}

function editNote(item: Note) {
  emit('edit', item.id)
}

onMounted(() => {
  void loadMine()
})
</script>

<template>
  <div class="notes">
    <div class="top">
      <div class="tabs">
        <button
          v-for="item in tabs"
          :key="item.id"
          type="button"
          class="tab"
          :class="{ 'is-on': tab === item.id }"
          @click="tab = item.id"
        >
          {{ item.label }}
        </button>
      </div>
      <div class="tools">
        <button type="button" class="icon-btn" :class="{ 'is-on': view === 'card' }" title="卡片" @click="view = 'card'">
          <Icon name="layout" :size="16" />
        </button>
        <button type="button" class="icon-btn" :class="{ 'is-on': view === 'list' }" title="列表" @click="view = 'list'">
          <Icon name="list" :size="16" />
        </button>
        <label class="search">
          <Icon name="search" :size="16" />
          <input v-model="query" type="search" placeholder="搜索笔记" />
        </label>
      </div>
    </div>

    <div class="batch">
      <label class="check">
        <input type="checkbox" :checked="allOn" @change="toggleAll" />
        全选本页
      </label>
      <span>已选 {{ selected.length }}</span>
      <button type="button" class="ghost" :disabled="!selected.length || busy" @click="batchHide">设为私密</button>
      <button type="button" class="ghost danger" :disabled="!selected.length || busy" @click="batchDelete">删除</button>
      <span v-if="toast" class="toast">{{ toast }}</span>
    </div>

    <p v-if="loading" class="hint">加载中…</p>
    <p v-else-if="failed" class="hint">{{ failed }}</p>

    <div v-else-if="filtered.length && view === 'card'" class="cards">
      <article v-for="item in filtered" :key="item.id" class="card" :class="{ 'is-pick': selected.includes(item.id) }">
        <label class="pick">
          <input type="checkbox" :checked="selected.includes(item.id)" @change="toggleOne(item.id)" />
        </label>
        <div class="cover">
          <video
            v-if="item.cover && isVideoNote(item)"
            :src="coverVideoSrc(item.cover)"
            muted
            playsinline
            preload="metadata"
          />
          <img v-else-if="item.cover && !isTextNote(item)" :src="item.cover" alt="" />
          <TextNoteCover v-else size="card" :title="item.title" :body="item.body" />
          <span v-if="isVideoNote(item)" class="play" aria-hidden="true">▶</span>
          <div class="hover-acts">
            <button type="button" class="act" title="预览笔记" aria-label="预览笔记" @click.stop="previewNote(item)">
              <Icon name="eye" :size="18" />
            </button>
            <button type="button" class="act" title="编辑笔记" aria-label="编辑笔记" @click.stop="editNote(item)">
              <Icon name="pencil" :size="18" />
            </button>
          </div>
        </div>
        <div class="meta">
          <p class="name">{{ item.title }}</p>
          <p v-if="chipsOf(item).length" class="chips">
            <span v-for="chip in chipsOf(item)" :key="chip">{{ chip }}</span>
          </p>
          <p class="sub">
            <span :class="['st', item.status === '已发布' ? 'ok' : item.status === '审核中' ? 'wait' : 'no']">{{ item.status }}</span>
            浏览 {{ item.views }}
          </p>
        </div>
      </article>
    </div>

    <div v-else-if="filtered.length" class="rows">
      <article v-for="item in filtered" :key="item.id" class="row" :class="{ 'is-pick': selected.includes(item.id) }">
        <label class="pick">
          <input type="checkbox" :checked="selected.includes(item.id)" @change="toggleOne(item.id)" />
        </label>
        <div class="thumb">
          <video
            v-if="item.cover && isVideoNote(item)"
            :src="coverVideoSrc(item.cover)"
            muted
            playsinline
            preload="metadata"
          />
          <img v-else-if="item.cover && !isTextNote(item)" :src="item.cover" alt="" />
          <TextNoteCover v-else size="thumb" :title="item.title" />
        </div>
        <div class="row-body">
          <p class="name">{{ item.title }}</p>
          <p v-if="chipsOf(item).length" class="chips">
            <span v-for="chip in chipsOf(item)" :key="chip">{{ chip }}</span>
          </p>
        </div>
        <span :class="['st', item.status === '已发布' ? 'ok' : item.status === '审核中' ? 'wait' : 'no']">{{ item.status }}</span>
        <span class="views">浏览 {{ item.views }}</span>
        <div class="hover-acts is-row">
          <button type="button" class="act" title="预览笔记" @click.stop="previewNote(item)">
            <Icon name="eye" :size="16" />
          </button>
          <button type="button" class="act" title="编辑笔记" @click.stop="editNote(item)">
            <Icon name="pencil" :size="16" />
          </button>
        </div>
      </article>
    </div>

    <div v-else class="empty">
      <div class="mark" aria-hidden="true">
        <span class="sheet"><i /><i /><i /></span>
        <span class="lens" />
      </div>
      <p v-if="tab === '审核中' || tab === '未通过'">审核尚未接入</p>
      <p v-else>没有找到相关笔记</p>
    </div>

    <CreatorNotePreview
      v-if="previewOpen"
      :detail="previewDetail"
      :loading="previewLoading"
      :error="previewError"
      @close="closePreview"
    />
  </div>
</template>

<style scoped>
.notes {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  margin: 0 20px 20px 0;
  padding: 16px 24px 24px;
  box-sizing: border-box;
  overflow: auto;
  border-radius: 20px;
  background: #fff;
}

.top,
.tools,
.batch,
.row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.top {
  justify-content: space-between;
}

.tabs,
.tools {
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
.tab.is-on,
.icon-btn.is-on {
  color: #1f1f1f;
  font-weight: 600;
  background: rgb(248, 248, 248);
}

.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  color: #8a8a8a;
}

.search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 220px;
  height: 36px;
  padding: 0 14px;
  border-radius: 20px;
  background: rgb(248, 248, 248);
  color: #bbb;
}

.search input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  color: #1f1f1f;
  font: inherit;
  font-size: 14px;
  outline: none;
}

.batch {
  margin-top: 12px;
  color: #8a8a8a;
  font-size: 13px;
}

.check {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ghost {
  height: 28px;
  padding: 0 10px;
  border-radius: 8px;
  color: #555;
}

.ghost:hover:not(:disabled) {
  background: rgb(248, 248, 248);
}

.ghost:disabled {
  color: #ccc;
  cursor: default;
}

.ghost.danger:hover:not(:disabled) {
  color: #ff2442;
}

.toast {
  margin-left: auto;
  color: #1f1f1f;
}

.cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.card {
  position: relative;
  overflow: hidden;
  border-radius: 16px;
  background: rgb(248, 248, 248);
}

.card.is-pick,
.row.is-pick {
  outline: 2px solid #ff2442;
  outline-offset: -2px;
}

.hint {
  margin: 20px 0 0;
  color: #8a8a8a;
  font-size: 14px;
}

.cover {
  position: relative;
  height: 160px;
  background: #ececec;
}

.cover img,
.cover video,
.cover :deep(.tnc) {
  display: block;
  width: 100%;
  height: 160px;
  object-fit: cover;
}

.play {
  position: absolute;
  top: 10px;
  right: 10px;
  color: #fff;
  font-size: 14px;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.45);
}

.hover-acts {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(0, 0, 0, 0.35);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s ease;
}

.card:hover .hover-acts,
.row:hover .hover-acts {
  opacity: 1;
  pointer-events: auto;
}

.hover-acts.is-row {
  position: static;
  inset: auto;
  background: transparent;
  opacity: 0;
  margin-left: 4px;
}

.row:hover .hover-acts.is-row {
  opacity: 1;
}

.act {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #fff;
  color: #1f1f1f;
}

.act:hover {
  background: #ff2442;
  color: #fff;
}

.hover-acts.is-row .act {
  width: 32px;
  height: 32px;
  background: rgb(248, 248, 248);
}

.hover-acts.is-row .act:hover {
  background: #ff2442;
  color: #fff;
}

.thumb {
  overflow: hidden;
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 10px;
  background: #ececec;
}

.thumb img,
.thumb video,
.thumb :deep(.tnc) {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 8px 0 0;
}

.chips span {
  padding: 1px 8px;
  border-radius: 10px;
  background: rgb(248, 248, 248);
  color: #555;
  font-size: 12px;
}

.row-body {
  flex: 1;
  min-width: 0;
}

.meta {
  padding: 12px 14px 14px;
}

.name {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
}

.sub,
.views {
  margin: 8px 0 0;
  color: #8a8a8a;
  font-size: 12px;
}

.pick {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 3;
}

.row .pick {
  position: static;
}

.st {
  margin-right: 8px;
  padding: 1px 8px;
  border-radius: 10px;
  background: #eee;
}

.st.ok {
  background: #eef7ee;
  color: #2f7d32;
}

.st.wait {
  background: #fff4e5;
  color: #c27800;
}

.st.no {
  background: #fdeeee;
  color: #c0392b;
}

.rows {
  margin-top: 8px;
}

.row {
  padding: 10px 4px;
  border-bottom: 1px solid #f2f2f2;
}

.row .name {
  flex: 1;
  min-width: 0;
}

.empty {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  color: #8a8a8a;
}

.empty p {
  margin: 20px 0 0;
  font-size: 14px;
}

.mark {
  position: relative;
  width: 88px;
  height: 88px;
}

.sheet {
  position: absolute;
  top: 10px;
  left: 8px;
  display: flex;
  flex-direction: column;
  gap: 7px;
  width: 52px;
  height: 64px;
  padding: 16px 10px 10px;
  box-sizing: border-box;
  border-radius: 10px;
  background: rgb(248, 248, 248);
}

.sheet i {
  display: block;
  height: 4px;
  border-radius: 2px;
  background: #e4e4e4;
}

.lens {
  position: absolute;
  right: 4px;
  bottom: 6px;
  width: 36px;
  height: 36px;
  border: 3px solid #d0d0d0;
  border-radius: 50%;
  background: #fff;
  box-sizing: border-box;
}

@media (max-width: 900px) {
  .cards {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
