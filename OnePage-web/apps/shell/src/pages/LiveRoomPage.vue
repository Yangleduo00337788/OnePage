<!-- 独立直播间：学小红书左右分栏（黑底）。不做礼物/连麦。GET /live/sessions/{id}。 -->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError, type LiveSession, type UserProfile } from '@onepage/api-client'
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
const hostOpen = ref(false)
const reportOpen = ref(false)
const webFs = ref(false)
let hostTimer = 0

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

async function loadWatch(id: string) {
  failed.value = ''
  following.value = false
  host.value = null
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

function onSay() {
  /* 聊天未接，先占布局 */
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
        </div>
      </div>
      <aside v-show="!webFs" class="live-right">
        <p class="live-online">在线人数 · {{ formatCount(watching.viewerCount) }}</p>
        <div class="live-intro">
          <p class="live-intro-k">直播介绍</p>
          <h1>{{ watching.title }}</h1>
          <p class="live-intro-b">
            <span class="live-notice" aria-hidden="true"><Icon name="megaphone" :size="12" /></span>
            欢迎来到直播间。请理性发言，遵守社区规范。
          </p>
        </div>
        <div class="live-chat">
          <p class="live-chat-empty">还没有人发言</p>
        </div>
        <form class="live-say" @submit.prevent="onSay">
          <input v-model="draft" type="text" maxlength="200" placeholder="说点什么…" disabled />
        </form>
      </aside>
    </template>
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
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #c8c8c8;
}

.live-notice {
  display: flex;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  margin-top: 1px;
  border-radius: 6px;
  background: #c47a1a;
  color: #fff;
}

.live-chat {
  flex: 1;
  min-height: 0;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #2a2a2a;
  overflow: auto;
}

.live-chat-empty {
  margin: 12px 0 0;
  font-size: 13px;
  color: #6a6a6a;
}

.live-say {
  margin-top: 12px;
}

.live-say input {
  width: 100%;
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
