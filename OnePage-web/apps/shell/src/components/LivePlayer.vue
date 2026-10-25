<!-- 直播画面：悬停才出底栏。hls.js。 -->
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import Hls from 'hls.js'
import type { LiveChatLine, LiveSession } from '@onepage/api-client'
import Icon from './Icon.vue'

const props = defineProps<{ session: LiveSession; webFs?: boolean; chats?: LiveChatLine[] }>()
const emit = defineEmits<{ 'web-fs': []; share: [] }>()

const player = ref<HTMLVideoElement | null>(null)
const playFailed = ref('')
const playing = ref(false)
const muted = ref(true)
const volume = ref(0.8)
const pop = ref('')
const quality = ref(-1)
const levels = ref<{ id: number; label: string }[]>([{ id: -1, label: '自动' }])
const danmakuOn = ref(true)
const danmakuHint = ref('')
const flying = ref<{ id: string; text: string; top: number; dur: number }[]>([])
const isFs = ref(false)
let hls: Hls | null = null
let nativeOnError: (() => void) | null = null
let hintTimer = 0
let lane = 0
const seenDanmaku = new Set<string>()
let danmakuPrimed = false

const ended = computed(() => props.session.status === 'ended')
const failHint = computed(() => {
  if (ended.value) return '这场直播已经结束'
  return playFailed.value
})
const qualityLabel = computed(() => levels.value.find((item) => item.id === quality.value)?.label || '自动')

function stopPlayer() {
  playFailed.value = ''
  playing.value = false
  if (hls) {
    hls.destroy()
    hls = null
  }
  const el = player.value
  if (el) {
    if (nativeOnError) {
      el.removeEventListener('error', nativeOnError)
      nativeOnError = null
    }
    el.removeAttribute('src')
    el.load()
  }
}

function markFail(text: string) {
  if (ended.value) return
  playFailed.value = text
}

function applyVolume() {
  const el = player.value
  if (!el) return
  el.muted = muted.value || volume.value === 0
  el.volume = volume.value
}

function tryPlay() {
  const el = player.value
  if (!el) return
  applyVolume()
  void el
    .play()
    .then(() => {
      playing.value = true
    })
    .catch(() => {
      muted.value = true
      applyVolume()
      void el
        .play()
        .then(() => {
          playing.value = true
        })
        .catch(() => {
          playing.value = false
        })
    })
}

function isProgressiveVideo(url: string) {
  const path = url.split('?')[0].toLowerCase()
  return path.endsWith('.mp4') || path.endsWith('.m4v') || path.endsWith('.webm')
}

function startPlayer(url: string) {
  stopPlayer()
  if (ended.value) return
  const el = player.value
  if (!el || !url) return
  if (isProgressiveVideo(url)) {
    levels.value = [{ id: -1, label: '自动' }]
    el.src = url
    nativeOnError = () => markFail('主播暂时没有推流，稍后再试')
    el.addEventListener('error', nativeOnError)
    tryPlay()
    return
  }
  if (Hls.isSupported()) {
    hls = new Hls({ enableWorker: true })
    hls.loadSource(url)
    hls.attachMedia(el)
    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      if (hls) {
        levels.value = [
          { id: -1, label: '自动' },
          ...hls.levels.map((lv, i) => ({ id: i, label: lv.height ? `${lv.height}P` : `线路${i + 1}` })),
        ]
        hls.currentLevel = quality.value
      }
      tryPlay()
    })
    hls.on(Hls.Events.ERROR, (_evt, data) => {
      if (data.fatal) markFail('主播暂时没有推流，稍后再试')
    })
  } else if (el.canPlayType('application/vnd.apple.mpegurl')) {
    el.src = url
    nativeOnError = () => markFail('主播暂时没有推流，稍后再试')
    el.addEventListener('error', nativeOnError)
    tryPlay()
  } else {
    markFail('当前浏览器不支持播放')
  }
}

function togglePlay() {
  const el = player.value
  if (!el || ended.value || playFailed.value) return
  if (el.paused) tryPlay()
  else {
    el.pause()
    playing.value = false
  }
}

function toggleMute() {
  muted.value = !muted.value
  if (!muted.value && volume.value === 0) volume.value = 0.8
  applyVolume()
}

function onVolBar(e: MouseEvent) {
  const bar = e.currentTarget as HTMLElement
  const r = bar.getBoundingClientRect()
  volume.value = Math.min(1, Math.max(0, (r.bottom - e.clientY) / r.height))
  muted.value = volume.value === 0
  applyVolume()
}

