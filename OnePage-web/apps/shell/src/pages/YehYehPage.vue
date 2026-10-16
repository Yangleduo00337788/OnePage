<!--
  耶耶首页 + 对话。无顶搜、左上圆形展开、白底输入卡。
  发送走 POST /ai/chat/stream（先检索再生成）；历史仍在本机 localStorage。
-->
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError, type AiCitation } from '@onepage/api-client'
import Icon from '../components/Icon.vue'
import EmptyState from '../components/EmptyState.vue'
import { useAuthModal } from '../stores/authModal'
import { useNoteDetail } from '../stores/noteDetail'
import { useYehYeh, type YehSession, type YehStage } from '../stores/yehyeh'
import { renderYehAnswer } from '../yehFormat'

const FALLBACK_PRESET = '帮我发现今天值得一看的生活方式笔记'
const FALLBACK_QUESTIONS = [
  '周末去哪玩比较合适？',
  '帮我搭配一套通勤穿搭',
  '最近有哪些值得看的生活方式笔记？',
]

const route = useRoute()
const router = useRouter()
const yeh = useYehYeh()
const auth = useAuthModal()
const noteDetail = useNoteDetail()

const preset = ref(FALLBACK_PRESET)
const questions = ref([...FALLBACK_QUESTIONS])
const editing = ref(false)
const sidebarOpen = ref(false)
const plusOpen = ref(false)
const composerOpen = ref(false)
const draft = ref('')
const chatDraft = ref('')
const sending = ref(false)
const plusRoot = ref<HTMLElement | null>(null)
const chatPlusRoot = ref<HTMLElement | null>(null)
const composerRoot = ref<HTMLElement | null>(null)
const chatLog = ref<HTMLElement | null>(null)
const homeBox = ref<HTMLTextAreaElement | null>(null)
const chatBox = ref<HTMLTextAreaElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const pendingImages = ref<string[]>([])
const consumedQuery = ref('')
const copiedAt = ref(-1)
const stage = ref<YehStage>('retrieve')
const noteCount = ref(0)
const STAGE_HOLD = 560
const stageOrder: Record<YehStage, number> = { retrieve: 0, notes: 1, write: 2 }
let stageQueue: { s: YehStage; n: number }[] = []
let stagePlaying = false
let chatAbort: AbortController | null = null

