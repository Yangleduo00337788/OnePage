<!-- 独立直播间：学小红书左右分栏（黑底）。不做礼物/连麦。GET /live/sessions/{id}。 -->
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError, type LiveChatLine, type LiveSession, type UserProfile } from '@onepage/api-client'
import Icon from '../components/Icon.vue'
import IdentityBadges from '../components/IdentityBadges.vue'
import EmptyState from '../components/EmptyState.vue'
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
const peekOpen = ref(false)
const peekPageId = ref('')
const peekNick = ref('')
const peekAva = ref('')
const peekProfile = ref<UserProfile | null>(null)
const peekFollowed = ref(false)
const peekBox = ref({ top: '0px', left: '0px' })
const peekUserId = ref('')
const peekCache = new Map<string, UserProfile>()
let peekTimer = 0
const reportOpen = ref(false)
const reportKind = ref<'live' | 'user'>('live')
const reportTargetId = ref('')
const webFs = ref(false)
const askEnd = ref(false)
const ending = ref(false)
const DEFAULT_NOTICE = '欢迎来到直播间。请理性发言，遵守社区规范。'
type SharePeer = {
  userId: string
  pageId: string
  nickname: string
  avatarUrl: string
  conversationId?: string
}
const shareOpen = ref(false)
const sharePeers = ref<SharePeer[]>([])
const shareLoading = ref(false)
const shareBusy = ref('')
const noticeOpen = ref(false)
const noticeDraft = ref('')
const noticeBusy = ref(false)
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
const peekSelf = computed(() => {
  const me = session.me?.userId
  const uid = peekProfile.value?.userId
  return Boolean(me && uid && me === uid)
})
const peekBio = computed(() => peekProfile.value?.bio?.trim() || '这个人还没有简介')
const noticeText = computed(() => watching.value?.notice?.trim() || DEFAULT_NOTICE)

const peekStats = computed(() => {
  const s = peekProfile.value?.stats
  return {
    following: formatCount(s?.followingCount ?? 0),
    fans: formatCount(s?.followerCount ?? 0),
    likes: formatCount((s?.likeReceivedCount ?? 0) + (s?.favoriteReceivedCount ?? 0)),
  }
})

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
  void beatWatch(id)
  pollTimer = window.setInterval(() => {
    void peekWatch(id)
    void beatWatch(id)
  }, 8000)
  chatTimer = window.setInterval(() => {
    void loadChat(id, false)
  }, 3000)
}

function presenceIdOf(sessionId: string) {
  const key = `onepage-live-presence-${sessionId}`
  let id = sessionStorage.getItem(key)
  if (!id) {
    id = crypto.randomUUID()
    sessionStorage.setItem(key, id)
  }
  return id
}

async function beatWatch(id: string) {
  if (watching.value?.status !== 'live') return
  try {
    const tick = await api.liveHeartbeat(id, presenceIdOf(id))
    if (!watching.value || watching.value.sessionId !== id) return
    watching.value = { ...watching.value, viewerCount: tick.viewerCount, status: tick.status }
    if (tick.join) mergeChat([tick.join])
    if (tick.status === 'ended') stopPoll()
  } catch (e) {
    if (e instanceof ApiError && e.status === 409) {
      if (watching.value) watching.value = { ...watching.value, status: 'ended' }
      stopPoll()
    }
  }
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
  peekOpen.value = false
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
  reportKind.value = 'live'
  reportTargetId.value = watching.value?.sessionId || ''
  reportOpen.value = true
}

function openPeekReport() {
  const userId = peekProfile.value?.userId || peekUserId.value
  if (!userId || peekSelf.value) return
  if (!session.me) {
    auth.show('login', route.fullPath)
    return
  }
  peekOpen.value = false
  reportKind.value = 'user'
  reportTargetId.value = userId
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
  peekOpen.value = false
  void router.push(`/u/${encodeURIComponent(pageId)}`)
}

function keepPeek() {
  window.clearTimeout(peekTimer)
}

function closePeek() {
  window.clearTimeout(peekTimer)
  peekTimer = window.setTimeout(() => {
    peekOpen.value = false
  }, 120)
}

