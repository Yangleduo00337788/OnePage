<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Pause, Square, Volume2 } from 'lucide-vue-next'
import DocToc from '@/components/DocToc.vue'
import DocStats from '@/components/DocStats.vue'
import DocChildren from '@/components/DocChildren.vue'
import DocHomeNav from '@/components/DocHomeNav.vue'
import DocHomeHero from '@/components/DocHomeHero.vue'
import { useI18n } from '@/composables/useI18n'
import {
  catalog,
  docDir,
  findNavNode,
  formatDocUpdatedAt,
  getDoc,
  getNeighbors,
  isExpandableNavFolder,
  isProtectedDoc,
  slugFromPath,
} from '@/lib/content'
import { canCreateIn, canEditSlug, canMoveDoc, createWorkspaceDoc, moveWorkspaceDoc, promoteDocToFolder } from '@/lib/doc-manage'
import { pendingEditSlug, pendingMoveSlug } from '@/lib/doc-session'
import { hydrateContentImages } from '@/lib/content-images'
import { saveDocOverride, removeDocOverride, docOverrides } from '@/lib/doc-store'
import { docDrafts, removeDocDraft, saveDocDraft } from '@/lib/doc-drafts'
import { bindUnsavedBeforeUnload, setUnsavedFlush, setUnsavedLeave } from '@/lib/editor-session'
import { renderMermaidIn } from '@/lib/mermaid-render'
import { syncCatalogFromDisk } from '@/lib/catalog-sync'
import {
  deleteWorkspaceFile,
  reconnectWorkspace,
  serializeMarkdown,
  useWorkspaceFs,
  writeRepoFile,
  writeWorkspaceFile,
} from '@/lib/workspace-fs'
import { pushWorkspaceFile, useRemoteGit } from '@/lib/remote-git'
import { reindexDoc } from '@/lib/search'
import DocVisualEditor from '@/components/DocVisualEditor.vue'
import DocCreateDialog from '@/components/DocCreateDialog.vue'
import DocMoveDialog from '@/components/DocMoveDialog.vue'
import { Button } from '@/components/ui/button'
import { useRoles } from '@/composables/useRoles'
import { workspaceName } from '@/lib/branding'
import { useDocSpeech } from '@/composables/useDocSpeech'
import { renderMarkdown, stripMarkdown, type TocItem } from '@/lib/markdown'
import { messages } from '@/i18n/messages'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const { selected, ownedSlugs } = useRoles()
const { ready: fsReady, status: fsStatus } = useWorkspaceFs()
const { ready: remoteReady, autoPush } = useRemoteGit()
const { status: speechStatus, speak, pause: pauseSpeech, resume: resumeSpeech, stop: stopSpeech, supported: speechOk } =
  useDocSpeech()

const slug = computed(() => slugFromPath(route.path))

const doc = computed(() => {
  void catalog.revision
  void docOverrides.get(slug.value)
  return getDoc(slug.value)
})
const neighbors = computed(() => {
  void catalog.revision
  return getNeighbors(slug.value)
})
const canEdit = computed(() => {
  if (!doc.value) return false
  return canEditSlug(doc.value.slug, selected.value, ownedSlugs.value)
})
const createParent = computed(() => {
  const current = doc.value
  if (!current) return ''
  if (!current.slug) return 'start'
  if (current.segments[0] === 'space' || current.segments[0] === 'start') {
    return current.isIndex ? current.slug : docDir(current) || current.slug
  }
  return current.isIndex ? current.slug : docDir(current)
})
const canCreate = computed(() => canCreateIn(createParent.value, selected.value, ownedSlugs.value))
const canDelete = computed(() => canEdit.value && doc.value && !isProtectedDoc(doc.value))
const canMove = computed(() => Boolean(doc.value && canMoveDoc(doc.value, selected.value, ownedSlugs.value)))
const updatedLabel = computed(() => {
  void catalog.revision
  const stamp = formatDocUpdatedAt(doc.value?.updatedAt, locale.value)
  return stamp ? `${t.value('docUpdatedAt')} ${stamp}` : ''
})
const hasStoredDraft = computed(() => {
  void docDrafts.size
  const stored = docDrafts.get(slug.value)
  if (stored === undefined || !doc.value) return false
  return stored !== doc.value.content
})

