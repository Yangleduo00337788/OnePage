<!-- 创作者开播台：主区铺满。左 OBS 教程，右标题/密钥。关播二次确认。 -->
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api, ApiError, type LiveStart } from '@onepage/api-client'
import ImageCropDialog from '@onepage/image-crop'
import Icon from './Icon.vue'

const CATEGORIES = ['游戏', '才艺直播', '生活分享', '兴趣爱好', '科技新闻', '运动户外']
const community = import.meta.env.VITE_COMMUNITY_ORIGIN || 'http://localhost:5174'

const GUIDE = [
  {
    title: '填写本场信息',
    body: '右侧填写标题、分类，可选 3:4 封面，点「开始直播」。一场账号同时只能开一场。',
    img: '/live-guide-1.jpg',
  },
  {
    title: '打开 OBS',
    body: '安装并打开 OBS Studio（或同类推流软件）。本页不内嵌摄像头，画面由 OBS 推上来。',
    img: '/live-guide-2.jpg',
  },
  {
    title: '填入推流地址',
    body: 'OBS：设置 → 推流。服务选「自定义」，分别粘贴服务器和串流密钥。不要把密钥发给别人。',
    img: '/live-guide-3.jpg',
  },
  {
    title: '先推流，再进房',
    body: '先在 OBS 点「开始推流」，再点右侧「进入直播间」。观众在社区直播页看这场。',
    img: '/live-guide-4.jpg',
  },
  {
    title: '先关播，再停推',
    body: '结束时先点「结束直播」并确认，再在 OBS 点「停止推流」。不要先停 OBS。',
    img: '/live-guide-5.jpg',
  },
]

const title = ref('')
const category = ref('生活分享')
const starting = ref(false)
const ending = ref(false)
const askEnd = ref(false)
const failed = ref('')
const host = ref<LiveStart | null>(null)
const copied = ref('')
const coverMediaId = ref('')
const notice = ref('')
const noticeBusy = ref(false)
const coverPreview = ref('')
const cropSrc = ref('')
const cropName = ref('')
const uploading = ref(false)
const pick = ref<HTMLInputElement | null>(null)

const pushServer = computed(() => {
  const url = host.value?.pushUrl || ''
  const i = url.lastIndexOf('/')
  return i > 0 ? url.slice(0, i) : url
})

const streamKey = computed(() => host.value?.streamKey || '')

const guideOn = computed(() => {
  if (!host.value) return 0
  return 2
})

async function loadMine() {
  try {
    const mine = await api.myLive()
    host.value = mine ?? null
    if (mine?.notice) notice.value = mine.notice
  } catch {
    host.value = null
  }
}

function stopCrop() {
  if (cropSrc.value) URL.revokeObjectURL(cropSrc.value)
  cropSrc.value = ''
  cropName.value = ''
}

function setCoverPreview(next: string) {
  if (coverPreview.value.startsWith('blob:')) URL.revokeObjectURL(coverPreview.value)
  coverPreview.value = next
}

function pickCover(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  stopCrop()
  cropName.value = file.name
  cropSrc.value = URL.createObjectURL(file)
}

async function onCropped(file: File) {
  stopCrop()
  setCoverPreview(URL.createObjectURL(file))
  uploading.value = true
  failed.value = ''
  try {
    const mimeType = file.type || 'image/jpeg'
    const ticket = await api.presign(mimeType, file.size, 'cover')
    await api.putObject(ticket.uploadUrl, file, mimeType)
    await api.completeUpload(ticket.mediaId)
    coverMediaId.value = ticket.mediaId
  } catch (e) {
    setCoverPreview('')
    coverMediaId.value = ''
    failed.value = e instanceof ApiError ? e.message : '封面上传失败'
  } finally {
    uploading.value = false
  }
}

function clearCover() {
  coverMediaId.value = ''
  setCoverPreview('')
}

async function saveNotice() {
  if (!host.value || noticeBusy.value) return
  noticeBusy.value = true
  failed.value = ''
  try {
    const r = await api.updateLiveNotice(host.value.roomId, notice.value)
    host.value = { ...host.value, notice: r.notice }
    notice.value = r.notice
  } catch (e) {
    failed.value = e instanceof ApiError ? e.message : '保存失败'
  } finally {
    noticeBusy.value = false
  }
}

async function goLive() {
  const name = title.value.trim()
  if (!name || starting.value || uploading.value) return
  starting.value = true
  failed.value = ''
  try {
    const room = await api.createLiveRoom({
      title: name,
      category: category.value,
      coverMediaId: coverMediaId.value || undefined,
      notice: notice.value.trim() || undefined,
    })
    host.value = await api.startLive(room.roomId)
    title.value = ''
    notice.value = host.value.notice || ''
    coverMediaId.value = ''
    setCoverPreview('')
  } catch (e) {
    if (e instanceof ApiError && e.status === 409) {
      await loadMine()
    } else {
      failed.value = e instanceof ApiError ? e.message : '开播失败'
    }
  } finally {
    starting.value = false
  }
}

