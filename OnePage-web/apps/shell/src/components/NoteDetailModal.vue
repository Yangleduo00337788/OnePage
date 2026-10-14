<!-- 帖子详情：左封面铺满轮播，右作者/正文/评论/底栏。从卡片放大打开，关闭回到该帖。 -->
<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError, type PostDetail, type UserProfile } from '@onepage/api-client'
import { useNoteDetail } from '../stores/noteDetail'
import { useAuthModal } from '../stores/authModal'
import { useSession } from '../stores/session'
import Icon from './Icon.vue'
import EmptyState from './EmptyState.vue'
import TextNoteCover from './TextNoteCover.vue'
import { formatCount } from '../data/home'

type Comment = { id: string; name: string; text: string; time: string; avatarUrl?: string }

function isLivePost(id?: string): id is string {
  return Boolean(id)
}

function relativeTime(iso: string) {
  const t = Date.parse(iso.includes('T') ? iso : iso.replace(' ', 'T'))
  if (!Number.isFinite(t)) return iso.replace('T', ' ').slice(0, 16)
  const d = Date.now() - t
  if (d < 60_000) return '刚刚'
  if (d < 3_600_000) return `${Math.floor(d / 60_000)} 分钟前`
  if (d < 86_400_000) return `${Math.floor(d / 3_600_000)} 小时前`
  return iso.replace('T', ' ').slice(0, 16)
}

const router = useRouter()
const auth = useAuthModal()
const session = useSession()
const detail = useNoteDetail()
const full = ref<PostDetail | null>(null)
const authorProfile = ref<UserProfile | null>(null)
const followingAuthor = ref(false)
const followBusy = ref(false)
const likeCount = ref(0)
const favCount = ref(0)
const commentTotal = ref(0)
let loadSeq = 0
const idx = ref(0)
const liked = ref(false)
const saved = ref(false)
const moreOpen = ref(false)
const shareOpen = ref(false)
type SharePeer = { userId: string; pageId: string; nickname: string; avatarUrl: string; conversationId?: string }
const sharePeers = ref<SharePeer[]>([])
const shareLoading = ref(false)
const shareBusy = ref('')
const draft = ref('')
const toast = ref('')
const comments = ref<Comment[]>([])
const moreWrap = ref<HTMLElement | null>(null)
const commentBox = ref<HTMLInputElement | null>(null)
const cardEl = ref<HTMLElement | null>(null)
const player = ref<HTMLVideoElement | null>(null)
const stageEl = ref<HTMLElement | null>(null)
const grown = ref(false)
const closing = ref(false)
const playing = ref(false)
const muted = ref(false)
const volume = ref(0.8)
const rate = ref(1)
const isFs = ref(false)
const isPip = ref(false)
const keepPip = ref(false)
const quality = ref<(typeof QUALITIES)[number]['id']>('auto')
const pop = ref<'q' | 's' | 'v' | ''>('')
const authorOpen = ref(false)
const current = ref(0)
const durationSec = ref(0)
const ANIM_MS = 520
let closeTimer = 0
let popTimer = 0
let popLock = false
let authorTimer = 0

const SPEEDS = [
  { rate: 1, label: '默认' },
  { rate: 1.25, label: '1.25倍' },
  { rate: 2, label: '2倍' },
  { rate: 3, label: '3倍' },
] as const

const QUALITIES = [
  { id: 'auto', label: '自动' },
  { id: '720', label: '720P' },
  { id: '1080', label: '1080P' },
] as const

const gallery = computed(() => {
  const note = detail.note
  if (!note) return [] as string[]
  if (full.value) {
    const imgs = full.value.media.filter((m) => !m.mimeType.startsWith('video/')).map((m) => m.url)
    if (imgs.length) return imgs
    if (full.value.coverUrl) return [full.value.coverUrl]
    if (note.image) return [note.image]
    return []
  }
  return note.image ? [note.image] : []
})

const total = computed(() => gallery.value.length)
const pageText = computed(() => `${idx.value + 1}/${total.value || 1}`)
const isVideo = computed(() => {
  if (full.value) {
    return (
      full.value.postType === 'video' ||
      full.value.media.some((m) => m.mimeType.startsWith('video/'))
    )
  }
  return Boolean(detail.note?.video)
})
const mediaRatio = ref(0)

function onImgLoad(e: Event) {
  const el = e.target as HTMLImageElement
  if (el.naturalWidth > 0 && el.naturalHeight > 0) {
    mediaRatio.value = el.naturalWidth / el.naturalHeight
  }
}

