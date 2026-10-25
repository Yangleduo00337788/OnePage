<!--
  账号设置：设密/改密、短信找回、绑定 GitHub/Google。
  未登录打开登录弹窗后回本页。忘记密码在登录弹窗仍保留（未登录才能用）。
-->
<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '../components/Icon.vue'
import { api, ApiError } from '@onepage/api-client'
import { useSession } from '../stores/session'
import { useAuthModal } from '../stores/authModal'

const router = useRouter()
const session = useSession()
const auth = useAuthModal()

const phone = ref('')
const email = ref('')
const hasPassword = ref(false)
const githubBound = ref(false)
const googleBound = ref(false)

const oldPassword = ref('')
const newPassword = ref('')
const pwdError = ref('')
const pwdHint = ref('')
const pwdSaving = ref(false)

const resetPhone = ref('')
const resetCode = ref('')
const resetPasswordValue = ref('')
const resetError = ref('')
const resetHint = ref('')
const resetSaving = ref(false)
const showReset = ref(false)
const sendingCode = ref(false)
const cooldown = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | undefined

onUnmounted(() => {
  if (cooldownTimer) clearInterval(cooldownTimer)
})

function passwordRuleError(value: string) {
  if (value.length < 8 || value.length > 32 || !/[A-Za-z]/.test(value) || !/\d/.test(value)) {
    return '密码需 8～32 位，并同时包含字母和数字'
  }
  return ''
}

async function loadAccount() {
  await session.refresh()
  if (!session.me) {
    auth.show('login', '/settings')
    await router.replace('/')
    return
  }
  try {
    const acc = await api.account()
    phone.value = acc.phone
    email.value = acc.email
    hasPassword.value = acc.hasPassword
    githubBound.value = acc.githubBound
    googleBound.value = acc.googleBound
    if (acc.phone && !resetPhone.value) resetPhone.value = acc.phone
  } catch {
    hasPassword.value = false
  }
}

