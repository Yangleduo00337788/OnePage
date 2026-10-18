<!--
  登录/注册弹窗。调 auth 接口；成功后 session.refresh 读 Cookie 是否生效。
  若接口 200 但 me 仍空，多半是 Vite 没剥掉 Secure Cookie 或网关没起。
-->
<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError } from '@onepage/api-client'
import { useAuthModal } from '../stores/authModal'
import { useSession } from '../stores/session'
import { LEGAL } from '../data/legal'
import Icon from './Icon.vue'
import BrandLogo from './BrandLogo.vue'

const auth = useAuthModal()
const session = useSession()
const router = useRouter()

const loginTab = ref<'password' | 'sms'>('sms')
const account = ref('')
const password = ref('')
const phone = ref('')
const code = ref('')
const email = ref('')
const nickname = ref('')
const error = ref('')
const hint = ref('')
const loading = ref(false)
const sendingCode = ref(false)
const cooldown = ref(0)
const agreed = ref(false)
const resetting = ref(false)
const legal = ref<null | 'terms' | 'privacy'>(null)
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

watch(
  () => [auth.open, auth.mode] as const,
  ([visible]) => {
    if (!visible) return
    error.value = auth.notice || ''
    hint.value = ''
    legal.value = null
    agreed.value = false
    resetting.value = false
  },
)

/** 登录成功：再拉 /users/me，确认 Session Cookie 真的带上了，再关弹窗跳转。 */
async function afterAuth() {
  await session.refresh()
  if (!session.me) {
    error.value = '登录未保持，请确认网关已启动后重试'
    return
  }
  auth.hide()
  await router.push(auth.redirect || '/me')
}

function requireAgree() {
  if (agreed.value) return true
  error.value = '请先阅读并同意用户协议和隐私协议'
  return false
}

function startOAuth(provider: 'github' | 'google') {
  error.value = ''
  hint.value = ''
  if (!requireAgree()) return
  window.location.assign(`/api/v1/auth/oauth/${provider}/authorize`)
}

function passwordRuleError(value: string) {
  if (value.length < 8 || value.length > 32 || !/[A-Za-z]/.test(value) || !/\d/.test(value)) {
    return '密码需 8～32 位，并同时包含字母和数字'
  }
  return ''
}

async function login() {
  error.value = ''
  hint.value = ''
  if (!requireAgree()) return
  loading.value = true
  try {
    if (loginTab.value === 'password') {
      await api.login(account.value, password.value)
    } else {
      await api.smsLogin(phone.value, code.value)
    }
    await afterAuth()
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
    const res = await api.sendSms(mobile, resetting.value ? 'reset' : 'login')
    startCooldown(res.cooldownSec || 60)
    hint.value = '验证码已发送，开发环境填 888888'
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '发送失败'
  } finally {
    sendingCode.value = false
  }
}

async function register() {
  error.value = ''
  hint.value = ''
  if (!requireAgree()) return
  if (!phone.value.trim() && !email.value.trim()) {
    error.value = '手机号、邮箱至少填一项'
    return
  }
  const passwordError = passwordRuleError(password.value)
  if (passwordError) {
    error.value = passwordError
    return
  }
  loading.value = true
  try {
    await api.register({
      phone: phone.value || undefined,
      email: email.value || undefined,
      password: password.value,
      nickname: nickname.value || undefined,
    })
    await afterAuth()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '注册失败'
  } finally {
    loading.value = false
  }
}

async function resetPassword() {
  error.value = ''
  hint.value = ''
  if (!requireAgree()) return
  if (!phone.value.trim()) {
    error.value = '请填写手机号'
    return
  }
  const passwordError = passwordRuleError(password.value)
  if (passwordError) {
    error.value = passwordError
    return
  }
  loading.value = true
  try {
    await api.resetPassword({
      phone: phone.value.trim(),
      code: code.value,
      password: password.value,
    })
    await afterAuth()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '重置失败'
  } finally {
    loading.value = false
  }
}

function openReset() {
  error.value = ''
  hint.value = ''
  resetting.value = true
  loginTab.value = 'password'
}

const doc = () => (legal.value ? LEGAL[legal.value] : null)

function onClose() {
  if (legal.value) legal.value = null
  else if (resetting.value) resetting.value = false
  else auth.hide()
}

