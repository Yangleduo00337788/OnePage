<!-- 运营 AI 按功能分页：模型 / 耶耶 / 发帖。保存只改本页字段。 -->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api, ApiError, type OpsAiConfig } from '@onepage/api-client'

const props = defineProps<{ kind: 'model' | 'yeh' | 'polish' | 'review' }>()

const QUESTION_MAX = 6
const QUESTION_SLOTS = 3

const titles = {
  model: {
    h1: '模型',
    lead: '两套兼容 OpenAI Chat Completions：文字给耶耶、润色和审稿正文；看图给识图和画面机审。密钥只显示尾号。「恢复默认」会清掉 Redis 里全部 AI 配置。',
  },
  yeh: {
    h1: '耶耶',
    lead: '开关、RAG 注入、问答提示词、首页文案。保存只改耶耶，不动模型和发帖。',
  },
  polish: {
    h1: '发帖',
    lead: '创作者编辑页的优化 / 续写。保存只改发帖，不动模型、耶耶和审稿。',
  },
  review: {
    h1: '审稿',
    lead: '文字走文字模型，图文画面走看图模型，两套分数取高再比阈值。视频抽帧下一步。先保存再试审。',
  },
} as const

const heading = computed(() => titles[props.kind])

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
const chatSystem = ref('')
const polishSystem = ref('')
const continueSystem = ref('')
const retrieveLimit = ref(8)
const contextBodyMax = ref(400)
const temperature = ref(0.4)
const maxTokens = ref(0)
const streamEnabled = ref(false)
const reviewEnabled = ref(true)
const reviewSystem = ref('')
const reviewRiskLimit = ref(40)
const visionLlmBaseUrl = ref('')
const visionLlmModel = ref('')
const visionLlmApiKey = ref('')
const visionLlmKeySet = ref(false)
const visionLlmKeyMasked = ref('')
const tryScore = ref<number | null>(null)
const tryTitle = ref('周末去海边')
const tryBody = ref('今天天气很好，拍了几张日落。')
const tryImageUrls = ref('')
const trying = ref(false)
const tryVerdict = ref('')
const tryReason = ref('')

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
  chatSystem.value = data.chatSystem || ''
  polishSystem.value = data.polishSystem || ''
  continueSystem.value = data.continueSystem || ''
  retrieveLimit.value = data.retrieveLimit || 8
  contextBodyMax.value = data.contextBodyMax || 400
  temperature.value = data.temperature ?? 0.4
  maxTokens.value = data.maxTokens ?? 0
  streamEnabled.value = data.streamEnabled
  reviewEnabled.value = data.reviewEnabled !== false
  reviewSystem.value = data.reviewSystem || ''
  reviewRiskLimit.value = data.reviewRiskLimit || 40
  visionLlmBaseUrl.value = data.visionLlmBaseUrl || ''
  visionLlmModel.value = data.visionLlmModel || ''
  visionLlmKeySet.value = data.visionLlmKeySet
  visionLlmKeyMasked.value = data.visionLlmKeyMasked || ''
  visionLlmApiKey.value = ''
}

function questionList() {
  return questions.value.map((item) => item.trim()).filter(Boolean)
}