const dialogWidth = computed(() => {
  if (isVideo.value || !mediaRatio.value) return ''
  const maxH = Math.min(780, window.innerHeight - 48)
  const maxCard = Math.min(1080, window.innerWidth - 48)
  const right = 400
  const maxStage = Math.max(240, maxCard - right)
  const stageW = Math.min(maxStage, maxH * mediaRatio.value)
  return `${Math.round(stageW + right)}px`
})
const videoSrc = computed(() => {
  if (full.value) {
    return full.value.media.find((m) => m.mimeType.startsWith('video/'))?.url || ''
  }
  return detail.note?.video ? detail.note.image || '' : ''
})
const timeText = computed(() => `${fmtTime(current.value)} / ${fmtTime(durationSec.value)}`)
const qualityLabel = computed(() => QUALITIES.find((item) => item.id === quality.value)?.label || '自动')
const speedLabel = computed(() => (rate.value === 1 ? '倍速' : `${rate.value}倍`))
const shellOpen = computed(() => Boolean(detail.open && detail.note) || keepPip.value)
const progress = computed(() => (durationSec.value > 0 ? current.value / durationSec.value : 0))
const avatar = computed(
  () => full.value?.author.avatarUrl || detail.note?.avatarUrl || authorProfile.value?.avatarUrl || '',
)
const topics = computed(() => {
  const text = `${detail.note?.title || ''} ${body.value}`
  const tags = [...text.matchAll(/#[^\s#]+/g)].map((m) => m[0])
  return [...new Set(tags)].slice(0, 8)
})
const settingChips = computed(() => {
  const p = full.value
  if (!p) return [] as string[]
  const chips: string[] = []
  if (p.original) chips.push('原创')
  if (p.contentKind) chips.push(p.contentKind)
  if (p.eventTitle) chips.push(p.eventTitle)
  if (p.visibility === 'self') chips.push('仅自己可见')
  return chips
})
const body = computed(() => full.value?.body || detail.note?.body || '')
const published = computed(() => {
  if (full.value?.publishedAt) return relativeTime(full.value.publishedAt)
  return ''
})
const likeLabel = computed(() =>
  full.value || isLivePost(detail.note?.id) ? formatCount(likeCount.value) : detail.note?.likes || '0',
)
const favLabel = computed(() =>
  full.value || isLivePost(detail.note?.id) ? formatCount(favCount.value) : saved.value ? '1' : '0',
)
const commentLabel = computed(() =>
  full.value || isLivePost(detail.note?.id) ? formatCount(commentTotal.value) : String(comments.value.length),
)
const mine = computed(() => {
  const me = session.me
  if (!me) return false
  if (full.value?.author.userId && full.value.author.userId === me.userId) return true
  return Boolean(detail.note?.pageId && detail.note.pageId === me.pageId)
})
const authorBio = computed(() => {
  const bio = authorProfile.value?.bio?.trim()
  return bio || '暂无'
})
const authorGender = computed(() => authorProfile.value?.gender ?? 0)
const authorStats = computed(() => {
  const s = authorProfile.value?.stats
  return {
    following: formatCount(s?.followingCount ?? 0),
    fans: formatCount(s?.followerCount ?? 0),
    likes: formatCount((s?.likeReceivedCount ?? 0) + (s?.favoriteReceivedCount ?? 0)),
  }
})
watch(
  () => detail.note?.id,
  (id) => {
    idx.value = 0
    mediaRatio.value = 0
    liked.value = false
    saved.value = false
    moreOpen.value = false
    shareOpen.value = false
    draft.value = ''
    comments.value = []
    commentTotal.value = 0
    likeCount.value = 0
    favCount.value = 0
    full.value = null
    authorProfile.value = null
    followingAuthor.value = false
    current.value = 0
    durationSec.value = 0
    playing.value = false
    muted.value = false
    rate.value = 1
    quality.value = 'auto'
    pop.value = ''
    authorOpen.value = false
    isFs.value = false
    if (keepPip.value && document.pictureInPictureElement) void document.exitPictureInPicture()
    keepPip.value = false
    isPip.value = false
    if (id) void loadLive(id)
  },
)

function fmtTime(s: number) {
  if (!Number.isFinite(s) || s < 0) return '00:00'
  const m = Math.floor(s / 60)
  const sec = Math.floor(s % 60)
  return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
}

function applyMedia() {
  const el = player.value
  if (!el) return
  el.volume = volume.value
  el.muted = muted.value || volume.value === 0
  el.playbackRate = rate.value
}

function stopVideo() {
  const el = player.value
  if (el && document.pictureInPictureElement === el) return
  if (el) {
    el.pause()
    el.currentTime = 0
  }
  playing.value = false
  if (document.fullscreenElement) void document.exitFullscreen()
}

function tryPlay() {
  const el = player.value
  if (!el || !isVideo.value) return
  applyMedia()
  el.play()
    .then(() => {
      playing.value = true
    })
    .catch(() => {
      muted.value = true
      applyMedia()
      el.play()
        .then(() => {
          playing.value = true
        })
        .catch(() => {
          playing.value = false
        })
    })
}

function togglePlay() {
  const el = player.value
  if (!el) return
  if (el.paused) tryPlay()
  else {
    el.pause()
    playing.value = false
  }
}

function setVolume(v: number) {
  const next = Math.min(1, Math.max(0, v))
  volume.value = next
  muted.value = next === 0
  applyMedia()
}

function toggleMute() {
  if (muted.value || volume.value === 0) {
    muted.value = false
    if (volume.value === 0) volume.value = 0.8
  } else muted.value = true
  applyMedia()
}

function onVolWheel(e: WheelEvent) {
  e.preventDefault()
  e.stopPropagation()
  setVolume(volume.value - Math.sign(e.deltaY) * 0.08)
}

function onVolBar(e: MouseEvent) {
  const bar = e.currentTarget as HTMLElement
  const r = bar.getBoundingClientRect()
  setVolume((r.bottom - e.clientY) / r.height)
}

function dismissPop() {
  window.clearTimeout(popTimer)
  pop.value = ''
  popLock = true
  const el = document.activeElement
  if (el instanceof HTMLElement) el.blur()
}

function setRate(next: number) {
  rate.value = next
  applyMedia()
  dismissPop()
}

function setQuality(id: (typeof QUALITIES)[number]['id']) {
  quality.value = id
  dismissPop()
}

function openPop(name: 'q' | 's' | 'v') {
  if (popLock) return
  window.clearTimeout(popTimer)
  pop.value = name
}

function closePop() {
  window.clearTimeout(popTimer)
  popLock = false
  pop.value = ''
}

function openAuthor() {
  window.clearTimeout(authorTimer)
  authorOpen.value = true
}

function closeAuthor() {
  window.clearTimeout(authorTimer)
  authorTimer = window.setTimeout(() => {
    authorOpen.value = false
  }, 160)
}

function goAuthor() {
  const pid = full.value?.author.pageId || detail.note?.pageId || authorProfile.value?.pageId
  if (!pid) return
  window.clearTimeout(authorTimer)
  authorOpen.value = false
  if (player.value && document.pictureInPictureElement === player.value) {
    keepPip.value = true
    isPip.value = true
  } else {
    stopVideo()
  }
  closing.value = false
  grown.value = false
  detail.hide()
  void router.push(`/u/${pid}`)
}

async function togglePip() {
  const el = player.value
  if (!el) return
  try {
    if (document.pictureInPictureElement) await document.exitPictureInPicture()
    else await el.requestPictureInPicture()
  } catch {
    flash('当前浏览器不支持画中画')
  }
}

async function toggleFs() {
  const box = stageEl.value
  if (!box) return
  try {
    if (document.fullscreenElement) await document.exitFullscreen()
    else await box.requestFullscreen()
  } catch {
    flash('无法全屏')
  }
}

function onFsChange() {
  isFs.value = document.fullscreenElement === stageEl.value
}

function onLeavePip() {
  isPip.value = false
  keepPip.value = false
  if (!detail.open) {
    const el = player.value
    if (el) {
      el.pause()
      playing.value = false
    }
  }
}

function onMeta() {
  durationSec.value = player.value?.duration || 0
}

function onTime() {
  current.value = player.value?.currentTime || 0
}

function seek(e: MouseEvent) {
  const el = player.value
  const bar = e.currentTarget as HTMLElement
  if (!el || !durationSec.value) return
  const r = bar.getBoundingClientRect()
  const t = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width))
  el.currentTime = t * durationSec.value
  current.value = el.currentTime
}

