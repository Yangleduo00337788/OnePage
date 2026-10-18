<!--
  私信：左会话 GET /conversations，右对话拉/发明文。
  查询 c=会话 id。他人主页开聊后带这个参数。实时走 WS /ws/im，断线才 3 秒轮询。可发文本和图片。
-->
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError, type ConversationItem, type ImMessage } from '@onepage/api-client'
import Icon from '../components/Icon.vue'
import EmptyState from '../components/EmptyState.vue'
import { useAuthModal } from '../stores/authModal'
import { useNoteDetail } from '../stores/noteDetail'
import { useImSocket } from '../stores/imSocket'
import { useSession } from '../stores/session'

const route = useRoute()
const router = useRouter()
const session = useSession()
const imSocket = useImSocket()
const auth = useAuthModal()
const noteDetail = useNoteDetail()

const list = ref<ConversationItem[]>([])
const listFailed = ref('')
const activeId = ref('')
const messages = ref<ImMessage[]>([])
const threadFailed = ref('')
const loadingThread = ref(false)
const draft = ref('')
const sending = ref(false)
const viewerUrl = ref('')
const fileEl = ref<HTMLInputElement | null>(null)
const logEl = ref<HTMLElement | null>(null)
let poll: ReturnType<typeof setInterval> | null = null
let unsub: (() => void) | null = null
const IM_IMAGE_MAX = 10 * 1024 * 1024
const IM_IMAGE_MIMES = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif'])

function previewOf(item: ConversationItem) {
  const last = item.lastMessage
  if (!last) return '暂无消息'
  if (last.text) return last.text
  if (last.msgType === 'image') return '[图片]'
  if (last.msgType === 'post_card') return '[笔记]'
  if (last.msgType === 'live_card') return '[直播]'
  return '暂无消息'
}

function bubbleText(row: ImMessage) {
  return row.content?.text || ''
}

function isImage(row: ImMessage) {
  return row.msgType === 'image' && Boolean(row.content?.url)
}

function isPostCard(row: ImMessage) {
  return row.msgType === 'post_card' && Boolean(row.content?.postId)
}

function isLiveCard(row: ImMessage) {
  return row.msgType === 'live_card' && Boolean(row.content?.sessionId)
}

function openPostCard(row: ImMessage) {
  const id = row.content?.postId
  if (!id) return
  noteDetail.show({
    id,
    title: row.content?.title || '笔记',
    author: row.content?.authorNickname || '',
    likes: '0',
    image: row.content?.coverUrl || '',
  })
}

function openLiveCard(row: ImMessage) {
  const id = row.content?.sessionId
  if (!id) return
  void router.push(`/live/${encodeURIComponent(id)}`)
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
    if (!opts?.silent) void loadList()
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

function ingest(row: ImMessage, conversationId: string) {
  if (conversationId === activeId.value) {
    if (!messages.value.some((it) => it.messageId === row.messageId)) {
      messages.value = messages.value.concat(row)
      scrollBottom()
    }
  }
  void loadList()
}

async function send() {
  const text = draft.value.trim()
  if (!text || !activeId.value || sending.value) return
  sending.value = true
  threadFailed.value = ''
  try {
    const row = await api.sendMessage(activeId.value, text)
    draft.value = ''
    ingest(row, activeId.value)
  } catch (e) {
    threadFailed.value = e instanceof ApiError ? e.message : '发送失败'
  } finally {
    sending.value = false
  }
}

function pickImage() {
  if (sending.value) return
  fileEl.value?.click()
}

async function onImageFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !activeId.value || sending.value) return
  if (!IM_IMAGE_MIMES.has(file.type)) {
    threadFailed.value = '只支持 jpg / png / webp / gif'
    return
  }
  if (file.size > IM_IMAGE_MAX) {
    threadFailed.value = '图片不能超过 10MB'
    return
  }
  sending.value = true
  threadFailed.value = ''
  try {
    const ticket = await api.presign(file.type, file.size, 'im_image')
    await api.putObject(ticket.uploadUrl, file, file.type)
    await api.completeUpload(ticket.mediaId)
    const row = await api.sendImageMessage(activeId.value, ticket.mediaId)
    ingest(row, activeId.value)
  } catch (e) {
    threadFailed.value = e instanceof ApiError ? e.message : e instanceof Error ? e.message : '发送失败'
  } finally {
    sending.value = false
  }
}