function setQuality(id: number) {
  quality.value = id
  if (hls) hls.currentLevel = id
  pop.value = ''
}

function reload() {
  if (props.session.playUrlHls) startPlayer(props.session.playUrlHls)
}

function dropDanmaku(id: string) {
  flying.value = flying.value.filter((item) => item.id !== id)
}

function spawnDanmaku(id: string, text: string) {
  lane = (lane + 1) % 8
  flying.value = flying.value.concat({
    id,
    text,
    top: 8 + lane * 10,
    dur: 8 + (lane % 3),
  })
}

function ingestChats(rows: LiveChatLine[] | undefined) {
  if (!rows?.length) return
  if (!danmakuPrimed) {
    const start = Math.max(0, rows.length - 8)
    for (let i = 0; i < start; i++) seenDanmaku.add(rows[i].messageId)
    danmakuPrimed = true
  }
  for (const row of rows) {
    if (!row?.messageId || seenDanmaku.has(row.messageId)) continue
    seenDanmaku.add(row.messageId)
    if (!danmakuOn.value || row.kind === 'join') continue
    const body = (row.body || '').trim()
    if (!body) continue
    const nick = (row.author?.nickname || '').trim()
    spawnDanmaku(row.messageId, nick ? `${nick}：${body}` : body)
  }
}

function toggleDanmaku() {
  danmakuOn.value = !danmakuOn.value
  if (!danmakuOn.value) flying.value = []
  danmakuHint.value = danmakuOn.value ? '弹幕已开启' : '弹幕已关闭'
  window.clearTimeout(hintTimer)
  hintTimer = window.setTimeout(() => {
    danmakuHint.value = ''
  }, 1200)
}

async function togglePip() {
  const el = player.value
  if (!el) return
  try {
    if (document.pictureInPictureElement) await document.exitPictureInPicture()
    else await el.requestPictureInPicture()
  } catch {
    /* 浏览器不支持 */
  }
}

async function toggleFs() {
  const root = player.value?.closest('.live-stage') as HTMLElement | null
  const el = root || player.value
  if (!el) return
  if (document.fullscreenElement) await document.exitFullscreen()
  else await el.requestFullscreen()
}

function share() {
  emit('share')
}

function onFsChange() {
  isFs.value = Boolean(document.fullscreenElement)
}

async function attach() {
  await nextTick()
  const current = props.session
  if (current.status === 'ended') {
    stopPlayer()
    return
  }
  if (current.playUrlHls) startPlayer(current.playUrlHls)
  else stopPlayer()
}

watch(
  () => props.session.sessionId + (props.session.status || '') + (props.session.playUrlHls || ''),
  () => {
    void attach()
  },
)

watch(
  () => props.session.sessionId,
  () => {
    seenDanmaku.clear()
    danmakuPrimed = false
    flying.value = []
    lane = 0
  },
)

watch(
  () => props.chats,
  (rows) => ingestChats(rows),
  { deep: true, immediate: true },
)

onMounted(() => {
  document.addEventListener('fullscreenchange', onFsChange)
  void attach()
})

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', onFsChange)
  window.clearTimeout(hintTimer)
  stopPlayer()
})
</script>

