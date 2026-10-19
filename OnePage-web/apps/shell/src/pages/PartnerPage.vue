<!-- 主播入驻 / 大 V：POST /api/v1/support kind=live|verify。 -->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError } from '@onepage/api-client'
import Icon from '../components/Icon.vue'
import { useAuthModal } from '../stores/authModal'
import { useSession } from '../stores/session'

const route = useRoute()
const router = useRouter()
const session = useSession()
const auth = useAuthModal()
const isLive = computed(() => route.path.includes('live'))
const title = computed(() => (isLive.value ? '主播入驻' : '大 V 认证'))
const name = ref('')
const contact = ref('')
const intro = ref('')
const hint = ref('')
const sending = ref(false)

async function submit() {
  hint.value = ''
  if (!name.value.trim() || !contact.value.trim()) {
    hint.value = '请填写名称和联系方式'
    return
  }
  if (!session.me) {
    auth.show('login', route.path)
    return
  }
  sending.value = true
  try {
    await api.createSupport({
      kind: isLive.value ? 'live' : 'verify',
      title: name.value.trim(),
      contact: contact.value.trim(),
      body: intro.value.trim(),
    })
    name.value = ''
    contact.value = ''
    intro.value = ''
    hint.value = '已提交申请，我们会尽快联系你'
  } catch (e) {
    hint.value = e instanceof ApiError ? e.message : '提交失败'
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <section class="w-full py-4">
    <div class="mb-4 flex items-center gap-2">
      <button type="button" class="page-back" aria-label="返回" @click="router.back()">
        <Icon name="chevron-left" :size="20" />
      </button>
      <h1 class="text-[22px] font-semibold">{{ title }}</h1>
    </div>
    <p class="mt-2 text-[14px] leading-6 text-[#8a8a8a]">
      {{ isLive ? '申请开通直播能力，通过后可在直播页开播。' : '认证后主页展示认证标识，适合有稳定创作与影响力的账号。' }}
    </p>
    <label class="mt-6 block text-[14px] font-medium">名称 / 账号</label>
    <input v-model="name" class="field mt-2" placeholder="真实姓名或常用昵称" />
    <label class="mt-4 block text-[14px] font-medium">联系方式</label>
    <input v-model="contact" class="field mt-2" placeholder="手机号或邮箱" />
    <label class="mt-4 block text-[14px] font-medium">补充说明</label>
    <textarea v-model="intro" rows="4" class="field mt-2 resize-y" placeholder="过往作品、粉丝量或合作意向" />
    <p v-if="hint" class="mt-3 text-[13px]" :class="hint.startsWith('已') ? '' : 'text-[#ff2442]'">{{ hint }}</p>
    <button type="button" class="btn-auth mt-5 w-full max-w-[200px]" :disabled="sending" @click="submit">
      {{ sending ? '提交中…' : '提交申请' }}
    </button>
  </section>
</template>
