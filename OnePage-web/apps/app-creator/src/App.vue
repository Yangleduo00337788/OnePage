<!-- 创作者后台：带图标的左栏、数据报表子菜单、可收起；右上角铃铛创作消息 + 头像退出。 -->
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api, type UserProfile } from '@onepage/api-client'
import Icon from './Icon.vue'
import BrandLogo from './BrandLogo.vue'
import CreatorHome, { type PageId, type PublishKind } from './CreatorHome.vue'
import CreatorInbox from './CreatorInbox.vue'
import CreatorLogin from './CreatorLogin.vue'

const STAT_PAGES: PageId[] = ['account', 'content', 'fans']

const page = ref<PageId>('home')
const publishKind = ref<PublishKind>('image')
const editPostId = ref('')
const notesFocus = ref('')
const statsOpen = ref(false)
const collapsed = ref(localStorage.getItem('onepage.creator.navCollapsed') === '1')
const me = ref<UserProfile | null>(null)
const ready = ref(false)
const menuOpen = ref(false)
const userWrap = ref<HTMLElement | null>(null)

const statsKids: { id: PageId; label: string }[] = [
  { id: 'account', label: '账号概览' },
  { id: 'content', label: '内容分析' },
  { id: 'fans', label: '粉丝数据' },
]

const statsOn = computed(() => STAT_PAGES.includes(page.value))
const initial = computed(() => (me.value?.nickname || '创作者').slice(0, 1))
const nickname = computed(() => me.value?.nickname || '未登录')

onMounted(async () => {
  document.addEventListener('click', onDocClick)
  try {
    me.value = await api.me()
  } catch {
    me.value = null
  } finally {
    ready.value = true
  }
})

onUnmounted(() => {
  document.removeEventListener('click', onDocClick)
})

function onDocClick(e: MouseEvent) {
  if (!userWrap.value?.contains(e.target as Node)) menuOpen.value = false
}

function openPage(id: PageId, notesTab = '') {
  editPostId.value = ''
  if (id === 'notes') notesFocus.value = notesTab
  page.value = id
  if (STAT_PAGES.includes(id)) statsOpen.value = true
}

function toggleStats() {
  if (collapsed.value) {
    setCollapsed(false)
    statsOpen.value = true
    return
  }
  statsOpen.value = !statsOpen.value
}

function setCollapsed(next: boolean) {
  collapsed.value = next
  localStorage.setItem('onepage.creator.navCollapsed', next ? '1' : '0')
  if (next) statsOpen.value = false
}

async function logout() {
  menuOpen.value = false
  try {
    await api.logout()
  } catch {
    /* 网关已清 Cookie 也继续 */
  }
  me.value = null
}

function onLoggedIn(profile: UserProfile) {
  me.value = profile
}

const publishKinds = [
  { id: 'video' as PublishKind, label: '上传视频', icon: 'circle-play' },
  { id: 'image' as PublishKind, label: '上传图文', icon: 'image' },
  { id: 'article' as PublishKind, label: '写长文', icon: 'pencil' },
]

function goPublish(kind: PublishKind = 'image') {
  editPostId.value = ''
  publishKind.value = kind
  page.value = 'publish'
}

function goEdit(postId: string) {
  editPostId.value = postId
  page.value = 'publish'
}
</script>

