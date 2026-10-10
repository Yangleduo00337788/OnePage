<!--
  他人个人主页。P0 用笔记作者名映射一页号，资料与帖子来自占位数据。
  自己的资料仍走 /me。
-->
<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import Icon from '../components/Icon.vue'
import { useNoteDetail } from '../stores/noteDetail'
import { IM_THREADS } from '../data/im'
import {
  authorBioOf,
  authorGenderOf,
  authorOfPageId,
  authorStatsOf,
  avatarOf,
  coverOf,
  pageIdOf,
  postsByAuthor,
} from '../data/home'

const route = useRoute()
const router = useRouter()
const noteDetail = useNoteDetail()
const followed = ref(false)
const moreOpen = ref(false)
const moreWrap = ref<HTMLElement | null>(null)
const toast = ref('')

const pageId = computed(() => String(route.params.pageId || ''))
const author = computed(() => authorOfPageId(pageId.value))
const bio = computed(() => authorBioOf(author.value))
const gender = computed(() => authorGenderOf(author.value))
const stats = computed(() => authorStatsOf(author.value))
const avatar = computed(() => avatarOf(author.value || pageId.value))
const cover = computed(() => coverOf(author.value || pageId.value))
const notes = computed(() => (author.value ? postsByAuthor(author.value) : []))

function goMessage() {
  const name = author.value
  if (!name) return
  const hit = IM_THREADS.find((item) => item.name === name)
  void router.push({
    path: '/messages',
    query: { id: hit?.id || `u-${pageId.value}`, name },
  })
}

function report() {
  moreOpen.value = false
  toast.value = '已提交举报'
  window.setTimeout(() => {
    if (toast.value === '已提交举报') toast.value = ''
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

onUnmounted(() => document.removeEventListener('click', onDocClick))
</script>

<template>
  <section v-if="!author" class="flex min-h-[60vh] flex-col items-center justify-center pb-16 text-center">
    <p class="text-[16px] font-medium">没有这个人</p>
    <p class="mt-2 text-[13px] text-[#8a8a8a]">一页号 {{ pageId }} 对不上现有作者</p>
    <RouterLink to="/" class="me-empty-go mt-6">回首页</RouterLink>
  </section>

  <section v-else class="-mx-8 -mt-1 pb-16">
    <div class="relative h-[240px] w-full overflow-hidden bg-[#fafafa]">
      <img :src="cover" alt="" class="h-full w-full object-cover" />
    </div>

    <div class="relative px-8">
      <div class="absolute top-10 right-8 z-10 flex items-center gap-2">
        <button
          type="button"
          class="follow-btn"
          :class="followed ? 'is-on' : ''"
          @click="followed = !followed"
        >
          {{ followed ? '已关注' : '关注' }}
        </button>
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
          <img :src="avatar" class="h-full w-full object-cover" alt="" />
        </div>
      </div>

      <div class="mx-auto mt-4 max-w-[560px] text-center">
        <h1 class="mx-auto flex max-w-full items-center justify-center gap-1.5 text-[22px] font-semibold tracking-tight">
          <span class="truncate">{{ author }}</span>
          <Icon
            v-if="gender === 1"
            name="mars"
            :size="16"
            :stroke="2"
            class="shrink-0 text-[#4a90d9]"
          />
          <Icon
            v-else-if="gender === 2"
            name="venus"
            :size="16"
            :stroke="2"
            class="shrink-0 text-[#ff2442]"
          />
        </h1>
        <p class="mt-1 text-[13px] text-[#8a8a8a]">一页号：{{ pageIdOf(author) }}</p>
        <p class="mt-3 text-[14px] leading-6 text-[#555]">签名：{{ bio }}</p>
        <p class="mt-4 text-[14px] text-[#8a8a8a]">
          <span class="font-semibold tabular-nums text-[#1f1f1f]">{{ stats.following }}</span>
          关注
          <span class="mx-3 text-[#eee]">|</span>
          <span class="font-semibold tabular-nums text-[#1f1f1f]">{{ stats.fans }}</span>
          粉丝
          <span class="mx-3 text-[#eee]">|</span>
          <span class="font-semibold tabular-nums text-[#1f1f1f]">{{ stats.likes }}</span>
          获赞与收藏
        </p>
      </div>
    </div>

    <div class="mt-8 border-t border-[#ececec]">
      <div class="flex items-center justify-center gap-2 pt-5">
        <button type="button" class="me-tab is-on">笔记</button>
      </div>
      <div v-if="notes.length" class="flex flex-wrap justify-center gap-4 px-8 pt-6">
        <article
          v-for="note in notes"
          :key="note.id"
          :data-note-id="note.id"
          class="flex h-[440px] w-[280px] cursor-pointer flex-col"
          @click="noteDetail.show(note, $event.currentTarget)"
        >
          <div class="group relative min-h-0 flex-1 overflow-hidden rounded-2xl bg-[#ececec]">
            <img :src="note.image" :alt="note.title" class="h-full w-full object-cover" />
            <span class="pointer-events-none absolute inset-0 bg-black/0 group-hover:bg-black/25" />
            <span v-if="note.video" class="absolute top-2.5 right-2.5 z-[1] text-white drop-shadow">
              <svg viewBox="0 0 24 24" width="28" height="28" fill="currentColor" aria-hidden="true">
                <path d="M8 5.5v13l11-6.5z" />
              </svg>
            </span>
          </div>
          <h2 class="mt-2 shrink-0 line-clamp-2 text-[14px] leading-5">{{ note.title }}</h2>
          <div class="mt-2 flex shrink-0 items-center justify-between text-[12px] text-[#8a8a8a]">
            <span class="flex min-w-0 items-center gap-1.5">
              <span class="h-5 w-5 shrink-0 overflow-hidden rounded-full bg-[#d9d9d9]">
                <img :src="avatar" alt="" class="h-full w-full object-cover" />
              </span>
              <span class="truncate">{{ note.author }}</span>
            </span>
            <span class="flex items-center gap-1">
              <Icon name="heart" :size="13" />
              {{ note.likes }}
            </span>
          </div>
        </article>
      </div>
      <div v-else class="me-empty">
        <span class="me-empty-ico">
          <Icon name="sticky-note" :size="28" :stroke="1.6" />
        </span>
        <p class="mt-4 text-[16px] font-medium text-[#1f1f1f]">还没有公开笔记</p>
        <p class="mt-2 max-w-[280px] text-[13px] leading-6 text-[#8a8a8a]">TA 的生活还没有记成一页。</p>
      </div>
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

.follow-btn.is-on {
  background: #f2f2f2;
  color: #555;
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
