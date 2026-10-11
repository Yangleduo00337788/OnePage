<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onClickOutside } from '@vueuse/core'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ChevronRight, FileText, Folder, FolderInput, FolderOpen, FolderPlus, Pencil, Plus } from 'lucide-vue-next'
import { catalog, countWorkspaceDocs, docDir, getDoc, pageChildren, slugFromPath } from '@/lib/content'
import { canCreateIn, canEditSlug, canMoveDoc, createWorkspaceDoc } from '@/lib/doc-manage'
import { pendingEditSlug, pendingMoveSlug } from '@/lib/doc-session'
import NavTree from '@/components/NavTree.vue'
import DocCreateDialog from '@/components/DocCreateDialog.vue'
import { useI18n } from '@/composables/useI18n'
import { useRoles } from '@/composables/useRoles'
import { SPACE_NAV_ITEMS, activeSpaceItem, isSpacePath } from '@/lib/space-nav'
import { cn } from '@/lib/utils'

defineProps<{
  title: string
}>()

const emit = defineEmits<{
  navigate: []
}>()

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { selected, ownedSlugs } = useRoles()
const spaceOpen = ref(false)
const onSpace = computed(() => isSpacePath(route.path))
const activeId = computed(() => activeSpaceItem(route.path))
const creatingParent = ref('')
const creatingAsFolder = ref(false)
const createOpen = computed({
  get: () => creatingParent.value !== '',
  set: (value) => {
    if (!value) {
      creatingParent.value = ''
      creatingAsFolder.value = false
    }
  },
})
const actionMessage = ref('')
const anyRole = computed(() => selected.value.length > 0)
const addMenuOpen = ref(false)
const addMenuEl = ref<HTMLElement | null>(null)
onClickOutside(addMenuEl, () => {
  addMenuOpen.value = false
})
const docCount = computed(() => {
  void catalog.revision
  return countWorkspaceDocs().files
})
const harnessDocs = computed(() => {
  void catalog.revision
  return pageChildren('space/harness').docs
})

function isHarnessChildActive(slug: string | undefined) {
  return Boolean(slug) && route.path === `/${slug}`
}

watch(
  onSpace,
  (value) => {
    if (value) spaceOpen.value = true
  },
  { immediate: true },
)

function toggleSpace() {
  spaceOpen.value = !spaceOpen.value
}

function spaceSlug(to: string) {
  return to.replace(/^\/+/, '')
}

function canEditSpace(to: string) {
  if (to === '/settings') return false
  return canEditSlug(spaceSlug(to), selected.value, ownedSlugs.value)
}

function canCreateSpace(to: string) {
  if (to === '/settings') return false
  return canCreateIn(spaceSlug(to), selected.value, ownedSlugs.value)
}

function canMoveSpace(to: string) {
  if (to === '/settings') return false
  const current = getDoc(spaceSlug(to))
  if (!current) return false
  return canMoveDoc(current, selected.value, ownedSlugs.value)
}

function defaultCreateParent() {
  const slug = slugFromPath(route.path)
  if (!slug || slug === 'settings' || slug === 'space' || slug.startsWith('space/')) {
    return canCreateIn('start', selected.value, ownedSlugs.value) ? 'start' : ''
  }
  const current = getDoc(slug)
  if (current?.isIndex && canCreateIn(current.slug, selected.value, ownedSlugs.value)) {
    return current.slug
  }
  const parent = current ? docDir(current) : ''
  if (parent && canCreateIn(parent, selected.value, ownedSlugs.value)) return parent
  return canCreateIn('start', selected.value, ownedSlugs.value) ? 'start' : ''
}

function toggleAddMenu() {
  actionMessage.value = ''
  if (!anyRole.value) {
    actionMessage.value = t.value('pickRoleFirst')
    return
  }
  addMenuOpen.value = !addMenuOpen.value
}

function chooseAdd(asFolder: boolean) {
  addMenuOpen.value = false
  const parent = defaultCreateParent()
  if (!parent) {
    actionMessage.value = t.value('pickRoleFirst')
    return
  }
  openCreate({ parentSlug: parent, asFolder })
}

function openCreate(payload: { parentSlug: string; asFolder: boolean } | string) {
  actionMessage.value = ''
  if (typeof payload === 'string') {
    creatingParent.value = payload
    creatingAsFolder.value = false
    return
  }
  creatingParent.value = payload.parentSlug
  creatingAsFolder.value = payload.asFolder
}

function openMove(slug: string) {
  pendingMoveSlug.value = slug
  void router.push(slug ? `/${slug}` : '/')
  emit('navigate')
}

