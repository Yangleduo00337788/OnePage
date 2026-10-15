<!--
  「我的」页：资料展示 + 编辑弹窗 + 头像/封面上传。
  笔记 Tab GET /users/{pageId}/posts；收藏/点赞/历史走 /users/me/favorites|likes|history。
  点关注/粉丝数字打开关注列表；点获赞与收藏打开获赞/被收藏列表。
  未登录会被拉去首页并打开登录弹窗，redirect 仍是 /me。
-->
<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { openCreatorSite } from '../creatorSite'
import { api, ApiError } from '@onepage/api-client'
import { useSession } from '../stores/session'
import { useAuthModal } from '../stores/authModal'
import ImageCropDialog from '@onepage/image-crop'
import Icon from '../components/Icon.vue'
import SearchBox from '../components/SearchBox.vue'
import FollowListModal from '../components/FollowListModal.vue'
import ReceivedListModal from '../components/ReceivedListModal.vue'
import ProfileNoteList from '../components/ProfileNoteList.vue'

const GENDERS = [
  { id: 0, label: '未设置' },
  { id: 1, label: '男' },
  { id: 2, label: '女' },
] as const

const ME_TABS = ['笔记', '收藏', '点赞', '历史'] as const
const TAB_EMPTY: Record<
  (typeof ME_TABS)[number],
  { title: string; desc: string; action: string; to: string }
> = {
  笔记: {
    title: '还没有发布过笔记',
    desc: '把生活里的一页记下来，发布后会出现在这里。',
    action: '去发布',
    to: '/create',
  },
  收藏: {
    title: '还没有收藏',
    desc: '看到喜欢的笔记点一下收藏，以后都能在这里找到。',
    action: '去首页逛逛',
    to: '/',
  },
  点赞: {
    title: '还没有点过赞',
    desc: '给打动你的内容点个赞，它们会安静地待在这里。',
    action: '去发现',
    to: '/',
  },
  历史: {
    title: '还没有浏览记录',
    desc: '看过的笔记会留在这里，方便你随时再回来。',
    action: '去首页看看',
    to: '/',
  },
}

const router = useRouter()
const session = useSession()
const auth = useAuthModal()
const editing = ref(false)
const nickname = ref('')
const bio = ref('')
const gender = ref(0)
const message = ref('')
const error = ref('')
const toast = ref('')
const copied = ref(false)
const saving = ref(false)
const uploading = ref<'avatar' | 'cover' | null>(null)
const avatarInput = ref<HTMLInputElement | null>(null)
const coverInput = ref<HTMLInputElement | null>(null)
const crop = ref<{ purpose: 'avatar' | 'cover'; src: string; name: string } | null>(null)
const listTab = ref<(typeof ME_TABS)[number]>('笔记')
const tabDir = ref<'next' | 'prev'>('next')
const relation = ref<'following' | 'followers' | null>(null)
const receivedOpen = ref(false)

function onTabEmpty() {
  if (listTab.value === '笔记') {
    openCreatorSite()
    return
  }
  void router.push(TAB_EMPTY[listTab.value].to)
}

function selectTab(item: (typeof ME_TABS)[number]) {
  const from = ME_TABS.indexOf(listTab.value)
  const to = ME_TABS.indexOf(item)
  if (from === to) return
  tabDir.value = to > from ? 'next' : 'prev'
  listTab.value = item
}

onMounted(async () => {
  await session.refresh()
  if (!session.me) {
    auth.show('login', '/me')
    await router.replace('/')
  }
})

onUnmounted(() => closeCrop())

function onCoverClick(event: MouseEvent) {
  const t = event.target
  if (!(t instanceof Element)) return
  if (t.closest('button, a, input, textarea, label')) return
  coverInput.value?.click()
}

function openEdit() {
  if (!session.me) return
  nickname.value = session.me.nickname
  bio.value = session.me.bio
  gender.value = Number(session.me.gender) || 0
  error.value = ''
  message.value = ''
  editing.value = true
}

function closeEdit() {
  if (saving.value) return
  editing.value = false
}

function setGender(id: number) {
  gender.value = id
}

