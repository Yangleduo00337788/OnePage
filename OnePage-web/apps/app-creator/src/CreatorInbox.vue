<!-- 创作者顶栏铃铛：审核等创作消息。列表一行展示，点开看详情。 -->
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api, type NotificationItem } from '@onepage/api-client'
import Icon from './Icon.vue'

const emit = defineEmits<{
  edit: [string]
}>()

const TITLES: Record<string, string> = {
  review_reject: '笔记未通过',
}

const wrap = ref<HTMLElement | null>(null)
const open = ref(false)
const loading = ref(false)
const items = ref<NotificationItem[]>([])
const unread = ref(0)
const selected = ref<NotificationItem | null>(null)
const postTitle = ref('')
const postLoading = ref(false)
let poll: number | null = null

const badge = computed(() => (unread.value > 99 ? '99+' : String(unread.value)))
const selectedTitle = computed(() =>
  selected.value ? TITLES[selected.value.type] || '创作消息' : '',
)

function timeText(raw: string) {
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return ''
  const diff = Date.now() - d.getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days} 天前`
  return d.toLocaleDateString()
}

async function refreshUnread() {
  try {
    const data = await api.notificationUnread()
    unread.value = Number(data.review) || 0
  } catch {
    unread.value = items.value.filter((item) => !item.read).length
  }
}

function syncUnreadFromItems() {
  unread.value = items.value.filter((item) => !item.read).length
}

async function loadInbox() {
  loading.value = true
  selected.value = null
  postTitle.value = ''
  try {
    const page = await api.notifications('review', undefined, 30)
    items.value = page.items
    syncUnreadFromItems()
  } catch {
    items.value = []
    unread.value = 0
  } finally {
    loading.value = false
  }
}

function onDocClick(e: MouseEvent) {
  const target = e.target
  if (!(target instanceof Node) || !target.isConnected) return
  if (!wrap.value?.contains(target)) {
    open.value = false
    selected.value = null
  }
}

async function toggle(e: MouseEvent) {
  e.stopPropagation()
  open.value = !open.value
  if (open.value) await loadInbox()
  else selected.value = null
}

async function openItem(item: NotificationItem) {
  selected.value = item
  postTitle.value = ''
  if (!item.read) {
    item.read = true
    items.value = items.value.map((row) => (row.id === item.id ? { ...row, read: true } : row))
    syncUnreadFromItems()
    try {
      await api.markNotificationsRead({ ids: [item.id] })
    } catch {
      /* 标已读失败仍展示详情 */
    }
  }
  if (!item.postId) return
  postLoading.value = true
  try {
    const detail = await api.post(item.postId)
    postTitle.value = detail.title || ''
  } catch {
    postTitle.value = ''
  } finally {
    postLoading.value = false
  }
}

function goEdit() {
  const id = selected.value?.postId
  if (!id) return
  open.value = false
  selected.value = null
  emit('edit', id)
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
  void refreshUnread()
  poll = window.setInterval(() => {
    if (!open.value) void refreshUnread()
  }, 30000)
})

onUnmounted(() => {
  document.removeEventListener('click', onDocClick)
  if (poll != null) window.clearInterval(poll)
})
</script>

<template>
  <div ref="wrap" class="inbox">
    <button type="button" class="bell" title="创作消息" aria-label="创作消息" @click="toggle">
      <Icon name="bell" :size="18" />
      <span v-if="unread > 0" class="dot">{{ badge }}</span>
    </button>
    <div v-if="open" class="panel" role="dialog" aria-label="创作消息" @click.stop>
      <div class="head">
        <button v-if="selected" type="button" class="back" aria-label="返回" @click="selected = null">
          <Icon name="chevron-left" :size="16" />
        </button>
        <strong>{{ selected ? selectedTitle : '创作消息' }}</strong>
      </div>
      <p v-if="loading" class="hint">加载中…</p>
      <div v-else-if="selected" class="detail">
        <p v-if="postLoading" class="muted">正在加载笔记…</p>
        <p v-else-if="postTitle" class="note">{{ postTitle }}</p>
        <p class="reason">{{ selected.preview || '请修改后重新提交' }}</p>
        <p class="when">{{ timeText(selected.createdAt) }}</p>
        <button v-if="selected.postId" type="button" class="edit" @click="goEdit">去修改</button>
      </div>
      <ul v-else-if="items.length" class="list">
        <li v-for="item in items" :key="item.id">
          <button type="button" class="row" :class="{ 'is-unread': !item.read }" @click="openItem(item)">
            <span class="mark" />
            <span class="kind">{{ TITLES[item.type] || '创作消息' }}</span>
            <span class="preview">{{ item.preview || '请修改后重新提交' }}</span>
            <span class="time">{{ timeText(item.createdAt) }}</span>
          </button>
        </li>
      </ul>
      <div v-else class="empty">
        <img src="/empty-me.png" alt="" width="96" height="96" />
        <p>暂无创作消息</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.inbox {
  position: static;
}

.bell {
  position: relative;
  z-index: 1;
  overflow: visible;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 18px;
  color: #555;
}

.bell:hover {
  background: #f2f2f2;
}

.dot {
  position: absolute;
  top: 2px;
  right: 0;
  z-index: 2;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: #ff2442;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
  pointer-events: none;
}

.panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 40;
  display: flex;
  flex-direction: column;
  width: 520px;
  max-height: 480px;
  overflow: hidden;
  border: 1px solid #eee;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.16);
}

.head {
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 44px;
  padding: 8px 16px;
}

.head strong {
  font-size: 15px;
  font-weight: 600;
}

.back {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  margin-left: -8px;
  border-radius: 14px;
  color: #555;
}

.back:hover {
  background: #f2f2f2;
}

.hint,
.empty {
  padding: 24px 16px 28px;
  color: #8a8a8a;
  font-size: 13px;
  text-align: center;
}

.empty img {
  display: block;
  width: 96px;
  height: auto;
  margin: 0 auto 8px;
}

.list {
  margin: 0;
  padding: 0 8px 8px;
  overflow: auto;
  list-style: none;
}

.row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  height: 44px;
  padding: 0 8px;
  border-radius: 12px;
  text-align: left;
}

.row:hover {
  background: #f5f5f5;
}

.mark {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  border-radius: 50%;
  background: transparent;
}

.is-unread .mark {
  background: #ff2442;
}

.kind {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
}

.preview {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  color: #555;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time {
  flex-shrink: 0;
  color: #8a8a8a;
  font-size: 12px;
}

.detail {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 20px 20px;
}

.note {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.reason {
  margin: 0;
  color: #333;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.when,
.muted {
  margin: 0;
  color: #8a8a8a;
  font-size: 12px;
}

.edit {
  align-self: flex-start;
  height: 36px;
  margin-top: 6px;
  padding: 0 16px;
  border-radius: 18px;
  background: #ff2442;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}
</style>