onMounted(() => {
  void loadAccount()
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

async function savePassword() {
  pwdError.value = ''
  pwdHint.value = ''
  const rule = passwordRuleError(newPassword.value)
  if (rule) {
    pwdError.value = rule
    return
  }
  if (hasPassword.value && !oldPassword.value) {
    pwdError.value = '请填写原密码'
    return
  }
  pwdSaving.value = true
  try {
    const setting = !hasPassword.value
    const acc = await api.setPassword({
      oldPassword: hasPassword.value ? oldPassword.value : undefined,
      password: newPassword.value,
    })
    hasPassword.value = acc.hasPassword
    oldPassword.value = ''
    newPassword.value = ''
    pwdHint.value = setting ? '密码已设置' : '密码已更新'
  } catch (e) {
    pwdError.value = e instanceof ApiError ? e.message : '设置失败'
  } finally {
    pwdSaving.value = false
  }
}

async function sendResetCode() {
  resetError.value = ''
  resetHint.value = ''
  const mobile = resetPhone.value.trim()
  if (!mobile) {
    resetError.value = '请填写手机号'
    return
  }
  sendingCode.value = true
  try {
    const res = await api.sendSms(mobile, 'reset')
    startCooldown(res.cooldownSec || 60)
    resetHint.value = '验证码已发送，开发环境填 888888'
  } catch (e) {
    resetError.value = e instanceof ApiError ? e.message : '发送失败'
  } finally {
    sendingCode.value = false
  }
}

async function resetBySms() {
  resetError.value = ''
  resetHint.value = ''
  const rule = passwordRuleError(resetPasswordValue.value)
  if (rule) {
    resetError.value = rule
    return
  }
  if (!resetPhone.value.trim()) {
    resetError.value = '请填写手机号'
    return
  }
  resetSaving.value = true
  try {
    await api.resetPassword({
      phone: resetPhone.value.trim(),
      code: resetCode.value,
      password: resetPasswordValue.value,
    })
    hasPassword.value = true
    resetCode.value = ''
    resetPasswordValue.value = ''
    resetHint.value = '密码已重置'
    await session.refresh()
  } catch (e) {
    resetError.value = e instanceof ApiError ? e.message : '重置失败'
  } finally {
    resetSaving.value = false
  }
}

function bindOAuth(provider: 'github' | 'google') {
  window.location.assign(`/api/v1/auth/oauth/${provider}/authorize?intent=bind`)
}
</script>

<template>
  <section class="settings-page">
    <div class="mb-5 flex items-center gap-2">
      <button type="button" class="page-back" aria-label="返回" @click="router.back()">
        <Icon name="chevron-left" :size="20" />
      </button>
      <h1 class="text-[22px] font-semibold leading-7">设置</h1>
    </div>
    <p class="text-[13px] leading-5 text-[#8a8a8a]">管理密码和第三方登录。头像和资料请到「我的」修改。</p>

    <div class="settings-card">
      <h2 class="text-[15px] font-semibold">账号</h2>
      <div class="mt-3">
        <div class="settings-kv">
          <span class="text-[13px] text-[#8a8a8a]">手机号</span>
          <span class="text-[14px]">{{ phone || '未绑定' }}</span>
        </div>
        <div class="settings-kv">
          <span class="text-[13px] text-[#8a8a8a]">邮箱</span>
          <span class="text-[14px]">{{ email || '未绑定' }}</span>
        </div>
      </div>
    </div>

    <div class="settings-card">
      <h2 class="text-[15px] font-semibold">{{ hasPassword ? '修改密码' : '设置密码' }}</h2>
      <p class="mt-1 text-[13px] leading-5 text-[#8a8a8a]">
        {{ hasPassword ? '验证原密码后设置新密码。' : '短信或第三方开户还没有密码，设好后可用手机号或邮箱登录。' }}
      </p>
      <form class="mt-4 space-y-3" @submit.prevent="savePassword">
        <input
          v-if="hasPassword"
          v-model="oldPassword"
          type="password"
          class="field-auth text-[14px]"
          placeholder="原密码"
          autocomplete="current-password"
        />
        <input
          v-model="newPassword"
          type="password"
          class="field-auth text-[14px]"
          placeholder="8～32 位，须含字母和数字"
          autocomplete="new-password"
        />
        <p v-if="pwdError" class="text-[13px] text-[#ff2442]">{{ pwdError }}</p>
        <p v-else-if="pwdHint" class="text-[13px] text-[var(--ink)]">{{ pwdHint }}</p>
        <button type="submit" class="btn-auth btn-auth-inline text-[14px]" :disabled="pwdSaving">
          {{ pwdSaving ? '保存中…' : '确定' }}
        </button>
      </form>
      <button
        type="button"
        class="mt-4 text-[13px] text-[#8a8a8a] hover:text-[var(--ink)]"
        @click="showReset = !showReset"
      >
        {{ showReset ? '收起找回密码' : '忘记密码？' }}
      </button>
      <form v-if="showReset" class="mt-4 space-y-3" @submit.prevent="resetBySms">
        <p class="text-[13px] leading-5 text-[#8a8a8a]">用该账号绑定的手机号收验证码，重设后可继续登录。</p>
        <input v-model="resetPhone" class="field-auth text-[14px]" placeholder="手机号" />
        <div class="flex gap-2">
          <input v-model="resetCode" class="field-auth flex-1 text-[14px]" placeholder="验证码" />
          <button
            type="button"
            class="btn-auth-ghost shrink-0 text-[13px]"
            :disabled="sendingCode || cooldown > 0"
            @click="sendResetCode"
          >
            {{ sendingCode ? '发送中…' : cooldown > 0 ? `${cooldown}s` : '获取验证码' }}
          </button>
        </div>
        <input
          v-model="resetPasswordValue"
          type="password"
          class="field-auth text-[14px]"
          placeholder="8～32 位，须含字母和数字"
          autocomplete="new-password"
        />
        <p v-if="resetError" class="text-[13px] text-[#ff2442]">{{ resetError }}</p>
        <p v-else-if="resetHint" class="text-[13px] text-[var(--ink)]">{{ resetHint }}</p>
        <button type="submit" class="btn-auth btn-auth-inline text-[14px]" :disabled="resetSaving">
          {{ resetSaving ? '提交中…' : '重置密码' }}
        </button>
      </form>
    </div>

    <div class="settings-card">
      <h2 class="text-[15px] font-semibold">第三方登录</h2>
      <p class="mt-1 text-[13px] leading-5 text-[#8a8a8a]">绑定后可用 GitHub 或 Google 进入同一个账号。</p>
      <div class="mt-4 flex flex-wrap items-center gap-4">
        <div class="settings-oauth-item">
          <button
            v-if="!githubBound"
            type="button"
            class="btn-oauth btn-oauth-icon"
            aria-label="绑定 GitHub"
            @click="bindOAuth('github')"
          >
            <svg width="22" height="22" viewBox="0 0 24 24" aria-hidden="true">
              <path
                fill="currentColor"
                d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 10.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"
              />
            </svg>
          </button>
          <span v-else class="settings-oauth-bound" title="GitHub 已绑定">
            <svg width="22" height="22" viewBox="0 0 24 24" aria-hidden="true">
              <path
                fill="currentColor"
                d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 10.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"
              />
            </svg>
            <span class="text-[11px] text-[#8a8a8a]">已绑定</span>
          </span>
        </div>
        <div class="settings-oauth-item">
          <button
            v-if="!googleBound"
            type="button"
            class="btn-oauth btn-oauth-icon"
            aria-label="绑定 Google"
            @click="bindOAuth('google')"
          >
            <svg width="22" height="22" viewBox="0 0 24 24" aria-hidden="true">
              <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
              <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
              <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" />
              <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" />
            </svg>
          </button>
          <span v-else class="settings-oauth-bound" title="Google 已绑定">
            <svg width="22" height="22" viewBox="0 0 24 24" aria-hidden="true">
              <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
              <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
              <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" />
              <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" />
            </svg>
            <span class="text-[11px] text-[#8a8a8a]">已绑定</span>
          </span>
        </div>
      </div>
    </div>
  </section>
</template>
