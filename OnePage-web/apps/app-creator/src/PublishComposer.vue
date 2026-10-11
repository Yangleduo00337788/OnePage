<!-- 发布落地页：只提示规格和上传。点上传 / 开始写作后进入编辑页。 -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Icon from './Icon.vue'
import PublishEditor from './PublishEditor.vue'
import type { LocalFile, PublishKind } from './publishTypes'

export type { PublishKind }

const props = defineProps<{ kind: PublishKind }>()
const emit = defineEmits<{ 'update:kind': [PublishKind] }>()

const kinds: { id: PublishKind; label: string }[] = [
  { id: 'video', label: '上传视频' },
  { id: 'image', label: '上传图文' },
  { id: 'article', label: '写长文' },
]

const files = ref<LocalFile[]>([])
const phase = ref<'landing' | 'editor'>('landing')
const dragging = ref(false)
const drafts = computed(() => files.value.length)
const maxFiles = computed(() => (props.kind === 'video' ? 1 : 9))

watch(
  () => props.kind,
  () => {
    phase.value = 'landing'
  },
)

const cta = computed(() => {
  if (props.kind === 'image') return '上传图文'
  if (props.kind === 'article') return '开始写作'
  return '上传视频'
})

const hint = computed(() => {
  if (props.kind === 'image') return '拖拽图片到此处或点击上传，进入图文编辑'
  if (props.kind === 'article') return '点击开始写作进入长文编辑，也可稍后配图'
  return '拖拽视频到此处或点击上传，进入视频编辑（mp4 / mov，不超过 200MB）'
})

const accept = computed(() => (props.kind === 'video' ? 'video/mp4,video/quicktime,video/webm' : 'image/*'))

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
  return [
    { t: '视频大小', d: '单文件不超过 200MB。' },
    { t: '视频格式', d: '支持 mp4、mov、webm。' },
    { t: '视频分辨率', d: '接近 1080P 的视频在网页端上传画质更清晰。' },
  ]
})

function allowed(file: File) {
  if (file.size > 200 * 1024 * 1024) return false
  if (props.kind === 'video') {
    return file.type.startsWith('video/') || /\.(mp4|mov|webm)$/i.test(file.name)
  }
  return file.type.startsWith('image/') || file.type === '' || /\.(jpe?g|png|webp|gif)$/i.test(file.name)
}

function addList(list: FileList | File[]) {
  for (const file of Array.from(list)) {
    if (!allowed(file)) continue
    if (files.value.length >= maxFiles.value) break
    files.value.push({ name: file.name, url: URL.createObjectURL(file), file })
  }
}

function pickFiles(e: Event) {
  const list = (e.target as HTMLInputElement).files
  if (list?.length) addList(list)
  ;(e.target as HTMLInputElement).value = ''
  if (files.value.length || props.kind === 'article') enterEditor()
}

function startArticle() {
  enterEditor()
}

function onDrop(e: DragEvent) {
  dragging.value = false
  const list = e.dataTransfer?.files
  if (list?.length) addList(list)
  if (files.value.length) enterEditor()
}

function enterEditor() {
  phase.value = 'editor'
}

function removeFile(i: number) {
  URL.revokeObjectURL(files.value[i].url)
  files.value.splice(i, 1)
}

function reset() {
  for (const item of files.value) URL.revokeObjectURL(item.url)
  files.value = []
  phase.value = 'landing'
}

function switchKind(id: PublishKind) {
  if (id === props.kind) return
  reset()
  emit('update:kind', id)
}
</script>

<template>
  <PublishEditor
    v-if="phase === 'editor'"
    :kind="props.kind"
    :files="files"
    @leave="phase = 'landing'"
    @done="reset"
    @add-files="addList"
    @remove-file="removeFile"
  />

  <div v-else class="pub-page">
    <div class="top">
      <div class="tabs">
        <button
          v-for="item in kinds"
          :key="item.id"
          type="button"
          class="tab"
          :class="{ 'is-on': props.kind === item.id }"
          @click="switchKind(item.id)"
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
      <div class="empty">
        <Icon name="cloud-upload" :size="56" class="cloud" />
        <p>{{ hint }}</p>
        <button v-if="files.length" type="button" class="cta" @click="enterEditor">继续编辑</button>
        <button v-else-if="props.kind === 'article'" type="button" class="cta" @click="startArticle">
          {{ cta }}
        </button>
        <label v-else class="cta">
          {{ cta }}
          <input
            type="file"
            class="hidden"
            :accept="accept"
            :multiple="props.kind !== 'video'"
            @change="pickFiles"
          />
        </label>
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
  min-height: 360px;
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

.hidden {
  display: none;
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
