<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import SettingsPanel from '@/components/SettingsPanel.vue'
import RemotePushDialog from '@/components/RemotePushDialog.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useI18n } from '@/composables/useI18n'
import { hydrateRoles, useRoles } from '@/composables/useRoles'
import { catalog, docsBySlug } from '@/lib/content'
import { syncCatalogFromDisk } from '@/lib/catalog-sync'
import { isSharedWritableSlug, ROLES, type RoleId } from '@/lib/roles'
import { cn } from '@/lib/utils'
import {
  bindRemoteGit,
  detectProviderFromRepo,
  disconnectRemoteGit,
  gitProviders,
  hydrateRemoteGit,
  updateRemoteOptions,
  useRemoteGit,
  type GitProvider,
} from '@/lib/remote-git'
import {
  hydratePushCopy,
  savePushCopy,
  usePushCopy,
  type PushCopyBundle,
} from '@/lib/push-copy'
import {
  hydrateBranding,
  saveBranding,
  useBranding,
} from '@/lib/branding'
import {
  authorizeWorkspace,
  disconnectWorkspace,
  reconnectWorkspace,
  useWorkspaceFs,
} from '@/lib/workspace-fs'

const { locale, t } = useI18n()
const route = useRoute()
const { selected, ownedSlugs, toggle } = useRoles()
const { status, folderName, error, ready: fsReady } = useWorkspaceFs()
const {
  status: remoteStatus,
  ready: remoteReady,
  lastError: remoteLastError,
  lastPushAt,
  summary: remoteSummary,
  provider: boundProvider,
  contentRoot: boundRoot,
  repoPath,
  branch: boundBranch,
  host: boundHost,
  autoPush: boundAutoPush,
  tokenStored,
} = useRemoteGit()
const providers = gitProviders()
const providerKeys = ['github', 'gitee', 'gitlab'] as const
const working = ref(false)
const message = ref('')
const remoteWorking = ref(false)
const remoteMessage = ref('')
const provider = ref<GitProvider>('github')
const repoInput = ref('')
const branch = ref('main')
const token = ref('')
const contentRoot = ref('src/content')
const host = ref('https://gitlab.com')
const autoPush = ref(true)
const pushOpen = ref(false)
const openRoles = ref(false)
const openFolder = ref(false)
const openBrand = ref(false)
const openRemote = ref(false)
const openCopy = ref(false)
const { bundle: copyBundle } = usePushCopy()
const firstSuccess = ref('')
const firstHintText = ref('')
const copyLines = ref('')
const copyMessage = ref('')
const copyWorking = ref(false)
const { branding, logoLightSrc, logoDarkSrc } = useBranding()
const brandName = ref('')
const brandSloganZh = ref('')
const brandSloganEn = ref('')
const brandIntroZh = ref('')
const brandIntroEn = ref('')
const brandSameLogo = ref(false)
const lightBytes = ref<ArrayBuffer | null>(null)
const darkBytes = ref<ArrayBuffer | null>(null)
const lightPreview = ref('')
const darkPreview = ref('')
const brandWorking = ref(false)
const brandMessage = ref('')

watch(repoInput, (value) => {
  const detected = detectProviderFromRepo(value)
  if (detected) provider.value = detected
})

watch(
  [remoteReady, repoPath, boundBranch, boundRoot, boundHost, boundProvider, boundAutoPush],
  () => {
    if (!remoteReady.value) return
    if (boundProvider.value) provider.value = boundProvider.value
    if (repoPath.value) repoInput.value = repoPath.value
    if (boundBranch.value) branch.value = boundBranch.value
    if (boundRoot.value) contentRoot.value = boundRoot.value
    if (boundHost.value) host.value = boundHost.value
    autoPush.value = boundAutoPush.value
  },
  { immediate: true },
)

