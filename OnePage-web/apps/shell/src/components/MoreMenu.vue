<!--
  侧栏「更多」：无遮罩弹窗。协议/帮助等在弹窗内切 view，不跳路由（直达 /privacy 仍可用）。
  已登录才有设置、退出。设密/绑定在 /settings。
-->
<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import Icon from './Icon.vue'
import { LEGAL } from '../data/legal'
import { useMoreMenu } from '../stores/moreMenu'
import { useTheme, type ThemePref } from '../stores/theme'
import { useRoute, useRouter } from 'vue-router'
import { useSession } from '../stores/session'
import { useAuthModal } from '../stores/authModal'
import { openCreatorSite } from '../creatorSite'

type View = 'menu' | 'privacy' | 'terms' | 'help' | 'biz' | 'live' | 'verify' | 'shortcuts'

const more = useMoreMenu()
const theme = useTheme()
const session = useSession()
const auth = useAuthModal()
const route = useRoute()
const router = useRouter()
const panelRef = ref<HTMLElement | null>(null)
const view = ref<View>('menu')
const toast = ref('')

const THEMES: { id: ThemePref; icon: string; label: string }[] = [
  { id: 'system', icon: 'monitor', label: '跟随系统' },
  { id: 'light', icon: 'sun', label: '浅色模式' },
  { id: 'dark', icon: 'moon', label: '深色模式' },
]
const themeIndex = computed(() => Math.max(0, THEMES.findIndex((item) => item.id === theme.pref)))

const titles: Record<View, string> = {
  menu: '更多',
  privacy: '隐私',
  terms: '协议',
  help: '帮助与客服',
  biz: '业务合作',
  live: '主播入驻',
  verify: '大 V 认证',
  shortcuts: '键盘快捷键',
}

const legalKind = computed((): 'privacy' | 'terms' => (view.value === 'privacy' ? 'privacy' : 'terms'))
const legalDoc = computed(() => LEGAL[legalKind.value])

const content = ref('')
const contact = ref('')
const images = ref<{ file: File; url: string }[]>([])
const hint = ref('')
const sending = ref(false)
const applyName = ref('')
const applyContact = ref('')
const applyIntro = ref('')
const applyHint = ref('')

const shortcuts = [
  { keys: '/', action: '聚焦顶栏搜索' },
  { keys: 'Enter', action: '在搜索框中进入搜索页' },
  { keys: 'Esc', action: '关闭弹窗' },
]

function openView(next: View) {
  view.value = next
}

function back() {
  if (view.value === 'live' || view.value === 'verify') view.value = 'biz'
  else view.value = 'menu'
}