function openLegal(kind: 'terms' | 'privacy') {
  legal.value = kind
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="auth.open"
      class="fixed inset-0 z-[100] flex items-center justify-center bg-black/45 px-4"
      @click.self="auth.hide()"
    >
      <div
        class="relative flex h-[480px] w-[800px] max-w-[calc(100vw-32px)] overflow-hidden rounded-[20px] bg-white shadow-[0_20px_60px_rgba(0,0,0,0.16)]"
      >
        <button
          type="button"
          class="absolute top-3 right-3 z-10 flex h-8 w-8 items-center justify-center rounded-full text-[#999] hover:bg-black/5 hover:text-[#333]"
          aria-label="关闭"
          @click="onClose()"
        >
          <Icon name="x" :size="18" />
        </button>

        <div v-if="legal && doc()" class="h-full w-full overflow-y-auto px-10 py-8">
          <button type="button" class="mb-4 text-[13px] text-[#8a8a8a] hover:text-[#333]" @click="legal = null">
            ← 返回{{ resetting ? '找回密码' : auth.mode === 'login' ? '登录' : '注册' }}
          </button>
          <h2 class="text-[18px] font-semibold">{{ doc()!.title }}</h2>
          <p class="mt-1 text-[12px] text-[#bbb]">更新日期 {{ doc()!.updated }}</p>
          <p class="mt-4 text-[13px] leading-6 text-[#555]">{{ doc()!.intro }}</p>
          <div class="mt-5 space-y-5 text-[13px] leading-6 text-[#555]">
            <section v-for="sec in doc()!.sections" :key="sec.heading">
              <h3 class="mb-1.5 font-semibold text-[#1f1f1f]">{{ sec.heading }}</h3>
              <p v-for="(p, i) in sec.paragraphs" :key="i" class="mt-1">{{ p }}</p>
            </section>
          </div>
        </div>

        <template v-else>
          <aside class="relative flex h-full w-1/2 flex-col items-center justify-center overflow-hidden bg-[#fafafa] px-8">
            <span class="pointer-events-none absolute -top-16 -left-16 h-40 w-40 rounded-full bg-[#ff2442]/[0.06]" />
            <span class="pointer-events-none absolute right-[-40px] bottom-[-30px] h-36 w-36 rounded-full bg-[#ff2442]/[0.05]" />
            <span class="pointer-events-none absolute top-12 right-10 h-2 w-2 rounded-full bg-[#ff2442]/40" />
            <span class="pointer-events-none absolute top-24 left-8 h-1.5 w-1.5 rounded-full bg-[#1f1f1f]/15" />
            <span class="pointer-events-none absolute bottom-16 left-12 h-8 w-8 rounded-full border border-[#ff2442]/15" />
            <div class="relative"><BrandLogo size="auth" /></div>
            <div class="relative mt-3 flex flex-wrap justify-center gap-2">
              <span class="rounded-full bg-white/80 px-2.5 py-0.5 text-[11px] text-[#8a8a8a] ring-1 ring-[#eee]">记录生活</span>
              <span class="rounded-full bg-white/80 px-2.5 py-0.5 text-[11px] text-[#8a8a8a] ring-1 ring-[#eee]">耶耶 AI</span>
              <span class="rounded-full bg-white/80 px-2.5 py-0.5 text-[11px] text-[#8a8a8a] ring-1 ring-[#eee]">直播</span>
            </div>
            <div
              class="relative mt-6 flex h-[128px] w-[128px] items-center justify-center rounded-[16px] bg-white text-center text-[12px] leading-5 text-[#bbb] shadow-[0_4px_16px_rgba(0,0,0,0.04)] ring-1 ring-dashed ring-[#eee]"
            >
              App 扫码登录即将接入
            </div>
          </aside>

          <div class="flex h-full w-1/2 min-w-0 flex-col justify-center overflow-y-auto px-10 py-8">
            <div v-if="!resetting" class="relative grid grid-cols-2 rounded-[20px] bg-[#f7f7f7] p-1 text-[14px]">
              <span
                class="pointer-events-none absolute top-1 bottom-1 left-1 w-[calc(50%-4px)] rounded-[20px] bg-white shadow-sm transition-transform duration-300 ease-out"
                :class="auth.mode === 'register' ? 'translate-x-full' : 'translate-x-0'"
              />
              <button
                type="button"
                class="relative z-[1] py-2 font-medium transition-colors duration-300"
                :class="auth.mode === 'login' ? 'text-[#1f1f1f]' : 'text-[#8a8a8a]'"
                @click="auth.mode = 'login'"
              >
                登录
              </button>
              <button
                type="button"
                class="relative z-[1] py-2 font-medium transition-colors duration-300"
                :class="auth.mode === 'register' ? 'text-[#1f1f1f]' : 'text-[#8a8a8a]'"
                @click="auth.mode = 'register'"
              >
                注册
              </button>
            </div>

            <form v-if="resetting" class="mt-5 space-y-3" @submit.prevent="resetPassword">
              <button type="button" class="text-[13px] text-[#8a8a8a] hover:text-[#333]" @click="resetting = false">
                ← 返回登录
              </button>
              <p class="text-[15px] font-medium text-[#1f1f1f]">找回密码</p>
              <input v-model="phone" class="field-auth" placeholder="手机号" />
              <div class="flex gap-2">
                <input v-model="code" class="field-auth flex-1" placeholder="验证码" />
                <button
                  type="button"
                  class="btn-auth-ghost shrink-0 text-[13px]"
                  :disabled="sendingCode || cooldown > 0"
                  @click="sendCode"
                >
                  {{ sendingCode ? '发送中…' : cooldown > 0 ? `${cooldown}s` : '获取验证码' }}
                </button>
              </div>
              <input v-model="password" type="password" class="field-auth" placeholder="新密码 8～32 位，须含字母和数字" />
              <p class="text-[12px] text-[#bbb]">仅已用该手机号开过户的账号可重置。开发环境验证码 888888</p>
              <p v-if="hint" class="text-[13px] text-[#1f1f1f]">{{ hint }}</p>
              <p v-if="error" class="text-[13px] text-[#ff2442]">{{ error }}</p>
              <button class="btn-auth" :disabled="loading">{{ loading ? '提交中…' : '重置并登录' }}</button>
            </form>

            <form v-else-if="auth.mode === 'login'" class="mt-5 space-y-3" @submit.prevent="login">
              <div class="flex gap-4 text-[13px]">
                <button
                  type="button"
                  class="pb-1"
                  :class="loginTab === 'sms' ? 'font-medium text-[#1f1f1f] shadow-[inset_0_-2px_0_#ff2442]' : 'text-[#8a8a8a]'"
                  @click="loginTab = 'sms'"
                >
                  验证码
                </button>
                <button
                  type="button"
                  class="pb-1"
                  :class="loginTab === 'password' ? 'font-medium text-[#1f1f1f] shadow-[inset_0_-2px_0_#ff2442]' : 'text-[#8a8a8a]'"
                  @click="loginTab = 'password'"
                >
                  密码
                </button>
              </div>
              <template v-if="loginTab === 'password'">
                <input v-model="account" class="field-auth" placeholder="手机号或邮箱" />
                <input v-model="password" type="password" class="field-auth" placeholder="密码" />
                <button type="button" class="text-left text-[12px] text-[#8a8a8a] hover:text-[#ff2442]" @click="openReset">
                  忘记密码
                </button>
              </template>
              <template v-else>
                <input v-model="phone" class="field-auth" placeholder="手机号" />
                <div class="flex gap-2">
                  <input v-model="code" class="field-auth flex-1" placeholder="验证码" />
                  <button
                    type="button"
                    class="btn-auth-ghost shrink-0 text-[13px]"
                    :disabled="sendingCode || cooldown > 0"
                    @click="sendCode"
                  >
                    {{ sendingCode ? '发送中…' : cooldown > 0 ? `${cooldown}s` : '获取验证码' }}
                  </button>
                </div>
                <p class="text-[12px] text-[#bbb]">开发环境验证码固定 888888</p>
              </template>
              <p v-if="hint" class="text-[13px] text-[#1f1f1f]">{{ hint }}</p>
              <p v-if="error" class="text-[13px] text-[#ff2442]">{{ error }}</p>
              <button class="btn-auth" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>
            </form>

            <form v-else class="mt-5 space-y-3" @submit.prevent="register">
              <input v-model="nickname" class="field-auth" placeholder="昵称（可选）" />
              <input v-model="phone" class="field-auth" placeholder="手机号" />
              <input v-model="email" class="field-auth" placeholder="邮箱" />
              <p class="text-[12px] text-[#bbb]">手机号、邮箱至少填一项，昵称可不填</p>
              <input v-model="password" type="password" class="field-auth" placeholder="密码 8～32 位，须含字母和数字" />
              <p v-if="error" class="text-[13px] text-[#ff2442]">{{ error }}</p>
              <button class="btn-auth" :disabled="loading">{{ loading ? '创建中…' : '注册并登录' }}</button>
            </form>

            <label class="mt-4 flex cursor-pointer items-center gap-2 text-[12px] leading-none text-[#8a8a8a]">
              <input v-model="agreed" type="checkbox" class="h-3.5 w-3.5 shrink-0 accent-[#ff2442]" />
              <span class="leading-none">
                我已阅读并同意
                <button type="button" class="align-middle text-[#ff2442]" @click.prevent.stop="openLegal('terms')">《用户协议》</button>
                和
                <button type="button" class="align-middle text-[#ff2442]" @click.prevent.stop="openLegal('privacy')">《隐私政策》</button>
              </span>
            </label>

            <div v-if="auth.mode === 'login' && !resetting" class="mt-5">
              <div class="flex items-center gap-3 text-[12px] text-[#ccc]">
                <span class="h-px flex-1 bg-[#eee]" />
                其他方式
                <span class="h-px flex-1 bg-[#eee]" />
              </div>
              <div class="mt-3 flex justify-center gap-3">
                <button type="button" class="btn-oauth" @click="startOAuth('github')">
                  <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true">
                    <path
                      fill="currentColor"
                      d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 10.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"
                    />
                  </svg>
                  GitHub
                </button>
                <button type="button" class="btn-oauth" @click="startOAuth('google')">
                  <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true">
                    <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
                    <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
                    <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" />
                    <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" />
                  </svg>
                  Google
                </button>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </Teleport>
</template>
