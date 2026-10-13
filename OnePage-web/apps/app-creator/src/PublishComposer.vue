<!-- 发布落地页：只提示规格和上传。点上传 / 开始写作后进入编辑页。 -->
<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { api, ApiError } from '@onepage/api-client'
import ImageCropDialog from '@onepage/image-crop'
import Icon from './Icon.vue'
import PublishEditor from './PublishEditor.vue'
import type { LocalFile, PublishKind, PublishSeed } from './publishTypes'

export type { PublishKind }

const props = defineProps<{ kind: PublishKind; editPostId?: string }>()
const emit = defineEmits<{ 'update:kind': [PublishKind]; close: [] }>()

const kinds: { id: PublishKind; label: string }[] = [
  { id: 'video', label: '上传视频' },
  { id: 'image', label: '上传图文' },
  { id: 'article', label: '写长文' },
]

const files = ref<LocalFile[]>([])
const phase = ref<'landing' | 'editor'>('landing')
const dragging = ref(false)
const seed = ref<PublishSeed | null>(null)
const editError = ref('')
const editLoading = ref(false)
const drafts = computed(() => files.value.length)
const maxFiles = computed(() => (props.kind === 'video' ? 1 : 9))

watch(
  () => props.kind,
  () => {
    if (!props.editPostId) phase.value = 'landing'
  },
)

function kindOf(postType: string): PublishKind {
  if (postType === 'video') return 'video'
  if (postType === 'text') return 'article'
  return 'image'
}

async function loadEdit() {
  if (!props.editPostId) return
  editLoading.value = true
  editError.value = ''
  try {
    const detail = await api.post(props.editPostId)
    const nextKind = kindOf(detail.postType)
    if (nextKind !== props.kind) emit('update:kind', nextKind)
    files.value = (detail.media || []).map((m, i) => ({
      name: `media-${i + 1}`,
      url: m.url,
      mediaId: m.mediaId,
    }))
    seed.value = {
      title: detail.title || '',
      body: detail.body || '',
      category: detail.category || '',
      original: Boolean(detail.original),
      contentKind: detail.contentKind || '',
      eventId: detail.eventId || '',
      visibility: detail.visibility === 'self' ? 'self' : 'public',
    }
    phase.value = 'editor'
  } catch (e) {
    editError.value = e instanceof ApiError ? e.message : '加载笔记失败'
  } finally {
    editLoading.value = false
  }
}

watch(
  () => props.editPostId,
  () => {
    void loadEdit()
  },
  { immediate: true },
)

function leaveEditor() {
  if (props.editPostId) {
    emit('close')
    return
  }
  phase.value = 'landing'
}

function finishEditor() {
  if (props.editPostId) {
    emit('close')
    return
  }
  reset()
}

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

function isImageFile(file: File) {
  return file.type.startsWith('image/') || /\.(jpe?g|png|webp|gif)$/i.test(file.name)
}

const cropQueue = ref<File[]>([])
const cropSrc = ref('')
const cropName = ref('')
const recropAt = ref<number | null>(null)

function stopCrop() {
  if (cropSrc.value) URL.revokeObjectURL(cropSrc.value)
  cropSrc.value = ''
  cropName.value = ''
  recropAt.value = null
}

function roomLeft() {
  return maxFiles.value - files.value.length
}

function nextCrop() {
  if (cropSrc.value) URL.revokeObjectURL(cropSrc.value)
  cropSrc.value = ''
  const file = cropQueue.value[0]
  if (!file) {
    cropName.value = ''
    if (files.value.length || props.kind === 'article') enterEditor()
    return
  }
  cropName.value = file.name
  cropSrc.value = URL.createObjectURL(file)
}

function addList(list: FileList | File[]) {
  const wait: File[] = []
  for (const file of Array.from(list)) {
    if (!allowed(file)) continue
    if (!isImageFile(file) || props.kind === 'video') {
      if (files.value.length >= maxFiles.value) break
      files.value.push({ name: file.name, url: URL.createObjectURL(file), file })
      continue
    }
    wait.push(file)
  }
  const cap = roomLeft()
  cropQueue.value.push(...wait.slice(0, Math.max(0, cap)))
  if (!cropSrc.value) nextCrop()
}

function onCropConfirm(file: File) {
  if (recropAt.value != null) {
    const i = recropAt.value
    URL.revokeObjectURL(files.value[i].url)
    files.value[i] = { name: file.name, url: URL.createObjectURL(file), file }
    stopCrop()
    return
  }
  cropQueue.value.shift()
  if (files.value.length < maxFiles.value) {
    files.value.push({ name: file.name, url: URL.createObjectURL(file), file })
  }
  nextCrop()
}

function onCropCancel() {
  if (recropAt.value != null) {
    stopCrop()
    return
  }
  cropQueue.value.shift()
  nextCrop()
}

function recrop(i: number) {
  const item = files.value[i]
  if (!item?.file || !isImageFile(item.file)) return
  recropAt.value = i
  if (cropSrc.value) URL.revokeObjectURL(cropSrc.value)
  cropName.value = item.file.name
  cropSrc.value = URL.createObjectURL(item.file)
}

function pickFiles(e: Event) {
  const list = (e.target as HTMLInputElement).files
  if (list?.length) addList(list)
  ;(e.target as HTMLInputElement).value = ''
  if ((files.value.length || props.kind === 'article') && !cropSrc.value && !cropQueue.value.length) {
    enterEditor()
  }
}

function startArticle() {
  enterEditor()
}

function onDrop(e: DragEvent) {
  dragging.value = false
  const list = e.dataTransfer?.files
  if (list?.length) addList(list)
  if (files.value.length && !cropSrc.value && !cropQueue.value.length) enterEditor()
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
  cropQueue.value = []
  stopCrop()
  phase.value = 'landing'
}

onUnmounted(() => {
  cropQueue.value = []
  stopCrop()
})

function switchKind(id: PublishKind) {
  if (id === props.kind) return
  reset()
  emit('update:kind', id)
}
</script>

<template>
  <p v-if="props.editPostId && editLoading" class="hint">加载中…</p>
  <p v-else-if="props.editPostId && editError" class="hint">{{ editError }}</p>
  <template v-else>
    <PublishEditor
      v-if="phase === 'editor'"
      :kind="props.kind"
      :files="files"
      :seed="seed"
      :edit-post-id="props.editPostId"
      @leave="leaveEditor"
      @done="finishEditor"
      @add-files="addList"
      @remove-file="removeFile"
      @recrop="recrop"
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
    <ImageCropDialog
      v-if="cropSrc"
      :src="cropSrc"
      title="裁剪图片"
      :aspect="0.75"
      :file-name="cropName"
      :output-width="1080"
      @confirm="onCropConfirm"
      @cancel="onCropCancel"
    />
  </template>
</template>

<style scoped>
.hint {
  margin: 48px 20px 0 0;
  color: #8a8a8a;
  font-size: 14px;
}

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
