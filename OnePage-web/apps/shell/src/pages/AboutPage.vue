<!--
  关于我们。条款/帮助走弹窗。创作者弹窗点打开则整页去独立站。
  中间三张卡是可拖的装饰彩蛋。
-->
<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import Icon from '../components/Icon.vue'
import BrandLogo from '../components/BrandLogo.vue'
import { LEGAL } from '../data/legal'
import { useSession } from '../stores/session'
import { useAuthModal } from '../stores/authModal'
import { openCreatorSite } from '../creatorSite'

type Sheet = 'terms' | 'privacy' | 'help' | 'creator'
type Toy = { id: string; title: string; icon: string; x: number; y: number; rot: number; z: number; tint: string }

const sheet = ref<Sheet | null>(null)
const session = useSession()
const auth = useAuthModal()
const stage = ref<HTMLElement | null>(null)
let zTop = 3
let drag: { id: string; dx: number; dy: number } | null = null

const toys = ref<Toy[]>([
  { id: 'note', title: '记录每一页', icon: 'sticky-note', x: 52, y: 48, rot: -10, z: 1, tint: '#fff4ef' },
  { id: 'yeh', title: '耶耶来了', icon: 'message-circle-heart', x: 248, y: 86, rot: 8, z: 2, tint: '#eefcf8' },
  { id: 'live', title: '正在直播', icon: 'tv-minimal-play', x: 438, y: 32, rot: -6, z: 3, tint: '#f4f1ff' },
])

const sheetTitle: Record<Sheet, string> = {
  terms: '用户协议',
  privacy: '隐私政策',
  help: '帮助与客服',
  creator: '创作者中心',
}

const legalKind = computed((): 'privacy' | 'terms' => (sheet.value === 'privacy' ? 'privacy' : 'terms'))
const legalDoc = computed(() => LEGAL[legalKind.value])

const content = ref('')
const contact = ref('')
const images = ref<{ file: File; url: string }[]>([])
const hint = ref('')
const sending = ref(false)

function openSheet(next: Sheet) {
  sheet.value = next
  hint.value = ''
}

function closeSheet() {
  sheet.value = null
}

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

