<!--
  他人个人主页。资料走 GET /users/{pageId}；笔记走 GET /users/{pageId}/posts。
  自己的资料仍走 /me。
-->
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api, ApiError, type UserProfile } from '@onepage/api-client'
import Icon from '../components/Icon.vue'
import ProfileNoteList from '../components/ProfileNoteList.vue'
import { formatCount } from '../data/home'
import { useSession } from '../stores/session'

const route = useRoute()
const router = useRouter()
const session = useSession()
const moreOpen = ref(false)
const moreWrap = ref<HTMLElement | null>(null)
const toast = ref('')
const loading = ref(true)
const failed = ref('')
const profile = ref<UserProfile | null>(null)

const pageId = computed(() => String(route.params.pageId || ''))
const mine = computed(() => Boolean(session.me && session.me.pageId === pageId.value))

const likes = computed(() => {
  const s = profile.value?.stats
  if (!s) return '0'
  return formatCount((s.likeReceivedCount ?? 0) + (s.favoriteReceivedCount ?? 0))
})

async function load() {
  loading.value = true
  failed.value = ''
  profile.value = null
  if (!session.loaded) await session.refresh()
  if (!pageId.value) {
    failed.value = 'missing'
    loading.value = false
    return
  }
  if (session.me?.pageId === pageId.value) {
    await router.replace('/me')
    return
  }
  try {
    profile.value = await api.userByPageId(pageId.value)
  } catch (e) {
    failed.value =
      e instanceof ApiError && (e.status === 404 || e.code === 'NOT_FOUND') ? 'missing' : 'error'
  } finally {
    loading.value = false
  }
}

function goMessage() {
  void router.push('/messages')
}

function followSoon() {
  toast.value = '关注尚未接入'
  window.setTimeout(() => {
    if (toast.value === '关注尚未接入') toast.value = ''
  }, 1600)
}

function report() {
  moreOpen.value = false
  toast.value = '举报尚未接入'
  window.setTimeout(() => {
    if (toast.value === '举报尚未接入') toast.value = ''
  }, 1600)
}

function onDocClick(e: MouseEvent) {
  if (!moreWrap.value?.contains(e.target as Node)) moreOpen.value = false
}

watch(moreOpen, async (on) => {
  if (on) {
    await nextTick()
    document.addEventListener('click', onDocClick)
  } else document.removeEventListener('click', onDocClick)
})

watch(pageId, () => {
  void load()
})

onMounted(() => {
  void load()
})

onUnmounted(() => document.removeEventListener('click', onDocClick))
</script>

