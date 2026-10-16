<!--
  直播列表与观看。GET /live/sessions；开播 POST /live/rooms 再 start。
-->
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Hls from 'hls.js'
import { api, ApiError, type LiveSession, type LiveStart } from '@onepage/api-client'
import Icon from '../components/Icon.vue'
import EmptyState from '../components/EmptyState.vue'
import { useAuthModal } from '../stores/authModal'
import { LIVE_TABS } from '../data/home'

const route = useRoute()
const router = useRouter()
const auth = useAuthModal()

const tab = computed({
  get: () => {
    const c = route.query.cat
    if (typeof c === 'string' && (LIVE_TABS as readonly string[]).includes(c)) return c
    return '全部'
  },
  set: (value: string) => {
    const next = { ...route.query }
    if (value === '全部') delete next.cat
    else next.cat = value
    void router.replace({ query: next })
  },
})

const items = ref<LiveSession[]>([])
const loading = ref(false)
const failed = ref('')
const title = ref('')
const starting = ref(false)
const host = ref<LiveStart | null>(null)
const watching = ref<LiveSession | null>(null)
const player = ref<HTMLVideoElement | null>(null)
const playFailed = ref('')
let hls: Hls | null = null

function stopPlayer() {
  playFailed.value = ''
  if (hls) {
    hls.destroy()
    hls = null
  }
  const el = player.value
  if (el) {
    el.removeAttribute('src')
    el.load()
  }
}

function startPlayer(url: string) {
  stopPlayer()
  const el = player.value
  if (!el || !url) return
  if (Hls.isSupported()) {
    hls = new Hls({ enableWorker: true })
    hls.loadSource(url)
    hls.attachMedia(el)
    hls.on(Hls.Events.ERROR, (_evt, data) => {
      if (data.fatal) playFailed.value = '暂时播不了，确认推流已开始'
    })
  } else if (el.canPlayType('application/vnd.apple.mpegurl')) {
    el.src = url
  } else {
    playFailed.value = '当前浏览器不支持 HLS'
  }
  void el.play().catch(() => {
    /* 用户点开后再点播放 */
  })
}