function placePeek(el: HTMLElement) {
  const r = el.getBoundingClientRect()
  const width = 360
  let left = r.left - width - 10
  if (left < 8) left = 8
  let top = r.top
  const maxTop = window.innerHeight - 180
  if (top > maxTop) top = Math.max(8, maxTop)
  peekBox.value = { top: `${top}px`, left: `${left}px` }
}

async function openPeek(pageId: string, nick: string, ava: string, userId: string, ev: MouseEvent) {
  if (!pageId) return
  keepPeek()
  peekPageId.value = pageId
  peekNick.value = nick || '用户'
  peekAva.value = ava || ''
  peekUserId.value = userId || ''
  peekOpen.value = true
  placePeek(ev.currentTarget as HTMLElement)
  const cached = peekCache.get(pageId)
  if (cached) {
    peekProfile.value = cached
    peekUserId.value = cached.userId || peekUserId.value
    peekFollowed.value = Boolean(cached.followedByMe)
    if (pageId === watching.value?.owner?.pageId) peekFollowed.value = following.value
    return
  }
  peekProfile.value = null
  peekFollowed.value = false
  try {
    const profile = await api.userByPageId(pageId)
    peekCache.set(pageId, profile)
    if (peekPageId.value !== pageId) return
    peekProfile.value = profile
    peekUserId.value = profile.userId || peekUserId.value
    peekFollowed.value =
      pageId === watching.value?.owner?.pageId ? following.value : Boolean(profile.followedByMe)
  } catch {
    /* 资料失败仍可点进主页 */
  }
}

async function togglePeekFollow() {
  const pageId = peekPageId.value
  if (!pageId || peekSelf.value || followBusy.value) return
  if (!session.me) {
    auth.show('login', route.fullPath)
    return
  }
  followBusy.value = true
  try {
    if (peekFollowed.value) {
      await api.unfollowUser(pageId)
      peekFollowed.value = false
    } else {
      await api.followUser(pageId)
      peekFollowed.value = true
    }
    const cached = peekCache.get(pageId)
    if (cached) cached.followedByMe = peekFollowed.value
    if (pageId === watching.value?.owner?.pageId) following.value = peekFollowed.value
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) auth.show('login', route.fullPath)
  } finally {
    followBusy.value = false
  }
}

function needLogin(e: unknown) {
  if (e instanceof ApiError && e.status === 401) {
    auth.show('login', route.fullPath)
    return true
  }
  return false
}

function openShare() {
  if (!session.me) {
    auth.show('login', route.fullPath)
    return
  }
  shareOpen.value = true
}

async function loadSharePeers() {
  shareLoading.value = true
  sharePeers.value = []
  try {
    const conv = await api.conversations()
    const seen = new Set<string>()
    const rows: SharePeer[] = []
    for (const it of conv.items || []) {
      if (!it.peer?.userId || seen.has(it.peer.userId)) continue
      seen.add(it.peer.userId)
      rows.push({
        userId: it.peer.userId,
        pageId: it.peer.pageId,
        nickname: it.peer.nickname,
        avatarUrl: it.peer.avatarUrl,
        conversationId: it.conversationId,
      })
    }
    if (session.me) {
      const fol = await api.userFollowing(session.me.pageId, undefined, 50)
      for (const u of fol.items) {
        if (!u.userId || seen.has(u.userId) || u.userId === session.me.userId) continue
        seen.add(u.userId)
        rows.push({
          userId: u.userId,
          pageId: u.pageId,
          nickname: u.nickname,
          avatarUrl: u.avatarUrl,
        })
      }
    }
    sharePeers.value = rows
  } catch (e) {
    if (needLogin(e)) shareOpen.value = false
  } finally {
    shareLoading.value = false
  }
}

async function shareTo(peer: SharePeer) {
  const id = watching.value?.sessionId || ''
  if (!id || shareBusy.value) return
  shareBusy.value = peer.userId
  try {
    await api.shareLiveIm(
      id,
      peer.conversationId ? { conversationId: peer.conversationId } : { targetUserId: peer.userId },
    )
    flashHint('已发送')
    shareOpen.value = false
  } catch (e) {
    if (needLogin(e)) {
      shareOpen.value = false
      return
    }
    flashHint(e instanceof ApiError ? e.message : '发送失败')
  } finally {
    shareBusy.value = ''
  }
}

