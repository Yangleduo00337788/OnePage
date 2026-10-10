<!--
  私信：左会话列表，点开右侧对话。P0 本地占位，未接 WS。
-->
<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '../components/Icon.vue'
import { avatarOf } from '../data/home'
import { IM_THREADS, type ImThread } from '../data/im'

const route = useRoute()
const router = useRouter()
const threads = ref<ImThread[]>(IM_THREADS.map((t) => ({ ...t, messages: t.messages.map((m) => ({ ...m })) })))
const draft = ref('')
const log = ref<HTMLElement | null>(null)

const currentId = computed(() => (typeof route.query.id === 'string' ? route.query.id : ''))
const current = computed(() => threads.value.find((t) => t.id === currentId.value) ?? null)

function open(id: string) {
  const row = threads.value.find((t) => t.id === id)
  if (row) row.unread = 0
  router.replace({ query: { id } })
}

function nowLabel() {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function send() {
  const text = draft.value.trim()
  const row = current.value
  if (!text || !row) return
  row.messages.push({ id: `${Date.now()}`, side: 'me', text, time: nowLabel() })
  row.preview = text
  row.time = '刚刚'
  draft.value = ''
  nextTick(() => log.value?.scrollTo({ top: log.value.scrollHeight }))
}

function ensureThread() {
  const id = currentId.value
  if (!id || threads.value.some((item) => item.id === id)) return
  const name = typeof route.query.name === 'string' && route.query.name ? route.query.name : '私信'
  threads.value.unshift({
    id,
    name,
    avatar: avatarOf(name),
    preview: '还没有消息',
    time: '刚刚',
    unread: 0,
    messages: [],
  })
}

watch(
  () => [route.query.id, route.query.name],
  () => {
    ensureThread()
  },
  { immediate: true },
)

watch(currentId, async () => {
  await nextTick()
  log.value?.scrollTo({ top: log.value.scrollHeight })
})
</script>

<template>
  <section class="im-page">
    <aside class="im-list">
      <div class="im-list-head">消息</div>
      <button
        v-for="item in threads"
        :key="item.id"
        type="button"
        class="im-item"
        :class="item.id === currentId ? 'is-on' : ''"
        @click="open(item.id)"
      >
        <span class="im-avatar">
          <img :src="item.avatar" :alt="item.name" />
          <span v-if="item.unread" class="im-badge">{{ item.unread > 9 ? '9+' : item.unread }}</span>
        </span>
        <span class="min-w-0 flex-1 text-left">
          <span class="flex items-center justify-between gap-2">
            <span class="truncate text-[14px] font-medium text-[#1f1f1f]">{{ item.name }}</span>
            <span class="shrink-0 text-[11px] text-[#bbb]">{{ item.time }}</span>
          </span>
          <span class="mt-0.5 block truncate text-[13px] text-[#8a8a8a]">{{ item.preview }}</span>
        </span>
      </button>
    </aside>

    <div class="im-thread">
      <template v-if="current">
        <header class="im-thread-head">{{ current.name }}</header>
        <div ref="log" class="im-log">
          <div v-for="msg in current.messages" :key="msg.id" class="im-msg" :class="msg.side === 'me' ? 'is-me' : 'is-them'">
            <p class="im-bubble">{{ msg.text }}</p>
            <span class="im-time">{{ msg.time }}</span>
          </div>
        </div>
        <form class="im-composer" @submit.prevent="send">
          <input v-model="draft" class="im-input" placeholder="发消息…" />
          <button class="im-send" type="submit" :disabled="!draft.trim()">
            <Icon name="arrow-up" :size="16" :stroke="2.2" />
          </button>
        </form>
      </template>
      <div v-else class="im-empty">
        <Icon name="message-circle-more" :size="36" :stroke="1.5" />
        <p>选择左侧对话，开始聊天</p>
      </div>
    </div>
  </section>
</template>
