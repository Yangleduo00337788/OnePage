<!-- 社区规则：左资料/上传数字卡，右违禁词胶囊。 -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, ApiError, type CommunityPolicy } from '@onepage/api-client'

const loading = ref(false)
const saving = ref(false)
const failed = ref('')
const toast = ref('')
const draft = ref('')
const nicknameMax = ref(24)
const bioMax = ref(160)
const imageMaxMb = ref(10)
const videoMaxMb = ref(200)
const banned = ref<string[]>([])

const wordCount = computed(() => banned.value.length)

function flash(text: string) {
  toast.value = text
  window.setTimeout(() => {
    if (toast.value === text) toast.value = ''
  }, 1800)
}

function apply(data: CommunityPolicy) {
  banned.value = [...(data.bannedWords || [])]
  nicknameMax.value = data.nicknameMax
  bioMax.value = data.bioMax
  imageMaxMb.value = data.imageMaxMb
  videoMaxMb.value = data.videoMaxMb
}

function addWord() {
  const word = draft.value.trim()
  if (!word) return
  if (word.length > 32) {
    flash('每条不超过 32 字')
    return
  }
  if (banned.value.includes(word)) {
    flash('已经有这条')
    draft.value = ''
    return
  }
  if (banned.value.length >= 200) {
    flash('最多 200 条')
    return
  }
  banned.value = banned.value.concat(word)
  draft.value = ''
}

function dropWord(word: string) {
  banned.value = banned.value.filter((item) => item !== word)
}

async function load() {
  loading.value = true
  failed.value = ''
  try {
    apply(await api.opsPolicy())
  } catch (e) {
    failed.value = e instanceof ApiError ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  failed.value = ''
  try {
    apply(
      await api.opsSavePolicy({
        bannedWords: banned.value,
        nicknameMax: Number(nicknameMax.value),
        bioMax: Number(bioMax.value),
        imageMaxMb: Number(imageMaxMb.value),
        videoMaxMb: Number(videoMaxMb.value),
      }),
    )
    flash('已保存')
  } catch (e) {
    failed.value = e instanceof ApiError ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page">
    <section class="card">
      <div class="bar">
        <div>
          <h1>社区规则</h1>
          <p class="lead">改资料和上传按这里校验，保存后约 15 秒生效。</p>
        </div>
        <div class="actions">
          <button type="button" class="ghost" :disabled="loading || saving" @click="load">刷新</button>
          <button type="button" class="go" :disabled="loading || saving" @click="save">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
      <p v-if="toast" class="ok">{{ toast }}</p>
      <p v-if="failed" class="err">{{ failed }}</p>
      <p v-else-if="loading" class="muted">加载中…</p>
      <div v-else class="layout">
        <div class="col">
          <section class="panel">
            <h2>资料</h2>
            <div class="metrics">
              <label class="metric">
                <span class="metric-k">昵称</span>
                <span class="metric-v">
                  <input v-model.number="nicknameMax" type="number" min="1" max="64" />
                  <span>字</span>
                </span>
                <span class="metric-h">1–64</span>
              </label>
              <label class="metric">
                <span class="metric-k">签名</span>
                <span class="metric-v">
                  <input v-model.number="bioMax" type="number" min="1" max="500" />
                  <span>字</span>
                </span>
                <span class="metric-h">1–500</span>
              </label>
            </div>
          </section>
          <section class="panel">
            <h2>上传</h2>
            <p class="hint">头像、封面、笔记图、私信、反馈走图片；笔记视频走视频。时长不限。</p>
            <div class="metrics">
              <label class="metric">
                <span class="metric-k">图片</span>
                <span class="metric-v">
                  <input v-model.number="imageMaxMb" type="number" min="1" max="50" />
                  <span>MB</span>
                </span>
                <span class="metric-h">1–50</span>
              </label>
              <label class="metric">
                <span class="metric-k">视频</span>
                <span class="metric-v">
                  <input v-model.number="videoMaxMb" type="number" min="1" max="2048" />
                  <span>MB</span>
                </span>
                <span class="metric-h">1–2048</span>
              </label>
            </div>
          </section>
        </div>
        <section class="panel words">
          <div class="words-head">
            <h2>违禁词</h2>
            <span class="count">{{ wordCount }} / 200</span>
          </div>
          <form class="add" @submit.prevent="addWord">
            <input v-model="draft" maxlength="32" placeholder="输入后回车添加" />
            <button type="submit" class="ghost tight">添加</button>
          </form>
          <p v-if="!banned.length" class="empty">还没有词。添加后拦昵称、签名、发帖、评论、私信、弹幕、帮助申请；图片视频不扫画面</p>
          <div v-else class="chips">
            <span v-for="word in banned" :key="word" class="chip">
              {{ word }}
              <button type="button" class="x" aria-label="删除" @click="dropWord(word)">×</button>
            </span>
          </div>
        </section>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page {
  flex: 1;
  min-height: 0;
  padding: 0 20px 20px 0;
}

.card {
  min-height: calc(100vh - 92px);
  padding: 20px 24px 28px;
  border-radius: 20px;
  background: #fff;
}

.bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.actions {
  display: flex;
  gap: 8px;
}

h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

h2 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #1f1f1f;
}

.lead,
.ok,
.err,
.muted,
.hint,
.empty {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #8a8a8a;
}

.err {
  color: #ff2442;
}

.ok {
  color: #1f1f1f;
}

.go,
.ghost {
  height: 36px;
  padding: 0 16px;
  border-radius: 18px;
  font-size: 14px;
  font-weight: 600;
}

.go {
  background: #ff2442;
  color: #fff;
}

.ghost {
  background: #f3f3f3;
  color: #1f1f1f;
}

.ghost.tight {
  padding: 0 12px;
}

.go:disabled,
.ghost:disabled {
  opacity: 0.5;
}

.layout {
  display: grid;
  grid-template-columns: minmax(280px, 360px) 1fr;
  gap: 16px;
  margin-top: 20px;
  align-items: stretch;
}

.col {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel {
  padding: 16px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
}

.panel.words {
  min-height: 420px;
}

.metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 12px;
}

.metric {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px 12px;
  border-radius: 14px;
  background: #fff;
}

.metric-k,
.metric-h {
  color: #8a8a8a;
  font-size: 12px;
}

.metric-v {
  display: flex;
  align-items: baseline;
  gap: 6px;
  color: #8a8a8a;
  font-size: 13px;
}

.metric-v input {
  width: 88px;
  height: 32px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #1f1f1f;
  font-size: 22px;
  font-weight: 600;
  outline: none;
}

.words-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.count {
  color: #8a8a8a;
  font-size: 12px;
}

.add {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.add input {
  flex: 1;
  height: 36px;
  padding: 0 12px;
  border: 0;
  border-radius: 12px;
  background: #fff;
  color: #1f1f1f;
  font: inherit;
  outline: none;
}

.empty {
  margin-top: 20px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 4px 0 12px;
  border-radius: 16px;
  background: #fff;
  color: #1f1f1f;
  font-size: 13px;
}

.x {
  width: 24px;
  height: 24px;
  border-radius: 12px;
  color: #8a8a8a;
  font-size: 16px;
  line-height: 24px;
}

.x:hover {
  background: #f3f3f3;
  color: #1f1f1f;
}

@media (max-width: 960px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
