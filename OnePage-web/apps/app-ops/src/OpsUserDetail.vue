<!-- 用户详情：关注 / 粉丝 / 收件箱。可拆关注、删通知。 -->
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import {
  api,
  ApiError,
  type NotificationItem,
  type OpsUser,
  type UserBrief,
} from '@onepage/api-client'

const props = defineProps<{ user: OpsUser }>()
const emit = defineEmits<{ close: []; frozen: [OpsUser] }>()

type Tab = 'following' | 'followers' | 'notify'
type NotifyGroup = '' | 'comment' | 'reaction' | 'follow'

const tab = ref<Tab>('following')
const group = ref<NotifyGroup>('')
const peers = ref<UserBrief[]>([])
const notes = ref<NotificationItem[]>([])
const cursor = ref<string | undefined>(undefined)
const hasMore = ref(false)
const loading = ref(false)
const failed = ref('')
const toast = ref('')
const busyId = ref('')

const tabs: { id: Tab; label: string }[] = [
  { id: 'following', label: '关注' },
  { id: 'followers', label: '粉丝' },
  { id: 'notify', label: '通知' },
]

const groups: { id: NotifyGroup; label: string }[] = [
  { id: '', label: '全部' },
  { id: 'comment', label: '评论和@' },
  { id: 'reaction', label: '赞和收藏' },
  { id: 'follow', label: '新增关注' },
]

