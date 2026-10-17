<!-- 独立直播间：学小红书左右分栏（黑底）。不做礼物/连麦。GET /live/sessions/{id}。 -->
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError, type LiveChatLine, type LiveSession, type UserProfile } from '@onepage/api-client'
import Icon from '../components/Icon.vue'
import LivePlayer from '../components/LivePlayer.vue'
import ReportDialog from '../components/ReportDialog.vue'
import { formatCount } from '../data/home'
import { useSession } from '../stores/session'
import { useAuthModal } from '../stores/authModal'

const route = useRoute()
const router = useRouter()
const session = useSession()
const auth = useAuthModal()
const watching = ref<LiveSession | null>(null)
const host = ref<UserProfile | null>(null)
const failed = ref('')
const following = ref(false)
const followBusy = ref(false)
const draft = ref('')
const chats = ref<LiveChatLine[]>([])
const chatBox = ref<HTMLElement | null>(null)
const sayBusy = ref(false)
const sayHint = ref('')
let hintTimer = 0
const hostOpen = ref(false)
const reportOpen = ref(false)
const webFs = ref(false)
const askEnd = ref(false)
const ending = ref(false)
let hostTimer = 0
let pollTimer = 0
let chatTimer = 0

const isSelf = computed(() => {
  const me = session.me?.userId
  const owner = watching.value?.owner?.userId
  return Boolean(me && owner && me === owner)
})

const hostBio = computed(() => host.value?.bio?.trim() || '这个人还没有简介')
const hostStats = computed(() => {
  const s = host.value?.stats
  return {
    following: formatCount(s?.followingCount ?? 0),
    fans: formatCount(s?.followerCount ?? 0),
    likes: formatCount((s?.likeReceivedCount ?? 0) + (s?.favoriteReceivedCount ?? 0)),
  }
})
const likeLine = computed(() => `${hostStats.value.likes}赞`)

function stopPoll() {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = 0
  }
  if (chatTimer) {
    window.clearInterval(chatTimer)
    chatTimer = 0
  }
}

function startPoll(id: string) {
  stopPoll()
  pollTimer = window.setInterval(() => {
    void peekWatch(id)
  }, 8000)
  chatTimer = window.setInterval(() => {
    void loadChat(id, false)
  }, 3000)
}

function flashHint(text: string) {
  sayHint.value = text
  window.clearTimeout(hintTimer)
  hintTimer = window.setTimeout(() => {
    if (sayHint.value === text) sayHint.value = ''
  }, 2600)
}

function lastChatId() {
  const last = chats.value[chats.value.length - 1]
  return last?.messageId || ''
}