function prev() {
  if (!total.value) return
  idx.value = (idx.value - 1 + total.value) % total.value
}

function next() {
  if (!total.value) return
  idx.value = (idx.value + 1) % total.value
}

function originTransform(target: DOMRect) {
  const o = detail.origin
  if (!o) return 'none'
  const sx = o.w / target.width
  const sy = o.h / target.height
  const ox = o.x + o.w / 2 - (target.left + target.width / 2)
  const oy = o.y + o.h / 2 - (target.top + target.height / 2)
  return `translate(${ox}px, ${oy}px) scale(${sx}, ${sy})`
}

async function playIn() {
  grown.value = false
  closing.value = false
  await nextTick()
  const el = cardEl.value
  if (!el || !detail.origin) {
    grown.value = true
    return
  }
  el.style.transition = 'none'
  el.style.transform = originTransform(el.getBoundingClientRect())
  el.style.opacity = '1'
  void el.offsetWidth
  requestAnimationFrame(() => {
    el.style.transition = `transform ${ANIM_MS}ms cubic-bezier(0.4, 0, 0.2, 1), opacity ${ANIM_MS}ms cubic-bezier(0.4, 0, 0.2, 1)`
    el.style.transform = 'none'
    grown.value = true
  })
}

function scrollBack() {
  const id = detail.note?.id
  if (!id) return
  const node = document.querySelector(`[data-note-id="${id}"]`)
  node?.scrollIntoView({ block: 'center', behavior: 'smooth' })
}

function requestClose() {
  if (shareOpen.value) {
    shareOpen.value = false
    return
  }
  if (moreOpen.value) {
    moreOpen.value = false
    return
  }
  if (closing.value) return
  const stayPip = Boolean(player.value && document.pictureInPictureElement === player.value)
  if (stayPip) {
    keepPip.value = true
    isPip.value = true
    if (document.fullscreenElement) void document.exitFullscreen()
    scrollBack()
    detail.hide()
    closing.value = false
    grown.value = false
    return
  }
  stopVideo()
  closing.value = true
  const el = cardEl.value
  if (el && detail.origin) {
    el.style.transition = `transform ${ANIM_MS}ms cubic-bezier(0.4, 0, 0.2, 1), opacity ${ANIM_MS}ms cubic-bezier(0.4, 0, 0.2, 1)`
    el.style.transform = originTransform(el.getBoundingClientRect())
  }
  window.clearTimeout(closeTimer)
  closeTimer = window.setTimeout(() => {
    scrollBack()
    detail.hide()
    closing.value = false
    grown.value = false
  }, ANIM_MS)
}

function onKey(e: KeyboardEvent) {
  if (!detail.open) return
  if (e.key === 'Escape') {
    if (document.fullscreenElement) return
    requestClose()
  }
  if (shareOpen.value || closing.value) return
  if (isVideo.value) {
    if (e.key === ' ' && !(e.target instanceof HTMLInputElement)) {
      e.preventDefault()
      togglePlay()
    }
    return
  }
  if (e.key === 'ArrowLeft') prev()
  if (e.key === 'ArrowRight') next()
}

watch(
  () => detail.open,
  (on) => {
    if (on) {
      document.addEventListener('keydown', onKey)
      document.addEventListener('fullscreenchange', onFsChange)
      playIn()
    } else {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('fullscreenchange', onFsChange)
      if (!keepPip.value) stopVideo()
    }
  },
)

watch([grown, isVideo, videoSrc], () => {
  if (grown.value && isVideo.value && videoSrc.value) nextTick().then(() => tryPlay())
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKey)
  document.removeEventListener('fullscreenchange', onFsChange)
  window.clearTimeout(closeTimer)
  window.clearTimeout(popTimer)
  window.clearTimeout(authorTimer)
})

function flash(text: string) {
  toast.value = text
  window.setTimeout(() => {
    if (toast.value === text) toast.value = ''
  }, 1600)
}

function needLogin(e: unknown) {
  if (e instanceof ApiError && e.status === 401) {
    auth.show('login', '/')
    flash('请先登录')
    return true
  }
  return false
}