function patchBody(extra?: { clearLlmKey?: boolean; clearVisionLlmKey?: boolean }) {
  if (props.kind === 'model') {
    return {
      llmBaseUrl: llmBaseUrl.value.trim(),
      llmApiKey: llmApiKey.value.trim() || undefined,
      llmModel: llmModel.value.trim(),
      temperature: Number.isFinite(Number(temperature.value)) ? Number(temperature.value) : 0.4,
      maxTokens: Number.isFinite(Number(maxTokens.value)) ? Math.max(0, Number(maxTokens.value)) : 0,
      streamEnabled: streamEnabled.value,
      visionLlmBaseUrl: visionLlmBaseUrl.value.trim(),
      visionLlmApiKey: visionLlmApiKey.value.trim() || undefined,
      visionLlmModel: visionLlmModel.value.trim(),
      ...extra,
    }
  }
  if (props.kind === 'yeh') {
    return {
      yehPreset: yehPreset.value.trim(),
      yehQuestions: questionList(),
      chatEnabled: chatEnabled.value,
      chatSystem: chatSystem.value,
      retrieveLimit: retrieveLimit.value,
      contextBodyMax: contextBodyMax.value,
    }
  }
  if (props.kind === 'review') {
    return {
      reviewEnabled: reviewEnabled.value,
      reviewSystem: reviewSystem.value,
      reviewRiskLimit: Number.isFinite(Number(reviewRiskLimit.value))
        ? Math.min(100, Math.max(1, Number(reviewRiskLimit.value)))
        : 40,
    }
  }
  return {
    polishEnabled: polishEnabled.value,
    polishSystem: polishSystem.value,
    continueSystem: continueSystem.value,
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

async function clearKey(which: 'text' | 'vision') {
  saving.value = true
  failed.value = ''
  try {
    const extra = which === 'vision' ? { clearVisionLlmKey: true } : { clearLlmKey: true }
    apply(await api.opsSaveAiConfig(patchBody(extra)))
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

async function probe(which: 'text' | 'vision') {
  probing.value = true
  failed.value = ''
  try {
    const res =
      which === 'vision'
        ? await api.opsProbeVisionAi({
            visionLlmBaseUrl: visionLlmBaseUrl.value.trim() || undefined,
            visionLlmApiKey: visionLlmApiKey.value.trim() || undefined,
            visionLlmModel: visionLlmModel.value.trim() || undefined,
          })
        : await api.opsProbeAi({
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

function tryLabel(verdict: string) {
  if (verdict === 'pass') return '会自动上架'
  if (verdict === 'flag') return '会进人审'
  if (verdict === 'skip') return '未跑机审'
  return verdict
}

async function tryReview() {
  trying.value = true
  failed.value = ''
  tryVerdict.value = ''
  tryReason.value = ''
  tryScore.value = null
  try {
    const imageUrls = tryImageUrls.value
      .split('\n')
      .map((item) => item.trim())
      .filter(Boolean)
      .slice(0, 4)
    const res = await api.opsTryReviewAi({
      title: tryTitle.value.trim(),
      body: tryBody.value.trim(),
      imageUrls: imageUrls.length ? imageUrls : undefined,
    })
    tryVerdict.value = res.verdict || ''
    tryReason.value = res.reason || ''
    tryScore.value = res.score ?? null
    flash(tryLabel(tryVerdict.value))
  } catch (e) {
    failed.value = e instanceof ApiError ? e.message : '试审失败'
  } finally {
    trying.value = false
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

watch(
  () => props.kind,
  () => {
    void load()
  },
)

onMounted(() => load())
</script>

<template>
  <div class="page">
    <section class="card">
      <div class="bar">
        <div>
          <h1>{{ heading.h1 }}</h1>
          <p class="lead">{{ heading.lead }}</p>
        </div>
        <div class="actions">
          <button
            v-if="kind === 'model'"
            type="button"
            class="ghost"
            :disabled="saving || loading"
            @click="reset"
          >
            恢复默认
          </button>
          <button type="button" class="go" :disabled="saving || loading" @click="save">保存</button>
        </div>
      </div>
      <p v-if="toast" class="toast">{{ toast }}</p>
      <p v-if="failed" class="err">{{ failed }}</p>
      <p v-else-if="loading" class="muted">加载中…</p>
      <form v-else class="form" @submit.prevent="save">
        <p v-if="kind === 'model'" class="badge" :class="persisted ? 'is-saved' : ''">
          {{ persisted ? '已保存到 Redis，优先于环境变量' : '尚未单独保存，当前用环境变量' }}
        </p>

        <template v-if="kind === 'model'">
        <p class="switch-title">文字模型</p>
        <p class="hint">耶耶、润色、审稿正文共用。</p>
        <div class="row">
          <label>
            文字模型地址
            <input v-model="llmBaseUrl" placeholder="https://api.openai.com/v1" />
          </label>
          <label>
            文字模型名
            <input v-model="llmModel" placeholder="gpt-4o-mini" />
          </label>
        </div>
        <label>
          文字 API 密钥
          <input
            v-model="llmApiKey"
            type="password"
            autocomplete="new-password"
            :placeholder="llmKeySet ? `已保存 ${llmKeyMasked}，留空保存则不改` : '未配置'"
          />
        </label>
        <div class="actions start">
          <button type="button" class="ghost" :disabled="probing || saving" @click="probe('text')">探测连通</button>
          <button type="button" class="ghost" :disabled="saving || !llmKeySet" @click="clearKey('text')">
            清除密钥
          </button>
        </div>
        <p class="switch-title">看图模型</p>
        <p class="hint">可选。给耶耶识图和笔记画面机审预留；未配则画面先不审。可填视觉大模型或 OCR 接口（兼容 Chat Completions）。</p>
        <div class="row">
          <label>
            看图模型地址
            <input v-model="visionLlmBaseUrl" placeholder="可空" />
          </label>
          <label>
            看图模型名
            <input v-model="visionLlmModel" placeholder="可空" />
          </label>
        </div>
        <label>
          看图 API 密钥
          <input
            v-model="visionLlmApiKey"
            type="password"
            autocomplete="new-password"
            :placeholder="visionLlmKeySet ? `已保存 ${visionLlmKeyMasked}，留空保存则不改` : '未配置'"
          />
        </label>
        <div class="actions start">
          <button type="button" class="ghost" :disabled="probing || saving" @click="probe('vision')">探测连通</button>
          <button
            type="button"
            class="ghost"
            :disabled="saving || !visionLlmKeySet"
            @click="clearKey('vision')"
          >
            清除密钥
          </button>
        </div>
        <div class="row">
          <label>
            温度（0–2）
            <input v-model.number="temperature" type="number" min="0" max="2" step="0.1" />
          </label>
          <label>
            最大 token（0 不限制）
            <input v-model.number="maxTokens" type="number" min="0" max="8192" />
          </label>
        </div>
        <p class="hint">温度越高越发散。最大 token 写入上游 max_tokens；0 表示不设上限。探测走整段返回，不走流式。</p>
        <div class="switch-row">
          <button
            type="button"
            class="switch"
            :class="{ 'is-on': streamEnabled }"
            role="switch"
            :aria-checked="streamEnabled"
            @click="streamEnabled = !streamEnabled"
          >
            <span class="knob" />
          </button>
          <div>
            <p class="switch-title">耶耶流式输出</p>
            <p class="hint">打开后按片段吐字；关闭则一次生成完再显示。发帖润色始终整段 JSON，不受此开关影响。</p>
          </div>
        </div>
        </template>

        <template v-if="kind === 'yeh'">
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
        <p class="hint">
          问答先用问题检索公开笔记，再把命中标题和正文注入 user。搜不到不调模型，回复「暂时不能根据站内笔记回答」。
        </p>
        <div class="row">
          <label>
            检索条数（1–20）
            <input v-model.number="retrieveLimit" type="number" min="1" max="20" />
          </label>
          <label>
            每条正文字数（50–2000）
            <input v-model.number="contextBodyMax" type="number" min="50" max="2000" />
          </label>
        </div>
        <p class="hint">以 system 角色注入模型。用户问题和笔记仍走 user。留空保存则回到内置文案。</p>
        <label>
          问答提示词
          <textarea v-model="chatSystem" rows="5" maxlength="4000" />
        </label>
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
        </template>

        <template v-if="kind === 'polish'">
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
        <p class="hint">以 system 角色注入模型。留空保存则回到内置文案。</p>
        <label>
          优化提示词
          <textarea v-model="polishSystem" rows="5" maxlength="4000" />
        </label>
        <label>
          续写提示词
          <textarea v-model="continueSystem" rows="5" maxlength="4000" />
        </label>
        </template>

        <template v-if="kind === 'review'">
        <div class="switch-row">
          <button
            type="button"
            class="switch"
            :class="{ 'is-on': reviewEnabled }"
            role="switch"
            :aria-checked="reviewEnabled"
            @click="reviewEnabled = !reviewEnabled"
          >
            <span class="knob" />
          </button>
          <div>
            <p class="switch-title">公开笔记机审</p>
            <p class="hint">标题正文走文字模型，图文画面走看图模型，分数取高再比阈值。低于阈值自动上架；达阈值或模型挂了进人审，不自动驳回。视频抽帧下一步。</p>
          </div>
        </div>
        <label>
          进人审的风险分阈值（1–100）
          <input v-model.number="reviewRiskLimit" type="number" min="1" max="100" step="1" />
        </label>
        <p class="hint">分数低于此值自动过；大于等于此值推给人。默认 40。改完请保存。</p>
        <p class="hint">以 system 角色注入审稿模型。留空保存则回到内置文案。</p>
        <label>
          审稿提示词
          <textarea v-model="reviewSystem" rows="5" maxlength="4000" />
        </label>
        <p class="hint">试审不发帖、不进队列。文字用本页提示词，画面用内置看图审稿。附图须是本站媒体地址，每行一条，最多 4 张。改了提示词请先保存。</p>
        <label>
          试审标题
          <input v-model="tryTitle" maxlength="80" placeholder="随便写一条会过或不过的标题" />
        </label>
        <label>
          试审正文
          <textarea v-model="tryBody" rows="4" maxlength="2000" placeholder="例如加微信、发票，或正常生活记录" />
        </label>
        <label>
          试审附图地址
          <textarea
            v-model="tryImageUrls"
            rows="3"
            maxlength="2000"
            placeholder="每行一条本站图片地址，可空"
          />
        </label>
        <div class="actions start">
          <button
            type="button"
            class="go"
            :disabled="trying || saving || (!tryTitle.trim() && !tryBody.trim() && !tryImageUrls.trim())"
            @click="tryReview"
          >
            {{ trying ? '试审中…' : '试审' }}
          </button>
        </div>
        <p v-if="tryVerdict" class="hint">
          结论：{{ tryLabel(tryVerdict)
          }}{{ tryScore != null ? ` · 风险 ${tryScore}` : ''
          }}{{ tryReason ? ` · ${tryReason}` : '' }}
        </p>
        </template>
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

.form input,
.form textarea {
  width: 100%;
  padding: 0 12px;
  border: 0;
  border-radius: 12px;
  background: rgb(248, 248, 248);
  color: #1f1f1f;
  font: inherit;
  outline: none;
}

.form input {
  height: 36px;
}

.form textarea {
  padding: 10px 12px;
  min-height: 96px;
  resize: vertical;
  line-height: 1.5;
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
