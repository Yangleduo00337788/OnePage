<!-- 运营后台：左栏导航、右上头像退出。不是创作者中心。 -->
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api, type UserProfile } from '@onepage/api-client'
import Icon from './Icon.vue'
import BrandLogo from './BrandLogo.vue'
import OpsLogin from './OpsLogin.vue'
import OpsHome from './OpsHome.vue'
import OpsReports from './OpsReports.vue'
import OpsPlaceholder from './OpsPlaceholder.vue'

export type PageId = 'home' | 'reports' | 'users'

const page = ref<PageId>('home')
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
}

function setCollapsed(next: boolean) {
  collapsed.value = next
  localStorage.setItem('onepage.ops.navCollapsed', next ? '1' : '0')
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
          :title="collapsed ? '首页' : undefined"
          @click="openPage('home')"
        >
          <Icon name="house" :size="20" />
          <span v-if="!collapsed">首页</span>
        </button>
        <button
          type="button"
          class="nav-item"
          :class="{ 'is-on': page === 'reports' }"
          :title="collapsed ? '举报工单' : undefined"
          @click="openPage('reports')"
        >
          <Icon name="flag" :size="20" />
          <span v-if="!collapsed">举报工单</span>
        </button>
        <button
          type="button"
          class="nav-item"
          :class="{ 'is-on': page === 'users' }"
          :title="collapsed ? '用户处置' : undefined"
          @click="openPage('users')"
        >
          <Icon name="users" :size="20" />
          <span v-if="!collapsed">用户处置</span>
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
      <OpsPlaceholder
        v-else
        title="用户处置尚未接入"
        desc="封禁账号要等治理服务调 user。先在举报工单里处理。"
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
.nav-item.is-on {
  background: #eee;
  color: #1f1f1f;
}

.user .chev {
  margin-left: auto;
  color: #bbb;
  transition: transform 180ms ease;
}

.user .chev.is-open {
  transform: rotate(180deg);
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