function mergeChat(items: LiveChatLine[]) {
  if (!items.length) return
  const seen = new Set(chats.value.map((row) => row.messageId))
  const extra: LiveChatLine[] = []
  for (const row of items) {
    if (!seen.has(row.messageId)) {
      seen.add(row.messageId)
      extra.push(row)
    }
  }
  if (!extra.length) return
  chats.value = chats.value.concat(extra)
  void nextTick(() => {
    const el = chatBox.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function loadChat(id: string, reset: boolean) {
  try {
    const after = reset ? '' : lastChatId()
    const page = await api.liveMessages(id, after || undefined)
    if (reset) {
      chats.value = page.items || []
      void nextTick(() => {
        const el = chatBox.value
        if (el) el.scrollTop = el.scrollHeight
      })
    } else {
      mergeChat(page.items || [])
    }
  } catch {
    /* 轮询失败不打断观看 */
  }
}

async function peekWatch(id: string) {
  try {
    const next = await api.liveSession(id, { peek: true })
    if (!watching.value || watching.value.sessionId !== next.sessionId) return
    watching.value = next
    if (next.status === 'ended') {
      stopPoll()
      void loadChat(id, false)
    }
  } catch {
    if (watching.value) {
      watching.value = { ...watching.value, status: 'ended' }
    }
    stopPoll()
  }
}

async function loadWatch(id: string) {
  failed.value = ''
  following.value = false
  host.value = null
  askEnd.value = false
  chats.value = []
  draft.value = ''
  stopPoll()
  try {
    watching.value = await api.liveSession(id)
    const pageId = watching.value.owner?.pageId
    if (pageId) {
      try {
        const profile = await api.userByPageId(pageId)
        host.value = profile
        following.value = Boolean(profile.followedByMe)
      } catch {
        following.value = false
      }
    }
    void loadChat(id, true)
    if (watching.value.status === 'live') startPoll(id)
  } catch {
    watching.value = null
    failed.value = '这场直播已经结束或不存在'
  }
}

function goAuthor() {
  const pageId = watching.value?.owner?.pageId
  if (!pageId) return
  void router.push(`/u/${encodeURIComponent(pageId)}`)
}

function goList() {
  void router.push('/live')
}

function openHost() {
  window.clearTimeout(hostTimer)
  hostOpen.value = true
}

function closeHost() {
  window.clearTimeout(hostTimer)
  hostTimer = window.setTimeout(() => {
    hostOpen.value = false
  }, 120)
}

async function toggleFollow() {
  const pageId = watching.value?.owner?.pageId
  if (!pageId || isSelf.value || followBusy.value) return
  if (!session.me) {
    auth.show('login', route.fullPath)
    return
  }
  followBusy.value = true
  try {
    if (following.value) {
      await api.unfollowUser(pageId)
      following.value = false
    } else {
      await api.followUser(pageId)
      following.value = true
    }
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) auth.show('login', route.fullPath)
  } finally {
    followBusy.value = false
  }
}

function openReport() {
  if (!session.me) {
    auth.show('login', route.fullPath)
    return
  }
  reportOpen.value = true
}

function requestEnd() {
  askEnd.value = true
}

function cancelEnd() {
  askEnd.value = false
}

async function stopLive() {
  const roomId = watching.value?.roomId
  if (!roomId || ending.value) return
  ending.value = true
  try {
    await api.endLive(roomId)
    if (watching.value) {
      watching.value = { ...watching.value, status: 'ended' }
    }
    askEnd.value = false
    stopPoll()
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) auth.show('login', route.fullPath)
  } finally {
    ending.value = false
  }
}

function goChatAuthor(pageId: string) {
  if (!pageId) return
  void router.push(`/u/${encodeURIComponent(pageId)}`)
}

async function onSay() {
  const id = watching.value?.sessionId
  const text = draft.value.trim()
  if (!id || !text || sayBusy.value || watching.value?.status !== 'live') return
  if (!session.me) {
    auth.show('login', route.fullPath)
    return
  }
  sayBusy.value = true
  try {
    const line = await api.sendLiveMessage(id, text)
    draft.value = ''
    mergeChat([line])
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      auth.show('login', route.fullPath)
    } else {
      flashHint(e instanceof ApiError ? e.message : '发送失败')
    }
  } finally {
    sayBusy.value = false
  }
}

watch(
  () => route.params.sessionId,
  (id) => {
    webFs.value = false
    if (typeof id === 'string' && id) void loadWatch(id)
  },
)

onMounted(() => {
  const id = route.params.sessionId
  if (typeof id === 'string' && id) void loadWatch(id)
})

onUnmounted(() => {
  window.clearTimeout(hostTimer)
  window.clearTimeout(hintTimer)
  stopPoll()
})
</script>