async function copyLiveLink() {
  const id = watching.value?.sessionId || ''
  let url = `${location.origin}/live/${id}`
  if (id) {
    try {
      const r = await api.shareLiveLink(id)
      if (r.shareUrl) url = r.shareUrl
    } catch (e) {
      if (needLogin(e)) {
        shareOpen.value = false
        return
      }
    }
  }
  try {
    await navigator.clipboard.writeText(url)
    flashHint('链接已复制')
  } catch {
    flashHint('复制失败')
  }
  shareOpen.value = false
}

function openNotice() {
  if (!isSelf.value || watching.value?.status !== 'live') return
  noticeDraft.value = watching.value?.notice?.trim() || DEFAULT_NOTICE
  noticeOpen.value = true
}

async function saveNotice() {
  const roomId = watching.value?.roomId
  if (!roomId || noticeBusy.value) return
  noticeBusy.value = true
  try {
    const r = await api.updateLiveNotice(roomId, noticeDraft.value)
    if (watching.value) watching.value = { ...watching.value, notice: r.notice }
    noticeOpen.value = false
    flashHint('公告已更新')
  } catch (e) {
    if (needLogin(e)) {
      noticeOpen.value = false
      return
    }
    flashHint(e instanceof ApiError ? e.message : '保存失败')
  } finally {
    noticeBusy.value = false
  }
}

