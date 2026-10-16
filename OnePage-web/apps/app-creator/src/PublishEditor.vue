<!-- 发布编辑页：有图后再进来。白卡片叠放，底栏暂存 / 发布。 -->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api, ApiError, FEED_CATEGORIES } from '@onepage/api-client'
import Icon from './Icon.vue'
import { EVENTS } from './events'
import type { LocalFile, PublishKind, PublishSeed } from './publishTypes'

const props = defineProps<{
  kind: PublishKind
  files: LocalFile[]
  seed?: PublishSeed | null
  editPostId?: string
}>()
const emit = defineEmits<{
  leave: []
  done: []
  'add-files': [FileList | File[]]
  'remove-file': [number]
  recrop: [number]
}>()

const title = ref('')
const body = ref('')
const topicInput = ref('')
const topics = ref<string[]>([])
const visibility = ref<'public' | 'self' | 'friend'>('public')
const original = ref(false)
const typeOpen = ref(false)
const category = ref('')
const contentType = ref('')
const linkedEventId = ref('')
const scheduleOn = ref(false)
const scheduleAt = ref('')
const busy = ref(false)
const posted = ref('')
const topicFocus = ref(false)

const contentTypes = ['不声明', '虚构创作', '个人观点', '专业内容']
const categories = ref<string[]>([...FEED_CATEGORIES])
const events = ref(EVENTS.filter((item) => item.status === '进行中').slice(0, 2))
const topicHints = ref<string[]>([])
const visOpts = [
  { id: 'public' as const, label: '公开' },
  { id: 'friend' as const, label: '好友' },
  { id: 'self' as const, label: '仅自己' },
]
const visIndex = computed(() => visOpts.findIndex((item) => item.id === visibility.value))

