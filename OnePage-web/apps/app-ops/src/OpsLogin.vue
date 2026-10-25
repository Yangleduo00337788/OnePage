<!-- 运营独立登录：只登录不注册。audience=ops，非白名单不种 Session。 -->
<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { api, ApiError, type UserProfile } from '@onepage/api-client'
import DemoLoginCreds from '@onepage/demo-login-hints'
import BrandLogo from './BrandLogo.vue'

const emit = defineEmits<{ done: [UserProfile] }>()

const loginTab = ref<'password' | 'sms'>('sms')
const account = ref('')
const password = ref('')
const phone = ref('')
const code = ref('')
const error = ref('')
const hint = ref('')
const loading = ref(false)
const sendingCode = ref(false)
const cooldown = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | undefined

onUnmounted(() => {
  if (cooldownTimer) clearInterval(cooldownTimer)
})

function startCooldown(sec: number) {
  cooldown.value = sec
  if (cooldownTimer) clearInterval(cooldownTimer)
  cooldownTimer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0 && cooldownTimer) {
      clearInterval(cooldownTimer)
      cooldownTimer = undefined
    }
  }, 1000)
}

async function login() {
  error.value = ''
  hint.value = ''
  loading.value = true
  try {
    if (loginTab.value === 'password') await api.login(account.value, password.value, 'ops')
    else await api.smsLogin(phone.value, code.value, 'ops')
    const profile = await api.me()
    emit('done', profile)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '登录失败'
  } finally {
    loading.value = false
  }
}

async function sendCode() {
  error.value = ''
  hint.value = ''
  const mobile = phone.value.trim()
  if (!mobile) {
    error.value = '请先填写手机号'
    return
  }
  sendingCode.value = true
  try {
    const res = await api.sendSms(mobile, 'login')
    startCooldown(res.cooldownSec || 60)
    hint.value = '验证码已发送，开发环境填 888888'
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '发送失败'
  } finally {
    sendingCode.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="brand">
      <BrandLogo size="hero" />
      <p class="slogan">运营后台</p>
      <p class="en">内部员工，不对外开放</p>
    </div>
    <aside class="panel">
      <h1>登录运营后台</h1>
      <p class="sub">使用员工账号。此处不能注册</p>
      <form class="form" @submit.prevent="login">
        <div class="tabs">
          <button type="button" class="tab" :class="{ 'is-on': loginTab === 'sms' }" @click="loginTab = 'sms'">
            验证码
          </button>
          <button
            type="button"
            class="tab"
            :class="{ 'is-on': loginTab === 'password' }"
            @click="loginTab = 'password'"
          >
            密码
          </button>
        </div>
        <DemoLoginCreds site="ops" layout="form" class="demo-creds-panel" />
        <template v-if="loginTab === 'password'">
          <input v-model="account" placeholder="手机号或邮箱" />
          <input v-model="password" type="password" placeholder="密码" />
        </template>
        <template v-else>
          <input v-model="phone" placeholder="手机号" />
          <div class="row">
            <input v-model="code" placeholder="验证码" />
            <button type="button" class="ghost" :disabled="sendingCode || cooldown > 0" @click="sendCode">
              {{ sendingCode ? '发送中…' : cooldown > 0 ? `${cooldown}s` : '获取验证码' }}
            </button>
          </div>
          <p class="muted">开发环境验证码固定 888888</p>
        </template>
        <p v-if="hint" class="ok">{{ hint }}</p>
        <p v-if="error" class="err">{{ error }}</p>
        <button class="cta" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>
      </form>
    </aside>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 40px;
  box-sizing: border-box;
  height: 100%;
  overflow: auto;
  padding: 48px 40px;
  color: #1f1f1f;
  background: #f4f1ea;
}

.brand {
  position: relative;
  z-index: 1;
  flex: 1;
  min-width: 0;
}

.slogan {
  margin: 12px 0 0;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 0.12em;
}

.demo-creds-panel {
  align-self: stretch;
  width: 100%;
}

.en {
  margin: 10px 0 0;
  color: #8a8a8a;
  font-size: 13px;
  letter-spacing: 0.08em;
}

.panel {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  width: 380px;
  max-height: calc(100vh - 40px);
  overflow: auto;
  padding: 32px 28px 28px;
  box-sizing: border-box;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 16px 48px rgba(80, 60, 40, 0.12);
}

h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.sub {
  margin: 8px 0 0;
  color: #8a8a8a;
  font-size: 13px;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 28px;
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

.tab:hover,
.tab.is-on {
  color: #1f1f1f;
  font-weight: 600;
  background: rgb(248, 248, 248);
}

.form input {
  height: 40px;
  padding: 0 14px;
  border: 0;
  border-radius: 12px;
  background: rgb(248, 248, 248);
  color: #1f1f1f;
  font: inherit;
  outline: none;
}

.row {
  display: flex;
  gap: 8px;
}

.row input {
  flex: 1;
  min-width: 0;
}

.ghost {
  flex-shrink: 0;
  height: 40px;
  padding: 0 12px;
  border-radius: 20px;
  background: rgb(248, 248, 248);
  color: #555;
  font-size: 13px;
}

.ghost:disabled {
  color: #bbb;
}

.cta {
  height: 40px;
  margin-top: 6px;
  border-radius: 20px;
  background: #ff2442;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}

.cta:hover:not(:disabled) {
  background: #e61e3a;
}

.cta:disabled {
  opacity: 0.7;
}

.muted,
.ok,
.err {
  margin: 0;
  font-size: 13px;
}

.muted {
  color: #bbb;
}

.ok {
  color: #1f1f1f;
}

.err {
  color: #ff2442;
}

@media (max-width: 720px) {
  .page {
    flex-direction: column;
    align-items: stretch;
    padding: 32px 20px 20px;
  }

  .panel {
    width: 100%;
  }
}
</style>
