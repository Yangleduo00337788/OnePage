<!--
  产品壳：左侧 190px 导航常驻，右侧 header + RouterView 换页。
  <script setup>：组合式写法，顶层变量直接给模板用，不必 return。
  onMounted：DOM 挂上后拉一次 /users/me 和主题。
  watch 路由：换页关掉「更多」，避免面板留在错误位置。
-->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { api } from '@onepage/api-client'
import { useSession } from './stores/session'
import { useImSocket } from './stores/imSocket'
import { useAuthModal } from './stores/authModal'
import { useMoreMenu } from './stores/moreMenu'
import { useTheme } from './stores/theme'
import { useNoteDetail } from './stores/noteDetail'
import { noteFromPost } from './data/home'
import { openCreatorSite } from './creatorSite'
import Icon from './components/Icon.vue'
import BrandLogo from './components/BrandLogo.vue'
import SearchBox from './components/SearchBox.vue'
import AuthModal from './components/AuthModal.vue'
import MoreMenu from './components/MoreMenu.vue'
import NoteDetailModal from './components/NoteDetailModal.vue'

const session = useSession()
const imSocket = useImSocket()
const auth = useAuthModal()
const more = useMoreMenu()
const theme = useTheme()
const detail = useNoteDetail()
const route = useRoute()
onMounted(() => {
  session.refresh()
  theme.init()
})

watch(
  () => session.me?.userId,
  (userId) => {
    if (userId) {
      imSocket.connect()
    } else {
      imSocket.disconnect()
    }
  },
  { immediate: true },
)

imSocket.subscribe((frame) => {
  if (frame.op === 'message' || frame.op === 'ack') {
    void session.refreshUnread()
  }
})

watch(
  () => route.fullPath,
  () => more.hide(),
)

watch(
  () => route.query.post,
  async (id) => {
    if (typeof id !== 'string' || !id) return
    try {
      const post = await api.post(id)
      detail.show(noteFromPost(post))
    } catch {
      /* 笔记不存在或仅自己可见 */
    }
  },
  { immediate: true },
)

const nav = computed(() => [
  { to: '/', label: '首页', icon: 'house-heart', match: ['/'] },
  { to: '/yehyeh', label: '耶耶', icon: 'message-circle-heart', match: ['/yehyeh'], ai: true },
  { to: '/video', label: '视频', icon: 'circle-play', match: ['/video'] },
  { to: '/live', label: '直播', icon: 'tv-minimal-play', match: ['/live'] },
  { to: '/create', label: '发布', icon: 'badge-plus', match: ['/create'], creator: true },
  { to: '/notify', label: '通知', icon: 'bell-ring', match: ['/notify'], notify: true },
  { to: '/messages', label: '消息', icon: 'message-circle-more', match: ['/messages'], im: true },
])

const meNav = { to: '/me', label: '我的', icon: 'smile', match: ['/me'] }

const about = { to: '/about', label: '关于我们', icon: 'info' }
const moreBtn = ref<HTMLButtonElement | null>(null)

function active(item: { to?: string; match: string[] }) {
  if (item.to === '/live') {
    return route.path === '/live' || /^\/live\/[^/]+$/.test(route.path)
  }
  return item.match.includes(route.path)
}

function openLogin() {
  auth.show('login', '/me')
}

function onNav(item: { creator?: boolean }) {
  if (item.creator) openCreatorSite()
}

const yehShell = computed(() => route.path === '/yehyeh')
const liveRoom = computed(() => /^\/live\/[^/]+$/.test(route.path))
const hideTopBar = computed(
  () =>
    yehShell.value ||
    liveRoom.value ||
    route.path === '/messages' ||
    route.path === '/me' ||
    route.path.startsWith('/u/'),
)
const flushMain = computed(
  () =>
    yehShell.value ||
    liveRoom.value ||
    route.path === '/messages' ||
    route.path === '/me' ||
    route.path.startsWith('/u/'),
)
const searchFull = computed(() => route.path === '/' || route.path === '/search')
</script>