watch(shareOpen, (on) => {
  if (on) void loadSharePeers()
})

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
  window.clearTimeout(peekTimer)
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
        <LivePlayer :session="watching" :web-fs="webFs" @web-fs="webFs = !webFs" @share="openShare" />
        <div class="live-chip">
          <button type="button" class="live-chip-back" aria-label="返回" @click="goList">
            <Icon name="chevron-left" :size="20" />
          </button>
          <div class="host-hit" :class="{ 'is-open': hostOpen }" @mouseenter="openHost" @mouseleave="closeHost">
            <button type="button" class="live-chip-host" @click="goAuthor">
              <span class="live-chip-ava-wrap">
                <img
                  v-if="watching.owner?.avatarUrl"
                  :src="watching.owner.avatarUrl"
                  alt=""
                  class="live-chip-ava"
                />
                <span v-else class="live-chip-ava is-ph">{{ (watching.owner?.nickname || '主').slice(0, 1) }}</span>
                <IdentityBadges
                  size="sm"
                  :live-host="watching.owner?.liveHost ?? host?.liveHost"
                  :verified="watching.owner?.verified ?? host?.verified"
                />
              </span>
              <span class="live-chip-meta">
                <span class="live-chip-name">{{ watching.owner?.nickname || '主播' }}</span>
                <span class="live-chip-likes">{{ likeLine }}</span>
              </span>
            </button>
            <div class="host-card" @mouseenter="openHost" @mouseleave="closeHost">
              <div class="host-card-top">
                <button type="button" class="host-go relative" @click="goAuthor">
                  <img
                    v-if="watching.owner?.avatarUrl"
                    :src="watching.owner.avatarUrl"
                    alt=""
                    class="host-card-ava"
                  />
                  <span v-else class="host-card-ava is-ph">{{ (watching.owner?.nickname || '主').slice(0, 1) }}</span>
                  <IdentityBadges
                    size="md"
                    :live-host="watching.owner?.liveHost ?? host?.liveHost"
                    :verified="watching.owner?.verified ?? host?.verified"
                  />
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
            <button
              v-if="isSelf && watching.status === 'live'"
              type="button"
              class="live-notice-text is-edit"
              @click="openNotice"
            >
              {{ noticeText }}
            </button>
            <span v-else class="live-notice-text">{{ noticeText }}</span>
          </p>
        </div>
        <div ref="chatBox" class="live-chat">
          <p v-if="!chats.length" class="live-chat-empty">还没有人发言</p>
          <p
            v-for="row in chats"
            :key="row.messageId"
            class="live-chat-row"
            :class="{ 'is-join': row.kind === 'join' }"
          >
            <template v-if="row.kind === 'join'">
              <button
                type="button"
                class="live-join-name"
                @mouseenter="openPeek(row.author?.pageId || '', row.author?.nickname || '用户', row.author?.avatarUrl || '', row.author?.userId || '', $event)"
                @mouseleave="closePeek"
                @click="goChatAuthor(row.author?.pageId || '')"
              >
                {{ row.author?.nickname || '用户' }}
              </button>
              来了
            </template>
            <template v-else>
              <span
                v-if="row.author?.userId === watching.owner?.userId"
                class="live-host-tag"
              >主播</span>
              <button
                type="button"
                class="live-chat-name"
                :class="{ 'is-host': row.author?.userId === watching.owner?.userId }"
                @mouseenter="openPeek(row.author?.pageId || '', row.author?.nickname || '用户', row.author?.avatarUrl || '', row.author?.userId || '', $event)"
                @mouseleave="closePeek"
                @click="goChatAuthor(row.author?.pageId || '')"
              >
                {{ row.author?.nickname || '用户' }}
              </button>
              <span class="live-chat-body">{{ row.body }}</span>
            </template>
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
    <div
      v-if="peekOpen && peekPageId"
      class="host-card live-peek"
      :style="peekBox"
      @mouseenter="keepPeek"
      @mouseleave="closePeek"
    >
      <div class="host-card-top">
        <button type="button" class="host-go" @click="goChatAuthor(peekPageId)">
          <img v-if="peekProfile?.avatarUrl || peekAva" :src="peekProfile?.avatarUrl || peekAva" alt="" class="host-card-ava" />
          <span v-else class="host-card-ava is-ph">{{ peekNick.slice(0, 1) }}</span>
        </button>
        <button type="button" class="host-go host-card-name" @click="goChatAuthor(peekPageId)">
          {{ peekProfile?.nickname || peekNick }}
        </button>
        <button
          v-if="!peekSelf"
          type="button"
          class="host-report"
          aria-label="举报这个用户"
          title="举报这个用户"
          @click="openPeekReport"
        >
          <Icon name="triangle-alert" :size="16" />
        </button>
        <button
          v-if="!peekSelf"
          type="button"
          class="live-follow"
          :class="{ 'is-on': peekFollowed }"
          :disabled="followBusy"
          @click="togglePeekFollow"
        >
          {{ peekFollowed ? '已关注' : '关注' }}
        </button>
      </div>
      <p class="host-bio">{{ peekBio }}</p>
      <div class="host-stats">
        <span><strong>{{ peekStats.following }}</strong> 关注</span>
        <span><strong>{{ peekStats.fans }}</strong> 粉丝</span>
        <span><strong>{{ peekStats.likes }}</strong> 获赞与收藏</span>
      </div>
    </div>
    <div v-if="shareOpen" class="live-end-mask" @click.self="shareOpen = false">
      <div class="live-share-card">
        <p class="live-share-title">分享至</p>
        <div class="live-share-list">
          <p v-if="shareLoading" class="live-share-empty">加载中…</p>
          <EmptyState v-else-if="!sharePeers.length" compact src="/empty-people.png" title="暂无好友" />
          <button
            v-for="peer in sharePeers"
            :key="peer.userId"
            type="button"
            class="live-share-row"
            :disabled="Boolean(shareBusy)"
            @click="shareTo(peer)"
          >
            <img v-if="peer.avatarUrl" :src="peer.avatarUrl" alt="" />
            <span v-else class="live-share-ph">{{ (peer.nickname || '一').slice(0, 1) }}</span>
            <span class="live-share-meta">
              <strong>{{ peer.nickname }}</strong>
              <em>{{ peer.pageId }}</em>
            </span>
            <span class="live-share-send">{{ shareBusy === peer.userId ? '…' : '发送' }}</span>
          </button>
        </div>
        <button type="button" class="live-share-link" @click="copyLiveLink">
          <span><Icon name="link" :size="22" /></span>
          复制链接
        </button>
      </div>
    </div>
    <div v-if="noticeOpen" class="live-end-mask" @click.self="noticeOpen = false">
      <div class="live-end-card">
        <div class="live-end-head">
          <strong>本场公告</strong>
          <button type="button" class="live-end-x" aria-label="关闭" @click="noticeOpen = false">
            <Icon name="x" :size="16" />
          </button>
        </div>
        <textarea v-model="noticeDraft" maxlength="200" rows="4" />
        <p class="live-notice-count">{{ noticeDraft.length }}/200</p>
        <button type="button" class="live-end-yes" :disabled="noticeBusy" @click="saveNotice">
          {{ noticeBusy ? '保存中…' : '保存' }}
        </button>
      </div>
    </div>
    <ReportDialog
      :open="reportOpen"
      :target-type="reportKind"
      :target-id="reportTargetId"
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

