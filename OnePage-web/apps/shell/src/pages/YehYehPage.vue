<!--
  耶耶首页 + 对话。无顶搜、左上圆形展开、白底输入卡。
  AI 未接入，发送会明确告知尚未可用；历史仍在本机 localStorage。
-->
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '../components/Icon.vue'
import { useYehYeh, type YehSession } from '../stores/yehyeh'

const PRESET = '帮我发现今天值得一看的生活方式笔记'
const QUESTIONS = [
  '周末去哪玩比较合适？',
  '帮我搭配一套通勤穿搭',
  '最近有哪些值得看的生活方式笔记？',
]

const route = useRoute()
const router = useRouter()
const yeh = useYehYeh()

const sidebarOpen = ref(false)
const plusOpen = ref(false)
const composerOpen = ref(false)
const draft = ref(PRESET)
const chatDraft = ref('')
const sending = ref(false)
const plusRoot = ref<HTMLElement | null>(null)
const chatPlusRoot = ref<HTMLElement | null>(null)
const composerRoot = ref<HTMLElement | null>(null)
const chatLog = ref<HTMLElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const pendingImages = ref<string[]>([])
const consumedQuery = ref('')

const inChat = computed(() => Boolean(yeh.current && yeh.current.messages.length))
const usingPreset = computed(() => draft.value.trim() === PRESET || !draft.value.trim())
const homeReady = computed(() => Boolean(draft.value.trim()) && draft.value.trim() !== PRESET)
const showSuggests = computed(() => composerOpen.value && usingPreset.value)

const greeting = computed(() => {
  const h = new Date().getHours()
  const hi = h < 5 ? '晚上好' : h < 11 ? '早上好' : h < 14 ? '中午好' : h < 18 ? '下午好' : '晚上好'
  return `${hi}，想探索些什么？`
})

const groupedHistory = computed(() => {
  const now = new Date()
  const startToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const startMonth = new Date(now.getFullYear(), now.getMonth(), 1).getTime()
  const buckets: { label: string; items: YehSession[] }[] = [
    { label: '今天', items: [] },
    { label: '本月', items: [] },
    { label: '更早', items: [] },
  ]
  for (const item of yeh.sessions) {
    if (item.updatedAt >= startToday) buckets[0].items.push(item)
    else if (item.updatedAt >= startMonth) buckets[1].items.push(item)
    else buckets[2].items.push(item)
  }
  return buckets.filter((b) => b.items.length)
})

function unavailableReply() {
  return '耶耶还没接上，暂时不能回答。'
}

function payload(raw: string) {
  const text = raw.trim() || PRESET
  if (!pendingImages.value.length) return text
  const pics = pendingImages.value.join('、')
  pendingImages.value = []
  return `${text}（附图：${pics}）`
}

async function send(raw: string) {
  const text = payload(raw)
  sending.value = true
  composerOpen.value = false
  plusOpen.value = false
  draft.value = PRESET
  chatDraft.value = ''
  yeh.append(text, unavailableReply())
  if (route.query.q) router.replace({ path: '/yehyeh' })
  await nextTick()
  chatLog.value?.scrollTo({ top: chatLog.value.scrollHeight, behavior: 'smooth' })
  sending.value = false
}

function askHome() {
  send(draft.value.trim() || PRESET)
}

function askChat() {
  const t = chatDraft.value.trim()
  if (!t) return
  send(t)
}

function pickQuestion(q: string) {
  send(q)
}

function newSession() {
  yeh.startFresh()
  draft.value = PRESET
  chatDraft.value = ''
  composerOpen.value = false
  plusOpen.value = false
  pendingImages.value = []
}

function onPlusPick(kind: 'image' | 'fav' | 'like') {
  plusOpen.value = false
  if (kind === 'image') {
    fileInput.value?.click()
    return
  }
  send(kind === 'fav' ? '根据我收藏过的笔记，再推荐一些类似的内容' : '根据我点赞过的笔记，帮我找类似风格')
}

function onFiles(e: Event) {
  const input = e.target as HTMLInputElement
  const names = [...(input.files ?? [])].map((f) => f.name)
  input.value = ''
  if (!names.length) return
  pendingImages.value = [...pendingImages.value, ...names].slice(0, 4)
}

function onDocPointer(e: PointerEvent) {
  const t = e.target as Node
  if (plusOpen.value) {
    const inPlus = plusRoot.value?.contains(t) || chatPlusRoot.value?.contains(t)
    if (!inPlus) plusOpen.value = false
  }
  if (composerOpen.value && composerRoot.value && !composerRoot.value.contains(t)) {
    composerOpen.value = false
  }
}

onMounted(() => document.addEventListener('pointerdown', onDocPointer))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onDocPointer))

watch(
  () => route.query.q,
  (q) => {
    const topic = typeof q === 'string' ? q.trim() : ''
    if (!topic || topic === consumedQuery.value) return
    consumedQuery.value = topic
    send(topic)
  },
  { immediate: true },
)
</script>

