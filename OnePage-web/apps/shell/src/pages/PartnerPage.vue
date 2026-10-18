<!-- 主播入驻 / 大 V：同一组件看路径区分，提交仅前端提示。 -->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '../components/Icon.vue'

const route = useRoute()
const router = useRouter()
const isLive = computed(() => route.path.includes('live'))
const title = computed(() => (isLive.value ? '主播入驻' : '大 V 认证'))
const name = ref('')
const contact = ref('')
const intro = ref('')
const hint = ref('')

function submit() {
  if (!name.value.trim() || !contact.value.trim()) {
    hint.value = '请填写名称和联系方式'
    return
  }
  hint.value = '已提交申请，我们会尽快联系你'
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
    <p v-if="hint" class="mt-3 text-[13px]">{{ hint }}</p>
    <button type="button" class="btn-auth mt-5 w-full max-w-[200px]" @click="submit">提交申请</button>
  </section>
</template>