.live-chip-ava-wrap {
  position: relative;
  display: inline-flex;
  flex-shrink: 0;
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
  background: var(--panel);
  padding: 16px 18px 20px;
  color: var(--ink);
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
  background: var(--hover);
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
  background: var(--fill);
  color: var(--ink);
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
  background: var(--panel);
  color: var(--ink);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
}

.host-hit.is-open .host-card {
  display: flex;
}

.live-peek {
  position: fixed;
  display: flex;
  z-index: 50;
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
  background: var(--fill);
}

.host-card .live-follow {
  margin-right: 0;
  height: 32px;
  padding: 0 14px;
  border-radius: 20px;
}

.host-card .live-follow.is-on {
  background: var(--hover);
  color: var(--muted);
}

.host-bio {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--muted);
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
  color: var(--ink);
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

.live-notice-text {
  display: inline;
  color: inherit;
  font: inherit;
  text-align: left;
  vertical-align: top;
}

.live-notice-text.is-edit:hover {
  color: #fff;
}

.live-end-card textarea {
  display: block;
  width: 100%;
  margin: 0 0 8px;
  padding: 12px;
  border: 0;
  border-radius: 12px;
  background: var(--fill);
  color: var(--ink);
  font: inherit;
  font-size: 14px;
  line-height: 1.5;
  resize: none;
  outline: none;
}

.live-notice-count {
  margin: 0 0 12px;
  color: #8a8a8a;
  font-size: 12px;
  text-align: right;
}

.live-share-card {
  display: flex;
  width: 360px;
  height: 420px;
  flex-direction: column;
  border-radius: 20px;
  background: var(--panel);
}

.live-share-title {
  margin: 0;
  padding: 16px 0;
  text-align: center;
  font-size: 16px;
  font-weight: 500;
  color: var(--ink);
}

.live-share-list {
  min-height: 0;
  flex: 1;
  overflow: auto;
  padding: 0 8px;
}

.live-share-empty {
  padding: 40px 0;
  text-align: center;
  color: #8a8a8a;
  font-size: 13px;
}

.live-share-row {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 16px;
  text-align: left;
  color: var(--ink);
}

.live-share-row:hover {
  background: var(--fill);
}

.live-share-row img,
.live-share-ph {
  height: 44px;
  width: 44px;
  flex: none;
  border-radius: 50%;
  object-fit: cover;
}

.live-share-ph {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--hover);
  color: #8a8a8a;
  font-size: 14px;
}

.live-share-meta {
  min-width: 0;
  flex: 1;
}

.live-share-meta strong,
.live-share-meta em {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-style: normal;
}

.live-share-meta strong {
  font-size: 15px;
  font-weight: 500;
}

.live-share-meta em {
  margin-top: 2px;
  color: #8a8a8a;
  font-size: 12px;
}

.live-share-send {
  color: #ff2442;
  font-size: 12px;
}

.live-share-link {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px 0;
  border-top: 1px solid var(--line);
  color: var(--ink);
  font-size: 13px;
}

.live-share-link span {
  display: flex;
  height: 48px;
  width: 48px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--fill);
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
  color: var(--muted);
}

.live-chat-row {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.55;
  word-break: break-word;
}

.live-chat-row.is-join {
  color: #8a8a8a;
  font-size: 12px;
}

.live-join-name {
  margin-right: 4px;
  color: #8a8a8a;
  font-weight: 600;
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