function openEdit(slug: string) {
  pendingEditSlug.value = slug
  void router.push(slug ? `/${slug}` : '/')
  emit('navigate')
}

async function onCreate(payload: {
  title: string
  description: string
  relPath: string
  slug: string
  order: number
}) {
  actionMessage.value = ''
  try {
    await createWorkspaceDoc(payload)
    creatingParent.value = ''
    creatingAsFolder.value = false
    if (payload.slug && !payload.relPath.endsWith('/index.md')) {
      pendingEditSlug.value = payload.slug
    }
    await router.push(payload.slug ? `/${payload.slug}` : '/')
    emit('navigate')
  } catch (error) {
    const code = error instanceof Error ? error.message : ''
    if (code === 'NEED_WORKSPACE') actionMessage.value = t.value('needWorkspace')
    else if (code === 'EXISTS') actionMessage.value = t.value('docExists')
    else actionMessage.value = t.value('settingsFail')
  }
}
</script>

<template>
  <aside class="flex h-full flex-col">
    <div class="flex items-center gap-1 px-6 pb-3 pt-3 pr-8">
      <span class="min-w-0 flex-1 truncate text-[11px] font-medium uppercase tracking-[0.14em] text-muted-foreground">
        {{ title }}
      </span>
      <span
        class="shrink-0 whitespace-nowrap text-[11px] tabular-nums text-muted-foreground"
        :title="t('docsNavCountHint')"
      >
        {{ docCount }} {{ t('docStatsUnit') }}
      </span>
      <div ref="addMenuEl" class="relative shrink-0">
        <button
          type="button"
          class="flex size-7 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
          :title="t('docsNavAdd')"
          :aria-label="t('docsNavAdd')"
          :aria-expanded="addMenuOpen"
          @click.stop="toggleAddMenu"
        >
          <Plus class="size-3.5" />
        </button>
        <div
          v-if="addMenuOpen"
          class="absolute right-0 z-40 mt-1 w-40 overflow-hidden rounded-md border bg-background py-1 shadow-md"
        >
          <button
            type="button"
            class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-[13px] text-foreground hover:bg-accent"
            @click="chooseAdd(false)"
          >
            <FileText class="size-3.5 text-muted-foreground" />
            {{ t('newDoc') }}
          </button>
          <button
            type="button"
            class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-[13px] text-foreground hover:bg-accent"
            @click="chooseAdd(true)"
          >
            <FolderPlus class="size-3.5 text-muted-foreground" />
            {{ t('newFolder') }}
          </button>
        </div>
      </div>
    </div>
    <nav class="flex-1 overflow-y-auto px-6 pr-8 pb-8 scrollbar-thin">
      <NavTree
        :nodes="catalog.docsNavTree"
        @navigate="emit('navigate')"
        @create="openCreate"
        @edit="openEdit"
        @move="openMove"
      />
      <div class="mt-0.5 flex flex-col">
        <div
          class="flex items-center rounded-md transition-colors hover:bg-sidebar-accent/70"
          :class="onSpace ? 'font-medium' : ''"
        >
          <button
            type="button"
            class="flex size-7 shrink-0 items-center justify-center rounded-md text-muted-foreground transition-colors hover:text-sidebar-accent-foreground"
            :aria-expanded="spaceOpen"
            :aria-label="t('settingsNav')"
            @click.stop="toggleSpace"
          >
            <ChevronRight
              class="size-3.5 transition-transform duration-200"
              :class="spaceOpen ? 'rotate-90' : ''"
            />
          </button>
          <button
            type="button"
            :class="
              cn(
                'flex min-w-0 flex-1 items-center gap-1.5 px-2 py-1.5 text-left text-[13px] leading-5',
                onSpace
                  ? 'text-sidebar-accent-foreground'
                  : 'text-sidebar-foreground/80 hover:text-sidebar-accent-foreground',
              )
            "
            @click="toggleSpace"
          >
            <FolderOpen v-if="spaceOpen" class="size-3.5 shrink-0 text-muted-foreground" />
            <Folder v-else class="size-3.5 shrink-0 text-muted-foreground" />
            <span class="truncate">{{ t('settingsNav') }}</span>
          </button>
        </div>
        <div
          class="grid transition-[grid-template-rows] duration-200 ease-out"
          :class="spaceOpen ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]'"
        >
          <div class="overflow-hidden">
            <div class="mt-0.5 ml-3.5 border-l border-sidebar-border/80">
              <ul class="flex flex-col gap-0.5" style="padding-left: 0.7rem">
                <li v-for="item in SPACE_NAV_ITEMS" :key="item.id">
                  <div
                    class="group/nav ml-7 flex items-center rounded-md transition-colors"
                    :class="
                      activeId === item.id && (item.id !== 'harness' || route.path === item.to)
                        ? 'bg-sidebar-accent font-medium text-sidebar-accent-foreground'
                        : 'hover:bg-sidebar-accent/70'
                    "
                  >
                    <RouterLink
                      :to="item.to"
                      :class="
                        cn(
                          'flex min-w-0 flex-1 items-center gap-1.5 px-2 py-1.5 text-[13px] leading-5',
                          activeId === item.id
                            ? 'text-sidebar-accent-foreground'
                            : 'text-sidebar-foreground/75 hover:text-sidebar-accent-foreground',
                        )
                      "
                      @click="emit('navigate')"
                    >
                      <FileText class="size-3.5 shrink-0 text-muted-foreground" />
                      <span class="truncate">{{ t(item.labelKey) }}</span>
                    </RouterLink>
                    <div
                      v-if="anyRole && (canEditSpace(item.to) || canCreateSpace(item.to) || canMoveSpace(item.to))"
                      class="flex shrink-0 pr-0.5 opacity-0 transition-opacity group-hover/nav:opacity-100 group-focus-within/nav:opacity-100"
                      :class="activeId === item.id ? 'opacity-100' : ''"
                    >
                      <button
                        v-if="canEditSpace(item.to)"
                        type="button"
                        class="flex size-7 items-center justify-center rounded-md text-muted-foreground hover:text-sidebar-accent-foreground"
                        :title="t('editDoc')"
                        :aria-label="t('editDoc')"
                        @click.stop="openEdit(spaceSlug(item.to))"
                      >
                        <Pencil class="size-3.5" />
                      </button>
                      <button
                        v-if="canMoveSpace(item.to)"
                        type="button"
                        class="flex size-7 items-center justify-center rounded-md text-muted-foreground hover:text-sidebar-accent-foreground"
                        :title="t('moveDoc')"
                        :aria-label="t('moveDoc')"
                        @click.stop="openMove(spaceSlug(item.to))"
                      >
                        <FolderInput class="size-3.5" />
                      </button>
                      <button
                        v-if="canCreateSpace(item.to)"
                        type="button"
                        class="flex size-7 items-center justify-center rounded-md text-muted-foreground hover:text-sidebar-accent-foreground"
                        :title="t('newDoc')"
                        :aria-label="t('newDoc')"
                        @click.stop="openCreate({ parentSlug: spaceSlug(item.to), asFolder: false })"
                      >
                        <Plus class="size-3.5" />
                      </button>
                      <button
                        v-if="canCreateSpace(item.to)"
                        type="button"
                        class="flex size-7 items-center justify-center rounded-md text-muted-foreground hover:text-sidebar-accent-foreground"
                        :title="t('newFolder')"
                        :aria-label="t('newFolder')"
                        @click.stop="openCreate({ parentSlug: spaceSlug(item.to), asFolder: true })"
                      >
                        <FolderPlus class="size-3.5" />
                      </button>
                    </div>
                  </div>
                  <ul v-if="item.id === 'harness' && harnessDocs.length" class="mt-0.5 flex flex-col gap-0.5" style="padding-left: 1.15rem">
                    <li v-for="child in harnessDocs" :key="child.id">
                      <button
                        type="button"
                        class="group/nav ml-7 flex w-[calc(100%-1.75rem)] items-center rounded-md px-2 py-1.5 text-left text-[13px] leading-5 transition-colors"
                        :class="
                          isHarnessChildActive(child.slug)
                            ? 'bg-sidebar-accent font-medium text-sidebar-accent-foreground'
                            : 'text-sidebar-foreground/75 hover:bg-sidebar-accent/70 hover:text-sidebar-accent-foreground'
                        "
                        @click="child.slug ? openEdit(child.slug) : undefined"
                      >
                        <FileText class="mr-1.5 size-3.5 shrink-0 text-muted-foreground" />
                        <span class="truncate">{{ child.title }}</span>
                      </button>
                    </li>
                  </ul>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>
      <p v-if="actionMessage" class="mt-3 px-2 text-xs text-muted-foreground">{{ actionMessage }}</p>
    </nav>
    <DocCreateDialog
      v-model:open="createOpen"
      :parent-slug="creatingParent"
      :as-folder-default="creatingAsFolder"
      @create="onCreate"
    />
  </aside>
</template>