async function submitHelp() {
  hint.value = ''
  if (!content.value.trim()) {
    hint.value = '请填写反馈和意见'
    return
  }
  if (!session.me) {
    closeSheet()
    auth.show('login', '/about')
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

function onToyDown(e: PointerEvent, id: string) {
  const toy = toys.value.find((t) => t.id === id)
  const box = stage.value
  if (!toy || !box) return
  const r = box.getBoundingClientRect()
  drag = { id, dx: e.clientX - r.left - toy.x, dy: e.clientY - r.top - toy.y }
  zTop += 1
  toy.z = zTop
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
}

function onToyMove(e: PointerEvent) {
  if (!drag || !stage.value) return
  const toy = toys.value.find((t) => t.id === drag!.id)
  if (!toy) return
  const r = stage.value.getBoundingClientRect()
  toy.x = e.clientX - r.left - drag.dx
  toy.y = e.clientY - r.top - drag.dy
}

function onToyUp() {
  drag = null
}

onUnmounted(() => {
  images.value.forEach((item) => URL.revokeObjectURL(item.url))
})
</script>

<template>
  <article class="about-page">
    <header class="about-hero">
      <div class="mx-auto flex justify-center"><BrandLogo size="hero" /></div>
      <p class="mt-2 text-[12px] tracking-[0.16em] text-[#8a8a8a]">Every Day, A New Page</p>
      <h1 class="mt-6 text-[22px] font-semibold text-[var(--ink)]">关于我们</h1>
      <p class="mx-auto mt-3 max-w-[520px] text-[15px] leading-7 text-[var(--muted)]">
        ONEPAGE（一页）是生活方式社区。我们希望你每天都能留下一页：看见别人的日常，也写下自己的。
      </p>
    </header>

    <div ref="stage" class="about-stage" aria-label="可拖动的装饰卡片">
      <p class="about-stage-hint">随便拖一拖</p>
      <button
        v-for="toy in toys"
        :key="toy.id"
        type="button"
        class="about-toy"
        :style="{
          left: `${toy.x}px`,
          top: `${toy.y}px`,
          zIndex: toy.z,
          transform: `rotate(${toy.rot}deg)`,
          background: toy.tint,
        }"
        @pointerdown="onToyDown($event, toy.id)"
        @pointermove="onToyMove"
        @pointerup="onToyUp"
        @pointercancel="onToyUp"
      >
        <span class="about-toy-ico">
          <Icon :name="toy.icon" :size="22" />
        </span>
        <span class="about-toy-title">{{ toy.title }}</span>
      </button>
    </div>

    <section class="mt-8">
      <h2 class="text-[16px] font-semibold">联系与条款</h2>
      <p class="mt-2 text-[14px] leading-7 text-[var(--muted)]">
        使用本服务即表示你同意相关约定。点下面入口会打开弹窗，不离开这一页。
      </p>
      <div class="mt-5 flex flex-wrap gap-2">
        <button type="button" class="about-link" @click="openSheet('terms')">用户协议</button>
        <button type="button" class="about-link" @click="openSheet('privacy')">隐私政策</button>
        <button type="button" class="about-link" @click="openSheet('help')">帮助与客服</button>
        <button type="button" class="about-link" @click="openSheet('creator')">创作者中心</button>
      </div>
    </section>

    <p class="mt-14 text-center text-[12px] text-[#bbb]">ONEPAGE · 一页</p>
  </article>

  <Teleport to="body">
    <div v-if="sheet" class="about-mask" @click.self="closeSheet">
      <div class="about-dialog" role="dialog" :aria-label="sheetTitle[sheet]">
        <button type="button" class="icon-btn about-dialog-x" aria-label="关闭" @click="closeSheet">
          <Icon name="x" :size="18" />
        </button>
        <h2 class="pr-8 text-[18px] font-semibold">{{ sheetTitle[sheet] }}</h2>

        <div v-if="sheet === 'terms' || sheet === 'privacy'" class="about-dialog-body">
          <p class="text-[12px] text-[#8a8a8a]">更新于 {{ legalDoc.updated }}</p>
          <p class="mt-3 text-[14px] leading-7 text-[var(--muted)]">{{ legalDoc.intro }}</p>
          <section v-for="block in legalDoc.sections" :key="block.heading" class="mt-5">
            <h3 class="text-[14px] font-semibold">{{ block.heading }}</h3>
            <p v-for="p in block.paragraphs" :key="p" class="mt-2 text-[14px] leading-7 text-[var(--muted)]">{{ p }}</p>
          </section>
        </div>

        <div v-else-if="sheet === 'help'" class="about-dialog-body">
          <p class="text-[13px] leading-6 text-[#8a8a8a]">写下问题或建议，可附图和联系方式。</p>
          <textarea v-model="content" rows="5" class="field mt-3 text-[14px]" placeholder="反馈和意见" />
          <div class="mt-2 flex flex-wrap gap-1.5">
            <div v-for="(item, i) in images" :key="item.url" class="relative h-16 w-16 overflow-hidden rounded-lg bg-[var(--fill)]">
              <img :src="item.url" alt="" class="h-full w-full object-cover" />
              <button type="button" class="absolute top-0.5 right-0.5 rounded-full bg-black/55 px-1 text-[9px] text-white" @click="removeImage(i)">
                ×
              </button>
            </div>
            <label v-if="images.length < 6" class="flex h-16 w-16 cursor-pointer items-center justify-center rounded-lg bg-[var(--fill)] text-[20px] text-[#bbb]">
              +
              <input type="file" accept="image/*" multiple class="hidden" @change="pickImages" />
            </label>
          </div>
          <input v-model="contact" class="field mt-2 text-[14px]" placeholder="联系方式（手机 / 邮箱）" />
          <p v-if="hint" class="mt-2 text-[13px]" :class="hint.startsWith('已') ? '' : 'text-[#ff2442]'">{{ hint }}</p>
          <button type="button" class="btn-auth mt-4 h-10 max-w-[160px] text-[14px]" :disabled="sending" @click="submitHelp">
            {{ sending ? '提交中…' : '提交' }}
          </button>
        </div>

        <div v-else class="about-dialog-body">
          <p class="text-[13px] text-[#8a8a8a]">弹窗只作简介。完整数据与作品管理在独立创作者中心。</p>
          <div class="mt-4 grid grid-cols-3 gap-2">
            <div class="rounded-[16px] bg-[var(--fill)] p-3">
              <p class="text-[12px] text-[#8a8a8a]">近 7 日浏览</p>
              <p class="mt-2 text-[20px] font-semibold">—</p>
            </div>
            <div class="rounded-[16px] bg-[var(--fill)] p-3">
              <p class="text-[12px] text-[#8a8a8a]">新增关注</p>
              <p class="mt-2 text-[20px] font-semibold">—</p>
            </div>
            <div class="rounded-[16px] bg-[var(--fill)] p-3">
              <p class="text-[12px] text-[#8a8a8a]">互动</p>
              <p class="mt-2 text-[20px] font-semibold">—</p>
            </div>
          </div>
          <button type="button" class="btn-auth mt-4 h-9 text-[13px]" @click="closeSheet(); openCreatorSite()">
            打开创作者中心
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
