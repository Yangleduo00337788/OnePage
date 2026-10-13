<!--
  私信：左会话 GET /conversations，右对话拉/发明文。
  查询 c=会话 id。他人主页开聊后带这个参数。本期 HTTP，约 3 秒轮询。
-->
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError, type ConversationItem, type ImMessage } from '@onepage/api-client'
import Icon from '../components/Icon.vue'
import EmptyState from '../components/EmptyState.vue'
import { useAuthModal } from '../stores/authModal'
import { useSession } from '../stores/session'

const route = useRoute()
const router = useRouter()
const session = useSession()
const auth = useAuthModal()

const list = ref<ConversationItem[]>([])
const listFailed = ref('')
const activeId = ref('')
const messages = ref<ImMessage[]>([])
const threadFailed = ref('')
const loadingThread = ref(false)
const draft = ref('')
const sending = ref(false)
const logEl = ref<HTMLElement | null>(null)
let poll: ReturnType<typeof setInterval> | null = null

function previewOf(item: ConversationItem) {
  return item.lastMessage?.text || '暂无消息'
}

function bubbleText(row: ImMessage) {
  return row.content?.text || ''
}

function timeText(raw: string) {
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw
  return d.toLocaleString()
}

function scrollBottom() {
  void nextTick(() => {
    if (logEl.value) logEl.value.scrollTop = logEl.value.scrollHeight
  })
}

async function loadList() {
  try {
    const page = await api.conversations()
    list.value = page.items
    listFailed.value = ''
    const q = typeof route.query.c === 'string' ? route.query.c : ''
    if (q && page.items.some((it) => it.conversationId === q)) {
      activeId.value = q
    } else if (!activeId.value && page.items.length) {
      activeId.value = page.items[0].conversationId
    }
  } catch (e) {
    list.value = []
    if (e instanceof ApiError && e.status === 401) {
      auth.show('login', '/messages')
      return
    }
    listFailed.value = e instanceof ApiError ? e.message : '加载失败'
  }
}

async function loadThread(opts?: { silent?: boolean }) {
  if (!activeId.value) {
    messages.value = []
    return
  }
  if (!opts?.silent) loadingThread.value = true
  threadFailed.value = ''
  try {
    const page = await api.conversationMessages(activeId.value, undefined, 50)
    messages.value = page.messages
    scrollBottom()
    const row = list.value.find((it) => it.conversationId === activeId.value)
    if (row) row.unreadCount = 0
    await session.refreshUnread()
  } catch (e) {
    if (!opts?.silent) {
      messages.value = []
      threadFailed.value = e instanceof ApiError ? e.message : '加载失败'
    }
  } finally {
    if (!opts?.silent) loadingThread.value = false
  }
}

function select(id: string) {
  activeId.value = id
  void router.replace({ query: { c: id } })
}

async function send() {
  const text = draft.value.trim()
  if (!text || !activeId.value || sending.value) return
  sending.value = true
  try {
    const row = await api.sendMessage(activeId.value, text)
    draft.value = ''
    messages.value = messages.value.concat(row)
    scrollBottom()
    await loadList()
  } catch (e) {
    threadFailed.value = e instanceof ApiError ? e.message : '发送失败'
  } finally {
    sending.value = false
  }
}

const activePeer = () => list.value.find((it) => it.conversationId === activeId.value)?.peer

function isMine(row: ImMessage) {
  return row.senderId === session.me?.userId
}

function faceOf(row: ImMessage) {
  if (isMine(row)) {
    return {
      url: session.me?.avatarUrl || '',
      letter: (session.me?.nickname || '一').slice(0, 1),
    }
  }
  const peer = activePeer()
  return {
    url: peer?.avatarUrl || '',
    letter: (peer?.nickname || '一').slice(0, 1),
  }
}

const threadRows = computed(() =>
  messages.value.map((row) => ({
    row,
    mine: isMine(row),
    face: faceOf(row),
  })),
)

onMounted(async () => {
  await session.refresh()
  if (!session.me) {
    auth.show('login', '/messages')
    await router.replace('/')
    return
  }
  await loadList()
  await loadThread()
  poll = setInterval(() => {
    void loadList()
    void loadThread({ silent: true })
  }, 3000)
})

onUnmounted(() => {
  if (poll) clearInterval(poll)
})

watch(activeId, () => {
  void loadThread()
})

watch(
  () => route.query.c,
  (c) => {
    if (typeof c === 'string' && c && c !== activeId.value) {
      activeId.value = c
    }
  },
)
</script>

<template>
  <section class="im-page">
    <aside class="im-list">
      <div class="im-list-head">消息</div>
      <p v-if="listFailed" class="px-5 py-16 text-center text-[13px] text-[#8a8a8a]">{{ listFailed }}</p>
      <EmptyState v-else-if="!list.length" compact src="/empty-chat.png" title="还没有私信" />
      <button
        v-for="item in list"
        :key="item.conversationId"
        type="button"
        class="im-item"
        :class="{ 'is-on': item.conversationId === activeId }"
        @click="select(item.conversationId)"
      >
        <span class="im-avatar">
          <img v-if="item.peer.avatarUrl" :src="item.peer.avatarUrl" alt="" />
          <span
            v-else
            class="flex h-11 w-11 items-center justify-center rounded-full bg-[#ececec] text-[14px] text-[#8a8a8a]"
          >
            {{ (item.peer.nickname || '一').slice(0, 1) }}
          </span>
          <span v-if="item.unreadCount > 0" class="im-badge">
            {{ item.unreadCount > 99 ? '99+' : item.unreadCount }}
          </span>
        </span>
        <span class="min-w-0 flex-1">
          <span class="block truncate text-[15px] font-medium text-[#1f1f1f]">{{ item.peer.nickname }}</span>
          <span class="mt-0.5 block truncate text-[12px] text-[#8a8a8a]">{{ previewOf(item) }}</span>
        </span>
      </button>
    </aside>
    <div class="im-thread">
      <template v-if="activeId && activePeer()">
        <div class="im-thread-head">{{ activePeer()?.nickname }}</div>
        <div ref="logEl" class="im-log">
          <p v-if="loadingThread" class="py-10 text-center text-[13px] text-[#8a8a8a]">加载中…</p>
          <p v-else-if="threadFailed" class="py-10 text-center text-[13px] text-[#8a8a8a]">{{ threadFailed }}</p>
          <EmptyState v-else-if="!messages.length" compact src="/empty-chat.png" title="发一条消息打个招呼" />
          <div
            v-for="item in threadRows"
            :key="item.row.messageId"
            class="im-msg"
            :class="item.mine ? 'is-me' : 'is-them'"
          >
            <span class="im-msg-face" aria-hidden="true">
              <img v-if="item.face.url" :src="item.face.url" alt="" />
              <span v-else>{{ item.face.letter }}</span>
            </span>
            <div class="im-msg-body">
              <p class="im-bubble">{{ bubbleText(item.row) }}</p>
              <span class="im-time">{{ timeText(item.row.createdAt) }}</span>
            </div>
          </div>
        </div>
        <form class="im-composer" @submit.prevent="send">
          <input v-model="draft" class="im-input" maxlength="2000" placeholder="发消息…" />
          <button type="submit" class="im-send" :disabled="!draft.trim() || sending" aria-label="发送">
            <Icon name="send" :size="16" />
          </button>
        </form>
      </template>
      <EmptyState v-else class="im-empty-wrap" src="/empty-chat.png" title="选择一个会话开始聊天" />
    </div>
  </section>
</template>
