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
const logoOk = ref(true)
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
    error.value = ''
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
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/45 px-4"
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
            <div class="relative h-[132px] w-[220px] bg-[#fafafa]">
              <img
                v-show="logoOk"
                src="/logo-zh.png?v=4"
                alt="一页"
                class="h-full w-full bg-[#fafafa] object-contain"
                @error="logoOk = false"
              />
              <p v-if="!logoOk" class="flex h-full flex-col items-center justify-center">
                <span class="text-[36px] font-semibold tracking-widest">一页</span>
                <span class="mt-1 text-[11px] tracking-[0.12em] text-[#8a8a8a]">Every Day, A New Page</span>
              </p>
            </div>
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
                <a href="/api/v1/auth/oauth/github/authorize" class="rounded-[20px] bg-[#f7f7f7] px-4 py-1.5 text-[12px] text-[#555] hover:bg-[#eee]">
                  GitHub
                </a>
                <a href="/api/v1/auth/oauth/google/authorize" class="rounded-[20px] bg-[#f7f7f7] px-4 py-1.5 text-[12px] text-[#555] hover:bg-[#eee]">
                  Google
                </a>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </Teleport>
</template>