<template>
  <CreatorLogin v-if="ready && !me" @done="onLoggedIn" />
  <div v-else-if="ready" class="admin" :class="{ 'is-collapsed': collapsed }">
    <aside class="nav">
      <div class="nav-mark">
        <BrandLogo :mini="collapsed" />
      </div>
      <div class="pub">
        <button type="button" class="pub-btn" :title="collapsed ? '发布笔记' : undefined" @click="goPublish()">
          <Icon name="heart-plus" :size="18" />
          <span v-if="!collapsed">发布笔记</span>
          <Icon v-if="!collapsed" name="chevron-down" :size="14" class="pub-chev" />
        </button>
        <div class="pub-pop" role="menu">
          <button
            v-for="item in publishKinds"
            :key="item.id"
            type="button"
            class="pub-row"
            @click="goPublish(item.id)"
          >
              <Icon :name="item.icon" :size="20" />
            <span>{{ item.label }}</span>
          </button>
        </div>
      </div>
      <nav class="nav-list" aria-label="创作者后台">
        <button
          type="button"
          class="nav-item"
          :class="{ 'is-on': page === 'home' }"
          :title="collapsed ? '首页' : undefined"
          @click="openPage('home')"
        >
          <Icon name="house" :size="20" />
          <span v-if="!collapsed">首页</span>
        </button>
        <button
          type="button"
          class="nav-item"
          :class="{ 'is-on': page === 'notes' }"
          :title="collapsed ? '笔记管理' : undefined"
          @click="openPage('notes')"
        >
          <Icon name="sticky-note" :size="20" />
          <span v-if="!collapsed">笔记管理</span>
        </button>
        <button
          type="button"
          class="nav-item"
          :class="{ 'is-on': page === 'live' }"
          :title="collapsed ? '直播' : undefined"
          @click="openPage('live')"
        >
          <Icon name="radio" :size="20" />
          <span v-if="!collapsed">直播</span>
        </button>

        <div class="nav-group">
          <button
            type="button"
            class="nav-item"
            :class="{ 'is-on': statsOn }"
            :title="collapsed ? '数据报表' : undefined"
            @click="toggleStats"
          >
            <Icon name="chart-column" :size="20" />
            <span v-if="!collapsed">数据报表</span>
            <Icon v-if="!collapsed" name="chevron-down" :size="14" class="chev" :class="{ 'is-open': statsOpen }" />
          </button>
          <div v-if="statsOpen && !collapsed" class="sub">
            <button
              v-for="kid in statsKids"
              :key="kid.id"
              type="button"
              class="sub-item"
              :class="{ 'is-on': page === kid.id }"
              @click="openPage(kid.id)"
            >
              {{ kid.label }}
            </button>
          </div>
        </div>

        <button
          type="button"
          class="nav-item"
          :class="{ 'is-on': page === 'events' }"
          :title="collapsed ? '活动中心' : undefined"
          @click="openPage('events')"
        >
          <Icon name="calendar" :size="20" />
          <span v-if="!collapsed">活动中心</span>
        </button>
        <button
          type="button"
          class="nav-item"
          :class="{ 'is-on': page === 'dict' }"
          :title="collapsed ? '创作字典' : undefined"
          @click="openPage('dict')"
        >
          <Icon name="book-open" :size="20" />
          <span v-if="!collapsed">创作字典</span>
        </button>
      </nav>

      <button
        type="button"
        class="nav-item nav-fold"
        :title="collapsed ? '展开侧边栏' : '收起侧边栏'"
        @click="setCollapsed(!collapsed)"
      >
        <Icon name="panel-left" :size="20" />
        <span v-if="!collapsed">收起侧边栏</span>
      </button>
    </aside>

    <div class="main">
      <header class="bar">
        <div class="bar-tools">
          <CreatorInbox @edit="goEdit" />
          <div ref="userWrap" class="user-wrap">
            <button type="button" class="user" @click="menuOpen = !menuOpen">
              <span class="avatar">
                <img v-if="me?.avatarUrl" :src="me.avatarUrl" alt="" />
                <span v-else>{{ initial }}</span>
              </span>
              <span class="name">{{ nickname }}</span>
              <Icon name="chevron-down" :size="14" class="chev" :class="{ 'is-open': menuOpen }" />
            </button>
            <div v-if="menuOpen" class="menu">
              <button type="button" class="menu-item" @click="logout">退出登录</button>
            </div>
          </div>
        </div>
      </header>
      <CreatorHome
        :page="page"
        :me="me"
        :publish-kind="publishKind"
        :edit-post-id="editPostId"
        :notes-focus="notesFocus"
        @update:publish-kind="publishKind = $event"
        @publish="goPublish"
        @edit="goEdit"
        @open-stats="openPage('account')"
        @open-notes="(tab?: string) => openPage('notes', tab || '')"
        @open-events="openPage('events')"
        @open-live="openPage('live')"
      />
    </div>
  </div>
</template>