const editing = ref(false)
const draft = ref('')
const lastSaved = ref('')
const draftRestored = ref(false)
const saving = ref(false)
const saveMessage = ref('')
const visualEditor = ref<{ getMarkdown: () => string } | null>(null)
const canPromote = computed(() => Boolean(canEdit.value && doc.value && !doc.value.isIndex && !isProtectedDoc(doc.value)))
const isFolderLanding = computed(() => {
  void catalog.revision
  if (!doc.value?.isIndex || !slug.value) return false
  return isExpandableNavFolder(findNavNode(slug.value))
})
const creating = ref(false)
const creatingAsFolder = ref(false)
const moving = ref(false)
const articleRoot = ref<HTMLElement | null>(null)
let draftTimer = 0
let stopBeforeUnload: (() => void) | undefined

function liveBody() {
  return visualEditor.value?.getMarkdown() ?? draft.value
}

function isDirty() {
  return editing.value && liveBody() !== lastSaved.value
}

function syncLeaveGuard() {
  setUnsavedLeave(isDirty(), t.value('unsavedLeave'))
}

function flushDraftNow() {
  if (!editing.value) return
  const body = liveBody()
  draft.value = body
  if (body === lastSaved.value) void removeDocDraft(slug.value)
  else void saveDocDraft(slug.value, body)
  syncLeaveGuard()
}

function discardStoredDraft() {
  if (!window.confirm(t.value('discardDraftConfirm'))) return
  void removeDocDraft(slug.value)
}

function startEdit() {
  if (!canEdit.value || !doc.value) return
  lastSaved.value = doc.value.content
  const stored = docDrafts.get(slug.value)
  if (stored !== undefined && stored !== lastSaved.value) {
    draft.value = stored
    draftRestored.value = true
  } else {
    draft.value = lastSaved.value
    draftRestored.value = false
  }
  editing.value = true
  saveMessage.value = ''
  syncLeaveGuard()
}

function cancelEdit() {
  if (isDirty() && !window.confirm(t.value('discardDraftConfirm'))) return
  void removeDocDraft(slug.value)
  editing.value = false
  draft.value = ''
  draftRestored.value = false
  saveMessage.value = ''
  setUnsavedLeave(false)
}

async function saveEdit() {
  if (!canEdit.value || !doc.value) return
  saving.value = true
  saveMessage.value = ''
  try {
    if (fsStatus.value === 'need-permission') {
      await reconnectWorkspace()
    }
    if (!fsReady.value) {
      saveMessage.value = t.value('needWorkspace')
      return
    }
    const current = doc.value
    const body = visualEditor.value?.getMarkdown() ?? draft.value
    draft.value = body
    const fileText = serializeMarkdown(current, body)
    await writeWorkspaceFile(current.relPath, fileText)
    if (current.slug === 'space/harness/AGENTS') {
      await writeRepoFile('AGENTS.md', `${body.replace(/^\n+/, '')}\n`)
    }
    await saveDocOverride(current.slug, body)
    await removeDocDraft(current.slug)
    lastSaved.value = body
    await syncCatalogFromDisk()
    const updated = getDoc(current.slug)
    if (updated) reindexDoc(updated)
    editing.value = false
    draftRestored.value = false
    setUnsavedLeave(false)
    if (remoteReady.value && autoPush.value) {
      try {
        await pushWorkspaceFile(current.relPath, fileText)
        saveMessage.value = t.value('saveOkRemote')
      } catch {
        saveMessage.value = t.value('saveRemoteFail')
      }
    } else {
      saveMessage.value = t.value('saveOk')
    }
    await load()
  } catch {
    saveMessage.value = fsReady.value ? t.value('settingsFail') : t.value('needWorkspace')
  } finally {
    saving.value = false
  }
}

async function createDoc(payload: {
  title: string
  description: string
  relPath: string
  slug: string
  order: number
}) {
  saveMessage.value = ''
  try {
    await createWorkspaceDoc(payload)
    creating.value = false
    creatingAsFolder.value = false
    await router.push(payload.slug ? `/${payload.slug}` : '/')
    saveMessage.value = t.value('docCreated')
  } catch (error) {
    const code = error instanceof Error ? error.message : ''
    if (code === 'NEED_WORKSPACE') saveMessage.value = t.value('needWorkspace')
    else if (code === 'EXISTS') saveMessage.value = t.value('docExists')
    else saveMessage.value = t.value('settingsFail')
  }
}

