<!-- 运营 AI：模型、开关、耶耶文案。密钥不回显全文。 -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, ApiError, type OpsAiConfig } from '@onepage/api-client'

const QUESTION_MAX = 6
const QUESTION_SLOTS = 3

const loading = ref(false)
const saving = ref(false)
const probing = ref(false)
const failed = ref('')
const toast = ref('')
const persisted = ref(false)
const llmBaseUrl = ref('')
const llmModel = ref('')
const llmApiKey = ref('')
const llmKeySet = ref(false)
const llmKeyMasked = ref('')
const yehPreset = ref('')
const questions = ref<string[]>(['', '', ''])
const chatEnabled = ref(true)
const polishEnabled = ref(true)

const questionHint = computed(() => {
  const n = questions.value.filter((item) => item.trim()).length
  return `已填 ${n} / ${QUESTION_MAX} 条；点输入框后展开，点选即发送`
})

function flash(text: string) {
  toast.value = text
  window.setTimeout(() => {
    if (toast.value === text) toast.value = ''
  }, 1800)
}

function apply(data: OpsAiConfig) {
  llmBaseUrl.value = data.llmBaseUrl || ''
  llmModel.value = data.llmModel || ''
  llmKeySet.value = data.llmKeySet
  llmKeyMasked.value = data.llmKeyMasked || ''
  llmApiKey.value = ''
  yehPreset.value = data.yehPreset || ''
  const next = [...(data.yehQuestions || [])]
  while (next.length < QUESTION_SLOTS) next.push('')
  questions.value = next.slice(0, QUESTION_MAX)
  chatEnabled.value = data.chatEnabled
  polishEnabled.value = data.polishEnabled
  persisted.value = data.persisted
}

function questionList() {
  return questions.value.map((item) => item.trim()).filter(Boolean)
}

function patchBody(extra?: { clearLlmKey?: boolean }) {
  return {
    llmBaseUrl: llmBaseUrl.value.trim(),
    llmApiKey: llmApiKey.value.trim() || undefined,
    llmModel: llmModel.value.trim(),
    yehPreset: yehPreset.value.trim(),
    yehQuestions: questionList(),
    chatEnabled: chatEnabled.value,
    polishEnabled: polishEnabled.value,
    ...extra,
  }
}