async function loadLive(id: string) {
  const token = ++loadSeq
  try {
    const post = await api.post(id)
    if (token !== loadSeq) return
    full.value = post
    if (post.author.pageId) {
      try {
        authorProfile.value = await api.userByPageId(post.author.pageId)
        followingAuthor.value = Boolean(authorProfile.value?.followedByMe)
      } catch {
        authorProfile.value = null
        followingAuthor.value = false
      }
    }
    if (token !== loadSeq) return
    liked.value = post.likedByMe
    saved.value = post.favoritedByMe
    likeCount.value = post.likeCount
    favCount.value = post.favoriteCount
    commentTotal.value = post.commentCount
    void api.viewPost(id).catch(() => undefined)
    const page = await api.comments(id, undefined, 0, 50)
    if (token !== loadSeq) return
    comments.value = page.items.map((c) => ({
      id: c.commentId,
      name: c.author.nickname,
      avatarUrl: c.author.avatarUrl,
      text: c.content,
      time: relativeTime(c.createdAt),
    }))
    commentTotal.value = page.total
  } catch {
    if (token !== loadSeq) return
    comments.value = []
  }
}

async function toggleFollow() {
  const pid = full.value?.author.pageId || authorProfile.value?.pageId
  if (!pid || mine.value) return
  followBusy.value = true
  try {
    if (followingAuthor.value) {
      await api.unfollowUser(pid)
      followingAuthor.value = false
      if (authorProfile.value?.stats) {
        authorProfile.value.stats.followerCount = Math.max(0, (authorProfile.value.stats.followerCount ?? 0) - 1)
      }
      session.bumpFollowing(-1)
    } else {
      await api.followUser(pid)
      followingAuthor.value = true
      if (authorProfile.value?.stats) {
        authorProfile.value.stats.followerCount = (authorProfile.value.stats.followerCount ?? 0) + 1
      }
      session.bumpFollowing(1)
    }
  } catch (e) {
    if (!needLogin(e)) flash(e instanceof Error ? e.message : '操作失败')
  } finally {
    followBusy.value = false
  }
}

async function toggleLike() {
  const id = detail.note?.id
  if (!isLivePost(id)) return
  try {
    const r = liked.value ? await api.unlikePost(id) : await api.likePost(id)
    liked.value = r.liked
    likeCount.value = r.likeCount
  } catch (e) {
    if (!needLogin(e)) flash(e instanceof Error ? e.message : '操作失败')
  }
}

async function toggleSave() {
  const id = detail.note?.id
  if (!isLivePost(id)) return
  try {
    const r = saved.value ? await api.unfavoritePost(id) : await api.favoritePost(id)
    saved.value = r.favorited
    favCount.value = r.favoriteCount
  } catch (e) {
    if (!needLogin(e)) flash(e instanceof Error ? e.message : '操作失败')
  }
}

async function sendComment() {
  const text = draft.value.trim()
  if (!text) return
  const id = detail.note?.id
  if (!isLivePost(id)) return
  try {
    const c = await api.createComment(id, { content: text })
    comments.value = [
      {
        id: c.commentId,
        name: c.author.nickname,
        avatarUrl: c.author.avatarUrl,
        text: c.content,
        time: '刚刚',
      },
      ...comments.value,
    ]
    commentTotal.value += 1
    draft.value = ''
  } catch (e) {
    if (!needLogin(e)) flash(e instanceof Error ? e.message : '评论失败')
  }
}

function report() {
  moreOpen.value = false
  flash('举报尚未接入')
}

