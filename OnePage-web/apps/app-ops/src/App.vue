<!-- 运营后台：左栏导航、右上头像退出。不是创作者中心。 -->
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api, type UserProfile } from '@onepage/api-client'
import Icon from './Icon.vue'
import BrandLogo from './BrandLogo.vue'
import OpsLogin from './OpsLogin.vue'
import OpsHome from './OpsHome.vue'
import OpsReports from './OpsReports.vue'
import OpsUsers from './OpsUsers.vue'
import OpsPosts from './OpsPosts.vue'
import OpsComments from './OpsComments.vue'
import OpsCategories from './OpsCategories.vue'
import OpsCampaigns from './OpsCampaigns.vue'
import OpsTopics from './OpsTopics.vue'
import OpsMessages from './OpsMessages.vue'
import OpsMedia from './OpsMedia.vue'
import OpsAi from './OpsAi.vue'

export type PageId =
  | 'home'
  | 'reports'
  | 'users'
  | 'im'
  | 'posts'
  | 'comments'
  | 'media'
  | 'categories'
  | 'campaigns'
  | 'topics'
  | 'ai'

type NavItem = { id: PageId; label: string; icon: string }
type NavGroup = { id: string; label: string; icon: string; children: NavItem[] }

const groups: NavGroup[] = [
  {
    id: 'gov',
    label: '治理',
    icon: 'flag',
    children: [
      { id: 'reports', label: '工单', icon: 'flag' },
      { id: 'users', label: '用户', icon: 'users' },
      { id: 'im', label: '私信', icon: 'message-square' },
    ],
  },
  {
    id: 'content',
    label: '内容',
    icon: 'layout-grid',
    children: [
      { id: 'posts', label: '笔记', icon: 'sticky-note' },
      { id: 'comments', label: '评论', icon: 'message-square' },
      { id: 'media', label: '媒体库', icon: 'image' },
      { id: 'categories', label: '分类', icon: 'layout-grid' },
      { id: 'campaigns', label: '活动', icon: 'calendar' },
      { id: 'topics', label: '话题', icon: 'hash' },
    ],
  },
  {
    id: 'ai',
    label: 'AI',
    icon: 'spark',
    children: [{ id: 'ai', label: '配置', icon: 'spark' }],
  },
]

const page = ref<PageId>('home')
const openGroups = ref<Record<string, boolean>>({ gov: true, content: true, ai: true })
const collapsed = ref(localStorage.getItem('onepage.ops.navCollapsed') === '1')
const me = ref<UserProfile | null>(null)
const ready = ref(false)
const menuOpen = ref(false)
const userWrap = ref<HTMLElement | null>(null)

const initial = computed(() => (me.value?.nickname || '运营').slice(0, 1))
const nickname = computed(() => me.value?.nickname || '未登录')

onMounted(async () => {
  document.addEventListener('click', onDocClick)
  try {
    await api.opsMe()
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

function openPage(id: PageId) {
  page.value = id
  for (const group of groups) {
    if (group.children.some((item) => item.id === id)) {
      openGroups.value[group.id] = true
    }
  }
}

function groupActive(group: NavGroup) {
  return group.children.some((item) => item.id === page.value)
}

function toggleGroup(id: string) {
  if (collapsed.value) {
    setCollapsed(false)
    openGroups.value[id] = true
    return
  }
  openGroups.value[id] = !openGroups.value[id]
}

function setCollapsed(next: boolean) {
  collapsed.value = next
  localStorage.setItem('onepage.ops.navCollapsed', next ? '1' : '0')
}

async function logout() {
  menuOpen.value = false
  try {
    await api.logout('ops')
  } catch {
    /* 网关已清 Cookie 也继续 */
  }
  me.value = null
}

function onLoggedIn(profile: UserProfile) {
  me.value = profile
}
</script>

<template>
  <OpsLogin v-if="ready && !me" @done="onLoggedIn" />
  <div v-else-if="ready" class="admin" :class="{ 'is-collapsed': collapsed }">
    <aside class="nav">
      <div class="nav-mark">
        <BrandLogo :mini="collapsed" />
      </div>
      <nav class="nav-list" aria-label="运营后台">
        <button
          type="button"
          class="nav-item"
          :class="{ 'is-on': page === 'home' }"
          :title="collapsed ? '工作台' : undefined"
          @click="openPage('home')"
        >
          <Icon name="house" :size="20" />
          <span v-if="!collapsed">工作台</span>
        </button>
        <div v-for="group in groups" :key="group.id" class="nav-group">
          <button
            type="button"
            class="nav-item"
            :class="{ 'is-on': groupActive(group) }"
            :title="collapsed ? group.label : undefined"
            @click="toggleGroup(group.id)"
          >
            <Icon :name="group.icon" :size="20" />
            <span v-if="!collapsed">{{ group.label }}</span>
            <Icon
              v-if="!collapsed"
              name="chevron-down"
              :size="14"
              class="chev"
              :class="{ 'is-open': openGroups[group.id] }"
            />
          </button>
          <div v-if="!collapsed && openGroups[group.id]" class="sub">
            <button
              v-for="item in group.children"
              :key="item.id"
              type="button"
              class="sub-item"
              :class="{ 'is-on': page === item.id }"
              @click="openPage(item.id)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>
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
      </header>
      <OpsHome v-if="page === 'home'" @open="openPage($event as PageId)" />
      <OpsReports v-else-if="page === 'reports'" />
      <OpsUsers v-else-if="page === 'users'" />
      <OpsMessages v-else-if="page === 'im'" />
      <OpsPosts v-else-if="page === 'posts'" />
      <OpsComments v-else-if="page === 'comments'" />
      <OpsMedia v-else-if="page === 'media'" />
      <OpsCategories v-else-if="page === 'categories'" />
      <OpsCampaigns v-else-if="page === 'campaigns'" />
      <OpsTopics v-else-if="page === 'topics'" />
      <OpsAi v-else-if="page === 'ai'" />
    </div>
  </div>
</template>

<style>
html,
body,
#app {
  margin: 0;
  height: 100%;
  overflow: hidden;
  background: rgb(248, 248, 248);
  font-family: 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', system-ui, sans-serif;
  scrollbar-width: none;
}

* {
  scrollbar-width: none;
}

*::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
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
  height: 100%;
  overflow: hidden;
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
  margin: 0 auto 24px;
  align-items: center;
  justify-content: center;
}

.is-collapsed .nav-mark {
  margin: 0 0 24px;
}

.nav-list {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  gap: 2px;
  overflow: auto;
}

.nav-group {
  display: flex;
  flex-direction: column;
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
  text-align: left;
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
  overflow: auto;
  background: rgb(248, 248, 248);
}

.bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  height: 52px;
  padding: 0 20px 0 0;
  background: rgb(248, 248, 248);
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
  box-shadow: 0 16px 48px rgba(80, 60, 40, 0.16);
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