<style>
html,
body {
  margin: 0;
  min-height: 100vh;
  background: rgb(248, 248, 248);
  font-family: 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', system-ui, sans-serif;
}

button {
  font: inherit;
  background: none;
  border: 0;
  cursor: pointer;
}
</style>

<style scoped>
.admin {
  display: flex;
  min-height: 100vh;
  color: #1f1f1f;
  background: rgb(248, 248, 248);
}

.nav {
  display: flex;
  flex-direction: column;
  width: 200px;
  flex-shrink: 0;
  padding: 20px 8px 12px;
  background: rgb(248, 248, 248);
  transition: width 200ms ease;
}

.is-collapsed .nav {
  width: 72px;
  align-items: center;
}

.nav-mark {
  display: flex;
  margin: 0 auto 16px;
  align-items: center;
  justify-content: center;
}

.is-collapsed .nav-mark {
  margin: 0 0 16px;
}

.pub {
  position: relative;
  z-index: 30;
  display: flex;
  justify-content: center;
  width: 100%;
  margin: 0 0 20px;
}

.pub-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 40px;
  padding: 0 18px;
  border-radius: 20px;
  background: #ff2442;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}

.pub-btn :deep(svg) {
  display: block;
  flex-shrink: 0;
}

.is-collapsed .pub-btn {
  width: 40px;
  padding: 0;
}

.pub-chev {
  margin-left: 2px;
  transition: transform 180ms ease;
}

.pub:hover .pub-chev,
.pub:focus-within .pub-chev {
  transform: rotate(180deg);
}

.pub-pop {
  display: none;
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  padding: 6px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.14);
}

.pub:hover .pub-pop,
.pub:focus-within .pub-pop {
  display: block;
}

.pub-row {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  height: 46px;
  padding: 0 14px;
  border-radius: 10px;
  color: #1f1f1f;
  font-size: 15px;
  font-weight: 600;
  text-align: left;
}

.pub-row:hover {
  background: #f5f5f5;
}

.nav-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-item {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
  width: 100%;
  height: 44px;
  padding: 0 16px;
  border-radius: 8px;
  color: #666;
  font-size: 14px;
  font-weight: 400;
  line-height: 1;
}

.nav-item :deep(svg) {
  display: block;
  flex-shrink: 0;
}

.is-collapsed .nav-item {
  width: 40px;
  justify-content: center;
  padding: 0;
}

.nav-item:hover,
.nav-item.is-on,
.sub-item:hover,
.sub-item.is-on {
  background: #eee;
  color: #1f1f1f;
}

.nav-item .chev {
  margin-left: auto;
  color: #bbb;
  transition: transform 180ms ease;
}

.user .chev {
  margin-left: auto;
  color: #bbb;
  transition: transform 180ms ease;
}

.nav-item .chev.is-open,
.user .chev.is-open {
  transform: rotate(180deg);
}

.sub {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 2px 8px 8px 48px;
}

.sub-item {
  height: 34px;
  padding: 0 10px;
  border-radius: 10px;
  color: #555;
  font-size: 14px;
  text-align: left;
}

.sub-item.is-on {
  color: #1f1f1f;
  font-weight: 500;
}

.nav-fold {
  margin-top: auto;
  color: #666;
}

.main {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  min-height: 0;
  background: rgb(248, 248, 248);
}

.bar {
  overflow: visible;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  height: 52px;
  padding: 0 20px 0 0;
  background: rgb(248, 248, 248);
}

.bar-tools {
  position: relative;
  overflow: visible;
  display: flex;
  align-items: center;
  gap: 4px;
}

.user-wrap {
  position: relative;
}

.user {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 8px 0 4px;
  border-radius: 18px;
}

.user:hover {
  background: #f2f2f2;
}

.avatar {
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #f2f2f2;
  color: #555;
  font-size: 12px;
  font-weight: 600;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.name {
  max-width: 120px;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 20;
  min-width: 140px;
  padding: 8px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #eee;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.16);
}

.menu-item {
  display: block;
  width: 100%;
  height: 36px;
  padding: 0 8px;
  border-radius: 8px;
  text-align: left;
  font-size: 13px;
}

.menu-item:hover {
  background: #f2f2f2;
}
</style>