<template>
  <section class="live-page" :class="{ 'is-web-fs': webFs }">
    <div v-if="failed && !watching" class="live-miss">
      <p>{{ failed }}</p>
      <button type="button" class="live-back-btn" @click="goList">返回直播列表</button>
    </div>
    <template v-else-if="watching">
      <div class="live-left">
        <LivePlayer :session="watching" :web-fs="webFs" @web-fs="webFs = !webFs" />
        <div class="live-chip">
          <button type="button" class="live-chip-back" aria-label="返回" @click="goList">
            <Icon name="chevron-left" :size="20" />
          </button>
          <div class="host-hit" :class="{ 'is-open': hostOpen }" @mouseenter="openHost" @mouseleave="closeHost">
            <button type="button" class="live-chip-host" @click="goAuthor">
              <img
                v-if="watching.owner?.avatarUrl"
                :src="watching.owner.avatarUrl"
                alt=""
                class="live-chip-ava"
              />
              <span v-else class="live-chip-ava is-ph">{{ (watching.owner?.nickname || '主').slice(0, 1) }}</span>
              <span class="live-chip-meta">
                <span class="live-chip-name">{{ watching.owner?.nickname || '主播' }}</span>
                <span class="live-chip-likes">{{ likeLine }}</span>
              </span>
            </button>
            <div class="host-card" @mouseenter="openHost" @mouseleave="closeHost">
              <div class="host-card-top">
                <button type="button" class="host-go" @click="goAuthor">
                  <img
                    v-if="watching.owner?.avatarUrl"
                    :src="watching.owner.avatarUrl"
                    alt=""
                    class="host-card-ava"
                  />
                  <span v-else class="host-card-ava is-ph">{{ (watching.owner?.nickname || '主').slice(0, 1) }}</span>
                </button>
                <button type="button" class="host-go host-card-name" @click="goAuthor">
                  {{ watching.owner?.nickname || '主播' }}
                </button>
                <button
                  v-if="!isSelf"
                  type="button"
                  class="host-report"
                  aria-label="举报这场直播"
                  title="举报这场直播"
                  @click="openReport"
                >
                  <Icon name="triangle-alert" :size="16" />
                </button>
                <button
                  v-if="!isSelf"
                  type="button"
                  class="live-follow"
                  :class="{ 'is-on': following }"
                  :disabled="followBusy"
                  @click="toggleFollow"
                >
                  {{ following ? '已关注' : '关注' }}
                </button>
              </div>
              <p class="host-bio">{{ hostBio }}</p>
              <div class="host-stats">
                <span><strong>{{ hostStats.following }}</strong> 关注</span>
                <span><strong>{{ hostStats.fans }}</strong> 粉丝</span>
                <span><strong>{{ hostStats.likes }}</strong> 获赞与收藏</span>
              </div>
            </div>
          </div>
          <button
            v-if="!isSelf"
            type="button"
            class="live-follow"
            :class="{ 'is-on': following }"
            :disabled="followBusy"
            @click="toggleFollow"
          >
            {{ following ? '已关注' : '关注' }}
          </button>
          <button
            v-else-if="watching.status === 'live'"
            type="button"
            class="live-end-chip"
            @click="requestEnd"
          >
            结束直播
          </button>
        </div>
      </div>
      <aside v-show="!webFs" class="live-right">
        <p class="live-online">在线人数 · {{ formatCount(watching.viewerCount) }}</p>
        <div class="live-intro">
          <p class="live-intro-k">直播介绍</p>
          <h1>{{ watching.title }}</h1>
          <p class="live-intro-b">
            <span class="live-notice">公告</span>
            欢迎来到直播间。请理性发言，遵守社区规范。
          </p>
        </div>
        <div ref="chatBox" class="live-chat">
          <p v-if="!chats.length" class="live-chat-empty">还没有人发言</p>
          <p v-for="row in chats" :key="row.messageId" class="live-chat-row">
            <span
              v-if="row.author?.userId === watching.owner?.userId"
              class="live-host-tag"
            >主播</span>
            <button
              type="button"
              class="live-chat-name"
              :class="{ 'is-host': row.author?.userId === watching.owner?.userId }"
              @click="goChatAuthor(row.author?.pageId || '')"
            >
              {{ row.author?.nickname || '用户' }}
            </button>
            <span class="live-chat-body">{{ row.body }}</span>
          </p>
        </div>
        <form class="live-say" @submit.prevent="onSay">
          <input
            v-model="draft"
            type="text"
            maxlength="200"
            :placeholder="watching.status === 'ended' ? '直播已结束' : '说点什么…'"
            :disabled="watching.status !== 'live' || sayBusy"
          />
          <button
            type="submit"
            class="live-say-send"
            :disabled="watching.status !== 'live' || sayBusy || !draft.trim()"
          >
            发送
          </button>
        </form>
        <p v-if="sayHint" class="live-say-hint">{{ sayHint }}</p>
      </aside>
    </template>
    <div v-if="askEnd" class="live-end-mask" @click.self="cancelEnd">
      <div class="live-end-card">
        <div class="live-end-head">
          <strong>结束直播</strong>
          <button type="button" class="live-end-x" aria-label="关闭" @click="cancelEnd">
            <Icon name="x" :size="16" />
          </button>
        </div>
        <p>确定结束这场直播吗？结束后观众将无法继续观看。请先在这里结束，再在 OBS 停止推流。</p>
        <button type="button" class="live-end-no" :disabled="ending" @click="cancelEnd">再想想</button>
        <button type="button" class="live-end-yes" :disabled="ending" @click="stopLive">
          {{ ending ? '结束中…' : '确定结束' }}
        </button>
      </div>
    </div>
    <ReportDialog
      :open="reportOpen"
      target-type="live"
      :target-id="watching?.sessionId || ''"
      @close="reportOpen = false"
    />
  </section>
