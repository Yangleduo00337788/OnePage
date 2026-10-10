<!-- 发布工作台：顶栏体裁 Tab、中间大上传区、底三列规格。对照创作服务平台布局。 -->
<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from './Icon.vue'

export type PublishKind = 'image' | 'video' | 'article' | 'podcast'

const props = defineProps<{ kind: PublishKind }>()
const emit = defineEmits<{ 'update:kind': [PublishKind] }>()

const kinds: { id: PublishKind; label: string }[] = [
  { id: 'video', label: '上传视频' },
  { id: 'image', label: '上传图文' },
  { id: 'article', label: '写长文' },
  { id: 'podcast', label: '发播客' },
]

const files = ref<{ name: string; url: string }[]>([])
const posted = ref('')
const title = ref('')
const body = ref('')
const topicInput = ref('')
const topics = ref<string[]>([])
const visibility = ref<'public' | 'self' | 'friend'>('public')
const dragging = ref(false)
const drafts = computed(() => files.value.length)

const visOpts = [
  { id: 'public' as const, label: '公开' },
  { id: 'friend' as const, label: '好友可见' },
  { id: 'self' as const, label: '仅自己可见' },
]

function addTopic() {
  const tag = topicInput.value.trim().replace(/^#/, '')
  if (!tag || topics.value.includes(tag) || topics.value.length >= 10) return
  topics.value.push(tag)
  topicInput.value = ''
}

function removeTopic(tag: string) {
  topics.value = topics.value.filter((item) => item !== tag)
}

const cta = computed(() => {
  if (props.kind === 'image') return '上传图文'
  if (props.kind === 'article') return '开始写作'
  if (props.kind === 'podcast') return '上传音频'
  return '上传视频'
})

const hint = computed(() => {
  if (props.kind === 'image') return '拖拽图片到此处或点击上传'
  if (props.kind === 'article') return '在下方写正文，也可拖入配图'
  if (props.kind === 'podcast') return '拖拽音频到此处或点击上传'
  return '拖拽视频到此处或点击上传'
})

const accept = computed(() => {
  if (props.kind === 'video') return 'video/*'
  if (props.kind === 'podcast') return 'audio/*'
  return 'image/*'
})

const specs = computed(() => {
  if (props.kind === 'image') {
    return [
      { t: '图片数量', d: '最多 9 张，建议 3 张以上。' },
      { t: '图片格式', d: '支持 jpg、png、webp。' },
      { t: '图片大小', d: '单张建议不超过 20MB。' },
    ]
  }
  if (props.kind === 'article') {
    return [
      { t: '字数', d: '正文建议不少于 300 字。' },
      { t: '插图', d: '可配图，勿使用未授权素材。' },
      { t: '版权', d: '内容须为原创或已获授权。' },
    ]
  }
  if (props.kind === 'podcast') {
    return [
      { t: '音频大小', d: '单集建议不超过 200MB。' },
      { t: '音频格式', d: '支持 mp3、m4a、wav。' },
      { t: '时长', d: '建议 3～60 分钟，人声清晰。' },
    ]
  }
  return [
    { t: '视频大小', d: '支持时长 1 秒以上，最大 20GB 视频文件。' },
    { t: '视频格式', d: '支持常见视频格式，推荐 mp4、mov。' },
    { t: '视频分辨率', d: '接近 1080P 的视频在网页端上传画质更清晰。' },
  ]
})

function addList(list: FileList | File[]) {
  for (const file of Array.from(list)) {
    files.value.push({ name: file.name, url: URL.createObjectURL(file) })
  }
}

function pickFiles(e: Event) {
  const list = (e.target as HTMLInputElement).files
  if (list) addList(list)
  ;(e.target as HTMLInputElement).value = ''
}

function onDrop(e: DragEvent) {
  dragging.value = false
  const list = e.dataTransfer?.files
  if (list?.length) addList(list)
}

function removeFile(i: number) {
  URL.revokeObjectURL(files.value[i].url)
  files.value.splice(i, 1)
}

function submit() {
  if (!title.value.trim()) {
    posted.value = '请先填写标题'
    return
  }
  posted.value = '已保存为草稿（P0 占位，未真正发布）'
}
</script>

<template>
  <div class="pub-page">
    <div class="top">
      <div class="tabs">
        <button
          v-for="item in kinds"
          :key="item.id"
          type="button"
          class="tab"
          :class="{ 'is-on': props.kind === item.id }"
          @click="emit('update:kind', item.id)"
        >
          {{ item.label }}
        </button>
      </div>
      <button type="button" class="drafts">草稿箱({{ drafts }})</button>
    </div>

    <div
      class="stage"
      :class="{ 'is-drag': dragging }"
      @dragover.prevent="dragging = true"
      @dragleave="dragging = false"
      @drop.prevent="onDrop"
    >
      <div v-if="files.length" class="thumbs">
        <div v-for="(item, i) in files" :key="item.url" class="thumb">
          <video v-if="props.kind === 'video' || props.kind === 'podcast'" :src="item.url" muted />
          <img v-else :src="item.url" alt="" />
          <button type="button" class="thumb-x" @click="removeFile(i)">×</button>
        </div>
      </div>
      <div v-else class="empty">
        <Icon name="cloud-upload" :size="56" class="cloud" />
        <p>{{ hint }}</p>
        <label class="cta">
          {{ cta }}
          <input type="file" class="hidden" :accept="accept" multiple @change="pickFiles" />
        </label>
      </div>
      <label v-if="files.length" class="cta cta-more">
        继续上传
        <input type="file" class="hidden" :accept="accept" multiple @change="pickFiles" />
      </label>
    </div>

    <div class="meta">
      <input v-model="title" class="title" maxlength="40" placeholder="标题（必填）" />
      <textarea
        v-model="body"
        class="body"
        :rows="props.kind === 'article' ? 8 : 4"
        :placeholder="props.kind === 'article' ? '写正文' : '写一段说明，让别人更想点进来'"
      />
      <div class="topics">
        <span v-for="tag in topics" :key="tag" class="chip">
          #{{ tag }}
          <button type="button" @click="removeTopic(tag)">×</button>
        </span>
        <input
          v-model="topicInput"
          class="topic-in"
          maxlength="16"
          placeholder="加话题，回车确认"
          @keydown.enter.prevent="addTopic"
        />
      </div>
      <div class="vis">
        <span>谁可以看</span>
        <button
          v-for="item in visOpts"
          :key="item.id"
          type="button"
          :class="{ 'is-on': visibility === item.id }"
          @click="visibility = item.id"
        >
          {{ item.label }}
        </button>
      </div>
      <div class="actions">
        <span v-if="posted">{{ posted }}</span>
        <button type="button" class="post" @click="submit">发布笔记</button>
      </div>
    </div>

    <div class="specs">
      <article v-for="item in specs" :key="item.t" class="spec">
        <h3>{{ item.t }}</h3>
        <p>{{ item.d }}</p>
      </article>
    </div>
  </div>
</template>

<style scoped>
.pub-page {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  margin: 0 20px 20px 0;
  padding: 16px 24px 20px;
  box-sizing: border-box;
  border-radius: 20px;
  background: rgb(255, 255, 255);
}

.top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.tabs {
  display: flex;
  gap: 8px;
}

.tab {
  height: 36px;
  padding: 0 14px;
  border-radius: 20px;
  color: #555;
  font-size: 14px;
}

.tab:hover {
  background: rgb(248, 248, 248);
}

.tab.is-on {
  color: #1f1f1f;
  font-weight: 600;
  background: rgb(248, 248, 248);
}

.drafts {
  height: 32px;
  padding: 0 14px;
  border-radius: 16px;
  background: #f5f5f5;
  color: #555;
  font-size: 13px;
}

.stage {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 420px;
  margin-top: 12px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
}

.stage.is-drag {
  background: #eee;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  color: #888;
}

.cloud {
  color: #d8d8d8;
}

.empty p {
  margin: 16px 0 20px;
  font-size: 14px;
}

.cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 36px;
  padding: 0 22px;
  border-radius: 18px;
  background: #ff2442;
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.cta:hover {
  background: #e61e3a;
}

.cta-more {
  margin: 16px 0 24px;
}

.hidden {
  display: none;
}

.thumbs {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
  padding: 24px;
}

.thumb {
  position: relative;
  width: 160px;
  height: 160px;
  overflow: hidden;
  border-radius: 12px;
  background: #fff;
}

.thumb img,
.thumb video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb-x {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 22px;
  height: 22px;
  border-radius: 11px;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
}

.meta {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
}

.title,
.body,
.topic-in {
  width: 100%;
  box-sizing: border-box;
  border: 0;
  border-radius: 12px;
  background: rgb(248, 248, 248);
  color: #1f1f1f;
  font: inherit;
}

.title {
  height: 44px;
  padding: 0 14px;
  font-size: 16px;
  font-weight: 600;
}

.body {
  padding: 12px 14px;
  resize: vertical;
}

.topics {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 6px 10px;
  border-radius: 12px;
  background: rgb(248, 248, 248);
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  border-radius: 14px;
  background: #fff;
  color: #ff2442;
  font-size: 13px;
}

.chip button {
  color: #bbb;
}

.topic-in {
  width: auto;
  min-width: 140px;
  flex: 1;
  height: 32px;
  padding: 0 6px;
  background: transparent;
}

.vis {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #8a8a8a;
  font-size: 13px;
}

.vis button {
  height: 32px;
  padding: 0 12px;
  border-radius: 16px;
  color: #555;
}

.vis button.is-on {
  color: #1f1f1f;
  font-weight: 600;
  background: rgb(248, 248, 248);
}

.actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  color: #8a8a8a;
  font-size: 13px;
}

.post {
  height: 36px;
  padding: 0 22px;
  border-radius: 18px;
  background: #ff2442;
  color: #fff;
  font-size: 14px;
  font-weight: 500;
}

.post:hover {
  background: #e61e3a;
}

.specs {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 0;
  margin-top: 8px;
  padding: 20px 8px 0;
}

.spec {
  padding: 0 28px;
  border-left: 1px solid #ececec;
}

.spec:first-child {
  border-left: 0;
  padding-left: 8px;
}

.spec h3 {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.spec p {
  margin: 0;
  color: #8a8a8a;
  font-size: 13px;
  line-height: 1.6;
}
</style>
