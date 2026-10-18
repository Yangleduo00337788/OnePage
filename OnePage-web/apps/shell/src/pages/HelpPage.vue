<!-- 帮助反馈 P0 写入 localStorage，不调后端。 -->
<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '../components/Icon.vue'
import { useSession } from '../stores/session'
import { useAuthModal } from '../stores/authModal'

const router = useRouter()
const session = useSession()
const auth = useAuthModal()

const content = ref('')
const contact = ref('')
const images = ref<{ file: File; url: string }[]>([])
const hint = ref('')
const sending = ref(false)

function pickImages(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  for (const file of files) {
    if (!file.type.startsWith('image/')) continue
    if (images.value.length >= 6) break
    images.value.push({ file, url: URL.createObjectURL(file) })
  }
}

function removeImage(i: number) {
  URL.revokeObjectURL(images.value[i].url)
  images.value.splice(i, 1)
}

async function submit() {
  hint.value = ''
  if (!content.value.trim()) {
    hint.value = '请填写反馈和意见'
    return
  }
  if (!session.me) {
    auth.show('login', '/help')
    return
  }
  sending.value = true
  try {
    const payload = {
      content: content.value.trim(),
      contact: contact.value.trim(),
      imageCount: images.value.length,
      at: Date.now(),
    }
    const prev = JSON.parse(localStorage.getItem('onepage-feedback') || '[]') as unknown[]
    prev.unshift(payload)
    localStorage.setItem('onepage-feedback', JSON.stringify(prev.slice(0, 20)))
    content.value = ''
    contact.value = ''
    images.value.forEach((item) => URL.revokeObjectURL(item.url))
    images.value = []
    hint.value = '已提交，我们会尽快查看'
  } finally {
    sending.value = false
  }
}

onUnmounted(() => {
  images.value.forEach((item) => URL.revokeObjectURL(item.url))
})
</script>

<template>
  <section class="w-full py-4">
    <div class="mb-4 flex items-center gap-2">
      <button type="button" class="page-back" aria-label="返回" @click="router.back()">
        <Icon name="chevron-left" :size="20" />
      </button>
      <h1 class="text-[22px] font-semibold">帮助与客服</h1>
    </div>
    <p class="mt-2 text-[14px] leading-6 text-[#8a8a8a]">遇到问题、想提建议，或需要人工协助，都可以写在这里。我们会按你留下的联系方式回复。</p>

    <label class="mt-6 block text-[14px] font-medium">反馈和意见</label>
    <textarea
      v-model="content"
      rows="6"
      class="field mt-2 w-full resize-y rounded-[12px] text-[14px]"
      placeholder="请尽量写清发生了什么、你期望怎样处理"
    />

    <label class="mt-5 block text-[14px] font-medium">图片（选填，最多 6 张）</label>
    <div class="mt-2 flex flex-wrap gap-2">
      <div v-for="(item, i) in images" :key="item.url" class="relative h-20 w-20 overflow-hidden rounded-xl bg-[#ececec]">
        <img :src="item.url" alt="" class="h-full w-full object-cover" />
        <button type="button" class="absolute top-1 right-1 rounded-full bg-black/55 px-1.5 text-[10px] text-white" @click="removeImage(i)">
          ×
        </button>
      </div>
      <label v-if="images.length < 6" class="flex h-20 w-20 cursor-pointer items-center justify-center rounded-xl bg-[#f7f7f7] text-[24px] text-[#bbb]">
        +
        <input type="file" accept="image/*" multiple class="hidden" @change="pickImages" />
      </label>
    </div>

    <label class="mt-5 block text-[14px] font-medium">联系方式</label>
    <input v-model="contact" class="field mt-2" placeholder="手机号 / 邮箱，方便我们联系你" />

    <p v-if="hint" class="mt-3 text-[13px]" :class="hint.startsWith('已') ? 'text-[#1f1f1f]' : 'text-[#ff2442]'">{{ hint }}</p>
    <button type="button" class="btn-auth mt-5 w-full max-w-[200px]" :disabled="sending" @click="submit">
      {{ sending ? '提交中…' : '提交' }}
    </button>
  </section>
</template>