<template>
  <div class="min-h-screen text-[var(--ink)]" :class="liveRoom ? 'bg-black' : 'bg-[var(--bg)]'">
    <aside
      v-if="!liveRoom"
      class="fixed inset-y-0 left-0 z-20 flex w-[var(--sidebar)] flex-col items-center bg-[var(--sidebar-bg)] px-2.5 py-5"
    >
      <RouterLink to="/" class="mb-5 inline-flex" aria-label="ONEPAGE"><BrandLogo /></RouterLink>

      <nav class="flex w-full flex-col items-center space-y-1 text-[16px] font-medium text-[#1f1f1f]">
        <RouterLink
          v-for="item in nav"
          :key="item.label"
          :to="item.to"
          class="flex w-[var(--nav-pill)] items-center justify-center overflow-visible rounded-full py-2.5 hover:bg-[#f2f2f2]"
          :class="active(item) ? 'bg-[#f2f2f2]' : ''"
          @click="onNav(item)"
        >
          <span class="flex w-[68px] min-w-[68px] max-w-[68px] items-center gap-3 overflow-visible">
            <Icon :name="item.icon" :size="24" :stroke="1.75" class="shrink-0" />
            <span class="flex shrink-0 items-center gap-1.5 whitespace-nowrap">
              {{ item.label }}
              <span
                v-if="item.ai"
                class="rounded-full bg-[#ff2442] px-1.5 py-0.5 text-[10px] font-medium leading-none text-white"
              >
                AI
              </span>
              <span
                v-else-if="item.notify && session.unreadNotify > 0"
                class="min-w-[16px] rounded-full bg-[#ff2442] px-1 py-0.5 text-center text-[10px] font-medium leading-none text-white"
              >
                {{ session.unreadNotify > 99 ? '99+' : session.unreadNotify }}
              </span>
              <span
                v-else-if="item.im && session.unreadIm > 0"
                class="min-w-[16px] rounded-full bg-[#ff2442] px-1 py-0.5 text-center text-[10px] font-medium leading-none text-white"
              >
                {{ session.unreadIm > 99 ? '99+' : session.unreadIm }}
              </span>
            </span>
          </span>
        </RouterLink>
        <!-- 未登录显示红「登录」；已登录才是「我的」。loaded 防止刷新瞬间闪一下错误项。 -->
        <button v-if="session.loaded && !session.me" type="button" class="nav-login" @click="openLogin">登录</button>
        <RouterLink
          v-else
          :to="meNav.to"
          class="flex w-[var(--nav-pill)] items-center justify-center overflow-visible rounded-full py-2.5 hover:bg-[#f2f2f2]"
          :class="active(meNav) ? 'bg-[#f2f2f2]' : ''"
        >
          <span class="flex w-[68px] min-w-[68px] max-w-[68px] items-center gap-3 overflow-visible">
            <Icon :name="meNav.icon" :size="24" :stroke="1.75" class="shrink-0" />
            <span class="shrink-0 whitespace-nowrap">{{ meNav.label }}</span>
          </span>
        </RouterLink>
      </nav>

      <nav class="mt-auto flex w-full flex-col items-center space-y-1 pb-2 text-[16px] font-medium text-[#1f1f1f]">
        <button
          ref="moreBtn"
          type="button"
          class="nav-pill nav-more flex w-[var(--nav-pill)] items-center justify-center overflow-visible rounded-full py-2.5"
          :class="more.open ? 'is-on' : ''"
          @click.stop="more.toggleFrom(moreBtn)"
        >
          <span class="flex w-[68px] min-w-[68px] max-w-[68px] items-center gap-3 overflow-visible">
            <Icon name="menu" :size="24" :stroke="1.75" class="shrink-0" />
            <span class="shrink-0 whitespace-nowrap">更多</span>
          </span>
        </button>
        <RouterLink
          :to="about.to"
          class="flex w-[var(--nav-pill)] items-center justify-center overflow-visible rounded-full py-2.5 hover:bg-[#f2f2f2]"
          :class="route.path === about.to ? 'bg-[#f2f2f2]' : ''"
        >
          <span class="flex w-[68px] min-w-[68px] max-w-[68px] items-center gap-3 overflow-visible">
            <Icon :name="about.icon" :size="24" :stroke="1.75" class="shrink-0" />
            <span class="shrink-0 whitespace-nowrap">{{ about.label }}</span>
          </span>
        </RouterLink>
      </nav>
    </aside>

    <div class="min-h-screen" :class="liveRoom ? 'ml-0 bg-black' : 'ml-[var(--sidebar)] bg-[var(--bg)]'">
      <header
        v-if="!hideTopBar"
        class="sticky top-0 z-10 flex h-[72px] items-center overflow-visible bg-[var(--bg)] px-8"
        :class="searchFull ? 'justify-center' : 'justify-end'"
      >
        <SearchBox :compact="!searchFull" />
      </header>

      <main class="bg-[var(--bg)]" :class="flushMain ? 'p-0' : 'px-8 pb-16 pt-1'">
        <!-- 当前路由对应的页面组件插在这里，侧栏不重绘。 -->
        <RouterView />
      </main>
    </div>
    <NoteDetailModal />
    <MoreMenu />
    <AuthModal />
  </div>
</template>