const writableDocs = computed(() => {
  void catalog.revision
  const keys = new Set([...ownedSlugs.value])
  if (selected.value.length) {
    for (const doc of catalog.docs) {
      if (isSharedWritableSlug(doc.slug)) keys.add(doc.slug)
    }
  }
  const list = [...keys]
    .map((slug) => docsBySlug.get(slug))
    .filter((doc): doc is NonNullable<typeof doc> => Boolean(doc))
  const seen = new Set<string>()
  return list.filter((doc) => {
    const key = doc.slug || 'index'
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
})

function titleOf(id: RoleId) {
  const role = ROLES.find((item) => item.id === id)!
  return locale.value === 'zh' ? role.titleZh : role.titleEn
}

function descOf(id: RoleId) {
  const role = ROLES.find((item) => item.id === id)!
  return locale.value === 'zh' ? role.descZh : role.descEn
}

function checked(id: RoleId) {
  return selected.value.includes(id)
}

async function connect() {
  working.value = true
  message.value = ''
  try {
    await authorizeWorkspace()
    await hydrateRemoteGit()
    await hydratePushCopy()
    await hydrateBranding()
    await hydrateRoles()
    message.value = t.value('settingsOk')
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') return
    message.value = error.value === 'invalid' ? t.value('settingsInvalid') : t.value('settingsFail')
  } finally {
    working.value = false
  }
}

async function reconnect() {
  working.value = true
  message.value = ''
  try {
    await reconnectWorkspace()
    await hydrateRemoteGit()
    await hydratePushCopy()
    await hydrateBranding()
    await hydrateRoles()
    message.value = t.value('settingsOk')
  } catch {
    message.value = t.value('settingsFail')
  } finally {
    working.value = false
  }
}

async function disconnect() {
  working.value = true
  message.value = ''
  try {
    await disconnectWorkspace()
  } finally {
    working.value = false
  }
}

async function bindRemote() {
  remoteWorking.value = true
  remoteMessage.value = ''
  try {
    await bindRemoteGit({
      provider: provider.value,
      repoInput: repoInput.value,
      branch: branch.value,
      token: token.value,
      contentRoot: contentRoot.value,
      autoPush: autoPush.value,
      host: host.value,
    })
    token.value = ''
    remoteMessage.value = t.value('remoteOk')
  } catch (err) {
    const code = err instanceof Error ? err.message : ''
    if (code === 'REPO_INVALID') remoteMessage.value = t.value('remoteRepoInvalid')
    else if (code === 'TOKEN_REQUIRED') remoteMessage.value = t.value('remoteTokenRequired')
    else remoteMessage.value = remoteLastError.value || code || t.value('remoteFail')
  } finally {
    remoteWorking.value = false
  }
}

async function unbindRemote() {
  remoteWorking.value = true
  remoteMessage.value = ''
  try {
    await disconnectRemoteGit()
    repoInput.value = ''
    token.value = ''
  } finally {
    remoteWorking.value = false
  }
}

async function toggleAutoPush() {
  autoPush.value = !autoPush.value
  if (remoteReady.value) await updateRemoteOptions({ autoPush: autoPush.value })
}

async function saveCopy() {
  copyWorking.value = true
  copyMessage.value = ''
  try {
    const next: PushCopyBundle = {
      ...copyBundle.value,
      [locale.value]: {
        firstSuccess: firstSuccess.value,
        firstHint: firstHintText.value,
        lines: copyLines.value.split('\n'),
      },
    }
    await savePushCopy(next)
    copyMessage.value = t.value('copySaved')
  } catch (err) {
    const code = err instanceof Error ? err.message : ''
    copyMessage.value = code === 'NOT_READY' ? t.value('copyNeedFolder') : code || t.value('settingsFail')
  } finally {
    copyWorking.value = false
  }
}

watch(branding, (value) => {
  brandName.value = value.name
  brandSloganZh.value = value.sloganZh
  brandSloganEn.value = value.sloganEn
  brandIntroZh.value = value.introZh
  brandIntroEn.value = value.introEn
}, { immediate: true })

function revokePreview(url: string) {
  if (url.startsWith('blob:')) URL.revokeObjectURL(url)
}

async function onLogoFile(kind: 'light' | 'dark', event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  const buffer = await file.arrayBuffer()
  const preview = URL.createObjectURL(file)
  if (kind === 'light') {
    revokePreview(lightPreview.value)
    lightBytes.value = buffer
    lightPreview.value = preview
    return
  }
  revokePreview(darkPreview.value)
  darkBytes.value = buffer
  darkPreview.value = preview
}

async function saveBrand() {
  brandWorking.value = true
  brandMessage.value = ''
  try {
    const light = lightBytes.value ?? undefined
    const dark = brandSameLogo.value ? light ?? darkBytes.value ?? undefined : darkBytes.value ?? undefined
    await saveBranding({
      name: brandName.value,
      sloganZh: brandSloganZh.value,
      sloganEn: brandSloganEn.value,
      introZh: brandIntroZh.value,
      introEn: brandIntroEn.value,
      light,
      dark,
    })
    await syncCatalogFromDisk()
    lightBytes.value = null
    darkBytes.value = null
    revokePreview(lightPreview.value)
    revokePreview(darkPreview.value)
    lightPreview.value = ''
    darkPreview.value = ''
    brandMessage.value = t.value('brandSaved')
  } catch (err) {
    const code = err instanceof Error ? err.message : ''
    if (code === 'NOT_READY') brandMessage.value = t.value('brandNeedFolder')
    else if (code === 'NAME_REQUIRED') brandMessage.value = t.value('brandNameRequired')
    else if (code === 'LOGO_TOO_LARGE') brandMessage.value = t.value('brandLogoTooLarge')
    else brandMessage.value = code || t.value('settingsFail')
  } finally {
    brandWorking.value = false
  }
}

watch(
  [locale, copyBundle],
  () => {
    const pack = copyBundle.value[locale.value]
    firstSuccess.value = pack.firstSuccess
    firstHintText.value = pack.firstHint
    copyLines.value = pack.lines.join('\n')
  },
  { immediate: true },
)

watch(
  () => [route.path, route.hash] as const,
  async () => {
    if (route.path !== '/settings') return
    const id = route.hash.replace(/^#/, '')
    if (!id) return
    openRoles.value = id === 'roles'
    openFolder.value = id === 'folder'
    openBrand.value = id === 'brand'
    openRemote.value = id === 'remote'
    openCopy.value = id === 'copy'
    await nextTick()
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  },
  { immediate: true },
)

function tokenHelpHref() {
  return providers[provider.value].tokenHelp
}
</script>

<template>
  <div class="px-6 py-8 md:px-10 lg:px-12">
    <article class="mx-auto max-w-3xl">
      <h1 class="text-3xl font-semibold tracking-tight">{{ t('settingsTitle') }}</h1>
      <p class="mt-2 text-sm leading-6 text-muted-foreground">{{ t('settingsHint') }}</p>
      <div class="mt-3 h-px w-full bg-border" />

      <SettingsPanel id="roles" v-model:open="openRoles" :title="t('rolesTitle')" :hint="t('rolesHint')">
        <div class="mt-4 grid gap-3 sm:grid-cols-2">
          <button
            v-for="role in ROLES"
            :key="role.id"
            type="button"
            :class="
              cn(
                'rounded-xl border bg-card p-4 text-left transition-colors hover:bg-accent/40',
                checked(role.id) && 'border-foreground/20 bg-accent/50',
              )
            "
            @click="toggle(role.id)"
          >
            <div class="flex items-start gap-3">
              <span
                :class="
                  cn(
                    'mt-0.5 flex size-4 shrink-0 items-center justify-center rounded border',
                    checked(role.id) ? 'border-foreground bg-foreground' : 'border-input bg-background',
                  )
                "
              >
                <span v-if="checked(role.id)" class="size-1.5 rounded-sm bg-background" />
              </span>
              <div class="min-w-0">
                <div class="text-sm font-medium">{{ role.shortName }} · {{ titleOf(role.id) }}</div>
                <p class="mt-1 text-[13px] leading-5 text-muted-foreground">{{ descOf(role.id) }}</p>
              </div>
            </div>
          </button>
        </div>
        <p v-if="!fsReady" class="mt-3 text-sm text-muted-foreground">{{ t('brandNeedFolder') }}</p>

        <div class="mt-4 rounded-xl border bg-muted/40 p-5">
          <h3 class="text-sm font-semibold">{{ t('writableNow') }}</h3>
          <p v-if="!selected.length" class="mt-2 text-sm leading-6 text-muted-foreground">
            {{ t('writableEmpty') }}
          </p>
          <ul v-else class="mt-3 space-y-1.5">
            <li v-for="doc in writableDocs" :key="doc.slug || 'index'">
              <RouterLink
                :to="doc.slug ? `/${doc.slug}` : '/'"
                class="text-sm text-foreground underline-offset-4 hover:underline"
              >
                {{ doc.title }}
              </RouterLink>
              <span v-if="doc.description" class="text-xs text-muted-foreground"> · {{ doc.description }}</span>
            </li>
          </ul>
          <p class="mt-3 text-xs leading-5 text-muted-foreground">{{ t('writableNote') }}</p>
        </div>
      </SettingsPanel>

      <SettingsPanel id="folder" v-model:open="openFolder" :title="t('settingsFolder')" :hint="t('settingsFolderHint')">

        <p class="mt-4 text-sm">
          <span class="text-muted-foreground">{{ t('settingsStatus') }}：</span>
          <span v-if="status === 'ready'">{{ t('settingsReady') }}（{{ folderName }}）</span>
          <span v-else-if="status === 'need-permission'">{{ t('settingsNeedPerm') }}（{{ folderName }}）</span>
          <span v-else-if="status === 'unsupported'">{{ t('settingsUnsupported') }}</span>
          <span v-else>{{ t('settingsDisconnected') }}</span>
        </p>

        <div class="mt-4 flex flex-wrap gap-2">
          <Button size="sm" :disabled="working || status === 'unsupported'" @click="connect">
            {{ t('settingsAuthorize') }}
          </Button>
          <Button
            v-if="status === 'need-permission'"
            size="sm"
            variant="outline"
            :disabled="working"
            @click="reconnect"
          >
            {{ t('settingsReconnect') }}
          </Button>
          <Button
            v-if="status === 'ready' || status === 'need-permission'"
            size="sm"
            variant="ghost"
            :disabled="working"
            @click="disconnect"
          >
            {{ t('settingsDisconnect') }}
          </Button>
        </div>
        <p v-if="message" class="mt-3 text-sm text-muted-foreground">{{ message }}</p>
      </SettingsPanel>

      <SettingsPanel id="brand" v-model:open="openBrand" :title="t('brandTitle')" :hint="t('brandHint')">
        <label class="mt-4 block text-xs text-muted-foreground">{{ t('brandName') }}</label>
        <Input v-model="brandName" class="mt-1.5" maxlength="80" autocomplete="off" />

        <label class="mt-4 block text-xs text-muted-foreground">{{ t('brandSloganZh') }}</label>
        <Input v-model="brandSloganZh" class="mt-1.5" autocomplete="off" />

        <label class="mt-4 block text-xs text-muted-foreground">{{ t('brandSloganEn') }}</label>
        <Input v-model="brandSloganEn" class="mt-1.5" autocomplete="off" />

        <label class="mt-4 block text-xs text-muted-foreground">{{ t('brandIntroZh') }}</label>
        <textarea
          v-model="brandIntroZh"
          class="mt-1.5 min-h-20 w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />

        <label class="mt-4 block text-xs text-muted-foreground">{{ t('brandIntroEn') }}</label>
        <textarea
          v-model="brandIntroEn"
          class="mt-1.5 min-h-20 w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />

        <p class="mt-4 text-xs leading-5 text-muted-foreground">{{ t('brandLogoHint') }}</p>
        <div class="mt-3 grid gap-4 sm:grid-cols-2">
          <div>
            <label class="block text-xs text-muted-foreground">{{ t('brandLogoLight') }}</label>
            <img :src="lightPreview || logoLightSrc" :alt="t('brandLogoLight')" class="mt-2 h-12 w-auto max-w-full object-contain" />
            <input
              class="mt-2 block w-full text-xs text-muted-foreground file:mr-2 file:rounded-md file:border-0 file:bg-secondary file:px-2 file:py-1 file:text-xs"
              type="file"
              accept="image/png,image/jpeg,image/webp"
              @change="onLogoFile('light', $event)"
            />
          </div>
          <div>
            <label class="block text-xs text-muted-foreground">{{ t('brandLogoDark') }}</label>
            <img
              :src="brandSameLogo ? lightPreview || logoLightSrc : darkPreview || logoDarkSrc"
              :alt="t('brandLogoDark')"
              class="mt-2 h-12 w-auto max-w-full object-contain"
            />
            <input
              class="mt-2 block w-full text-xs text-muted-foreground file:mr-2 file:rounded-md file:border-0 file:bg-secondary file:px-2 file:py-1 file:text-xs"
              type="file"
              accept="image/png,image/jpeg,image/webp"
              :disabled="brandSameLogo"
              @change="onLogoFile('dark', $event)"
            />
          </div>
        </div>
        <label class="mt-3 flex cursor-pointer items-center gap-2 text-sm">
          <input v-model="brandSameLogo" type="checkbox" class="size-4 accent-foreground" />
          {{ t('brandSameLogo') }}
        </label>

        <div class="mt-4 flex flex-wrap items-center gap-2">
          <Button size="sm" :disabled="brandWorking || !fsReady" @click="saveBrand">{{ t('brandSave') }}</Button>
          <p v-if="!fsReady" class="text-sm text-muted-foreground">{{ t('brandNeedFolder') }}</p>
          <p v-else-if="brandMessage" class="text-sm text-muted-foreground">{{ brandMessage }}</p>
        </div>
      </SettingsPanel>

      <SettingsPanel id="remote" v-model:open="openRemote" :title="t('remoteTitle')" :hint="t('remoteHint')">

        <p class="mt-4 text-sm">
          <span class="text-muted-foreground">{{ t('remoteStatus') }}：</span>
          <span v-if="remoteReady">{{ t('remoteReady') }}（{{ remoteSummary }}）</span>
          <span v-else-if="remoteStatus === 'error'">{{ t('remoteError') }}</span>
          <span v-else>{{ t('remoteDisconnected') }}</span>
        </p>
        <p v-if="lastPushAt" class="mt-1 text-xs text-muted-foreground">
          {{ t('remoteLastPush') }}：{{ lastPushAt.replace('T', ' ').slice(0, 19) }}
        </p>
        <p v-if="remoteReady" class="mt-1 text-xs text-muted-foreground">{{ t('remoteTokenSaved') }}</p>

        <div class="mt-4">
          <p class="text-xs text-muted-foreground">{{ t('remoteProvider') }}</p>
          <div class="mt-2 flex flex-wrap gap-2">
            <Button
              v-for="key in providerKeys"
              :key="key"
              type="button"
              size="sm"
              :variant="provider === key ? 'default' : 'outline'"
              @click="provider = key"
            >
              {{ providers[key].label }}
            </Button>
          </div>
        </div>

        <label class="mt-4 block text-xs text-muted-foreground">{{ t('remoteRepo') }}</label>
        <Input v-model="repoInput" class="mt-1.5" :placeholder="t('remoteRepoHint')" autocomplete="off" />

        <div class="mt-4 grid gap-4 sm:grid-cols-2">
          <div>
            <label class="block text-xs text-muted-foreground">{{ t('remoteBranch') }}</label>
            <Input v-model="branch" class="mt-1.5" placeholder="main" autocomplete="off" />
          </div>
          <div>
            <label class="block text-xs text-muted-foreground">{{ t('remoteRoot') }}</label>
            <Input v-model="contentRoot" class="mt-1.5" placeholder="src/content" autocomplete="off" />
          </div>
        </div>
        <p class="mt-1.5 text-xs leading-5 text-muted-foreground">{{ t('remoteRootHint') }}</p>

        <div v-if="provider === 'gitlab'" class="mt-4">
          <label class="block text-xs text-muted-foreground">{{ t('remoteHost') }}</label>
          <Input v-model="host" class="mt-1.5" placeholder="https://gitlab.com" autocomplete="off" />
        </div>

        <label class="mt-4 block text-xs text-muted-foreground">{{ t('remoteToken') }}</label>
        <Input
          v-model="token"
          class="mt-1.5"
          type="password"
          autocomplete="off"
          :placeholder="tokenStored ? t('remoteTokenPlaceholder') : t('remoteTokenHint')"
        />
        <p class="mt-1.5 text-xs leading-5 text-muted-foreground">{{ t('remoteTokenFileHint') }}</p>
        <a
          :href="tokenHelpHref()"
          target="_blank"
          rel="noreferrer noopener"
          class="mt-1.5 inline-block text-xs text-muted-foreground underline-offset-4 hover:underline"
        >
          {{ t('remoteTokenHelp') }}
        </a>

        <label class="mt-4 flex cursor-pointer items-center gap-2 text-sm">
          <input type="checkbox" class="size-4 accent-foreground" :checked="autoPush" @change="toggleAutoPush" />
          {{ t('remoteAutoPush') }}
        </label>

        <div class="mt-4 flex flex-wrap gap-2">
          <Button size="sm" :disabled="remoteWorking" @click="bindRemote">{{ t('remoteBind') }}</Button>
          <Button
            v-if="remoteReady"
            size="sm"
            variant="warning"
            :disabled="remoteWorking"
            @click="pushOpen = true"
          >
            {{ t('remotePush') }}
          </Button>
          <Button
            v-if="remoteReady"
            size="sm"
            variant="ghost"
            :disabled="remoteWorking"
            @click="unbindRemote"
          >
            {{ t('remoteDisconnect') }}
          </Button>
        </div>
        <p v-if="remoteMessage" class="mt-3 text-sm text-muted-foreground">{{ remoteMessage }}</p>
      </SettingsPanel>

      <SettingsPanel id="copy" v-model:open="openCopy" :title="t('copyTitle')" :hint="t('copyHint')">
        <label class="mt-4 block text-xs text-muted-foreground">{{ t('copyFirst') }}</label>
        <Input v-model="firstSuccess" class="mt-1.5" autocomplete="off" />

        <label class="mt-4 block text-xs text-muted-foreground">{{ t('copyFirstHintLabel') }}</label>
        <textarea
          v-model="firstHintText"
          class="mt-1.5 min-h-16 w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />

        <label class="mt-4 block text-xs text-muted-foreground">{{ t('copyLines') }}</label>
        <p class="mt-1 text-xs text-muted-foreground">{{ t('copyLinesHint') }}</p>
        <textarea
          v-model="copyLines"
          class="mt-1.5 min-h-40 w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />

        <div class="mt-4 flex flex-wrap items-center gap-2">
          <Button size="sm" :disabled="copyWorking" @click="saveCopy">{{ t('copySave') }}</Button>
          <p v-if="copyMessage" class="text-sm text-muted-foreground">{{ copyMessage }}</p>
        </div>
      </SettingsPanel>
    </article>
    <RemotePushDialog :open="pushOpen" @close="pushOpen = false" />
  </div>
</template>