async function applyMove(payload: {
  title: string
  description: string
  parentSlug: string
  stem: string
}) {
  const current = doc.value
  if (!current) return
  saveMessage.value = ''
  try {
    const nextSlug = await moveWorkspaceDoc({
      slug: current.slug,
      title: payload.title,
      description: payload.description,
      parentSlug: payload.parentSlug,
      stem: payload.stem,
    })
    moving.value = false
    pendingMoveSlug.value = null
    saveMessage.value = t.value('moveDocOk')
    await router.push(nextSlug ? `/${nextSlug}` : '/')
  } catch (error) {
    const code = error instanceof Error ? error.message : ''
    if (code === 'NEED_WORKSPACE') saveMessage.value = t.value('needWorkspace')
    else if (code === 'EXISTS') saveMessage.value = t.value('docExists')
    else if (code === 'NESTED') saveMessage.value = t.value('moveDocNested')
    else saveMessage.value = t.value('settingsFail')
  }
}

function tryStartPendingMove() {
  if (pendingMoveSlug.value === null) return
  if (pendingMoveSlug.value !== slug.value) return
  if (!canMove.value) return
  moving.value = true
  pendingMoveSlug.value = null
}

async function deleteCurrent() {
  const current = doc.value
  if (!current || !canDelete.value) return
  if (!window.confirm(t.value('deleteDocConfirm'))) return
  saveMessage.value = ''
  try {
    if (fsStatus.value === 'need-permission') await reconnectWorkspace()
    if (!fsReady.value) {
      saveMessage.value = t.value('needWorkspace')
      return
    }
    await deleteWorkspaceFile(current.relPath)
    await removeDocOverride(current.slug)
    await removeDocDraft(current.slug)
    await syncCatalogFromDisk()
    const parent = docDir(current)
    await router.push(parent ? `/${parent}` : '/')
  } catch {
    saveMessage.value = t.value('settingsFail')
  }
}

const html = ref('')
const toc = ref<TocItem[]>([])
const activeId = ref('')
const loading = ref(true)
let loadGen = 0
let raf = 0
const HEADER_OFFSET = 88

async function load() {
  const gen = ++loadGen
  loading.value = true
  const current = doc.value
  if (!current) {
    if (gen !== loadGen) return
    html.value = ''
    toc.value = []
    activeId.value = ''
    loading.value = false
    return
  }
  const rendered = await renderMarkdown(current.content, docDir(current))
  if (gen !== loadGen) return
  html.value = rendered.html
  toc.value = rendered.toc
  loading.value = false
  await nextTick()
  if (route.hash) {
    const id = decodeURIComponent(route.hash.slice(1))
    document.getElementById(id)?.scrollIntoView()
  }
  updateActive()
}

function headingEls(): HTMLElement[] {
  return toc.value
    .map((item) => document.getElementById(item.id))
    .filter((el): el is HTMLElement => Boolean(el))
}

function updateActive() {
  const headings = headingEls()
  if (!headings.length) {
    activeId.value = ''
    return
  }

  const scrollBottom = window.scrollY + window.innerHeight
  const docHeight = document.documentElement.scrollHeight
  if (scrollBottom >= docHeight - 8) {
    activeId.value = headings[headings.length - 1]!.id
    return
  }

  let current = headings[0]!.id
  for (const heading of headings) {
    if (heading.getBoundingClientRect().top - HEADER_OFFSET <= 0) current = heading.id
    else break
  }
  activeId.value = current
}

function onScroll() {
  if (raf) return
  raf = window.requestAnimationFrame(() => {
    raf = 0
    updateActive()
  })
}

function scrollToHeading(id: string) {
  const el = document.getElementById(id)
  if (!el) return
  activeId.value = id
  el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  history.replaceState(history.state, '', `#${id}`)
}

async function onContentClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  const button = target?.closest('.copy-code') as HTMLButtonElement | null
  if (button) {
    const encoded = button.getAttribute('data-copy')
    if (!encoded) return
    await navigator.clipboard.writeText(decodeURIComponent(encoded))
    button.textContent = t.value('copied')
    window.setTimeout(() => {
      button.textContent = t.value('copy')
    }, 1200)
    return
  }

  const anchor = target?.closest('a') as HTMLAnchorElement | null
  if (!anchor || anchor.target === '_blank') return
  const href = anchor.getAttribute('href')
  if (!href || href.startsWith('#') || href.startsWith('mailto:') || href.startsWith('http')) return
  if (href.startsWith('/')) {
    event.preventDefault()
    void router.push(href)
  }
}