<template>
  <section class="yeh-page">
    <input ref="fileInput" class="hidden" type="file" accept="image/*" multiple @change="onFiles" />

    <aside class="yeh-rail" :class="sidebarOpen ? 'is-open' : ''">
      <div class="yeh-rail-inner">
        <button type="button" class="yeh-new" @click="newSession">
          <Icon name="square-pen" :size="18" />
          新建会话
        </button>
        <div class="yeh-rail-line" />
        <div class="yeh-history">
          <p v-if="!yeh.sessions.length" class="yeh-history-empty">还没有会话</p>
          <template v-for="group in groupedHistory" :key="group.label">
            <p class="yeh-rail-label">{{ group.label }}</p>
            <div
              v-for="item in group.items"
              :key="item.id"
              class="yeh-history-item"
              :class="item.id === yeh.currentId ? 'is-on' : ''"
            >
              <button type="button" class="yeh-history-title" @click="yeh.open(item.id)">{{ item.title }}</button>
              <button type="button" class="yeh-history-del" aria-label="删除会话" @click.stop="yeh.remove(item.id)">
                <Icon name="trash-2" :size="14" />
              </button>
            </div>
          </template>
          <p v-if="yeh.sessions.length" class="yeh-history-end">没有更多了</p>
        </div>
      </div>
    </aside>

    <div class="yeh-main">
      <div class="yeh-toolbar">
        <button
          type="button"
          class="yeh-icon-btn"
          :class="sidebarOpen ? 'is-on' : ''"
          aria-label="展开会话侧栏"
          @click="sidebarOpen = !sidebarOpen"
        >
          <Icon name="panel-left" :size="20" />
        </button>
        <button v-if="inChat" type="button" class="yeh-icon-btn" aria-label="新建会话" @click="newSession">
          <Icon name="square-pen" :size="18" />
        </button>
      </div>

      <template v-if="!inChat">
        <div class="yeh-home">
          <div class="yeh-home-stack">
            <div class="yeh-hello">
              <Icon name="message-circle-heart" :size="24" :stroke="1.75" />
              <h1>{{ greeting }}</h1>
            </div>
            <div class="yeh-composer-slot">
              <div ref="composerRoot" class="yeh-composer" :class="{ 'is-open': showSuggests, 'is-ready': homeReady }">
                <textarea
                  v-model="draft"
                  class="yeh-textarea"
                  :class="usingPreset ? 'is-preset' : ''"
                  rows="3"
                  @focus="composerOpen = true"
                />
                <div class="yeh-composer-bar">
                  <div ref="plusRoot" class="relative">
                    <button type="button" class="yeh-plus" aria-label="更多" @click="plusOpen = !plusOpen">
                      <Icon name="plus" :size="18" />
                    </button>
                    <div v-show="plusOpen" class="yeh-plus-menu">
                      <button type="button" @click="onPlusPick('image')">
                        <Icon name="image" :size="20" />
                        上传图片
                      </button>
                      <button type="button" @click="onPlusPick('fav')">
                        <Icon name="bookmark" :size="20" />
                        我的收藏
                      </button>
                      <button type="button" @click="onPlusPick('like')">
                        <Icon name="heart" :size="20" />
                        我的点赞
                      </button>
                    </div>
                  </div>
                  <button type="button" class="yeh-ask" :class="homeReady ? 'is-ready' : ''" :disabled="sending" @click="askHome">
                    <Icon :name="homeReady ? 'arrow-right' : 'message-circle-heart'" :size="14" :stroke="2" />
                    问耶耶
                  </button>
                </div>
                <p v-if="pendingImages.length" class="yeh-attach">已选 {{ pendingImages.join('、') }}</p>
                <div class="yeh-expand">
                  <div class="yeh-expand-inner">
                    <div class="yeh-composer-line" />
                    <div class="yeh-suggests">
                      <button v-for="q in QUESTIONS" :key="q" type="button" @mousedown.prevent="pickQuestion(q)">
                        <Icon name="spark" :size="14" />
                        <span>{{ q }}</span>
                        <Icon name="arrow-right" :size="14" class="yeh-suggest-arrow" />
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>

      <template v-else>
        <header class="yeh-chat-head">
          <h1>{{ yeh.current?.title }}</h1>
        </header>
        <div ref="chatLog" class="yeh-chat-log">
          <div v-for="(msg, i) in yeh.current?.messages" :key="i" class="yeh-msg" :class="msg.role === 'user' ? 'is-user' : 'is-ai'">
            <div v-if="msg.role === 'user'" class="yeh-bubble">{{ msg.text }}</div>
            <p v-else class="yeh-ai-text">{{ msg.text }}</p>
          </div>
        </div>
        <div class="yeh-chat-foot">
          <div class="yeh-composer yeh-composer-chat">
            <textarea
              v-model="chatDraft"
              class="yeh-textarea yeh-textarea-chat"
              rows="1"
              placeholder="搜索或者输入任何问题"
              @keydown.enter.exact.prevent="askChat"
            />
            <div class="yeh-composer-bar">
              <div ref="chatPlusRoot" class="relative">
                <button type="button" class="yeh-plus" aria-label="更多" @click="plusOpen = !plusOpen">
                  <Icon name="plus" :size="18" />
                </button>
                <div v-show="plusOpen" class="yeh-plus-menu yeh-plus-menu-up">
                  <button type="button" @click="onPlusPick('image')">
                    <Icon name="image" :size="20" />
                    上传图片
                  </button>
                  <button type="button" @click="onPlusPick('fav')">
                    <Icon name="bookmark" :size="20" />
                    我的收藏
                  </button>
                  <button type="button" @click="onPlusPick('like')">
                    <Icon name="heart" :size="20" />
                    我的点赞
                  </button>
                </div>
              </div>
              <button
                type="button"
                class="yeh-send"
                :class="chatDraft.trim() ? 'is-on' : ''"
                :disabled="sending || !chatDraft.trim()"
                aria-label="发送"
                @click="askChat"
              >
                <Icon name="arrow-up" :size="16" :stroke="2.2" />
              </button>
            </div>
          </div>
          <p class="yeh-disclaimer">内容由AI生成，请注意甄别！</p>
        </div>
      </template>
    </div>
  </section>
</template>