async function loadList() {
  loading.value = true
  failed.value = ''
  try {
    const page = await api.liveSessions({ category: tab.value === '全部' ? undefined : tab.value })
    items.value = page.items || []
  } catch (e) {
    items.value = []
    failed.value = e instanceof ApiError ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadWatch(id: string) {
  try {
    watching.value = await api.liveSession(id)
  } catch {
    watching.value = null
  }
}

async function goLive() {
  const name = title.value.trim()
  if (!name || starting.value) return
  starting.value = true
  failed.value = ''
  try {
    const room = await api.createLiveRoom({ title: name, category: tab.value === '全部' ? '' : tab.value })
    host.value = await api.startLive(room.roomId)
    title.value = ''
    await loadList()
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) auth.show('login', '/live')
    else failed.value = e instanceof ApiError ? e.message : '开播失败'
  } finally {
    starting.value = false
  }
}

async function stopLive() {
  if (!host.value) return
  try {
    await api.endLive(host.value.roomId)
  } catch {
    /* 关播失败仍清本机状态 */
  }
  host.value = null
  await loadList()
}

function openWatch(id: string) {
  void router.replace({ query: { ...route.query, s: id } })
}

function closeWatch() {
  const next = { ...route.query }
  delete next.s
  watching.value = null
  void router.replace({ query: next })
}

function toTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

watch(
  () => [tab.value, route.query.s] as const,
  ([, s]) => {
    void loadList()
    if (typeof s === 'string' && s) void loadWatch(s)
    else watching.value = null
  },
)

watch(watching, async (session) => {
  await nextTick()
  if (session?.playUrlHls) startPlayer(session.playUrlHls)
  else stopPlayer()
})

onMounted(() => {
  void loadList()
  const s = route.query.s
  if (typeof s === 'string' && s) void loadWatch(s)
})

onUnmounted(() => {
  stopPlayer()
})
</script>

<template>
  <section>
    <div class="mb-5 flex flex-wrap items-center justify-center gap-x-7 gap-y-2">
      <button
        v-for="item in LIVE_TABS"
        :key="item"
        type="button"
        class="feed-tab"
        :class="tab === item ? 'is-on' : ''"
        @click="tab = item"
      >
        {{ item }}
      </button>
    </div>

    <div class="mx-auto mb-6 flex w-full max-w-[720px] items-center gap-2">
      <input
        v-model="title"
        class="h-10 flex-1 rounded-[20px] bg-[#f3f3f3] px-4 text-[14px] outline-none"
        maxlength="100"
        placeholder="填写标题后开播"
        @keydown.enter.prevent="goLive"
      />
      <button
        type="button"
        class="h-10 rounded-[20px] bg-[#ff2442] px-4 text-[14px] text-white disabled:opacity-50"
        :disabled="starting || !title.trim()"
        @click="goLive"
      >
        {{ starting ? '开播中…' : '我要开播' }}
      </button>
    </div>

    <p v-if="host" class="mx-auto mb-6 max-w-[720px] rounded-[16px] bg-[#fff7f8] p-4 text-[13px] text-[#1f1f1f]">
      已开播。用推流软件推到：{{ host.pushUrl }}
      <button type="button" class="ml-3 text-[#ff2442]" @click="stopLive">结束直播</button>
    </p>
    <p v-if="failed" class="mb-4 text-center text-[13px] text-[#8a8a8a]">{{ failed }}</p>

    <p v-if="loading" class="py-16 text-center text-[14px] text-[#8a8a8a]">加载中…</p>
    <div v-else-if="items.length" class="feed-grid flex flex-wrap justify-center gap-4">
      <button
        v-for="item in items"
        :key="item.sessionId"
        type="button"
        class="w-[280px] overflow-hidden rounded-[16px] bg-white text-left"
        @click="openWatch(item.sessionId)"
      >
        <div class="relative h-[360px] bg-[#111]">
          <img v-if="item.coverUrl" :src="item.coverUrl" alt="" class="h-full w-full object-cover" />
          <span class="absolute right-2 top-2 rounded bg-[#ff4d4f] px-1.5 py-0.5 text-[11px] text-white">直播中</span>
        </div>
        <p class="mt-2 truncate px-1 text-[15px] font-medium">{{ item.title }}</p>
        <p class="mt-0.5 flex items-center gap-1 px-1 text-[13px] text-[#8a8a8a]">
          <span>{{ item.owner?.nickname || '主播' }}</span>
          <span>·</span>
          <Icon name="users" :size="12" />
          <span>{{ item.viewerCount }}</span>
        </p>
      </button>
    </div>
    <EmptyState v-else src="/empty-live.png" title="还没有直播" desc="登录后填写标题即可开播。没有推流服务时播放会失败。" />

    <div v-if="watching" class="yeh-pick" @click.self="closeWatch">
      <div class="yeh-pick-card" style="width: min(720px, 94vw)">
        <div class="yeh-pick-head">
          <strong>{{ watching.title }}</strong>
          <button type="button" @click="closeWatch">关闭</button>
        </div>
        <video
          ref="player"
          class="w-full rounded-[12px] bg-black"
          controls
          playsinline
          muted
        />
        <p v-if="playFailed" class="mt-2 text-[12px] text-[#8a8a8a]">{{ playFailed }}</p>
        <p class="mt-2 text-[12px] text-[#8a8a8a]">{{ watching.owner?.nickname }} · {{ watching.viewerCount }} 人在看</p>
      </div>
    </div>

    <div class="feed-fabs">
      <button type="button" class="feed-fab" @click="toTop">
        <Icon name="chevron-up" :size="20" />
        <span class="feed-tip">返回顶部</span>
      </button>
    </div>
  </section>
</template>