async function stopLive() {
  if (!host.value || ending.value) return
  ending.value = true
  try {
    await api.endLive(host.value.roomId)
  } catch {
    /* 关播失败仍清本机状态 */
  }
  host.value = null
  askEnd.value = false
  ending.value = false
}

function enterLive() {
  if (!host.value) return
  window.open(`${community}/live/${encodeURIComponent(host.value.sessionId)}`, '_blank', 'noopener,noreferrer')
}

async function copyText(label: string, text: string) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    copied.value = label
    window.setTimeout(() => {
      if (copied.value === label) copied.value = ''
    }, 1600)
  } catch {
    copied.value = ''
  }
}

onMounted(() => {
  void loadMine()
})

onUnmounted(() => {
  stopCrop()
  setCoverPreview('')
})
</script>

<template>
  <div class="live">
    <aside class="guide">
      <img class="hero" src="/live-guide-hero.jpg" alt="" width="320" height="240" />
      <h2>开播教程</h2>
      <p class="guide-lead">电脑直播用 OBS 推流。按顺序做，观众才能看到画面。</p>
      <ol>
        <li v-for="(item, i) in GUIDE" :key="item.title" :class="{ 'is-on': guideOn === i }">
          <img class="step" :src="item.img" alt="" width="56" height="56" />
          <div>
            <strong>{{ i + 1 }}. {{ item.title }}</strong>
            <p>{{ item.body }}</p>
          </div>
        </li>
      </ol>
    </aside>

    <article class="panel">
      <div class="inner">
        <div class="head">
          <h2>{{ host ? '正在直播' : '本场设置' }}</h2>
          <span v-if="host" class="pill">直播中</span>
        </div>

        <template v-if="!host">
          <p class="hint">填好后开始直播，左侧教程第 2 步起用 OBS。封面可选，比例 3:4。</p>
          <div class="cover-wrap">
            <input
              ref="pick"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              hidden
              @change="pickCover"
            />
            <button type="button" class="cover" :disabled="uploading" @click="pick?.click()">
              <img v-if="coverPreview" :src="coverPreview" alt="" />
              <span v-else>{{ uploading ? '上传中…' : '添加封面' }}</span>
            </button>
            <button v-if="coverPreview" type="button" class="cover-clear" @click="clearCover">去掉封面</button>
          </div>
          <label class="field">
            <span>标题</span>
            <input
              v-model="title"
              maxlength="100"
              placeholder="给这场直播起个名字"
              @keydown.enter.prevent="goLive"
            />
          </label>
          <label class="field">
            <span>分类</span>
            <select v-model="category">
              <option v-for="item in CATEGORIES" :key="item" :value="item">{{ item }}</option>
            </select>
          </label>
          <label class="field">
            <span>本场公告</span>
            <textarea v-model="notice" maxlength="200" placeholder="空则用默认欢迎文案" />
          </label>
          <p v-if="failed" class="err">{{ failed }}</p>
          <button
            type="button"
            class="go"
            :disabled="starting || uploading || !title.trim()"
            @click="goLive"
          >
            {{ starting ? '开播中…' : '开始直播' }}
          </button>
        </template>

        <template v-else>
          <p class="title">{{ host.title || '直播中' }}</p>
          <img v-if="host.coverUrl" class="cover-live" :src="host.coverUrl" alt="" />
          <label class="field">
            <span>本场公告</span>
            <textarea v-model="notice" maxlength="200" placeholder="空则用默认欢迎文案" />
          </label>
          <button type="button" class="end" :disabled="noticeBusy" @click="saveNotice">
            {{ noticeBusy ? '保存中…' : '保存公告' }}
          </button>
          <p v-if="failed" class="err">{{ failed }}</p>
          <p class="hint">复制下面两项到 OBS。先开始推流，再进直播间。关播请先点「结束直播」，再停 OBS。</p>
          <div class="row">
            <span>服务器</span>
            <code>{{ pushServer }}</code>
            <button type="button" @click="copyText('server', pushServer)">
              {{ copied === 'server' ? '已复制' : '复制' }}
            </button>
          </div>
          <div class="row">
            <span>串流密钥</span>
            <code>{{ streamKey }}</code>
            <button type="button" @click="copyText('key', streamKey)">
              {{ copied === 'key' ? '已复制' : '复制' }}
            </button>
          </div>
          <div class="row">
            <span>完整地址</span>
            <code>{{ host.pushUrl }}</code>
            <button type="button" @click="copyText('full', host.pushUrl)">
              {{ copied === 'full' ? '已复制' : '复制' }}
            </button>
          </div>
          <div class="actions">
            <button type="button" class="go" @click="enterLive">进入直播间</button>
            <button type="button" class="end" :disabled="ending" @click="askEnd = true">结束直播</button>
          </div>
        </template>
      </div>
    </article>

    <div v-if="askEnd" class="mask" @click.self="askEnd = false">
      <div class="dialog">
        <div class="dialog-head">
          <strong>结束直播</strong>
          <button type="button" class="x" aria-label="关闭" @click="askEnd = false">
            <Icon name="x" :size="16" />
          </button>
        </div>
        <p>确定结束这场直播吗？结束后观众将无法继续观看。请先在这里结束，再在 OBS 停止推流。</p>
        <button type="button" class="end" :disabled="ending" @click="askEnd = false">再想想</button>
        <button type="button" class="go" :disabled="ending" @click="stopLive">
          {{ ending ? '结束中…' : '确定结束' }}
        </button>
      </div>
    </div>
    <ImageCropDialog
      v-if="cropSrc"
      :src="cropSrc"
      title="裁剪直播封面"
      :aspect="0.75"
      :file-name="cropName"
      :output-width="1080"
      @confirm="onCropped"
      @cancel="stopCrop"
    />
  </div>