async function copyLink() {
  const id = detail.note?.id || ''
  let url = `${location.origin}/?note=${id}`
  if (isLivePost(id)) {
    try {
      const r = await api.shareLink(id)
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
    flash('链接已复制')
  } catch {
    flash('复制失败')
  }
  shareOpen.value = false
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
  const id = detail.note?.id || ''
  if (!id || shareBusy.value) return
  shareBusy.value = peer.userId
  try {
    await api.shareIm(
      id,
      peer.conversationId ? { conversationId: peer.conversationId } : { targetUserId: peer.userId },
    )
    flash('已发送')
    shareOpen.value = false
  } catch (e) {
    if (needLogin(e)) {
      shareOpen.value = false
      return
    }
    flash(e instanceof ApiError ? e.message : '发送失败')
  } finally {
    shareBusy.value = ''
  }
}

function onDocClick(e: MouseEvent) {
  if (!moreWrap.value?.contains(e.target as Node)) moreOpen.value = false
}

watch(shareOpen, (on) => {
  if (on) void loadSharePeers()
})

watch(moreOpen, async (on) => {
  if (on) {
    await nextTick()
    document.addEventListener('click', onDocClick)
  } else document.removeEventListener('click', onDocClick)
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="shellOpen && detail.note"
      class="fixed inset-0 z-50 flex items-center justify-center px-4"
      :class="[
        keepPip && !detail.open ? 'pip-hold' : '',
        grown && !closing && detail.open ? 'bg-black/45' : 'bg-black/0',
      ]"
      :style="{ transition: `background-color ${ANIM_MS}ms cubic-bezier(0.4, 0, 0.2, 1)` }"
      @click.self="requestClose"
    >
      <div
        ref="cardEl"
        class="relative flex h-[min(780px,calc(100vh-48px))] overflow-hidden rounded-[20px] bg-white shadow-[0_20px_60px_rgba(0,0,0,0.18)]"
        :style="{
          transformOrigin: 'center center',
          width: dialogWidth || 'min(1080px, calc(100vw - 48px))',
        }"
      >
        <div ref="stageEl" class="stage group relative min-w-0 flex-1 overflow-visible bg-black">
          <div class="absolute inset-0 overflow-hidden rounded-l-[20px]">
          <video
            v-if="isVideo"
            :key="`${detail.note.id}-${videoSrc}`"
            ref="player"
            class="h-full w-full cursor-pointer object-contain"
            :src="videoSrc"
            :poster="detail.note.image"
            playsinline
            loop
            preload="auto"
            @click="togglePlay"
            @loadedmetadata="onMeta"
            @timeupdate="onTime"
            @play="playing = true"
            @pause="playing = false"
            @enterpictureinpicture="isPip = true; keepPip = true"
            @leavepictureinpicture="onLeavePip"
          />
          <img
            v-else-if="gallery[idx]"
            :key="gallery[idx]"
            :src="gallery[idx]"
            :alt="detail.note.title"
            class="h-full w-full object-contain"
            @load="onImgLoad"
          />
          <TextNoteCover
            v-else
            size="feed"
            :title="detail.note?.title || ''"
            :body="body"
          />
          </div>

          <div v-if="isVideo" class="pointer-events-none absolute inset-0">
            <button
              v-show="!playing"
              type="button"
              class="pointer-events-auto absolute top-1/2 left-1/2 flex h-24 w-24 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full bg-black/45 text-white"
              aria-label="播放"
              @click="togglePlay"
            >
              <svg viewBox="0 0 24 24" width="56" height="56" fill="currentColor" aria-hidden="true">
                <path d="M8 5.5v13l11-6.5z" />
              </svg>
            </button>
            <div class="vbar">
              <button type="button" class="vprog" aria-label="进度" @click.stop="seek">
                <span :style="{ width: `${progress * 100}%` }" />
              </button>
              <div class="vrow">
                <button type="button" class="vbtn" :aria-label="playing ? '暂停' : '播放'" @click.stop="togglePlay">
                  <svg v-if="playing" class="vico" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                    <rect x="6" y="5" width="4.5" height="14" rx="1" />
                    <rect x="13.5" y="5" width="4.5" height="14" rx="1" />
                  </svg>
                  <svg v-else class="vico" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                    <path d="M8 5.5v13l11-6.5z" />
                  </svg>
                </button>
                <span class="vtime">{{ timeText }}</span>
                <span class="flex-1" />
                <div class="vtip" :class="pop === 'q' ? 'is-open' : ''" @mouseenter="openPop('q')" @mouseleave="closePop">
                  <button type="button" class="vql" aria-label="分辨率" @click.stop="openPop('q')">{{ qualityLabel }}</button>
                  <div class="vmenu">
                    <div class="vmenu-card">
                      <button
                        v-for="item in QUALITIES"
                        :key="item.id"
                        type="button"
                        :class="quality === item.id ? 'is-on' : ''"
                        @click.stop="setQuality(item.id)"
                      >
                        {{ item.label }}
                      </button>
                    </div>
                  </div>
                </div>
                <div class="vtip" :class="pop === 's' ? 'is-open' : ''" @mouseenter="openPop('s')" @mouseleave="closePop">
                  <button type="button" class="vql" aria-label="倍速" @click.stop="openPop('s')">{{ speedLabel }}</button>
                  <div class="vmenu">
                    <div class="vmenu-card">
                      <button
                        v-for="item in SPEEDS"
                        :key="item.rate"
                        type="button"
                        :class="rate === item.rate ? 'is-on' : ''"
                        @click.stop="setRate(item.rate)"
                      >
                        {{ item.label }}
                      </button>
                    </div>
                  </div>
                </div>
                <div class="vtip vol" :class="pop === 'v' ? 'is-open' : ''" @mouseenter="openPop('v')" @mouseleave="closePop" @wheel.prevent.stop="onVolWheel">
                  <button type="button" class="vbtn" :aria-label="muted || volume === 0 ? '取消静音' : '音量'" @click.stop="toggleMute">
                    <svg v-if="muted || volume === 0" class="vico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                      <path d="M11 5 6 9H3v6h3l5 4z" />
                      <path d="m16 10 5 5M21 10l-5 5" />
                    </svg>
                    <svg v-else class="vico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M11 5 6 9H3v6h3l5 4z" />
                      <path d="M15.5 8.5a5 5 0 0 1 0 7" />
                      <path d="M18 6a8 8 0 0 1 0 12" />
                    </svg>
                  </button>
                  <div class="vvol">
                    <button type="button" class="vvol-track" aria-label="音量" @click.stop="onVolBar">
                      <span :style="{ height: `${(muted ? 0 : volume) * 100}%` }" />
                    </button>
                  </div>
                </div>
                <button type="button" class="vbtn" aria-label="画中画" @click.stop="togglePip">
                  <Icon name="picture-in-picture-2" :size="24" :stroke="2" />
                </button>
                <button type="button" class="vbtn" :aria-label="isFs ? '退出全屏' : '全屏'" @click.stop="toggleFs">
                  <Icon :name="isFs ? 'minimize' : 'maximize'" :size="24" :stroke="2" />
                </button>
              </div>
            </div>
          </div>

          <div
            v-else
            class="pointer-events-none absolute inset-0 opacity-0 transition-opacity duration-200 group-hover:pointer-events-auto group-hover:opacity-100"
          >
            <span class="absolute top-4 right-4 rounded-full bg-black/45 px-2.5 py-1 text-[12px] text-white">{{ pageText }}</span>
            <button
              v-if="total > 1"
              type="button"
              class="absolute top-1/2 left-4 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full bg-black/40 text-white hover:bg-black/55"
              aria-label="上一张"
              @click="prev"
            >
              <Icon name="chevron-left" :size="20" />
            </button>
            <button
              v-if="total > 1"
              type="button"
              class="absolute top-1/2 right-4 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full bg-black/40 text-white hover:bg-black/55"
              aria-label="下一张"
              @click="next"
            >
              <Icon name="chevron-right" :size="20" />
            </button>
            <div class="absolute bottom-4 left-1/2 flex -translate-x-1/2 gap-1.5">
              <button
                v-for="(_, i) in gallery"
                :key="i"
                type="button"
                class="h-1.5 rounded-full transition-all"
                :class="i === idx ? 'w-4 bg-white' : 'w-1.5 bg-white/45'"
                :aria-label="`第 ${i + 1} 张`"
                @click="idx = i"
              />
            </div>
          </div>
        </div>

        <div class="relative flex w-[400px] shrink-0 flex-col border-l border-[#f2f2f2]">
          <div class="relative flex items-center gap-2 px-4 py-3">
            <div
              class="author-hit"
              :class="authorOpen ? 'is-open' : ''"
              @mouseenter="openAuthor"
              @mouseleave="closeAuthor"
            >
              <img v-if="avatar" :src="avatar" alt="" class="h-9 w-9 shrink-0 rounded-full object-cover" />
              <span v-else class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#f2f2f2] text-[13px] text-[#8a8a8a]">
                {{ (detail.note.author || '一').slice(0, 1) }}
              </span>
              <span class="flex min-w-0 items-center gap-1 truncate text-[15px] font-medium">
                <span class="truncate">{{ detail.note.author }}</span>
                <Icon
                  v-if="authorGender === 1"
                  name="mars"
                  :size="14"
                  :stroke="2"
                  class="shrink-0 text-[#4a90d9]"
                />
                <Icon
                  v-else-if="authorGender === 2"
                  name="venus"
                  :size="14"
                  :stroke="2"
                  class="shrink-0 text-[#ff2442]"
                />
              </span>
              <div class="author-card" @mouseenter="openAuthor" @mouseleave="closeAuthor">
                <div class="flex items-center gap-2.5">
                  <button type="button" class="author-go shrink-0" @click.stop="goAuthor">
                    <img v-if="avatar" :src="avatar" alt="" class="h-11 w-11 rounded-full object-cover" />
                    <span
                      v-else
                      class="flex h-11 w-11 items-center justify-center rounded-full bg-[#f2f2f2] text-[14px] text-[#8a8a8a]"
                    >
                      {{ (detail.note.author || '一').slice(0, 1) }}
                    </span>
                  </button>
                  <button
                    type="button"
                    class="author-go flex min-w-0 flex-1 items-center gap-1 text-left text-[16px] font-semibold"
                    @click.stop="goAuthor"
                  >
                    <span class="truncate">{{ detail.note.author }}</span>
                    <Icon
                      v-if="authorGender === 1"
                      name="mars"
                      :size="14"
                      :stroke="2"
                      class="shrink-0 text-[#4a90d9]"
                    />
                    <Icon
                      v-else-if="authorGender === 2"
                      name="venus"
                      :size="14"
                      :stroke="2"
                      class="shrink-0 text-[#ff2442]"
                    />
                  </button>
                  <button
                    v-if="!mine"
                    type="button"
                    class="follow-btn"
                    :class="{ 'is-on': followingAuthor }"
                    :disabled="followBusy"
                    @click.stop="toggleFollow"
                  >
                    {{ followBusy ? '…' : followingAuthor ? '已关注' : '关注' }}
                  </button>
                </div>
                <p class="mt-2.5 line-clamp-2 text-[13px] leading-5 text-[#555]">{{ authorBio }}</p>
                <div class="mt-2.5 grid grid-cols-3 gap-2 text-center">
                  <button type="button" class="author-go" @click.stop="goAuthor">
                    <p class="text-[16px] font-semibold">{{ authorStats.following }}</p>
                    <p class="mt-0.5 text-[11px] text-[#8a8a8a]">关注</p>
                  </button>
                  <button type="button" class="author-go" @click.stop="goAuthor">
                    <p class="text-[16px] font-semibold">{{ authorStats.fans }}</p>
                    <p class="mt-0.5 text-[11px] text-[#8a8a8a]">粉丝</p>
                  </button>
                  <button type="button" class="author-go" @click.stop="goAuthor">
                    <p class="text-[16px] font-semibold">{{ authorStats.likes }}</p>
                    <p class="mt-0.5 text-[11px] text-[#8a8a8a]">获赞和收藏</p>
                  </button>
                </div>
              </div>
            </div>
            <button
              v-if="!mine"
              type="button"
              class="follow-btn"
              :class="{ 'is-on': followingAuthor }"
              :disabled="followBusy"
              @click="toggleFollow"
            >
              {{ followBusy ? '…' : followingAuthor ? '已关注' : '关注' }}
            </button>
            <button type="button" class="close-x" aria-label="关闭" @click="requestClose">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <path d="M18 6 6 18M6 6l12 12" />
              </svg>
            </button>
          </div>

          <div class="min-h-0 flex-1 overflow-y-auto px-5 pb-4">
            <h2 class="text-[16px] font-semibold leading-6">{{ detail.note.title }}</h2>
            <p v-if="body" class="mt-2 text-[14px] leading-6 text-[#555]">{{ body }}</p>
            <div v-if="topics.length" class="mt-3 flex flex-wrap gap-2">
              <span v-for="tag in topics" :key="tag" class="text-[13px] text-[#ff2442]">{{ tag }}</span>
            </div>
            <div v-if="settingChips.length" class="mt-3 flex flex-wrap gap-2">
              <span
                v-for="chip in settingChips"
                :key="chip"
                class="rounded-full bg-[#f7f7f7] px-2.5 py-0.5 text-[12px] text-[#555]"
              >
                {{ chip }}
              </span>
            </div>
            <div class="relative mt-4 flex items-center justify-between text-[12px] text-[#8a8a8a]">
              <span>{{ published }}</span>
              <div ref="moreWrap" class="relative">
                <button type="button" class="flex h-7 w-7 items-center justify-center rounded-full hover:bg-[#f2f2f2]" aria-label="更多" @click.stop="moreOpen = !moreOpen">
                  <Icon name="ellipsis" :size="18" />
                </button>
                <div v-if="moreOpen" class="absolute right-0 top-full z-10 mt-1 min-w-[108px] rounded-[12px] bg-white py-1 shadow-[0_8px_28px_rgba(0,0,0,0.12)] ring-1 ring-[#eee]">
                  <button type="button" class="block w-full px-3 py-2 text-left text-[13px] hover:bg-[#f7f7f7]" @click="report">举报</button>
                </div>
              </div>
            </div>

            <div class="my-4 h-px bg-[#f2f2f2]" />

            <p class="mb-3 text-[13px] font-medium">评论 {{ commentLabel }}</p>
            <ul v-if="comments.length" class="space-y-3">
              <li v-for="item in comments" :key="item.id" class="flex gap-2.5">
                <img
                  v-if="item.avatarUrl"
                  :src="item.avatarUrl"
                  alt=""
                  class="mt-0.5 h-7 w-7 shrink-0 rounded-full object-cover"
                />
                <span
                  v-else
                  class="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-[#f2f2f2] text-[11px] text-[#8a8a8a]"
                >
                  {{ item.name.slice(0, 1) }}
                </span>
                <div class="min-w-0">
                  <p class="text-[13px]"><span class="mr-1.5 text-[#8a8a8a]">{{ item.name }}</span>{{ item.text }}</p>
                  <p class="mt-0.5 text-[11px] text-[#bbb]">{{ item.time }}</p>
                </div>
              </li>
            </ul>
            <EmptyState v-else compact src="/empty-chat.png" title="还没有评论" />
          </div>

          <div class="border-t border-[#f2f2f2] px-3 py-3">
            <div class="flex items-center gap-2">
              <input
                ref="commentBox"
                v-model="draft"
                class="h-9 min-w-0 flex-1 rounded-[20px] bg-[#f7f7f7] px-3.5 text-[13px] outline-none"
                placeholder="说点什么…"
                @keydown.enter.prevent="sendComment"
              />
              <div class="acts">
                <button type="button" class="act" :class="liked ? 'is-on' : ''" @click="toggleLike">
                  <svg class="act-ico" viewBox="0 0 24 24">
                    <path d="M12 20.5S4.5 15.6 3.2 11.6C2.2 8.6 3.8 5.5 6.8 5c1.9-.3 3.6.6 4.4 2.1C12.1 5.6 13.8 4.7 15.7 5c3 .5 4.6 3.6 3.6 6.6-1.3 4-8.3 8.9-7.3 8.9z" />
                  </svg>
                  <span class="num">{{ likeLabel }}</span>
                </button>
                <button type="button" class="act" :class="saved ? 'is-on' : ''" @click="toggleSave">
                  <svg class="act-ico" viewBox="0 0 24 24">
                    <path d="M7 3.8h10A1.7 1.7 0 0 1 18.7 5.5v15.2L12 16.4 5.3 20.7V5.5A1.7 1.7 0 0 1 7 3.8z" />
                  </svg>
                  <span class="num">{{ favLabel }}</span>
                </button>
                <button type="button" class="act" @click="commentBox?.focus()">
                  <svg class="act-ico" viewBox="0 0 24 24">
                    <path d="M6.2 4.5h11.6A2.2 2.2 0 0 1 20 6.7v8.4a2.2 2.2 0 0 1-2.2 2.2H9.4L4 21.2V6.7A2.2 2.2 0 0 1 6.2 4.5z" />
                  </svg>
                  <span class="num">{{ commentLabel }}</span>
                </button>
                <button type="button" class="act" @click="shareOpen = true">
                  <svg class="act-ico" viewBox="0 0 24 24">
                    <path d="M12 3.8v11.2" />
                    <path d="M8.2 7.4 12 3.8l3.8 3.6" />
                    <path d="M5.5 13.2v5.3A1.8 1.8 0 0 0 7.3 20.3h9.4a1.8 1.8 0 0 0 1.8-1.8v-5.3" />
                  </svg>
                  <span class="num">分享</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="shareOpen" class="absolute inset-0 z-30 flex items-center justify-center bg-black/25" @click.self="shareOpen = false">
        <div class="flex h-[420px] w-[360px] flex-col rounded-[20px] bg-white shadow-[0_16px_48px_rgba(0,0,0,0.16)]">
          <p class="py-4 text-center text-[16px] font-medium">分享至</p>
          <div class="min-h-0 flex-1 overflow-y-auto px-2">
            <p v-if="shareLoading" class="py-10 text-center text-[13px] text-[#8a8a8a]">加载中…</p>
            <EmptyState v-else-if="!sharePeers.length" compact src="/empty-people.png" title="暂无好友" />
            <button
              v-for="peer in sharePeers"
              :key="peer.userId"
              type="button"
              class="flex w-full items-center gap-3 rounded-[16px] px-3 py-2.5 text-left hover:bg-[#f7f7f7]"
              :disabled="Boolean(shareBusy)"
              @click="shareTo(peer)"
            >
              <img v-if="peer.avatarUrl" :src="peer.avatarUrl" alt="" class="h-11 w-11 rounded-full object-cover" />
              <span v-else class="flex h-11 w-11 items-center justify-center rounded-full bg-[#f2f2f2] text-[14px] text-[#8a8a8a]">
                {{ (peer.nickname || '一').slice(0, 1) }}
              </span>
              <span class="min-w-0 flex-1">
                <span class="block truncate text-[15px] font-medium">{{ peer.nickname }}</span>
                <span class="mt-0.5 block truncate text-[12px] text-[#8a8a8a]">{{ peer.pageId }}</span>
              </span>
              <span class="text-[12px] text-[#ff2442]">{{ shareBusy === peer.userId ? '…' : '发送' }}</span>
            </button>
          </div>
          <div class="flex justify-center gap-16 border-t border-[#f2f2f2] py-6">
            <button type="button" class="flex flex-col items-center gap-2 text-[13px] text-[#1f1f1f]" @click="copyLink">
              <span class="flex h-12 w-12 items-center justify-center rounded-full bg-[#f5f5f5]">
                <Icon name="link" :size="22" />
              </span>
              复制链接
            </button>
          </div>
        </div>
      </div>

      <p v-if="toast" class="pointer-events-none absolute bottom-8 left-1/2 z-40 -translate-x-1/2 rounded-full bg-black/70 px-4 py-2 text-[13px] text-white">{{ toast }}</p>
    </div>
  </Teleport>
</template>

<style scoped>
.pip-hold {
  inset: auto !important;
  right: auto;
  bottom: 0;
  left: 0;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  pointer-events: none;
  opacity: 0;
}

.vtime {
  margin-left: 4px;
  font-size: 12px;
  line-height: 32px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  color: rgba(255, 255, 255, 0.9);
}

.vql {
  height: 32px;
  padding: 0 6px;
  color: #fff;
  font-size: 13px;
  line-height: 32px;
  white-space: nowrap;
}

.vbar {
  pointer-events: none;
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 4;
  padding: 36px 14px 12px;
  overflow: visible;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.62), transparent);
  color: #fff;
  opacity: 0;
  transition: opacity 180ms ease;
}

