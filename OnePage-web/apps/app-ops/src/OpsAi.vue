<!-- 运营 AI 配置：模型、密钥、耶耶默认文案与联想。密钥不回显全文。 -->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, ApiError } from '@onepage/api-client'

const loading = ref(false)
const saving = ref(false)
const failed = ref('')
const toast = ref('')
const llmBaseUrl = ref('')
const llmModel = ref('')
const llmApiKey = ref('')
const llmKeySet = ref(false)
const llmKeyMasked = ref('')
const yehPreset = ref('')
const yehQuestionsText = ref('')
const chatEnabled = ref(true)
const polishEnabled = ref(true)

function flash(text: string) {
  toast.value = text
  window.setTimeout(() => {
    if (toast.value === text) toast.value = ''
  }, 1800)
}

async function load() {
  loading.value = true
  failed.value = ''
  try {
    const data = await api.opsAiConfig()
    llmBaseUrl.value = data.llmBaseUrl || ''
    llmModel.value = data.llmModel || ''
    llmKeySet.value = data.llmKeySet
    llmKeyMasked.value = data.llmKeyMasked || ''
    llmApiKey.value = ''
    yehPreset.value = data.yehPreset || ''
    yehQuestionsText.value = (data.yehQuestions || []).join('\n')
    chatEnabled.value = data.chatEnabled
    polishEnabled.value = data.polishEnabled
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
    const questions = yehQuestionsText.value
      .split('\n')
      .map((item) => item.trim())
      .filter(Boolean)
    const data = await api.opsSaveAiConfig({
      llmBaseUrl: llmBaseUrl.value.trim(),
      llmApiKey: llmApiKey.value.trim() || undefined,
      llmModel: llmModel.value.trim(),
      yehPreset: yehPreset.value.trim(),
      yehQuestions: questions,
      chatEnabled: chatEnabled.value,
      polishEnabled: polishEnabled.value,
    })
    llmKeySet.value = data.llmKeySet
    llmKeyMasked.value = data.llmKeyMasked || ''
    llmApiKey.value = ''
    flash('已保存')
  } catch (e) {
    failed.value = e instanceof ApiError ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}

onMounted(() => load())
</script>

<template>
  <div class="page">
    <section class="card">
      <div class="bar">
        <div>
          <h1>AI 配置</h1>
          <p class="lead">模型与密钥给润色/耶耶用；下面文案出现在耶耶首页。密钥只显示尾号，留空保存则不改。</p>
        </div>
        <button type="button" class="go" :disabled="saving || loading" @click="save">保存</button>
      </div>
      <p v-if="toast" class="toast">{{ toast }}</p>
      <p v-if="failed" class="err">{{ failed }}</p>
      <p v-else-if="loading" class="muted">加载中…</p>
      <form v-else class="form" @submit.prevent="save">
        <label>
          模型地址
          <input v-model="llmBaseUrl" placeholder="https://api.openai.com/v1" />
        </label>
        <label>
          模型名
          <input v-model="llmModel" placeholder="gpt-4o-mini" />
        </label>
        <label>
          API 密钥
          <input
            v-model="llmApiKey"
            type="password"
            autocomplete="new-password"
            :placeholder="llmKeySet ? `已保存 ${llmKeyMasked}，留空不改` : '未配置'"
          />
        </label>
        <div class="switches">
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
          <span>耶耶问答</span>
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
          <span>发帖优化 / 续写</span>
        </div>
        <label>
          耶耶默认发送文案
          <input v-model="yehPreset" maxlength="80" placeholder="不编辑时点问耶耶发出去的话" />
        </label>
        <label>
          首页联想（一行一条）
          <textarea v-model="yehQuestionsText" rows="6" placeholder="点编辑后不再显示这些联想" />
        </label>
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

h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.lead,
.toast,
.err,
.muted {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #8a8a8a;
}

.err {
  color: #ff2442;
}

.go {
  height: 36px;
  padding: 0 16px;
  border-radius: 18px;
  background: #ff2442;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.go:disabled {
  opacity: 0.5;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 640px;
  margin-top: 20px;
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
  resize: vertical;
  line-height: 1.5;
}

.switches {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #1f1f1f;
}

.switch {
  width: 40px;
  height: 24px;
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
</style>