</template>

<style scoped>
.live {
  display: flex;
  flex: 1;
  min-height: 0;
  margin: 0 20px 20px 0;
  overflow: hidden;
  border-radius: 20px;
  background: #fff;
}

.guide {
  width: 400px;
  flex-shrink: 0;
  padding: 20px 24px 24px;
  overflow: auto;
  background: #fff;
  border-right: 1px solid #f2f2f2;
}

.hero {
  display: block;
  width: 100%;
  max-height: 200px;
  margin: 0 0 8px;
  object-fit: contain;
}

.step {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  border-radius: 14px;
  object-fit: cover;
  background: rgb(248, 248, 248);
}

.guide h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.guide-lead {
  margin: 8px 0 20px;
  color: #8a8a8a;
  font-size: 13px;
  line-height: 1.6;
}

.guide ol {
  margin: 0;
  padding: 0;
  list-style: none;
}

.guide li {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;
  color: #555;
}

.guide li.is-on .step {
  box-shadow: 0 0 0 2px #ff2442;
}

.guide li.is-on strong {
  color: #ff2442;
}

.guide strong {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #1f1f1f;
}

.guide li p {
  margin: 6px 0 0;
  color: #8a8a8a;
  font-size: 13px;
  line-height: 1.65;
}

.panel {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  justify-content: center;
  padding: 32px 24px;
  overflow: auto;
}

.inner {
  width: min(400px, 100%);
}

.head {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 12px;
}

.head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.pill {
  height: 22px;
  padding: 0 10px;
  border-radius: 11px;
  background: #ffe8ee;
  color: #ff2442;
  font-size: 12px;
  line-height: 22px;
}

.hint {
  margin: 0 0 20px;
  color: #8a8a8a;
  font-size: 13px;
  line-height: 1.6;
  text-align: center;
}

.cover-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.cover {
  overflow: hidden;
  width: 120px;
  height: 160px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
  color: #8a8a8a;
  font-size: 13px;
}

.cover img,
.cover-live {
  display: block;
  width: 120px;
  height: 160px;
  margin: 0 auto 12px;
  border-radius: 16px;
  object-fit: cover;
}

.cover img {
  width: 100%;
  height: 100%;
  margin: 0;
}

.cover-clear {
  color: #8a8a8a;
  font-size: 12px;
}

.cover-clear:hover {
  color: #1f1f1f;
}

.title {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  text-align: center;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 16px;
  color: #8a8a8a;
  font-size: 13px;
}

.field input,
.field select,
.field textarea {
  border: 0;
  border-radius: 22px;
  background: rgb(248, 248, 248);
  padding: 0 16px;
  color: #1f1f1f;
  font: inherit;
  font-size: 14px;
  outline: none;
}

.field input,
.field select {
  height: 44px;
}

.field textarea {
  min-height: 88px;
  padding: 12px 16px;
  border-radius: 16px;
  resize: none;
}

.err {
  margin: 0 0 8px;
  color: #8a8a8a;
  font-size: 13px;
}

.go,
.end {
  display: flex;
  height: 44px;
  align-items: center;
  justify-content: center;
  border-radius: 22px;
  font-size: 14px;
}

.go {
  width: 100%;
  background: #ff2442;
  color: #fff;
}

.go:disabled,
.end:disabled {
  opacity: 0.5;
}

.end {
  width: 100%;
  background: rgb(248, 248, 248);
  color: #1f1f1f;
}

.actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 20px;
}

.row {
  display: grid;
  grid-template-columns: 72px 1fr auto;
  align-items: center;
  gap: 12px;
  min-height: 44px;
  margin-bottom: 10px;
  padding: 0 16px;
  border-radius: 22px;
  background: rgb(248, 248, 248);
  color: #8a8a8a;
  font-size: 13px;
}

.row code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #1f1f1f;
  font-size: 13px;
}

.row button {
  color: #ff2442;
  font-size: 13px;
}

.mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
}

.dialog {
  width: min(400px, 90vw);
  padding: 16px 18px 20px;
  border-radius: 20px;
  background: #fff;
}

.dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 16px;
}

.x {
  display: flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.x:hover {
  background: #f2f2f2;
}

.dialog p {
  margin: 0 0 16px;
  color: #8a8a8a;
  font-size: 13px;
  line-height: 1.6;
}

.dialog .go,
.dialog .end {
  width: 100%;
  margin-top: 8px;
}

@media (max-width: 900px) {
  .live {
    flex-direction: column;
  }

  .guide {
    width: auto;
    max-height: 42%;
    border-right: 0;
    border-bottom: 1px solid #f2f2f2;
  }
}
</style>