function tryStartPendingEdit() {
  if (pendingEditSlug.value === null) return
  if (pendingEditSlug.value !== slug.value) return
  if (!canEdit.value || !doc.value) return
  startEdit()
  pendingEditSlug.value = null
}

function speakableFromArticle() {
  const root = articleRoot.value
  if (!root) return ''
  const clone = root.cloneNode(true) as HTMLElement
  clone.querySelectorAll('.copy-code, .mermaid-wrap, pre, code').forEach((el) => el.remove())
  return clone.innerText.replace(/\s+/g, ' ').trim()
}

function speakCurrent() {
  const current = doc.value
  if (!current || editing.value) return
  const fromDom = speakableFromArticle()
  const fallback = [current.title, current.description, stripMarkdown(current.content)].filter(Boolean).join('。')
  const text = fromDom || fallback
  saveMessage.value = ''
  if (!speak(text, locale.value)) saveMessage.value = t.value('speakEmpty')
}

function openNewDoc() {
  creatingAsFolder.value = false
  creating.value = true
}

async function openNewFolder() {
  const current = doc.value
  if (!current) return
  saveMessage.value = ''
  try {
    if (!current.isIndex && current.slug) {
      await promoteDocToFolder(current.slug)
      saveMessage.value = t.value('promoteFolderOk')
    }
    creatingAsFolder.value = true
    creating.value = true
  } catch (error) {
    const code = error instanceof Error ? error.message : ''
    if (code === 'NEED_WORKSPACE') saveMessage.value = t.value('needWorkspace')
    else if (code === 'EXISTS') saveMessage.value = t.value('docExists')
    else saveMessage.value = t.value('settingsFail')
  }
}

async function promoteCurrent() {
  const current = doc.value
  if (!current || !canPromote.value) return
  saveMessage.value = ''
  try {
    await promoteDocToFolder(current.slug)
    saveMessage.value = t.value('promoteFolderOk')
  } catch (error) {
    const code = error instanceof Error ? error.message : ''
    if (code === 'NEED_WORKSPACE') saveMessage.value = t.value('needWorkspace')
    else if (code === 'EXISTS') saveMessage.value = t.value('docExists')
    else saveMessage.value = t.value('settingsFail')
  }
}

watch(editing, (value) => {
  if (value) stopSpeech()
})

watch(slug, () => {
  stopSpeech()
  editing.value = false
  draftRestored.value = false
  setUnsavedLeave(false)
  void load()
  queueMicrotask(() => {
    tryStartPendingEdit()
    tryStartPendingMove()
  })
})

watch(locale, () => {
  if (!editing.value) void load()
})

watch(
  () => catalog.revision,
  () => {
    if (!editing.value) void load()
  },
)

watch(pendingEditSlug, () => {
  tryStartPendingEdit()
})

watch(pendingMoveSlug, () => {
  tryStartPendingMove()
})

watch(draft, () => {
  if (!editing.value) return
  syncLeaveGuard()
  window.clearTimeout(draftTimer)
  draftTimer = window.setTimeout(() => {
    if (!editing.value) return
    const body = liveBody()
    if (body === lastSaved.value) void removeDocDraft(slug.value)
    else void saveDocDraft(slug.value, body)
    syncLeaveGuard()
  }, 700)
})

watch(
  () => [doc.value?.title, workspaceName()] as const,
  ([title]) => {
    const product = workspaceName()
    document.title = title ? `${title} · ${product}` : product
  },
  { immediate: true },
)

watch(html, async () => {
  await nextTick()
  document.querySelectorAll('.copy-code').forEach((btn) => {
    btn.textContent = messages[locale.value].copy
  })
  if (articleRoot.value && doc.value) {
    await hydrateContentImages(articleRoot.value, docDir(doc.value))
    await renderMermaidIn(articleRoot.value)
  }
})