</template>

<style scoped>
.live-page {
  display: flex;
  height: 100vh;
  min-height: 480px;
  overflow: hidden;
  background: #000;
}

.live-left {
  position: relative;
  flex: 1;
  min-width: 0;
  background: #0a0a0a;
}

.live-chip {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 4;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.45);
}

.live-chip-back,
.live-chip-host {
  display: flex;
  align-items: center;
  color: #fff;
}

.live-chip-back {
  width: 32px;
  height: 32px;
  justify-content: center;
  border-radius: 50%;
}

.live-chip-back:hover,
.live-chip-back:focus-visible,
.live-chip-back:active {
  background: rgba(255, 255, 255, 0.18);
}

.live-chip-host {
  gap: 8px;
  text-align: left;
}

.live-chip-ava,
.host-card-ava {
  border-radius: 50%;
  object-fit: cover;
  background: #333;
}

.live-chip-ava {
  height: 36px;
  width: 36px;
}

.live-chip-ava.is-ph,
.host-card-ava.is-ph {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
}

.live-chip-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
}

.live-chip-name {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 600;
}

.live-chip-likes {
  font-size: 11px;
  color: #c8c8c8;
}

.live-follow {
  height: 28px;
  margin-right: 6px;
  padding: 0 12px;
  border-radius: 14px;
  background: #ff2442;
  color: #fff;
  font-size: 13px;
}

.live-follow.is-on {
  background: rgba(255, 255, 255, 0.18);
}

.live-end-chip {
  height: 28px;
  margin-right: 6px;
  padding: 0 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  font-size: 13px;
}

.live-end-chip:hover {
  background: #ff2442;
}

.live-end-mask {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
}

.live-end-card {
  width: min(400px, 90vw);
  border-radius: 20px;
  background: #fff;
  padding: 16px 18px 20px;
  color: #1f1f1f;
}

.live-end-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 16px;
}