async function save() {
  error.value = ''
  message.value = ''
  saving.value = true
  try {
    session.me = await api.patchMe({
      nickname: nickname.value,
      bio: bio.value,
      gender: Number(gender.value),
    })
    editing.value = false
    showToast('资料已保存')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}

function showToast(text: string) {
  toast.value = text
  window.setTimeout(() => {
    if (toast.value === text) toast.value = ''
  }, 2200)
}

function closeCrop() {
  if (crop.value) URL.revokeObjectURL(crop.value.src)
  crop.value = null
}

function onFile(event: Event, purpose: 'avatar' | 'cover') {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (crop.value) URL.revokeObjectURL(crop.value.src)
  crop.value = { purpose, src: URL.createObjectURL(file), name: file.name }
}

/**
 * 裁完再走三步上传：presign → 浏览器直传 MinIO → complete → PATCH 资料绑 mediaId。
 * PUT 的 Content-Type 必须和申请预签名时一致，否则签名对不上。
 */
async function onCropped(file: File) {
  const purpose = crop.value?.purpose
  closeCrop()
  if (!purpose) return
  uploading.value = purpose
  try {
    const mimeType = file.type || 'image/jpeg'
    const ticket = await api.presign(mimeType, file.size, purpose)
    await api.putObject(ticket.uploadUrl, file, mimeType)
    await api.completeUpload(ticket.mediaId)
    session.me = await api.patchMe(
      purpose === 'avatar' ? { avatarMediaId: ticket.mediaId } : { coverMediaId: ticket.mediaId },
    )
    showToast(purpose === 'avatar' ? '头像已更新' : '封面已更新')
  } catch (e) {
    showToast(e instanceof ApiError ? e.message : '上传失败')
  } finally {
    uploading.value = null
  }
}

async function copyPageId() {
  if (!session.me) return
  try {
    await navigator.clipboard.writeText(session.me.pageId)
    copied.value = true
    window.setTimeout(() => {
      copied.value = false
    }, 1600)
  } catch {
    showToast('复制失败')
  }
}

async function logout() {
  await session.logout()
  await router.replace('/')
}
</script>

<template>
  <section v-if="session.me" class="relative pb-16">
    <input
      ref="coverInput"
      type="file"
      accept="image/jpeg,image/png,image/webp,image/gif"
      class="hidden"
      @change="onFile($event, 'cover')"
    />
    <input
      ref="avatarInput"
      type="file"
      accept="image/jpeg,image/png,image/webp,image/gif"
      class="hidden"
      @change="onFile($event, 'avatar')"
    />

    <div class="profile-hero-actions absolute top-6 right-8 z-20 flex items-center gap-2">
      <SearchBox compact hero />
      <button type="button" class="me-action" @click="openEdit">
        <Icon name="pencil" :size="14" :stroke="1.8" />
        编辑
      </button>
      <button type="button" class="me-action" @click="logout">退出登录</button>
    </div>

    <div class="profile-hero cursor-pointer" @click="onCoverClick">
      <div class="profile-hero-bg">
        <img v-if="session.me.coverUrl" :src="session.me.coverUrl" alt="" />
        <div v-else class="relative h-full w-full overflow-hidden bg-[#fafafa]">
          <span class="pointer-events-none absolute -top-10 -left-8 h-48 w-48 rounded-full bg-[#ff2442]/[0.07]" />
          <span class="pointer-events-none absolute right-[-24px] bottom-[-28px] h-44 w-44 rounded-full bg-[#ff2442]/[0.05]" />
        </div>
      </div>
      <div class="profile-hero-shade" />
      <span class="profile-hero-hint">
        <Icon name="camera" :size="14" :stroke="1.8" />
        {{ uploading === 'cover' ? '封面上传中…' : session.me.coverUrl ? '更换封面' : '点击添加封面' }}
      </span>
      <div class="profile-hero-body">
        <div class="flex justify-center">
          <div class="relative">
            <button
              type="button"
              class="group h-24 w-24 overflow-hidden rounded-full bg-[#f3f3f3] ring-4 ring-white"
              :disabled="uploading === 'avatar'"
              @click="avatarInput?.click()"
            >
              <img
                v-if="session.me.avatarUrl"
                :src="session.me.avatarUrl"
                class="h-full w-full object-cover"
                alt=""
              />
              <span v-else class="flex h-full items-center justify-center text-[#c4c4c4]">
                <Icon name="smile" :size="36" :stroke="1.5" />
              </span>
              <span
                class="absolute inset-0 flex items-center justify-center bg-black/40 text-[11px] text-white opacity-0 transition group-hover:opacity-100"
              >
                {{ uploading === 'avatar' ? '上传中' : '换头像' }}
              </span>
            </button>
            <button
              type="button"
              class="absolute right-0 bottom-0 flex h-7 w-7 items-center justify-center rounded-full bg-white text-[#1f1f1f] shadow-sm ring-1 ring-[#eee]"
              :disabled="uploading === 'avatar'"
              aria-label="更换头像"
              @click="avatarInput?.click()"
            >
              <Icon name="camera" :size="14" :stroke="1.8" />
            </button>
          </div>
        </div>

        <div class="mx-auto mt-4 max-w-[560px] text-center text-white">
          <h1 class="mx-auto flex max-w-full items-center justify-center gap-1.5 text-[22px] font-semibold tracking-tight">
            <span class="truncate">{{ session.me.nickname }}</span>
            <Icon
              v-if="session.me.gender === 1"
              name="mars"
              :size="16"
              :stroke="2"
              class="shrink-0 text-[#4a90d9]"
            />
            <Icon
              v-else-if="session.me.gender === 2"
              name="venus"
              :size="16"
              :stroke="2"
              class="shrink-0 text-[#ff2442]"
            />
          </h1>
          <button
            type="button"
            class="mt-1 inline-flex items-center gap-1 text-[13px] text-white/80 hover:text-white"
            @click="copyPageId"
          >
            一页号：{{ session.me.pageId }}
            <Icon name="copy" :size="13" :stroke="1.8" />
            <span v-if="copied" class="text-[#ff2442]">已复制</span>
          </button>
          <p class="mt-3 text-[14px] leading-6 text-white/85">
            签名：{{ session.me.bio?.trim() ? session.me.bio : '暂无' }}
          </p>
          <p class="mt-4 text-[14px] text-white/80">
            <button type="button" class="stat-link" @click="relation = 'following'">
              <span class="font-semibold tabular-nums text-white">{{ session.me.stats.followingCount }}</span>
              关注
            </button>
            <span class="mx-3 text-white/30">|</span>
            <button type="button" class="stat-link" @click="relation = 'followers'">
              <span class="font-semibold tabular-nums text-white">{{ session.me.stats.followerCount }}</span>
              粉丝
            </button>
            <span class="mx-3 text-white/30">|</span>
            <button type="button" class="stat-link" @click="receivedOpen = true">
              <span class="font-semibold tabular-nums text-white">{{
                (session.me.stats.likeReceivedCount ?? 0) + (session.me.stats.favoriteReceivedCount ?? 0)
              }}</span>
              获赞与收藏
            </button>
          </p>
        </div>
      </div>
    </div>

    <div class="border-t border-[#ececec]">
      <div class="flex items-center justify-center gap-2 pt-5">
        <button
          v-for="item in ME_TABS"
          :key="item"
          type="button"
          class="me-tab"
          :class="{ 'is-on': listTab === item }"
          @click="selectTab(item)"
        >
          {{ item }}
        </button>
      </div>
      <div class="relative overflow-hidden px-8">
        <Transition :name="tabDir === 'next' ? 'me-pane-next' : 'me-pane-prev'" mode="out-in">
          <ProfileNoteList
            :key="listTab"
            :source="listTab === '笔记' ? 'user-posts' : listTab === '收藏' ? 'favorites' : listTab === '点赞' ? 'likes' : 'history'"
            :page-id="session.me.pageId"
            :empty-title="TAB_EMPTY[listTab].title"
            :empty-desc="TAB_EMPTY[listTab].desc"
            :empty-primary="listTab === '笔记'"
            @empty-action="onTabEmpty"
          >
            <template #action>{{ TAB_EMPTY[listTab].action }}</template>
          </ProfileNoteList>
        </Transition>
      </div>
    </div>

    <p
      v-if="toast"
      class="fixed bottom-8 left-1/2 z-40 -translate-x-1/2 rounded-full bg-[#1f1f1f] px-4 py-2 text-[13px] text-white"
    >
      {{ toast }}
    </p>
    <FollowListModal
      :open="Boolean(relation)"
      :page-id="session.me.pageId"
      :kind="relation || 'following'"
      @close="relation = null"
    />
    <ReceivedListModal :open="receivedOpen" @close="receivedOpen = false" />

    <Teleport to="body">
      <div
        v-if="editing"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/45 px-4"
        @click.self="closeEdit"
      >
        <div class="relative w-[440px] max-w-[calc(100vw-32px)] rounded-[20px] bg-white px-8 py-7 shadow-[0_20px_60px_rgba(0,0,0,0.16)]">
          <button type="button" class="icon-btn absolute top-3 right-3" aria-label="关闭" @click="closeEdit">
            <Icon name="x" :size="18" />
          </button>
          <h2 class="text-[18px] font-semibold">编辑资料</h2>
          <form class="mt-6 space-y-4" @submit.prevent="save">
            <label class="block">
              <span class="mb-2 block text-[13px] text-[#8a8a8a]">昵称</span>
              <input v-model="nickname" class="field-auth" placeholder="怎么称呼你" maxlength="64" />
            </label>
            <label class="block">
              <span class="mb-2 block text-[13px] text-[#8a8a8a]">签名</span>
              <textarea
                v-model="bio"
                class="field-auth resize-none"
                placeholder="介绍一下自己"
                maxlength="500"
              />
            </label>
            <div>
              <span class="mb-2 block text-[13px] text-[#8a8a8a]">性别</span>
              <div class="flex gap-2">
                <button
                  v-for="item in GENDERS"
                  :key="item.id"
                  type="button"
                  class="gender-pill"
                  :class="{ 'is-on': Number(gender) === item.id }"
                  @click.prevent="setGender(item.id)"
                >
                  {{ item.label }}
                </button>
              </div>
            </div>
            <p v-if="error" class="text-[13px] text-[#ff2442]">{{ error }}</p>
            <button class="btn-auth mt-2" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button>
          </form>
        </div>
      </div>
    </Teleport>
    <ImageCropDialog
      v-if="crop"
      :src="crop.src"
      :title="crop.purpose === 'avatar' ? '裁剪头像' : '裁剪封面'"
      :aspect="crop.purpose === 'avatar' ? 1 : 3"
      :round="crop.purpose === 'avatar'"
      :file-name="crop.name"
      :output-width="crop.purpose === 'avatar' ? 800 : 1920"
      @confirm="onCropped"
      @cancel="closeCrop"
    />
  </section>
</template>

<style scoped>
.stat-link:hover span {
  color: #ff2442;
}
</style>