async function load() {
  loading.value = true
  failed.value = ''
  try {
    apply(await api.opsAiConfig())
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
    apply(await api.opsSaveAiConfig(patchBody()))
    flash('已保存')
  } catch (e) {
    failed.value = e instanceof ApiError ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}

async function clearKey() {
  saving.value = true
  failed.value = ''
  try {
    apply(await api.opsSaveAiConfig(patchBody({ clearLlmKey: true })))
    flash('已清除密钥')
  } catch (e) {
    failed.value = e instanceof ApiError ? e.message : '清除失败'
  } finally {
    saving.value = false
  }
}

async function reset() {
  saving.value = true
  failed.value = ''
  try {
    apply(await api.opsResetAiConfig())
    flash('已恢复环境变量默认')
  } catch (e) {
    failed.value = e instanceof ApiError ? e.message : '恢复失败'
  } finally {
    saving.value = false
  }
}

async function probe() {
  probing.value = true
  failed.value = ''
  try {
    const res = await api.opsProbeAi({
      llmBaseUrl: llmBaseUrl.value.trim() || undefined,
      llmApiKey: llmApiKey.value.trim() || undefined,
      llmModel: llmModel.value.trim() || undefined,
    })
    if (res.ok) flash(res.message)
    else failed.value = res.message
  } catch (e) {
    failed.value = e instanceof ApiError ? e.message : '探测失败'
  } finally {
    probing.value = false
  }
}

function addQuestion() {
  if (questions.value.length >= QUESTION_MAX) return
  questions.value = [...questions.value, '']
}

function removeQuestion(index: number) {
  const next = questions.value.filter((_, i) => i !== index)
  while (next.length < QUESTION_SLOTS) next.push('')
  questions.value = next.slice(0, QUESTION_MAX)
}

onMounted(() => load())
</script>

<template>
  <div class="page">
    <section class="card">
      <div class="bar">
        <div>
          <h1>AI 配置</h1>
          <p class="lead">
            兼容 OpenAI Chat Completions。密钥只显示尾号。未单独保存时用环境变量。
          </p>
        </div>
        <div class="actions">
          <button type="button" class="ghost" :disabled="saving || loading" @click="reset">恢复默认</button>
          <button type="button" class="go" :disabled="saving || loading" @click="save">保存</button>
        </div>
      </div>
      <p v-if="toast" class="toast">{{ toast }}</p>
      <p v-if="failed" class="err">{{ failed }}</p>
      <p v-else-if="loading" class="muted">加载中…</p>
      <form v-else class="form" @submit.prevent="save">
        <p class="badge" :class="persisted ? 'is-saved' : ''">
          {{ persisted ? '已保存到 Redis，优先于环境变量' : '尚未单独保存，当前用环境变量' }}
        </p>

        <h2>模型接入</h2>
        <div class="row">
          <label>
            模型地址
            <input v-model="llmBaseUrl" placeholder="https://api.openai.com/v1" />
          </label>
          <label>
            模型名
            <input v-model="llmModel" placeholder="gpt-4o-mini" />
          </label>
        </div>
        <label>
          API 密钥
          <input
            v-model="llmApiKey"
            type="password"
            autocomplete="new-password"
            :placeholder="llmKeySet ? `已保存 ${llmKeyMasked}，留空保存则不改` : '未配置'"
          />
        </label>
        <div class="actions start">
          <button type="button" class="ghost" :disabled="probing || saving" @click="probe">探测连通</button>
          <button type="button" class="ghost" :disabled="saving || !llmKeySet" @click="clearKey">清除密钥</button>
        </div>

        <h2>能力开关</h2>
        <div class="switch-row">
          <button
            type="button"
            class="switch"
            :class="{ 'is-on': chatEnabled }"
            role="switch"
            :aria-checked="chatEnabled"
            @click="chatEnabled = !chatEnabled"
          >
            <span class="knob" />
          </button>
          <div>
            <p class="switch-title">耶耶问答</p>
            <p class="hint">关闭后社区问耶耶会提示暂时不能回答，不调模型。</p>
          </div>
        </div>
        <div class="switch-row">
          <button
            type="button"
            class="switch"
            :class="{ 'is-on': polishEnabled }"
            role="switch"
            :aria-checked="polishEnabled"
            @click="polishEnabled = !polishEnabled"
          >
            <span class="knob" />
          </button>
          <div>
            <p class="switch-title">发帖优化 / 续写</p>
            <p class="hint">关闭后创作者编辑页按钮会失败并保留原文。</p>
          </div>
        </div>

        <h2>耶耶首页</h2>
        <label>
          默认发送文案
          <input v-model="yehPreset" maxlength="80" placeholder="未点输入框时点问耶耶发出去的话" />
        </label>
        <p class="hint">{{ questionHint }}</p>
        <label v-for="(_, index) in questions" :key="index">
          联想 {{ index + 1 }}
          <div class="q-line">
            <input v-model="questions[index]" maxlength="40" :placeholder="`第 ${index + 1} 条`" />
            <button
              v-if="questions.length > QUESTION_SLOTS"
              type="button"
              class="ghost tight"
              @click="removeQuestion(index)"
            >
              删
            </button>
          </div>
        </label>
        <button
          v-if="questions.length < QUESTION_MAX"
          type="button"
          class="ghost"
          @click="addQuestion"
        >
          加一条联想
        </button>
      </form>
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

.actions.start {
  justify-content: flex-start;
}

h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

h2 {
  margin: 8px 0 0;
  font-size: 14px;
  font-weight: 600;
  color: #1f1f1f;
}

.lead,
.toast,
.err,
.muted,
.hint {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #8a8a8a;
}

.err {
  color: #ff2442;
}

.badge {
  margin: 0;
  display: inline-flex;
  align-self: flex-start;
  padding: 4px 10px;
  border-radius: 999px;
  background: #f3f3f3;
  color: #8a8a8a;
  font-size: 12px;
}

.badge.is-saved {
  background: #fff1f2;
  color: #ff2442;
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

.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 720px;
  margin-top: 20px;
}

.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: #8a8a8a;
}

.form input {
  width: 100%;
  height: 36px;
  padding: 0 12px;
  border: 0;
  border-radius: 12px;
  background: rgb(248, 248, 248);
  color: #1f1f1f;
  font: inherit;
  outline: none;
}

.q-line {
  display: flex;
  gap: 8px;
}

.switch-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.switch-title {
  margin: 0;
  font-size: 13px;
  color: #1f1f1f;
}

.switch-row .hint {
  margin: 4px 0 0;
}

.switch {
  flex: none;
  width: 40px;
  height: 24px;
  margin-top: 2px;
  border-radius: 12px;
  background: #e8e8e8;
  position: relative;
}

.switch.is-on {
  background: #ff2442;
}

.knob {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff;
  transition: left 160ms ease;
}

.switch.is-on .knob {
  left: 19px;
}

@media (max-width: 720px) {
  .row {
    grid-template-columns: 1fr;
  }
}
</style>