.stage:hover .vbar,
.stage:focus-within .vbar,
.vbar:has(.is-open) {
  pointer-events: auto;
  opacity: 1;
}

.vprog {
  display: block;
  width: 100%;
  height: 4px;
  padding: 0;
  overflow: hidden;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.28);
}

.vprog span {
  display: block;
  height: 100%;
  border-radius: 4px;
  background: #fff;
}

.vrow {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  margin-top: 10px;
}

.vico {
  display: block;
  width: 24px;
  height: 24px;
}

.vbtn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: #fff;
}

.vbtn :deep(svg) {
  display: block;
  width: 24px;
  height: 24px;
}

.vtip {
  position: relative;
  z-index: 1;
}

.vtip::before {
  position: absolute;
  right: -12px;
  bottom: 100%;
  left: -12px;
  height: 14px;
  content: '';
}

.vtip:hover,
.vtip:focus-within,
.vtip.is-open {
  z-index: 8;
}

.vmenu,
.vvol {
  position: absolute;
  bottom: 100%;
  left: 50%;
  z-index: 8;
  display: flex;
  box-sizing: content-box;
  visibility: hidden;
  opacity: 0;
  pointer-events: none;
  transform: translateX(-50%);
}

.vtip.is-open .vmenu,
.vtip.is-open .vvol {
  visibility: visible;
  opacity: 1;
  pointer-events: auto;
}