function startPoll() {
  if (poll) return
  poll = setInterval(() => {
    if (imSocket.connected) return
    void loadList()
    void loadThread({ silent: true })
  }, 3000)
}

function stopPoll() {
  if (poll) {
    clearInterval(poll)
    poll = null
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
  startPoll()
  unsub = imSocket.subscribe((frame) => {
    if ((frame.op === 'message' || frame.op === 'ack') && frame.payload && frame.conversationId) {
      ingest(frame.payload, frame.conversationId)
      if (frame.conversationId === activeId.value) {
        void loadThread({ silent: true })
      }
    }
  })
})

onUnmounted(() => {
  stopPoll()
  unsub?.()
})

watch(
  () => imSocket.connected,
  (online) => {
    if (online) {
      void loadList()
      void loadThread({ silent: true })
    }
  },
)

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
              <button
                v-if="isImage(item.row)"
                type="button"
                class="im-pic"
                @click="viewerUrl = item.row.content.url || ''"
              >
                <img :src="item.row.content.url" alt="" />
              </button>
              <button
                v-else-if="isPostCard(item.row)"
                type="button"
                class="im-card"
                @click="openPostCard(item.row)"
              >
                <img v-if="item.row.content.coverUrl" :src="item.row.content.coverUrl" alt="" />
                <span v-else class="im-card-fallback">{{ item.row.content.title || '笔记' }}</span>
                <span class="im-card-meta">
                  <span class="im-card-title">{{ item.row.content.title || '笔记' }}</span>
                  <span class="im-card-sub">{{ item.row.content.authorNickname || '笔记' }}</span>
                </span>
              </button>
              <button
                v-else-if="isLiveCard(item.row)"
                type="button"
                class="im-card"
                @click="openLiveCard(item.row)"
              >
                <img v-if="item.row.content.coverUrl" :src="item.row.content.coverUrl" alt="" />
                <span v-else class="im-card-fallback">{{ item.row.content.title || '直播' }}</span>
                <span class="im-card-meta">
                  <span class="im-card-title">{{ item.row.content.title || '直播' }}</span>
                  <span class="im-card-sub">{{ item.row.content.authorNickname || '直播' }}</span>
                </span>
              </button>
              <p v-else class="im-bubble">{{ bubbleText(item.row) }}</p>
              <span class="im-time">{{ timeText(item.row.createdAt) }}</span>
            </div>
          </div>
        </div>
        <form class="im-composer" @submit.prevent="send">
          <input
            ref="fileEl"
            class="sr-only"
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif"
            @change="onImageFile"
          />
          <button
            type="button"
            class="im-attach"
            :disabled="sending"
            aria-label="发送图片"
            @click="pickImage"
          >
            <Icon name="image" :size="18" />
          </button>
          <input v-model="draft" class="im-input" maxlength="2000" placeholder="发消息…" />
          <button type="submit" class="im-send" :disabled="!draft.trim() || sending" aria-label="发送">
            <Icon name="send" :size="16" />
          </button>
        </form>
      </template>
      <EmptyState v-else class="im-empty-wrap" src="/empty-chat.png" title="选择一个会话开始聊天" />
    </div>
    <button v-if="viewerUrl" type="button" class="im-viewer" aria-label="关闭图片" @click="viewerUrl = ''">
      <img :src="viewerUrl" alt="" />
    </button>
  </section>
</template>