<template>
  <div class="live-stage group" @click="togglePlay">
    <video
      ref="player"
      class="live-video"
      playsinline
      :muted="muted"
      :poster="session.coverUrl || undefined"
    />
    <div v-if="failHint" class="live-fail">{{ failHint }}</div>
    <div v-if="danmakuOn" class="danmaku-layer" aria-hidden="true">
      <span
        v-for="item in flying"
        :key="item.id"
        class="danmaku-item"
        :style="{ top: `${item.top}%`, animationDuration: `${item.dur}s` }"
        @animationend="dropDanmaku(item.id)"
      >
        {{ item.text }}
      </span>
    </div>
    <p v-if="danmakuHint" class="live-toast">{{ danmakuHint }}</p>
    <div class="vbar" @click.stop>
      <div class="vrow">
        <button type="button" class="vbtn" :aria-label="playing ? '暂停' : '播放'" @click="togglePlay">
          <svg v-if="playing" class="vico-fill" viewBox="0 0 24 24" aria-hidden="true">
            <rect x="5" y="3.5" width="5" height="17" rx="1.2" />
            <rect x="14" y="3.5" width="5" height="17" rx="1.2" />
          </svg>
          <svg v-else class="vico-fill" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M5 3.2v17.6L21.2 12z" />
          </svg>
        </button>
        <button type="button" class="vbtn" aria-label="刷新" @click="reload">
          <Icon name="rotate-cw" :size="20" :stroke="2" />
        </button>
        <span class="vgap" />
        <div class="vtip" :class="pop === 'q' ? 'is-open' : ''" @mouseenter="pop = 'q'" @mouseleave="pop = ''">
          <button type="button" class="vql" aria-label="分辨率">{{ qualityLabel }}</button>
          <div class="vmenu">
            <div class="vmenu-card">
              <button
                v-for="item in levels"
                :key="item.id"
                type="button"
                :class="quality === item.id ? 'is-on' : ''"
                @click="setQuality(item.id)"
              >
                {{ item.label }}
              </button>
            </div>
          </div>
        </div>
        <button
          type="button"
          class="vql"
          :class="{ 'is-off': !danmakuOn }"
          aria-label="弹幕开关"
          @click="toggleDanmaku"
        >
          弹幕
        </button>
        <div
          class="vtip vol"
          :class="pop === 'v' ? 'is-open' : ''"
          @mouseenter="pop = 'v'"
          @mouseleave="pop = ''"
        >
          <button type="button" class="vbtn" :aria-label="muted || volume === 0 ? '取消静音' : '音量'" @click="toggleMute">
            <Icon :name="muted || volume === 0 ? 'volume-x' : 'volume-2'" :size="20" :stroke="2" />
          </button>
          <div class="vvol">
            <button type="button" class="vvol-track" aria-label="音量" @click.stop="onVolBar">
              <span :style="{ height: `${(muted ? 0 : volume) * 100}%` }" />
            </button>
          </div>
        </div>
        <button type="button" class="vbtn" aria-label="画中画" @click="togglePip">
          <Icon name="picture-in-picture-2" :size="20" :stroke="2" />
        </button>
        <button type="button" class="vbtn" :aria-label="webFs ? '退出网页全屏' : '网页全屏'" @click="emit('web-fs')">
          <Icon :name="webFs ? 'shrink' : 'expand'" :size="20" :stroke="2" />
        </button>
        <button type="button" class="vbtn" :aria-label="isFs ? '退出全屏' : '全屏'" @click="toggleFs">
          <Icon :name="isFs ? 'minimize' : 'maximize'" :size="20" :stroke="2" />
        </button>
        <button type="button" class="vbtn" aria-label="分享" @click="share">
          <Icon name="share-2" :size="20" :stroke="2" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.live-stage {
  position: relative;
  width: 100%;
  height: 100%;
  background: #000;
  cursor: pointer;
}

.live-video {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.live-fail,
.live-toast {
  position: absolute;
  left: 50%;
  z-index: 2;
  color: #eee;
  font-size: 14px;
}

.live-fail {
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: none;
  left: 0;
  background: rgba(0, 0, 0, 0.45);
  padding: 16px;
}

.live-toast {
  bottom: 72px;
  transform: translateX(-50%);
  padding: 6px 12px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.65);
  font-size: 12px;
}

.danmaku-layer {
  pointer-events: none;
  position: absolute;
  inset: 0 0 56px;
  z-index: 3;
  overflow: hidden;
}

.danmaku-item {
  position: absolute;
  left: 100%;
  color: #fff;
  font-size: 14px;
  line-height: 1.4;
  white-space: nowrap;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.8);
  animation-name: danmaku-fly;
  animation-timing-function: linear;
  animation-fill-mode: forwards;
}

@keyframes danmaku-fly {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(calc(-100vw - 100%));
  }
}

.vbar {
  pointer-events: none;
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 4;
  padding: 40px 20px 16px;
  overflow: visible;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.62), transparent);
  color: #fff;
  opacity: 0;
  transition: opacity 180ms ease;
}

.live-stage:hover .vbar,
.live-stage:focus-within .vbar,
.vbar:has(.is-open) {
  pointer-events: auto;
  opacity: 1;
}

.vrow {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 36px;
}

.vgap {
  flex: 1;
}

.vbtn {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  color: #fff;
}

.vbtn :deep(svg) {
  display: block;
  width: 20px;
  height: 20px;
}

.vico-fill {
  display: block;
  width: 20px;
  height: 20px;
  fill: currentColor;
}

.vql {
  height: 36px;
  padding: 0 10px;
  color: #fff;
  font-size: 13px;
  line-height: 36px;
  white-space: nowrap;
}

.vql.is-off {
  opacity: 0.45;
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
</style>
