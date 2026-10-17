<!--
  直播列表与观看。GET /live/sessions；开播弹层 POST /live/rooms 再 start；刷新 GET /live/mine。
-->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError, type LiveSession, type LiveStart } from '@onepage/api-client'
import Icon from '../components/Icon.vue'
import EmptyState from '../components/EmptyState.vue'
import LiveFeedCard from '../components/LiveFeedCard.vue'
import { useAuthModal } from '../stores/authModal'
import { useSession } from '../stores/session'
import { LIVE_TABS } from '../data/home'

const route = useRoute()
const router = useRouter()
const session = useSession()
const auth = useAuthModal()

const categories = LIVE_TABS.filter((item) => item !== '全部')

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
const category = ref('生活分享')
const starting = ref(false)
const host = ref<LiveStart | null>(null)
const hostOpen = ref(false)
const copied = ref('')

const pushServer = computed(() => {
  const url = host.value?.pushUrl || ''
  const i = url.lastIndexOf('/')
  return i > 0 ? url.slice(0, i) : url
})

const streamKey = computed(() => host.value?.streamKey || '')

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

async function loadMine() {
  if (!session.me) {
    host.value = null
    return
  }
  try {
    const mine = await api.myLive()
    host.value = mine ?? null
  } catch {
    host.value = null
  }
}

function openHost() {
  if (!session.me) {
    auth.show('login', '/live')
    return
  }
  failed.value = ''
  if (!host.value) {
    title.value = ''
    category.value = tab.value === '全部' ? '生活分享' : tab.value
  }
  hostOpen.value = true
}

function closeHost() {
  hostOpen.value = false
}

async function goLive() {
  const name = title.value.trim()
  if (!name || starting.value) return
  starting.value = true
  failed.value = ''
  try {
    const room = await api.createLiveRoom({ title: name, category: category.value })
    host.value = await api.startLive(room.roomId)
    title.value = ''
    await loadList()
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      hostOpen.value = false
      auth.show('login', '/live')
    } else if (e instanceof ApiError && e.status === 409) {
      await loadMine()
    } else {
      failed.value = e instanceof ApiError ? e.message : '开播失败'
    }
  } finally {
    starting.value = false
  }
}

async function stopLive() {
  if (!host.value) return
  const roomId = host.value.roomId
  try {
    await api.endLive(roomId)
  } catch {
    /* 关播失败仍清本机状态 */
  }
  host.value = null
  hostOpen.value = false
  await loadList()
}

async function copyText(label: string, text: string) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    copied.value = label
    window.setTimeout(() => {
      if (copied.value === label) copied.value = ''
    }, 1600)
  } catch {
    copied.value = ''
  }
}

function openWatch(id: string) {
  void router.push(`/live/${encodeURIComponent(id)}`)
}

function toTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

watch(
  () => tab.value,
  () => {
    void loadList()
  },
)

watch(
  () => session.me?.userId,
  () => {
    void loadMine()
  },
)