.live-end-x {
  display: flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.live-end-x:hover {
  background: #f2f2f2;
}

.live-end-card p {
  margin: 0 0 16px;
  font-size: 13px;
  line-height: 1.6;
  color: #8a8a8a;
}

.live-end-no,
.live-end-yes {
  display: flex;
  width: 100%;
  height: 40px;
  align-items: center;
  justify-content: center;
  border-radius: 20px;
  font-size: 14px;
}

.live-end-no {
  background: #f3f3f3;
  color: #1f1f1f;
}

.live-end-yes {
  margin-top: 8px;
  background: #ff2442;
  color: #fff;
}

.host-hit {
  position: relative;
}

.host-card {
  position: absolute;
  top: calc(100% + 10px);
  left: 0;
  z-index: 30;
  display: none;
  width: 360px;
  flex-direction: column;
  padding: 14px 16px 12px;
  border-radius: 20px;
  background: #fff;
  color: #1f1f1f;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
}

.host-hit.is-open .host-card {
  display: flex;
}

.host-card-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.host-card-ava {
  height: 44px;
  width: 44px;
}

.host-card-name {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 16px;
  font-weight: 600;
  text-align: left;
}

.host-report {
  display: flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #8a8a8a;
}

.host-report:hover {
  background: #f3f3f3;
}

.host-card .live-follow {
  margin-right: 0;
  height: 32px;
  padding: 0 14px;
  border-radius: 20px;
}

.host-card .live-follow.is-on {
  background: #f2f2f2;
  color: #555;
}

.host-bio {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #555;
}

.host-stats {
  display: flex;
  gap: 14px;
  margin-top: 10px;
  font-size: 12px;
  color: #8a8a8a;
}

.host-stats strong {
  margin-right: 4px;
  color: #1f1f1f;
  font-size: 14px;
}

.live-right {
  display: flex;
  width: 340px;
  min-height: 0;
  flex-shrink: 0;
  flex-direction: column;
  background: #161616;
  color: #f2f2f2;
  padding: 16px 16px 12px;
}

.live-online {
  margin: 0 0 14px;
  font-size: 13px;
  color: #b3b3b3;
}

.live-intro-k {
  margin: 0 0 6px;
  font-size: 12px;
  color: #8a8a8a;
}

.live-intro h1 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
}

.live-intro-b {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 22px;
  color: #c8c8c8;
}

.live-notice {
  display: inline-flex;
  height: 22px;
  margin-right: 8px;
  padding: 0 6px;
  align-items: center;
  vertical-align: top;
  border-radius: 16px;
  background: #3a3a3a;
  color: #c8c8c8;
  font-size: 11px;
  font-weight: 700;
  line-height: 22px;
}

.live-chat {
  flex: 1;
  min-height: 0;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #2a2a2a;
  overflow: auto;
  scrollbar-width: none;
}

.live-chat::-webkit-scrollbar {
  display: none;
}

.live-chat-empty {
  margin: 12px 0 0;
  font-size: 13px;
  color: #6a6a6a;
}

.live-chat-row {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.55;
  word-break: break-word;
}

.live-chat-name {
  color: #8a8a8a;
  font-weight: 600;
}

.live-chat-name::after {
  content: '：';
  font-weight: 500;
}

.live-chat-name.is-host {
  color: #ff2442;
}

.live-host-tag {
  display: inline-flex;
  height: 16px;
  margin-right: 6px;
  padding: 0 6px;
  align-items: center;
  border-radius: 16px;
  background: linear-gradient(180deg, #fff8dc 0%, #ffe9b0 100%);
  color: #8a6a1a;
  font-size: 10px;
  font-weight: 700;
  line-height: 16px;
  vertical-align: 1px;
}

.live-chat-body {
  color: #f2f2f2;
}

.live-say {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}

.live-say input {
  flex: 1;
  min-width: 0;
  height: 40px;
  border-radius: 20px;
  background: #2a2a2a;
  padding: 0 16px;
  font-size: 13px;
  color: #f2f2f2;
  outline: none;
}

.live-say input:disabled {
  color: #8a8a8a;
}

.live-say-send {
  flex-shrink: 0;
  height: 40px;
  padding: 0 16px;
  border-radius: 20px;
  background: #ff2442;
  color: #fff;
  font-size: 13px;
  line-height: 40px;
  white-space: nowrap;
}

.live-say-send:disabled {
  background: #5c1822;
  color: #8a8a8a;
  cursor: default;
}

.live-say-hint {
  margin-top: 8px;
  color: #ff8a9a;
  font-size: 12px;
}

.live-miss {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: #eee;
  font-size: 14px;
}

.live-back-btn {
  height: 40px;
  padding: 0 16px;
  border-radius: 20px;
  background: #ff2442;
  color: #fff;
  font-size: 14px;
}
</style>