.vmenu {
  flex-direction: column;
  min-width: 96px;
  padding: 0;
  background: none;
}

.vmenu-card {
  overflow: hidden;
  border-radius: 20px;
  background: rgba(28, 28, 28, 0.92);
  padding: 8px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.35);
}

.vmenu button {
  width: 100%;
  padding: 8px 12px;
  border-radius: 12px;
  color: #fff;
  font-size: 12px;
  text-align: left;
  white-space: nowrap;
}

.vmenu button:hover,
.vmenu button.is-on {
  background: rgba(255, 255, 255, 0.12);
}

.vvol {
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 118px;
  padding: 12px 0;
  border-radius: 20px;
  background: rgba(0, 0, 0, 0.72);
}

.vvol-track {
  position: relative;
  width: 28px;
  height: 94px;
  padding: 0;
}

.vvol-track::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  width: 4px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.25);
  transform: translateX(-50%);
  content: '';
}

.vvol-track span {
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 4px;
  min-height: 4px;
  border-radius: 4px;
  background: #fff;
  transform: translateX(-50%);
}

.author-hit {
  position: relative;
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 8px;
}

.author-card {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  z-index: 30;
  display: none;
  box-sizing: border-box;
  flex-direction: column;
  width: 360px;
  height: auto;
  padding: 14px 16px 12px;
  overflow: hidden;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.14);
}