onMounted(() => {
  const s = route.query.s
  if (typeof s === 'string' && s) {
    void router.replace(`/live/${encodeURIComponent(s)}`)
    return
  }
  void loadList()
  void loadMine()
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

    <div class="feed-refresh" :class="loading ? 'is-on' : ''" aria-hidden="true">
      <span v-show="loading" class="feed-spinner" />
    </div>

    <p v-if="!loading && failed && !hostOpen" class="mb-4 text-center text-[13px] text-[#8a8a8a]">{{ failed }}</p>

    <div v-if="items.length" class="feed-grid flex flex-wrap justify-center gap-4" :class="loading ? 'is-refreshing' : ''">
      <LiveFeedCard
        v-for="item in items"
        :key="item.sessionId"
        :item="item"
        @click="openWatch(item.sessionId)"
      />
    </div>
    <EmptyState
      v-else-if="!loading"
      src="/empty-live.png"
      title="还没有人开播"
      desc="登录后点右下角「我要开播」，用推流软件推到给出的地址即可。"
    />

    <div v-if="hostOpen" class="live-host-mask" @click.self="closeHost">
      <div class="live-host-card">
        <div class="live-host-head">
          <strong>{{ host ? '正在直播' : '我要开播' }}</strong>
          <button type="button" class="live-host-x" aria-label="关闭" @click="closeHost">
            <Icon name="x" :size="16" />
          </button>
        </div>

        <template v-if="!host">
          <label class="live-field">
            <span>标题</span>
            <input
              v-model="title"
              maxlength="100"
              placeholder="给这场直播起个名字"
              @keydown.enter.prevent="goLive"
            />
          </label>
          <label class="live-field">
            <span>分类</span>
            <select v-model="category">
              <option v-for="item in categories" :key="item" :value="item">{{ item }}</option>
            </select>
          </label>
          <p v-if="failed" class="live-err">{{ failed }}</p>
          <button
            type="button"
            class="live-go"
            :disabled="starting || !title.trim()"
            @click="goLive"
          >
            {{ starting ? '开播中…' : '开始直播' }}
          </button>
        </template>

        <template v-else>
          <p class="live-host-title">{{ host.title || '直播中' }}</p>
          <p class="live-obs-hint">在 OBS 里选「自定义」推流，分别填入服务器和串流密钥。</p>
          <div class="live-obs-row">
            <span>服务器</span>
            <code>{{ pushServer }}</code>
            <button type="button" @click="copyText('server', pushServer)">
              {{ copied === 'server' ? '已复制' : '复制' }}
            </button>
          </div>
          <div class="live-obs-row">
            <span>串流密钥</span>
            <code>{{ streamKey }}</code>
            <button type="button" @click="copyText('key', streamKey)">
              {{ copied === 'key' ? '已复制' : '复制' }}
            </button>
          </div>
          <div class="live-obs-row">
            <span>完整地址</span>
            <code>{{ host.pushUrl }}</code>
            <button type="button" @click="copyText('full', host.pushUrl)">
              {{ copied === 'full' ? '已复制' : '复制' }}
            </button>
          </div>
          <ol class="live-steps">
            <li>打开 OBS 或同类推流软件</li>
            <li>设置 → 推流：服务选自定义，填入上面两项</li>
            <li>开始推流后，观众点卡片即可看见画面</li>
          </ol>
          <button type="button" class="live-end" @click="stopLive">结束直播</button>
        </template>
      </div>
    </div>

    <div class="feed-fabs">
      <button type="button" class="live-fab" @click="openHost">
        {{ host ? '直播中' : '我要开播' }}
      </button>
      <button type="button" class="feed-fab" @click="toTop">
        <Icon name="chevron-up" :size="20" />
        <span class="feed-tip">返回顶部</span>
      </button>
      <button type="button" class="feed-fab" :class="loading ? 'is-spin' : ''" @click="loadList()">
        <Icon name="refresh" :size="18" />
        <span class="feed-tip">刷新</span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.live-fab {
  display: flex;
  height: 44px;
  align-items: center;
  justify-content: center;
  align-self: flex-end;
  padding: 0 16px;
  border-radius: 22px;
  background: #ff2442;
  color: #fff;
  font-size: 14px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

.live-host-mask {
  position: fixed;
  inset: 0;
  z-index: 45;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
}

.live-host-card {
  width: min(440px, 92vw);
  max-height: min(88vh, 640px);
  overflow: auto;
  border-radius: 20px;
  background: #fff;
  padding: 16px 18px 20px;
  color: #1f1f1f;
}

.live-host-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  font-size: 16px;
}

.live-host-x {
  display: flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.live-host-x:hover {
  background: #f2f2f2;
}

.live-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #8a8a8a;
}

.live-field input,
.live-field select {
  height: 40px;
  border-radius: 20px;
  background: #f3f3f3;
  padding: 0 14px;
  font-size: 14px;
  color: #1f1f1f;
  outline: none;
}

.live-go,
.live-end {
  display: flex;
  width: 100%;
  height: 40px;
  align-items: center;
  justify-content: center;
  margin-top: 8px;
  border-radius: 20px;
  font-size: 14px;
}

.live-go {
  background: #ff2442;
  color: #fff;
}

.live-go:disabled {
  opacity: 0.5;
}

.live-end {
  background: #f3f3f3;
  color: #1f1f1f;
}

.live-err {
  margin: 0 0 8px;
  font-size: 13px;
  color: #8a8a8a;
}

.live-host-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 600;
}

.live-obs-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: #8a8a8a;
}

.live-obs-row {
  display: grid;
  grid-template-columns: 72px 1fr auto;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #8a8a8a;
}

.live-obs-row code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: #1f1f1f;
}

.live-obs-row button {
  color: #ff2442;
  font-size: 12px;
}

.live-steps {
  margin: 12px 0 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.7;
  color: #555;
}
</style>