function addToDesktop() {
  const url = `${location.origin}/`
  const blob = new Blob([`[InternetShortcut]\r\nURL=${url}\r\n`], { type: 'application/octet-stream' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = 'ONEPAGE.url'
  a.click()
  URL.revokeObjectURL(a.href)
  toast.value = '已下载快捷方式，放到桌面即可'
  window.setTimeout(() => {
    toast.value = ''
  }, 3200)
}

function openSettings() {
  more.hide()
  if (!session.me) {
    auth.show('login', '/settings')
    return
  }
  void router.push('/settings')
}

async function logout() {
  more.hide()
  if (!session.me) {
    auth.show('login')
    return
  }
  await session.logout()
  if (route.path === '/me' || route.path === '/settings') {
    await router.replace('/')
  }
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
  sending.value = true
  try {
    const prev = JSON.parse(localStorage.getItem('onepage-feedback') || '[]') as unknown[]
    prev.unshift({
      content: content.value.trim(),
      contact: contact.value.trim(),
      imageCount: images.value.length,
      at: Date.now(),
    })
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

function submitApply() {
  if (!applyName.value.trim() || !applyContact.value.trim()) {
    applyHint.value = '请填写名称和联系方式'
    return
  }
  applyHint.value = '已提交申请，我们会尽快联系你'
}

function onDocDown(e: MouseEvent) {
  const t = e.target as HTMLElement
  if (panelRef.value?.contains(t)) return
  if (t.closest('.nav-more')) return
  more.hide()
}

watch(
  () => more.open,
  (open) => {
    if (open) {
      view.value = 'menu'
      window.setTimeout(() => document.addEventListener('mousedown', onDocDown), 0)
    } else {
      document.removeEventListener('mousedown', onDocDown)
      view.value = 'menu'
    }
  },
)

onUnmounted(() => {
  document.removeEventListener('mousedown', onDocDown)
  images.value.forEach((item) => URL.revokeObjectURL(item.url))
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="more.open"
      ref="panelRef"
      class="more-panel pointer-events-auto fixed z-50 flex max-h-[min(640px,calc(100vh-48px))] flex-col overflow-hidden rounded-[16px] bg-[var(--bg)] shadow-[0_16px_48px_rgba(0,0,0,0.16)] ring-1 ring-[#eee]"
      :class="view === 'menu' ? 'is-menu' : 'is-page'"
      :style="{ left: `${more.left}px`, bottom: `${more.bottom}px` }"
    >
      <template v-if="view === 'menu'">
        <div class="overflow-y-auto px-2 py-2">
          <button type="button" class="more-row" @click="openView('privacy')">
            <span>隐私</span>
            <Icon name="chevron" :size="14" class="text-[#bbb]" />
          </button>
          <button type="button" class="more-row" @click="openView('terms')">
            <span>协议</span>
            <Icon name="chevron" :size="14" class="text-[#bbb]" />
          </button>
          <button type="button" class="more-row" @click="openView('help')">
            <span>帮助与客服</span>
            <Icon name="chevron" :size="14" class="text-[#bbb]" />
          </button>

          <div class="more-split" />

          <button type="button" class="more-row" @click="more.hide(); openCreatorSite()">
            <span>创作者中心</span>
            <Icon name="arrow-up-right" :size="14" class="text-[#bbb]" />
          </button>
          <button type="button" class="more-row" @click="openView('biz')">
            <span>业务合作</span>
            <Icon name="chevron" :size="14" class="text-[#bbb]" />
          </button>

          <div class="more-split" />

          <button type="button" class="more-row" @click="openView('shortcuts')">
            <span>键盘快捷键</span>
            <Icon name="chevron" :size="14" class="text-[#bbb]" />
          </button>
          <button type="button" class="more-row" @click="addToDesktop">
            <span>添加 ONEPAGE 到桌面</span>
          </button>
          <p v-if="toast" class="px-2 pb-1 text-[11px] leading-4 text-[#8a8a8a]">{{ toast }}</p>

          <div class="more-split" />

          <div class="more-row more-row-theme">
            <span>主题模式</span>
            <div class="theme-slide" role="radiogroup" aria-label="主题模式">
              <span class="theme-thumb" :style="{ transform: `translateX(${themeIndex * 24}px)` }" />
              <button
                v-for="item in THEMES"
                :key="item.id"
                type="button"
                class="theme-ico"
                :class="theme.pref === item.id ? 'is-on' : ''"
                :title="item.label"
                :aria-label="item.label"
                @click="theme.setPref(item.id)"
              >
                <Icon :name="item.icon" :size="14" :stroke="1.75" />
              </button>
            </div>
          </div>

          <button v-if="session.me" type="button" class="more-row" @click="openSettings">
            <span>设置</span>
            <Icon name="chevron" :size="14" class="text-[#bbb]" />
          </button>
          <button type="button" class="more-row" @click="logout">
            {{ session.me ? '退出登录' : '登录' }}
          </button>
        </div>
      </template>

      <template v-else>
        <div class="flex shrink-0 items-center gap-1 border-b border-[#eee] px-2 py-2">
          <button type="button" class="flex h-7 w-7 items-center justify-center rounded-full hover:bg-[#f2f2f2]" @click="back">
            <Icon name="chevron-left" :size="16" />
          </button>
          <h2 class="text-[14px] font-semibold">{{ titles[view] }}</h2>
        </div>
        <div class="more-page overflow-y-auto px-3 py-3">
          <template v-if="view === 'privacy' || view === 'terms'">
            <p class="text-[11px] text-[#8a8a8a]">更新于 {{ legalDoc.updated }}</p>
            <p class="mt-2 text-[12px] leading-5 text-[#555]">{{ legalDoc.intro }}</p>
            <section v-for="block in legalDoc.sections" :key="block.heading" class="mt-3">
              <h3 class="text-[12px] font-semibold">{{ block.heading }}</h3>
              <p v-for="p in block.paragraphs" :key="p" class="mt-1 text-[12px] leading-5 text-[#555]">{{ p }}</p>
            </section>
          </template>

          <template v-else-if="view === 'help'">
            <p class="text-[12px] leading-5 text-[#8a8a8a]">写下问题或建议，可附图和联系方式。</p>
            <textarea v-model="content" rows="5" class="field mt-3 text-[12px]" placeholder="反馈和意见" />
            <div class="mt-2 flex flex-wrap gap-1.5">
              <div v-for="(item, i) in images" :key="item.url" class="relative h-14 w-14 overflow-hidden rounded-lg bg-[#ececec]">
                <img :src="item.url" alt="" class="h-full w-full object-cover" />
                <button type="button" class="absolute top-0.5 right-0.5 rounded-full bg-black/55 px-1 text-[9px] text-white" @click="removeImage(i)">
                  ×
                </button>
              </div>
              <label v-if="images.length < 6" class="flex h-14 w-14 cursor-pointer items-center justify-center rounded-lg bg-[#f7f7f7] text-[20px] text-[#bbb]">
                +
                <input type="file" accept="image/*" multiple class="hidden" @change="pickImages" />
              </label>
            </div>
            <input v-model="contact" class="field mt-2 text-[12px]" placeholder="联系方式（手机 / 邮箱）" />
            <p v-if="hint" class="mt-2 text-[12px]" :class="hint.startsWith('已') ? '' : 'text-[#ff2442]'">{{ hint }}</p>
            <button type="button" class="btn-auth mt-3 h-9 text-[13px]" :disabled="sending" @click="submitHelp">
              {{ sending ? '提交中…' : '提交' }}
            </button>
          </template>

          <template v-else-if="view === 'biz'">
            <button type="button" class="more-row" @click="openView('live')">
              <span>主播入驻</span>
              <Icon name="chevron" :size="14" class="text-[#bbb]" />
            </button>
            <button type="button" class="more-row" @click="openView('verify')">
              <span>大 V 认证</span>
              <Icon name="chevron" :size="14" class="text-[#bbb]" />
            </button>
          </template>

          <template v-else-if="view === 'live' || view === 'verify'">
            <p class="text-[12px] leading-5 text-[#8a8a8a]">
              {{ view === 'live' ? '申请开通直播能力。' : '认证后主页展示认证标识。' }}
            </p>
            <input v-model="applyName" class="field mt-3 text-[12px]" placeholder="名称 / 账号" />
            <input v-model="applyContact" class="field mt-2 text-[12px]" placeholder="联系方式" />
            <textarea v-model="applyIntro" rows="3" class="field mt-2 text-[12px]" placeholder="补充说明" />
            <p v-if="applyHint" class="mt-2 text-[12px]">{{ applyHint }}</p>
            <button type="button" class="btn-auth mt-3 h-9 text-[13px]" @click="submitApply">提交申请</button>
          </template>

          <template v-else-if="view === 'shortcuts'">
            <ul class="divide-y divide-[#f2f2f2]">
              <li v-for="row in shortcuts" :key="row.keys" class="flex items-center justify-between py-2">
                <span class="text-[12px]">{{ row.action }}</span>
                <kbd class="rounded-md bg-[#f7f7f7] px-1.5 py-0.5 text-[11px] text-[#555]">{{ row.keys }}</kbd>
              </li>
            </ul>
          </template>
        </div>
      </template>
    </div>
  </Teleport>
</template>