.author-hit.is-open .author-card,
.author-hit:hover .author-card {
  display: flex;
}

.author-thumbs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-top: 10px;
}

.author-go {
  cursor: pointer;
}

.author-go:hover {
  opacity: 0.82;
}

.follow-btn {
  flex-shrink: 0;
  height: 32px;
  padding: 0 14px;
  border-radius: 20px;
  background: #ff2442;
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  line-height: 32px;
}

.follow-btn.is-on {
  background: #f2f2f2;
  color: #555;
}

.close-x {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #8a8a8a;
}

.close-x:hover,
.close-x:focus-visible,
.close-x:active {
  background: #f2f2f2;
  color: #1f1f1f;
}

.acts {
  display: flex;
  flex-shrink: 0;
  align-items: center;
}

.act {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  width: 48px;
  min-width: 48px;
  height: 48px;
  flex-shrink: 0;
  padding: 0;
  color: #333;
}

.act.is-on {
  color: #ff2442;
}

.act-ico {
  display: block;
  width: 24px;
  height: 24px;
  overflow: visible;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.act .num {
  display: block;
  width: 48px;
  height: 14px;
  overflow: hidden;
  color: #8a8a8a;
  font-size: 11px;
  line-height: 14px;
  text-align: center;
  white-space: nowrap;
}

.act.is-on .num {
  color: #ff2442;
}
</style>