const maxFiles = computed(() => (props.kind === 'video' ? 1 : 9))
const mediaLabel = computed(() => {
  if (props.kind === 'video') return '视频编辑'
  if (props.kind === 'article') return '配图'
  return '图片编辑'
})
const accept = computed(() =>
  props.kind === 'video' ? 'video/mp4,video/quicktime,video/webm' : 'image/*',
)
function addTopic() {
  const tag = topicInput.value.trim().replace(/^#/, '')
  if (!tag || topics.value.includes(tag) || topics.value.length >= 10) return
  topics.value.push(tag)
  topicInput.value = ''
  topicHints.value = []
}

function pickHint(tag: string) {
  topicInput.value = tag
  addTopic()
}

onMounted(() => {
  void api
    .catalog()
    .then((data) => {
      const names = (data.categories || []).map((row) => row.name).filter(Boolean)
      if (names.length) categories.value = names
      const live = (data.campaigns || [])
        .filter((row) => row.enabled && row.status === 'live')
        .map((row) => ({
          id: row.eventId,
          title: row.title,
          time: row.intro || '',
          cover: row.coverUrl || '',
          status: '进行中' as const,
        }))
      if (live.length) events.value = live.slice(0, 4)
    })
    .catch(() => {
      /* 目录失败时沿用内置分类和活动 */
    })
})

watch(topicInput, async (q) => {
  if (!topicFocus.value) {
    topicHints.value = []
    return
  }
  try {
    const rows = await api.suggestTopics(q.trim() || undefined)
    topicHints.value = rows.map((row) => row.name).filter((name) => !topics.value.includes(name))
  } catch {
    topicHints.value = []
  }
})

function pickFiles(e: Event) {
  const list = (e.target as HTMLInputElement).files
  if (list?.length) emit('add-files', list)
  ;(e.target as HTMLInputElement).value = ''
}

function postTypeOf(): 'text' | 'image' | 'video' | 'mixed' {
  if (props.kind === 'video') return 'video'
  if (props.kind === 'article' && props.files.length === 0) return 'text'
  return 'image'
}

function purposeOf(file: File): 'post_image' | 'post_video' {
  if (file.type.startsWith('video/') || /\.(mp4|mov|webm)$/i.test(file.name)) return 'post_video'
  return 'post_image'
}

function mimeOf(file: File) {
  if (file.type) return file.type
  if (/\.mov$/i.test(file.name)) return 'video/quicktime'
  if (/\.webm$/i.test(file.name)) return 'video/webm'
  if (/\.mp4$/i.test(file.name)) return 'video/mp4'
  return purposeOf(file) === 'post_video' ? 'video/mp4' : 'image/jpeg'
}

function isVideoItem(item: LocalFile) {
  if (props.kind === 'video') return true
  if (item.file?.type.startsWith('video/')) return true
  return /\.(mp4|webm|mov)(\?|#|$)/i.test(item.url)
}

function applySeed(seed: PublishSeed) {
  title.value = seed.title.slice(0, 20)
  body.value = seed.body
  category.value = seed.category
  original.value = seed.original
  contentType.value = seed.contentKind || '不声明'
  linkedEventId.value = seed.eventId
  visibility.value = seed.visibility === 'friend' ? 'friend' : seed.visibility === 'self' ? 'self' : 'public'
}

watch(
  () => props.seed,
  (seed) => {
    if (seed) applySeed(seed)
  },
  { immediate: true },
)

async function uploadOne(item: LocalFile): Promise<string> {
  if (item.mediaId) return item.mediaId
  const file = item.file
  if (!file) throw new Error('缺少文件')
  const mimeType = mimeOf(file)
  const ticket = await api.presign(mimeType, file.size, purposeOf(file))
  await api.putObject(ticket.uploadUrl, file, mimeType)
  await api.completeUpload(ticket.mediaId)
  return ticket.mediaId
}

async function submit() {
  if (busy.value) return
  posted.value = ''
  if (!title.value.trim()) {
    posted.value = '请先填写标题'
    return
  }
  if (props.kind === 'article' && !body.value.trim()) {
    posted.value = '请填写正文'
    return
  }
  if ((props.kind === 'image' || props.kind === 'video') && props.files.length === 0) {
    posted.value = props.kind === 'video' ? '请先上传视频' : '请先上传图片'
    return
  }
  if (!category.value) {
    posted.value = '请选择笔记分类'
    return
  }
  if (scheduleOn.value && !scheduleAt.value) {
    posted.value = '请选择定时发布时间'
    return
  }
  await savePost(false)
}

async function stash() {
  if (busy.value) return
  if (props.editPostId && !props.seed?.isDraft) {
    emit('leave')
    return
  }
  if (!title.value.trim() && !body.value.trim() && !props.files.length) {
    posted.value = '请先写点内容再暂存'
    return
  }
  await savePost(true)
}

async function savePost(asDraft: boolean) {
  const text =
    [body.value.trim(), topics.value.map((tag) => `#${tag}`).join(' ')].filter(Boolean).join('\n') ||
    title.value.trim() ||
    (asDraft ? '草稿' : '')
  if (!asDraft && !text) {
    posted.value = '请填写正文'
    return
  }
  busy.value = true
  posted.value = ''
  try {
    const mediaIds: string[] = []
    for (const item of props.files) mediaIds.push(await uploadOne(item))
    const payload = {
      title: title.value.trim(),
      body: text,
      mediaIds,
      postType: postTypeOf(),
      category: category.value,
      original: original.value,
      contentKind: contentType.value === '不声明' ? '' : contentType.value,
      eventId: linkedEventId.value || undefined,
      visibility: visibility.value,
      draft: asDraft || undefined,
      scheduledAt: scheduleOn.value && scheduleAt.value ? scheduleAt.value : undefined,
    }
    if (props.editPostId) {
      const saved = await api.patchPost(props.editPostId, payload)
      posted.value = asDraft
        ? '已暂存'
        : saved.reviewStatus === 'pending'
          ? '已保存并提交审核'
          : '已保存'
    } else {
      const created = await api.createPost(payload)
      if (asDraft) {
        posted.value = '已暂存到草稿箱'
      } else if (visibility.value === 'self') {
        posted.value = `发布成功 #${created.postId}（仅自己可见，发现流不展示）`
      } else if (visibility.value === 'friend') {
        posted.value = `发布成功 #${created.postId}（好友可见，关注流可见）`
      } else if (created.reviewStatus === 'pending') {
        posted.value = scheduleOn.value
          ? `已提交审核 #${created.postId}，通过且到达定时后出现在发现流`
          : `已提交审核 #${created.postId}，通过后出现在发现流`
      } else {
        posted.value = `发布成功 #${created.postId}`
      }
    }
    window.setTimeout(() => emit('done'), 600)
  } catch (e) {
    posted.value = e instanceof ApiError ? e.message : asDraft ? '暂存失败' : props.editPostId ? '保存失败' : '发布失败'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="ed">
    <div class="scroll">
      <section class="card">
        <div class="card-h">
          <span>{{ mediaLabel }} {{ props.files.length }}/{{ maxFiles }}</span>
        </div>
        <div class="thumbs">
          <label v-if="props.files.length < maxFiles" class="thumb thumb-add">
            <Icon name="plus" :size="28" />
            <input
              type="file"
              class="hidden"
              :accept="accept"
              :multiple="props.kind !== 'video'"
              @change="pickFiles"
            />
          </label>
          <div v-for="(item, i) in props.files" :key="item.url" class="thumb">
            <video v-if="isVideoItem(item)" :src="item.url" muted />
            <button
              v-else
              type="button"
              class="thumb-recrop"
              :disabled="!item.file"
              @click="item.file && emit('recrop', i)"
            >
              <img :src="item.url" alt="" />
            </button>
            <span v-if="i === 0 && props.kind === 'image'" class="cover">1</span>
            <button type="button" class="thumb-x" aria-label="移除" @click="emit('remove-file', i)">
              <Icon name="x" :size="12" :stroke="2.5" />
            </button>
          </div>
        </div>
      </section>

      <section class="card write">
        <div class="title-row">
          <input
            v-model="title"
            class="title"
            maxlength="20"
            placeholder="填写标题会有更多赞哦"
            autofocus
          />
          <span class="count">{{ title.length }}/20</span>
        </div>
        <textarea
          v-model="body"
          class="body"
          :class="{ 'is-long': props.kind === 'article' }"
          :maxlength="props.kind === 'article' ? 8000 : 1000"
          :placeholder="
            props.kind === 'article'
              ? '写正文，建议不少于 300 字'
              : '输入正文描述，真诚有价值的分享予人温暖'
          "
        />
        <div class="chips">
          <span v-for="tag in topics" :key="tag" class="chip">
            #{{ tag }}
            <button type="button" aria-label="去掉话题" @click="topics = topics.filter((t) => t !== tag)">
              <Icon name="x" :size="12" />
            </button>
          </span>
        </div>
        <div class="tools">
          <button type="button" class="tool" :class="{ 'is-on': topicFocus }" @click="topicFocus = !topicFocus">
            # 话题
          </button>
          <span class="count">{{ body.length }}/{{ props.kind === 'article' ? 8000 : 1000 }}</span>
        </div>
        <input
          v-if="topicFocus"
          v-model="topicInput"
          class="topic-in"
          maxlength="16"
          placeholder="输入话题，回车添加"
          @keydown.enter.prevent="addTopic"
        />
        <div v-if="topicFocus && topicHints.length" class="hints">
          <button v-for="tag in topicHints" :key="tag" type="button" @click="pickHint(tag)">#{{ tag }}</button>
        </div>
      </section>

      <section class="card">
        <div class="card-h">
          <span>笔记分类</span>
          <span class="more">必选，对应首页 Tab</span>
        </div>
        <div class="cats">
          <button
            v-for="item in categories"
            :key="item"
            type="button"
            class="cat"
            :class="{ 'is-on': category === item }"
            @click="category = item"
          >
            {{ item }}
          </button>
        </div>
      </section>

      <section class="card">
        <div class="card-h">
          <span>内容设置</span>
        </div>
        <div class="duo">
          <div class="cell">
            <div>
              <p class="row-l">声明原创作品</p>
              <p class="sub">打开后在帖子详情展示原创标识</p>
            </div>
            <button
              type="button"
              class="switch"
              :class="{ 'is-on': original }"
              role="switch"
              :aria-checked="original"
              @click="original = !original"
            >
              <span class="knob" />
            </button>
          </div>
          <button type="button" class="cell" @click="typeOpen = !typeOpen">
            <div>
              <p class="row-l">添加内容类型声明</p>
              <p class="sub">{{ contentType || '帮助读者理解内容性质' }}</p>
            </div>
            <Icon name="chevron-down" :size="16" class="chev" :class="{ 'is-open': typeOpen }" />
          </button>
        </div>
        <div v-if="typeOpen" class="type-list">
          <button
            v-for="item in contentTypes"
            :key="item"
            type="button"
            :class="{ 'is-on': contentType === item }"
            @click="contentType = contentType === item ? '' : item"
          >
            {{ item }}
          </button>
        </div>
      </section>

      <section class="card">
        <div class="card-h">
          <span>关联活动</span>
          <span class="more">进行中</span>
        </div>
        <div class="duo">
          <article v-for="item in events" :key="item.id" class="cell event">
            <img :src="item.cover" alt="" />
            <div class="event-body">
              <h3>{{ item.title }}</h3>
              <p>{{ item.time }}</p>
            </div>
            <button
              type="button"
              class="link"
              :class="{ 'is-on': linkedEventId === item.id }"
              @click.stop="linkedEventId = linkedEventId === item.id ? '' : item.id"
            >
              {{ linkedEventId === item.id ? '已关联' : '关联' }}
            </button>
          </article>
        </div>
      </section>

      <section class="card">
        <div class="card-h">
          <span>更多设置</span>
        </div>
        <div class="duo">
          <div class="cell stack">
            <div>
              <p class="row-l">笔记可见</p>
              <p class="sub">公开进发现流；仅自己不展示给别人</p>
            </div>
            <div class="slide" role="radiogroup" aria-label="笔记可见">
              <span class="slide-thumb" :style="{ transform: `translateX(${visIndex * 100}%)` }" />
              <button
                v-for="item in visOpts"
                :key="item.id"
                type="button"
                role="radio"
                :aria-checked="visibility === item.id"
                :class="{ 'is-on': visibility === item.id }"
                @click="visibility = item.id"
              >
                {{ item.label }}
              </button>
            </div>
          </div>
          <div class="cell stack">
            <div class="cell-top">
              <div>
                <p class="row-l">定时发布</p>
                <p class="sub">到点后公开笔记进入审核或发现流；好友/仅自己直接按该时间可见</p>
              </div>
              <button
                type="button"
                class="switch"
                :class="{ 'is-on': scheduleOn }"
                role="switch"
                :aria-checked="scheduleOn"
                @click="scheduleOn = !scheduleOn"
              >
                <span class="knob" />
              </button>
            </div>
            <input
              v-if="scheduleOn"
              v-model="scheduleAt"
              class="when"
              type="datetime-local"
            />
          </div>
        </div>
      </section>
    </div>

    <footer class="bar">
      <span class="msg">{{ posted }}</span>
      <button type="button" class="ghost" :disabled="busy" @click="stash">
        {{ props.editPostId && !props.seed?.isDraft ? '取消' : busy ? '暂存中…' : '暂存离开' }}
      </button>
      <button type="button" class="post" :disabled="busy" @click="submit">
        {{ busy ? (props.editPostId ? '保存中…' : '发布中…') : props.editPostId ? '保存' : '发布' }}
      </button>
    </footer>
  </div>
</template>

<style scoped>
.ed {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  margin: 0 20px 20px 0;
}

.scroll {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  overflow: auto;
  padding-bottom: 8px;
}

.card {
  padding: 20px 24px;
  border-radius: 20px;
  background: #fff;
}

.card-h {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  color: #1f1f1f;
  font-size: 15px;
  font-weight: 600;
}

.thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.thumb {
  position: relative;
  width: 96px;
  height: 96px;
  overflow: hidden;
  border-radius: 12px;
  background: rgb(248, 248, 248);
}

.thumb img,
.thumb video {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb-recrop {
  display: block;
  width: 100%;
  height: 100%;
  padding: 0;
  border: 0;
  background: transparent;
}

.thumb-add {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c8c8c8;
  cursor: pointer;
}

.cover {
  position: absolute;
  top: 6px;
  left: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 9px;
  background: #ff2442;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.thumb-x {
  position: absolute;
  top: 6px;
  right: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  line-height: 0;
  border-radius: 11px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
}

.hidden {
  display: none;
}

.write .title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.title,
.body,
.topic-in {
  outline: none;
  box-shadow: none;
}

.title {
  flex: 1;
  height: 40px;
  border: 0;
  background: transparent;
  color: #1f1f1f;
  font: inherit;
  font-size: 16px;
  font-weight: 600;
}

.body {
  width: 100%;
  min-height: 220px;
  margin-top: 12px;
  padding: 0;
  border: 0;
  resize: none;
  background: transparent;
  color: #1f1f1f;
  font: inherit;
  line-height: 1.7;
}

.body.is-long {
  min-height: 360px;
}

.count {
  color: #bbb;
  font-size: 12px;
  font-weight: 400;
}

.cats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cat {
  height: 32px;
  padding: 0 14px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
  color: #1f1f1f;
  font-size: 13px;
}

.cat.is-on {
  background: #ff2442;
  color: #fff;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 8px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  border-radius: 14px;
  background: rgb(248, 248, 248);
  color: #ff2442;
  font-size: 13px;
}

.chip button {
  display: flex;
  align-items: center;
  color: #bbb;
}

.tools {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
}

.tool {
  height: 32px;
  padding: 0 12px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
  color: #555;
  font-size: 13px;
}

.tool.is-on {
  color: #1f1f1f;
  font-weight: 600;
}

.topic-in {
  width: 100%;
  height: 40px;
  margin-top: 10px;
  padding: 0 12px;
  border: 0;
  border-radius: 12px;
  background: rgb(248, 248, 248);
  font: inherit;
}

.hints {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.hints button {
  height: 28px;
  padding: 0 10px;
  border-radius: 14px;
  background: rgb(248, 248, 248);
  color: #555;
  font-size: 12px;
}

.card-h .more {
  color: #8a8a8a;
  font-size: 13px;
  font-weight: 400;
}

.duo {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0;
}

.cell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
  padding: 4px 20px 4px 0;
  border-right: 1px solid #f0f0f0;
  text-align: left;
  background: none;
}

.cell:last-child {
  padding-right: 0;
  padding-left: 20px;
  border-right: 0;
}

.cell.stack {
  flex-direction: column;
  align-items: stretch;
  gap: 10px;
}

.cell-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.row-l {
  margin: 0;
  color: #1f1f1f;
  font-size: 14px;
}

.sub {
  margin: 4px 0 0;
  color: #8a8a8a;
  font-size: 12px;
}

.switch {
  flex: none;
  width: 44px;
  height: 26px;
  padding: 0;
  border-radius: 13px;
  background: #e8e8e8;
}

.switch.is-on {
  background: #ff2442;
}

.knob {
  display: block;
  width: 22px;
  height: 22px;
  margin: 2px;
  border-radius: 11px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.15);
  transition: transform 0.2s ease;
}

.switch.is-on .knob {
  transform: translateX(18px);
}

.chev {
  flex: none;
  color: #bbb;
  transition: transform 0.2s;
}

.chev.is-open {
  transform: rotate(180deg);
}

.type-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0 0 8px;
}

.type-list button {
  height: 32px;
  padding: 0 12px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
  color: #555;
  font-size: 13px;
}

.type-list button.is-on {
  color: #fff;
  background: #ff2442;
}

.event {
  display: flex;
  align-items: center;
  gap: 12px;
}

.event img {
  width: 56px;
  height: 56px;
  object-fit: cover;
  border-radius: 12px;
  background: rgb(248, 248, 248);
}

.event-body {
  flex: 1;
  min-width: 0;
}

.event-body h3 {
  margin: 0;
  overflow: hidden;
  color: #1f1f1f;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.event-body p {
  margin: 4px 0 0;
  color: #8a8a8a;
  font-size: 12px;
}

.link {
  flex: none;
  height: 32px;
  padding: 0 14px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
  color: #1f1f1f;
  font-size: 13px;
}

.link.is-on {
  color: #fff;
  background: #ff2442;
}

.slide {
  position: relative;
  display: grid;
  flex: none;
  grid-template-columns: 1fr 1fr 1fr;
  width: 100%;
  height: 32px;
  padding: 3px;
  border-radius: 16px;
  background: rgb(245, 245, 245);
}

.slide-thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: calc((100% - 6px) / 3);
  height: 26px;
  border-radius: 13px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
  pointer-events: none;
  transition: transform 0.2s ease;
}

.slide button {
  position: relative;
  z-index: 1;
  height: 26px;
  padding: 0;
  color: #888;
  font-size: 12px;
}

.slide button.is-on {
  color: #1f1f1f;
  font-weight: 600;
}

.when {
  width: 100%;
  height: 44px;
  margin-top: 4px;
  padding: 0 12px;
  box-sizing: border-box;
  border: 0;
  border-radius: 12px;
  background: rgb(248, 248, 248);
  color: #1f1f1f;
  font: inherit;
  outline: none;
}

.bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 8px 4px;
}

.msg {
  margin-right: auto;
  color: #8a8a8a;
  font-size: 13px;
}

.ghost,
.post {
  height: 40px;
  padding: 0 24px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
}

.ghost {
  background: #fff;
  color: #1f1f1f;
}

.ghost:hover {
  background: #f2f2f2;
}

.post {
  background: #ff2442;
  color: #fff;
}

.post:hover:not(:disabled) {
  background: #e61e3a;
}

.post:disabled {
  opacity: 0.65;
  cursor: default;
}
</style>