const typeLabel: Record<string, string> = {
  comment: '评论',
  reply: '回复',
  mention: '@',
  like_post: '赞笔记',
  favorite_post: '收藏',
  like_comment: '赞评论',
  follow: '关注',
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

function nameOf(row: UserBrief) {
  return row.nickname || row.pageId || row.userId || '用户'
}

async function load(reset: boolean) {
  loading.value = true
  failed.value = ''
  if (reset) {
    peers.value = []
    notes.value = []
    cursor.value = undefined
    hasMore.value = false
  }
  try {
    if (tab.value === 'notify') {
      const result = await api.opsUserNotifications(props.user.userId, {
        group: group.value || undefined,
        cursor: reset ? undefined : cursor.value,
        limit: 20,
      })
      notes.value = reset ? result.items : [...notes.value, ...result.items]
      cursor.value = result.nextCursor || undefined
      hasMore.value = result.hasMore
    } else {
      const fn = tab.value === 'following' ? api.opsUserFollowing : api.opsUserFollowers
      const result = await fn(props.user.userId, {
        cursor: reset ? undefined : cursor.value,
        limit: 20,
      })
      peers.value = reset ? result.items : [...peers.value, ...result.items]
      cursor.value = result.nextCursor || undefined
      hasMore.value = result.hasMore
    }
  } catch (e) {
    failed.value = e instanceof ApiError ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function dropPeer(peer: UserBrief) {
  if (!peer.userId) return
  busyId.value = peer.userId
  try {
    if (tab.value === 'following') {
      await api.opsRemoveFollowing(props.user.userId, peer.userId)
    } else {
      await api.opsRemoveFollower(props.user.userId, peer.userId)
    }
    peers.value = peers.value.filter((item) => item.userId !== peer.userId)
    flash('已取消关注关系')
  } catch (e) {
    flash(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    busyId.value = ''
  }
}

async function dropNote(row: NotificationItem) {
  busyId.value = row.id
  try {
    await api.opsHideNotification(row.id)
    notes.value = notes.value.filter((item) => item.id !== row.id)
    flash('已从收件箱删除')
  } catch (e) {
    flash(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    busyId.value = ''
  }
}

async function toggleFreeze() {
  busyId.value = props.user.userId
  try {
    const next = props.user.frozen
      ? await api.opsUnfreezeUser(props.user.userId)
      : await api.opsFreezeUser(props.user.userId)
    emit('frozen', next)
    flash(next.frozen ? '已冻结' : '已解冻')
  } catch (e) {
    flash(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    busyId.value = ''
  }
}

onMounted(() => load(true))
watch(tab, () => load(true))
watch(group, () => {
  if (tab.value === 'notify') load(true)
})
</script>

<template>
  <div class="mask" @click.self="emit('close')">
    <section class="sheet">
      <header class="head">
        <div>
          <h2>{{ user.nickname || '未命名' }}</h2>
          <p class="sub">{{ user.pageId || user.userId }}{{ user.frozen ? ' · 已冻结' : '' }}</p>
        </div>
        <div class="head-acts">
          <button
            type="button"
            :class="{ danger: !user.frozen }"
            :disabled="busyId === user.userId"
            @click="toggleFreeze"
          >
            {{ user.frozen ? '解冻' : '冻结' }}
          </button>
          <button type="button" class="x" aria-label="关闭" @click="emit('close')">×</button>
        </div>
      </header>
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
      <div v-if="tab === 'notify'" class="tabs is-sub">
        <button
          v-for="item in groups"
          :key="item.id || 'all'"
          type="button"
          class="tab"
          :class="{ 'is-on': group === item.id }"
          @click="group = item.id"
        >
          {{ item.label }}
        </button>
      </div>
      <p v-if="toast" class="toast">{{ toast }}</p>
      <p v-if="failed" class="err">{{ failed }}</p>
      <p v-else-if="loading && !(peers.length || notes.length)" class="muted">加载中…</p>
      <p v-else-if="tab !== 'notify' && !peers.length" class="muted">没有人</p>
      <p v-else-if="tab === 'notify' && !notes.length" class="muted">没有通知</p>
      <ul v-else-if="tab !== 'notify'" class="list">
        <li v-for="row in peers" :key="row.userId">
          <span>{{ nameOf(row) }}</span>
          <button type="button" class="danger" :disabled="busyId === row.userId" @click="dropPeer(row)">
            {{ tab === 'following' ? '取消关注' : '移除粉丝' }}
          </button>
        </li>
      </ul>
      <ul v-else class="list">
        <li v-for="row in notes" :key="row.id">
          <div>
            <p>
              {{ typeLabel[row.type] || row.type }} · {{ row.actor?.nickname || row.actor?.pageId || '用户' }}
              <span v-if="!row.read" class="tag">未读</span>
            </p>
            <p class="muted">{{ row.preview || clock(row.createdAt) }}</p>
          </div>
          <button type="button" class="danger" :disabled="busyId === row.id" @click="dropNote(row)">
            删除
          </button>
        </li>
      </ul>
      <button v-if="hasMore" type="button" class="more" :disabled="loading" @click="load(false)">
        {{ loading ? '加载中…' : '更多' }}
      </button>
    </section>
  </div>
</template>

<style scoped>
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
  width: min(640px, calc(100vw - 40px));
  max-height: min(88vh, 720px);
  overflow: auto;
  padding: 20px 24px 24px;
  border-radius: 20px;
  background: #fff;
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.head-acts {
  display: flex;
  align-items: center;
  gap: 8px;
}

h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.sub,
.toast,
.err,
.muted {
  margin: 6px 0 0;
  font-size: 13px;
  color: #8a8a8a;
}

.err {
  color: #ff2442;
}

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

.tabs.is-sub {
  margin-top: 8px;
}

.tab {
  height: 32px;
  padding: 0 12px;
  border-radius: 16px;
  color: #555;
  font-size: 13px;
}

.tab.is-on,
.tab:hover {
  color: #1f1f1f;
  font-weight: 600;
  background: rgb(248, 248, 248);
}

.list {
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
  font-size: 14px;
}

button {
  height: 32px;
  padding: 0 12px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
  color: #555;
  font-size: 13px;
}

.danger {
  color: #ff2442;
}

.x {
  width: 32px;
  padding: 0;
  color: #8a8a8a;
  font-size: 22px;
  line-height: 1;
}

.tag {
  margin-left: 6px;
  color: #ff2442;
  font-size: 12px;
}

.more {
  display: block;
  margin: 16px auto 0;
}
</style>