<template>
  <section v-if="loading" class="flex min-h-[60vh] items-center justify-center pb-16 text-[14px] text-[#8a8a8a]">
    加载中…
  </section>

  <section v-else-if="failed === 'error'" class="flex min-h-[60vh] flex-col items-center justify-center pb-16 text-center">
    <p class="text-[16px] font-medium">加载失败</p>
    <button type="button" class="me-empty-go mt-6" @click="load">重试</button>
  </section>

  <section
    v-else-if="failed === 'missing' || !profile"
    class="flex min-h-[60vh] flex-col items-center justify-center pb-16 text-center"
  >
    <p class="text-[16px] font-medium">没有这个人</p>
    <p class="mt-2 text-[13px] text-[#8a8a8a]">一页号 {{ pageId }} 不存在</p>
    <RouterLink to="/" class="me-empty-go mt-6">回首页</RouterLink>
  </section>

  <section v-else class="-mx-8 -mt-1 pb-16">
    <div class="relative h-[240px] w-full overflow-hidden bg-[#fafafa]">
      <img v-if="profile.coverUrl" :src="profile.coverUrl" alt="" class="h-full w-full object-cover" />
      <div v-else class="relative h-full w-full overflow-hidden bg-[#fafafa]">
        <span class="pointer-events-none absolute -top-10 -left-8 h-48 w-48 rounded-full bg-[#ff2442]/[0.07]" />
        <span class="pointer-events-none absolute right-[-24px] bottom-[-28px] h-44 w-44 rounded-full bg-[#ff2442]/[0.05]" />
      </div>
    </div>

    <div class="relative px-8">
      <div class="absolute top-10 right-8 z-10 flex items-center gap-2">
        <button v-if="!mine" type="button" class="follow-btn" @click="followSoon">关注</button>
        <button type="button" class="user-round" aria-label="发消息" @click="goMessage">
          <Icon name="message-circle" :size="18" />
        </button>
        <div ref="moreWrap" class="relative">
          <button
            type="button"
            class="user-round"
            aria-label="更多"
            @click.stop="moreOpen = !moreOpen"
          >
            <Icon name="ellipsis" :size="18" />
          </button>
          <div
            v-if="moreOpen"
            class="absolute right-0 top-full z-10 mt-1 min-w-[108px] rounded-[12px] bg-white py-1 shadow-[0_8px_28px_rgba(0,0,0,0.12)] ring-1 ring-[#eee]"
          >
            <button
              type="button"
              class="block w-full px-3 py-2 text-left text-[13px] hover:bg-[#f7f7f7]"
              @click="report"
            >
              举报
            </button>
          </div>
        </div>
      </div>
      <div class="flex justify-center">
        <div class="relative -mt-12 h-24 w-24 overflow-hidden rounded-full bg-[#f3f3f3] ring-4 ring-white">
          <img v-if="profile.avatarUrl" :src="profile.avatarUrl" class="h-full w-full object-cover" alt="" />
          <span v-else class="flex h-full items-center justify-center text-[28px] text-[#c4c4c4]">
            {{ (profile.nickname || '一').slice(0, 1) }}
          </span>
        </div>
      </div>

      <div class="mx-auto mt-4 max-w-[560px] text-center">
        <h1 class="mx-auto flex max-w-full items-center justify-center gap-1.5 text-[22px] font-semibold tracking-tight">
          <span class="truncate">{{ profile.nickname }}</span>
          <Icon
            v-if="profile.gender === 1"
            name="mars"
            :size="16"
            :stroke="2"
            class="shrink-0 text-[#4a90d9]"
          />
          <Icon
            v-else-if="profile.gender === 2"
            name="venus"
            :size="16"
            :stroke="2"
            class="shrink-0 text-[#ff2442]"
          />
        </h1>
        <p class="mt-1 text-[13px] text-[#8a8a8a]">一页号：{{ profile.pageId }}</p>
        <p class="mt-3 text-[14px] leading-6 text-[#555]">
          签名：{{ profile.bio?.trim() ? profile.bio : '暂无' }}
        </p>
        <p class="mt-4 text-[14px] text-[#8a8a8a]">
          <span class="font-semibold tabular-nums text-[#1f1f1f]">{{ profile.stats.followingCount }}</span>
          关注
          <span class="mx-3 text-[#eee]">|</span>
          <span class="font-semibold tabular-nums text-[#1f1f1f]">{{ profile.stats.followerCount }}</span>
          粉丝
          <span class="mx-3 text-[#eee]">|</span>
          <span class="font-semibold tabular-nums text-[#1f1f1f]">{{ likes }}</span>
          获赞与收藏
        </p>
      </div>
    </div>

    <div class="mt-8 border-t border-[#ececec]">
      <div class="flex items-center justify-center gap-2 pt-5">
        <button type="button" class="me-tab is-on">笔记</button>
      </div>
      <ProfileNoteList
        :page-id="profile.pageId"
        empty-title="还没有公开笔记"
        empty-desc="这里会展示已公开的笔记。"
      >
        <template #icon>
          <Icon name="sticky-note" :size="28" :stroke="1.6" />
        </template>
      </ProfileNoteList>
    </div>

    <p
      v-if="toast"
      class="fixed bottom-8 left-1/2 z-40 -translate-x-1/2 rounded-full bg-[#1f1f1f] px-4 py-2 text-[13px] text-white"
    >
      {{ toast }}
    </p>
  </section>
</template>

<style scoped>
.follow-btn {
  display: inline-flex;
  height: 36px;
  align-items: center;
  justify-content: center;
  padding: 0 18px;
  border-radius: 20px;
  background: #ff2442;
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
}

.user-round {
  display: inline-flex;
  height: 36px;
  width: 36px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #1f1f1f;
}

.user-round:hover {
  background: #f2f2f2;
}
</style>