function onThemeChange() {
  if (editing.value) return
  if (articleRoot.value) void renderMermaidIn(articleRoot.value)
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', onScroll, { passive: true })
  window.addEventListener('agi-theme', onThemeChange)
  stopBeforeUnload = bindUnsavedBeforeUnload()
  setUnsavedFlush(flushDraftNow)
  void load()
  queueMicrotask(() => {
    tryStartPendingEdit()
    tryStartPendingMove()
  })
})
onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', onScroll)
  window.removeEventListener('agi-theme', onThemeChange)
  if (raf) window.cancelAnimationFrame(raf)
  window.clearTimeout(draftTimer)
  stopBeforeUnload?.()
  setUnsavedFlush(null)
  setUnsavedLeave(false)
  stopSpeech()
})
</script>

<template>
  <div class="flex">
    <div class="min-w-0 flex-1 px-6 py-8 md:px-10 lg:px-12">
      <Transition name="doc" mode="out-in">
        <article v-if="doc" :key="slug || 'home'" class="mx-auto max-w-3xl">
          <template v-if="!slug && !editing">
            <div class="mb-4 flex items-baseline justify-between gap-4">
              <h1 class="min-w-0 text-3xl font-semibold tracking-tight text-balance">{{ doc.title }}</h1>
              <time v-if="updatedLabel" class="shrink-0 text-xs text-muted-foreground whitespace-nowrap">{{ updatedLabel }}</time>
            </div>
            <DocHomeHero />
          </template>
          <template v-else>
            <p v-if="doc.description && !isFolderLanding" class="mb-2 text-sm text-muted-foreground">{{ doc.description }}</p>
            <div class="flex items-baseline justify-between gap-4">
              <h1 class="min-w-0 text-3xl font-semibold tracking-tight text-balance">{{ doc.title }}</h1>
              <time v-if="updatedLabel" class="shrink-0 text-xs text-muted-foreground whitespace-nowrap">{{ updatedLabel }}</time>
            </div>
          </template>
          <p v-if="selected.length" class="mt-2 text-xs text-muted-foreground">
            {{ canEdit ? t('ownedBadge') : t('readOnlyBadge') }}
          </p>
          <p v-else class="mt-2 text-xs text-muted-foreground">
            <RouterLink to="/settings#roles" class="hover:underline">{{ t('pickRoleFirst') }}</RouterLink>
          </p>
          <div class="mt-3 flex flex-wrap items-center gap-2">
            <Button
              v-if="speechOk && !editing && !isFolderLanding && speechStatus === 'idle'"
              size="sm"
              variant="outline"
              :aria-label="t('speakDoc')"
              @click="speakCurrent"
            >
              <Volume2 class="size-3.5" />
              {{ t('speakDoc') }}
            </Button>
            <Button
              v-if="speechOk && !editing && !isFolderLanding && speechStatus === 'speaking'"
              size="sm"
              variant="outline"
              :aria-label="t('pauseSpeech')"
              @click="pauseSpeech"
            >
              <Pause class="size-3.5" />
              {{ t('pauseSpeech') }}
            </Button>
            <Button
              v-if="speechOk && !editing && !isFolderLanding && speechStatus === 'paused'"
              size="sm"
              variant="outline"
              :aria-label="t('resumeSpeech')"
              @click="resumeSpeech"
            >
              <Volume2 class="size-3.5" />
              {{ t('resumeSpeech') }}
            </Button>
            <Button
              v-if="speechOk && !editing && !isFolderLanding && (speechStatus === 'speaking' || speechStatus === 'paused')"
              size="sm"
              variant="ghost"
              :aria-label="t('stopSpeech')"
              @click="stopSpeech"
            >
              <Square class="size-3.5" />
              {{ t('stopSpeech') }}
            </Button>
            <p v-if="!speechOk && !editing && !isFolderLanding" class="w-full text-xs text-muted-foreground">{{ t('speechUnsupported') }}</p>
            <Button v-if="canEdit && !editing" size="sm" variant="outline" @click="startEdit">
              {{ t('editDoc') }}
            </Button>
            <Button v-if="canCreate && !editing" size="sm" variant="outline" @click="openNewDoc">
              {{ t('newDoc') }}
            </Button>
            <Button v-if="canCreate && !editing" size="sm" variant="outline" @click="openNewFolder">
              {{ t('newFolder') }}
            </Button>
            <Button v-if="canPromote && !editing" size="sm" variant="outline" @click="promoteCurrent">
              {{ t('promoteFolder') }}
            </Button>
            <Button v-if="canMove && !editing" size="sm" variant="outline" @click="moving = true">
              {{ t('moveDoc') }}
            </Button>
            <Button v-if="canDelete && !editing" size="sm" variant="ghost" @click="deleteCurrent">
              {{ t('deleteDoc') }}
            </Button>
            <template v-if="editing">
              <Button size="sm" :disabled="saving" @click="saveEdit">{{ t('saveDoc') }}</Button>
              <Button size="sm" variant="ghost" :disabled="saving" @click="cancelEdit">{{ t('cancelEdit') }}</Button>
              <RouterLink v-if="!fsReady" to="/settings" class="text-xs text-muted-foreground hover:underline">
                {{ t('settingsNav') }}
              </RouterLink>
            </template>
          </div>
          <p v-if="saveMessage" class="mt-2 text-xs text-muted-foreground">{{ saveMessage }}</p>
          <p v-if="editing && draftRestored" class="mt-2 text-xs text-muted-foreground">{{ t('draftRestored') }}</p>
          <p v-if="!editing && hasStoredDraft" class="mt-2 text-xs text-muted-foreground">
            {{ t('draftPending') }}
            <button type="button" class="ml-2 underline hover:text-foreground" @click="startEdit">{{ t('resumeDraft') }}</button>
            <button type="button" class="ml-2 underline hover:text-foreground" @click="discardStoredDraft">{{ t('discardDraft') }}</button>
          </p>
          <div class="mt-3 h-px w-full bg-border" />

          <DocVisualEditor
            v-if="editing"
            ref="visualEditor"
            v-model="draft"
            :doc-dir="docDir(doc)"
            @save="saveEdit"
          />
          <div
            v-else-if="!loading && !isFolderLanding"
            ref="articleRoot"
            class="prose prose-docs mt-8 max-w-none prose-headings:scroll-mt-24"
            @click="onContentClick"
            v-html="html"
          />
          <div v-else-if="loading" class="mt-10 space-y-3">
            <div class="h-4 w-5/6 animate-pulse rounded bg-muted" />
            <div class="h-4 w-full animate-pulse rounded bg-muted" />
            <div class="h-4 w-2/3 animate-pulse rounded bg-muted" />
          </div>

          <DocHomeNav v-if="!slug && !editing" />
          <DocChildren v-if="isFolderLanding && !editing" :slug="slug" />

          <div v-if="!isFolderLanding" class="mt-16 grid gap-4 border-t pt-8 sm:grid-cols-2">
            <RouterLink
              v-if="neighbors.prev"
              :to="neighbors.prev.slug ? `/${neighbors.prev.slug}` : '/'"
              class="group rounded-xl border bg-card p-4 transition-colors hover:bg-accent/60"
            >
              <div class="flex items-center gap-1 text-xs text-muted-foreground">
                <ArrowLeft class="size-3.5" />
                {{ t('previous') }}
              </div>
              <div class="mt-1 text-sm font-medium group-hover:text-foreground">{{ neighbors.prev.title }}</div>
            </RouterLink>
            <div v-else />
            <RouterLink
              v-if="neighbors.next"
              :to="neighbors.next.slug ? `/${neighbors.next.slug}` : '/'"
              class="group rounded-xl border bg-card p-4 text-right transition-colors hover:bg-accent/60"
            >
              <div class="flex items-center justify-end gap-1 text-xs text-muted-foreground">
                {{ t('next') }}
                <ArrowRight class="size-3.5" />
              </div>
              <div class="mt-1 text-sm font-medium">{{ neighbors.next.title }}</div>
            </RouterLink>
          </div>
        </article>
        <article v-else :key="'missing'" class="mx-auto max-w-3xl py-16">
          <h1 class="text-2xl font-semibold">{{ t('notFound') }}</h1>
          <p class="mt-2 text-muted-foreground">{{ t('notFoundHint') }}</p>
        </article>
      </Transition>
    </div>
    <aside class="sticky top-14 hidden h-[calc(100vh-3.5rem)] w-56 shrink-0 overflow-y-auto py-8 pr-6 lg:block scrollbar-thin">
      <DocStats />
      <DocToc
        v-if="!isFolderLanding"
        :items="toc"
        :active-id="activeId"
        :title="t('onThisPage')"
        @select="scrollToHeading"
      />
    </aside>
    <DocCreateDialog
      v-model:open="creating"
      :parent-slug="createParent"
      :as-folder-default="creatingAsFolder"
      @create="createDoc"
    />
    <DocMoveDialog v-model:open="moving" :slug="slug" @move="applyMove" />
  </div>
</template>