function wait(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

async function playStages() {
  if (stagePlaying) return
  stagePlaying = true
  while (stageQueue.length) {
    const item = stageQueue.shift()
    if (!item) break
    if (stageOrder[item.s] > stageOrder[stage.value]) await wait(STAGE_HOLD)
    if (stageOrder[item.s] < stageOrder[stage.value]) continue
    stage.value = item.s
    if (item.s === 'notes' || item.s === 'write') noteCount.value = item.n
  }
  stagePlaying = false
}

function enqueueStage(next: string, n: number) {
  if (next !== 'retrieve' && next !== 'notes' && next !== 'write') return
  if (next === stage.value && !stageQueue.length) {
    if (next === 'notes' || next === 'write') noteCount.value = n
    return
  }
  const last = stageQueue[stageQueue.length - 1]
  const from = last ? stageOrder[last.s] : stageOrder[stage.value]
  if (stageOrder[next] < from) return
  stageQueue.push({ s: next, n })
  void playStages()
}

async function waitStages() {
  while (stagePlaying || stageQueue.length) {
    await wait(80)
  }
}

const inChat = computed(() => Boolean(yeh.current && yeh.current.messages.length))
const homeReady = computed(() => editing.value && Boolean(draft.value.trim()))
const showSuggests = computed(() => !inChat.value && editing.value && questions.value.length > 0)

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

function payload(raw: string) {
  const text = raw.trim() || preset.value
  if (!pendingImages.value.length) return text
  const pics = pendingImages.value.join('、')
  pendingImages.value = []
  return `${text}（附图：${pics}）`
}

function fitBox(el: HTMLTextAreaElement | null, min: number, max: number) {
  if (!el) return
  el.style.height = '0px'
  const next = Math.max(min, Math.min(el.scrollHeight, max))
  el.style.height = `${next}px`
  el.style.overflowY = el.scrollHeight > max ? 'auto' : 'hidden'
}

function stopChat() {
  stageQueue = []
  stagePlaying = false
  chatAbort?.abort()
}

async function send(raw: string, reuse = false) {
  if (sending.value) return
  const text = reuse ? raw.trim() : payload(raw)
  if (!text) return
  composerOpen.value = false
  plusOpen.value = false
  editing.value = false
  draft.value = ''
  chatDraft.value = ''
  if (route.query.q) router.replace({ path: '/yehyeh' })
  if (reuse) yeh.dropLastAi()
  else yeh.appendUser(text)
  yeh.beginAi(text)
  stageQueue = []
  stagePlaying = false
  stage.value = 'retrieve'
  noteCount.value = 0
  sending.value = true
  chatAbort = new AbortController()
  await nextTick()
  fitBox(chatBox.value, 56, 200)
  chatLog.value?.scrollTo({ top: chatLog.value.scrollHeight, behavior: 'smooth' })
  try {
    const res = await api.aiChatStream(
      { question: text, sessionId: yeh.currentId || undefined },
      (delta) => {
        yeh.patchLastAi(delta)
        chatLog.value?.scrollTo({ top: chatLog.value.scrollHeight })
      },
      (next, n) => enqueueStage(next, n),
      chatAbort.signal,
    )
    if (res.stopped) {
      stageQueue = []
      yeh.finishLastAi(res.citations, res.answer || '已停止')
    } else {
      await waitStages()
      yeh.finishLastAi(res.citations, res.answer || '暂时不能回答')
    }
  } catch (e) {
    if (chatAbort?.signal.aborted) {
      yeh.finishLastAi(undefined, '已停止')
    } else if (e instanceof ApiError && e.status === 401) {
      yeh.finishLastAi(undefined, '请先登录后再问耶耶')
      auth.show('login', '/yehyeh')
    } else {
      yeh.finishLastAi(undefined, e instanceof ApiError ? e.message : '暂时不能回答')
    }
  }
  chatAbort = null
  sending.value = false
  await nextTick()
  fitBox(chatBox.value, 56, 200)
  chatLog.value?.scrollTo({ top: chatLog.value.scrollHeight, behavior: 'smooth' })
}

function askHome() {
  if (!editing.value) {
    send(preset.value)
    return
  }
  const text = draft.value.trim()
  if (!text) return
  send(text)
}

function startEdit() {
  editing.value = true
}

function askChat() {
  const t = chatDraft.value.trim()
  if (!t) return
  send(t)
}

function pickQuestion(q: string) {
  send(q)
}

function liveAi(index: number) {
  return sending.value && index === (yeh.current?.messages.length || 1) - 1
}

function stepState(name: YehStage) {
  const now = stageOrder[stage.value]
  const at = stageOrder[name]
  if (at < now) return 'is-ok'
  if (at === now) return 'is-on'
  return 'is-wait'
}

function notesStepLabel() {
  if (noteCount.value > 0) return `读到 ${noteCount.value} 条笔记`
  if (stage.value === 'notes') return '没有找到相关笔记'
  return '阅读命中笔记'
}

function citeLabel(cite: AiCitation) {
  const text = (cite.title || cite.snippet || '笔记').replace(/\s+/g, ' ').trim()
  return text.length > 16 ? `${text.slice(0, 16)}…` : text
}

function openCite(cite: AiCitation, ev?: Event) {
  if (!cite.postId) return
  noteDetail.show(
    {
      id: cite.postId,
      title: cite.title || cite.snippet || '笔记',
      author: cite.nickname || '',
      likes: '0',
      image: cite.coverUrl || '',
      pageId: cite.pageId,
    },
    ev?.currentTarget ?? null,
  )
}

function processQuestion(index: number) {
  const msg = yeh.current?.messages[index]
  if (msg?.process?.question) return msg.process.question
  const prev = yeh.current?.messages[index - 1]
  return prev?.role === 'user' ? prev.text : ''
}

function traceTitle(msg: { process?: { noteCount: number }; citations?: AiCitation[] }) {
  const n = msg.process?.noteCount || msg.citations?.length || 0
  return n > 0 ? `已检索 ${n} 条笔记` : '已完成检索'
}

function toggleTrace(index: number) {
  const msg = yeh.current?.messages[index]
  if (!msg || msg.role !== 'ai') return
  yeh.patchProcess(index, { open: !msg.process?.open, openStep: '' })
}

function toggleStep(index: number, name: YehStage) {
  const msg = yeh.current?.messages[index]
  if (!msg || msg.role !== 'ai') return
  yeh.patchProcess(index, {
    open: true,
    openStep: msg.process?.openStep === name ? '' : name,
  })
}

async function copyAnswer(index: number, text: string) {
  try {
    await navigator.clipboard.writeText(text)
    copiedAt.value = index
    window.setTimeout(() => {
      if (copiedAt.value === index) copiedAt.value = -1
    }, 1400)
  } catch {
    copiedAt.value = -1
  }
}

function regenerate(index: number) {
  const msgs = yeh.current?.messages
  if (!msgs || index !== msgs.length - 1) return
  const prev = msgs[index - 1]
  if (!prev || prev.role !== 'user') return
  send(prev.text, true)
}

function onAnswerClick(ev: MouseEvent, msgIndex: number) {
  const btn = (ev.target as HTMLElement | null)?.closest?.('.yeh-ref') as HTMLElement | null
  const n = Number(btn?.dataset.ref)
  if (!n) return
  const card = document.getElementById(`yeh-src-${msgIndex}-${n}`)
  card?.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' })
  card?.classList.add('is-on')
  window.setTimeout(() => card?.classList.remove('is-on'), 1200)
}

function newSession() {
  yeh.startFresh()
  draft.value = ''
  chatDraft.value = ''
  editing.value = false
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

onMounted(() => {
  document.addEventListener('pointerdown', onDocPointer)
  void api
    .aiBootstrap()
    .then((data) => {
      const next = (data.yehPreset || '').trim() || FALLBACK_PRESET
      const qs = (data.yehQuestions || []).map((item) => item.trim()).filter(Boolean)
      preset.value = next
      questions.value = qs.length ? qs : [...FALLBACK_QUESTIONS]
    })
    .catch(() => {
      /* 用内置默认文案 */
    })
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocPointer)
  chatAbort?.abort()
})

watch(draft, () => nextTick(() => fitBox(homeBox.value, 56, 200)))
watch(chatDraft, () => nextTick(() => fitBox(chatBox.value, 56, 200)))

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
          <EmptyState v-if="!yeh.sessions.length" compact src="/empty-ai.png" title="还没有会话" />
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
                  ref="homeBox"
                  v-model="draft"
                  class="yeh-textarea"
                  rows="1"
                  :placeholder="editing ? '' : preset"
                  @click="startEdit"
                  @focus="startEdit"
                  @input="fitBox(homeBox, 56, 200)"
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
                  <button
                    type="button"
                    class="yeh-ask"
                    :class="homeReady ? 'is-ready' : ''"
                    :disabled="sending"
                    @mousedown.prevent
                    @click="askHome"
                  >
                    <Icon :name="homeReady ? 'arrow-right' : 'message-circle-heart'" :size="14" :stroke="2" />
                    问耶耶
                  </button>
                </div>
                <p v-if="pendingImages.length" class="yeh-attach">已选 {{ pendingImages.join('、') }}</p>
                <div class="yeh-expand">
                  <div class="yeh-expand-inner">
                    <div class="yeh-composer-line" />
                    <div class="yeh-suggests">
                      <button v-for="q in questions" :key="q" type="button" @mousedown.prevent="pickQuestion(q)">
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
            <div v-else>
              <ol v-if="liveAi(i)" class="yeh-steps" aria-label="回答过程">
                <li class="yeh-step" :class="stepState('retrieve')">
                  <span class="yeh-mark"><span class="yeh-dot" /></span>
                  <span class="yeh-step-lab">检索站内笔记</span>
                </li>
                <li class="yeh-step" :class="stepState('notes')">
                  <span class="yeh-mark"><span class="yeh-dot" /></span>
                  <span class="yeh-step-lab">{{ notesStepLabel() }}</span>
                </li>
                <li class="yeh-step" :class="stepState('write')">
                  <span class="yeh-mark"><span class="yeh-dot" /></span>
                  <span class="yeh-step-lab">组织回答</span>
                </li>
              </ol>
              <p v-if="liveAi(i) && !msg.text" class="yeh-ai-text yeh-thinking">耶耶正在想…</p>
              <div v-else-if="liveAi(i)" class="yeh-ai-text yeh-stream">{{ msg.text }}</div>
              <template v-else>
                <div v-if="msg.process" class="yeh-trace">
                  <button type="button" class="yeh-trace-toggle" @click="toggleTrace(i)">
                    <Icon
                      name="chevron-right"
                      :size="14"
                      class="yeh-trace-caret"
                      :class="{ 'is-open': msg.process.open }"
                    />
                    {{ traceTitle(msg) }}
                  </button>
                  <ol v-if="msg.process.open" class="yeh-steps is-done">
                    <li class="yeh-step is-ok">
                      <button type="button" class="yeh-step-hit" @click="toggleStep(i, 'retrieve')">
                        <span class="yeh-mark"><span class="yeh-dot" /></span>
                        <span class="yeh-step-lab">检索站内笔记</span>
                        <Icon name="chevron-up" :size="12" class="yeh-step-caret" :class="{ 'is-open': msg.process.openStep === 'retrieve' }" />
                      </button>
                      <p v-if="msg.process.openStep === 'retrieve'" class="yeh-step-detail">
                        用「{{ processQuestion(i) || '问题' }}」检索公开笔记的标题、正文和作者。
                      </p>
                    </li>
                    <li class="yeh-step is-ok">
                      <button type="button" class="yeh-step-hit" @click="toggleStep(i, 'notes')">
                        <span class="yeh-mark"><span class="yeh-dot" /></span>
                        <span class="yeh-step-lab">
                          {{ msg.process.noteCount > 0 ? `读到 ${msg.process.noteCount} 条笔记` : '没有找到相关笔记' }}
                        </span>
                        <Icon name="chevron-up" :size="12" class="yeh-step-caret" :class="{ 'is-open': msg.process.openStep === 'notes' }" />
                      </button>
                      <div v-if="msg.process.openStep === 'notes'" class="yeh-step-detail">
                        <p v-if="!msg.citations?.length">没有可用的站内笔记。</p>
                        <ul v-else>
                          <li v-for="(cite, n) in msg.citations.slice(0, 8)" :key="cite.postId || n">
                            {{ n + 1 }}. {{ cite.title || cite.snippet || '笔记' }}
                          </li>
                        </ul>
                      </div>
                    </li>
                    <li class="yeh-step is-ok">
                      <button type="button" class="yeh-step-hit" @click="toggleStep(i, 'write')">
                        <span class="yeh-mark"><span class="yeh-dot" /></span>
                        <span class="yeh-step-lab">组织回答</span>
                        <Icon name="chevron-up" :size="12" class="yeh-step-caret" :class="{ 'is-open': msg.process.openStep === 'write' }" />
                      </button>
                      <p v-if="msg.process.openStep === 'write'" class="yeh-step-detail">
                        根据命中笔记归纳成短段落，句末用 [n] 对应来源，不复述全文。
                      </p>
                    </li>
                  </ol>
                </div>
                <div
                  class="yeh-ai-text yeh-md"
                  v-html="renderYehAnswer(msg.text)"
                  @click="onAnswerClick($event, i)"
                />
                <div v-if="msg.citations?.length" class="yeh-sources">
                  <button
                    v-for="(cite, n) in msg.citations.slice(0, 4)"
                    :id="`yeh-src-${i}-${n + 1}`"
                    :key="cite.postId || n"
                    type="button"
                    class="yeh-source"
                    :title="cite.title || cite.snippet || '笔记'"
                    @click="openCite(cite, $event)"
                  >
                    {{ n + 1 }} {{ citeLabel(cite) }}
                  </button>
                </div>
                <div class="yeh-ai-actions">
                  <button type="button" class="yeh-ai-act" aria-label="复制" @click="copyAnswer(i, msg.text)">
                    <Icon name="copy" :size="14" />
                    {{ copiedAt === i ? '已复制' : '复制' }}
                  </button>
                  <button
                    v-if="i === (yeh.current?.messages.length || 1) - 1"
                    type="button"
                    class="yeh-ai-act"
                    aria-label="重新生成"
                    :disabled="sending"
                    @click="regenerate(i)"
                  >
                    <Icon name="refresh" :size="14" />
                    重新生成
                  </button>
                </div>
              </template>
            </div>
          </div>
        </div>
        <div class="yeh-chat-foot">
          <div class="yeh-composer yeh-composer-chat">
            <textarea
              ref="chatBox"
              v-model="chatDraft"
              class="yeh-textarea"
              rows="1"
              placeholder="搜索或者输入任何问题"
              @keydown.enter.exact.prevent="askChat"
              @input="fitBox(chatBox, 56, 200)"
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
                v-if="sending"
                type="button"
                class="yeh-ask is-ready"
                aria-label="停止"
                @click="stopChat"
              >
                <Icon name="pause" :size="14" :stroke="2" />
                停止
              </button>
              <button
                v-else
                type="button"
                class="yeh-ask"
                :class="chatDraft.trim() ? 'is-ready' : ''"
                @click="askChat"
              >
                <Icon :name="chatDraft.trim() ? 'arrow-right' : 'message-circle-heart'" :size="14" :stroke="2" />
                问耶耶
              </button>
            </div>
          </div>
          <p class="yeh-disclaimer">内容由AI生成，请注意甄别！</p>
        </div>
      </template>
    </div>
  </section>
</template>
