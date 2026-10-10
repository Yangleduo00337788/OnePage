<!-- 笔记管理：卡片/列表、筛选、批量。P0 用占位数据。 -->
<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from './Icon.vue'
import { NOTES, type Note, type NoteStatus } from './notes'

type TabId = 'all' | NoteStatus
type View = 'card' | 'list'

const tab = ref<TabId>('all')
const query = ref('')
const view = ref<View>('card')
const selected = ref<string[]>([])
const notes = ref<Note[]>(NOTES.map((item) => ({ ...item })))
const toast = ref('')

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

function batchDelete() {
  if (!selected.value.length) return
  const ids = new Set(selected.value)
  notes.value = notes.value.filter((item) => !ids.has(item.id))
  selected.value = []
  flash('已删除所选笔记')
}

function batchHide() {
  if (!selected.value.length) return
  selected.value = []
  flash('已对所选设为仅自己可见')
}
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
      <button type="button" class="ghost" :disabled="!selected.length" @click="batchHide">设为私密</button>
      <button type="button" class="ghost danger" :disabled="!selected.length" @click="batchDelete">删除</button>
      <span v-if="toast" class="toast">{{ toast }}</span>
    </div>

    <div v-if="filtered.length && view === 'card'" class="cards">
      <article v-for="item in filtered" :key="item.id" class="card" :class="{ 'is-pick': selected.includes(item.id) }">
        <label class="pick">
          <input type="checkbox" :checked="selected.includes(item.id)" @change="toggleOne(item.id)" />
        </label>
        <img :src="item.cover" alt="" />
        <div class="meta">
          <p class="name">{{ item.title }}</p>
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
        <img :src="item.cover" alt="" />
        <p class="name">{{ item.title }}</p>
        <span :class="['st', item.status === '已发布' ? 'ok' : item.status === '审核中' ? 'wait' : 'no']">{{ item.status }}</span>
        <span class="views">浏览 {{ item.views }}</span>
      </article>
    </div>

    <div v-else class="empty">
      <div class="mark" aria-hidden="true">
        <span class="sheet"><i /><i /><i /></span>
        <span class="lens" />
      </div>
      <p>没有找到相关笔记</p>
    </div>
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

.card img {
  display: block;
  width: 100%;
  height: 160px;
  object-fit: cover;
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
  z-index: 1;
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

.row img {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  object-fit: cover;
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
