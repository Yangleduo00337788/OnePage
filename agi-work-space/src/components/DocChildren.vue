<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { FileText, Folder } from 'lucide-vue-next'
import { useI18n } from '@/composables/useI18n'
import { catalog, getDoc, pageChildren, type NavNode } from '@/lib/content'

const props = defineProps<{
  slug: string
}>()

const { t } = useI18n()
const groups = computed(() => {
  void catalog.revision
  return pageChildren(props.slug)
})
const hasDocs = computed(() => groups.value.docs.length > 0)
const hasFolders = computed(() => groups.value.folders.length > 0)

function hrefOf(node: NavNode) {
  if (node.slug === undefined) return ''
  return node.slug ? `/${node.slug}` : '/'
}

function descOf(node: NavNode) {
  if (node.slug === undefined) return ''
  return getDoc(node.slug)?.description ?? ''
}
</script>

<template>
  <section v-if="hasDocs || hasFolders" class="mt-10">
    <h2 class="text-sm font-semibold tracking-tight">{{ t('childrenTitle') }}</h2>
    <p class="mt-1 text-xs text-muted-foreground">{{ t('childrenHint') }}</p>
    <p v-if="hasFolders && !hasDocs" class="mt-2 text-xs text-muted-foreground">{{ t('childrenFoldersOnly') }}</p>

    <div v-if="hasFolders" class="mt-4">
      <p class="mb-2 flex items-center gap-1.5 text-[11px] font-medium uppercase tracking-[0.14em] text-muted-foreground">
        <Folder class="size-3.5" />
        {{ t('folderKind') }}
      </p>
      <div class="grid gap-2 sm:grid-cols-2">
        <div
          v-for="node in groups.folders"
          :key="node.id"
          class="rounded-xl border bg-card p-4 text-sm"
        >
          <div class="flex items-center gap-2 font-medium">
            <Folder class="size-3.5 shrink-0 text-muted-foreground" />
            {{ node.title }}
          </div>
          <p v-if="descOf(node)" class="mt-1 text-xs leading-5 text-muted-foreground">{{ descOf(node) }}</p>
        </div>
      </div>
    </div>

    <div v-if="hasDocs" class="mt-4">
      <p class="mb-2 flex items-center gap-1.5 text-[11px] font-medium uppercase tracking-[0.14em] text-muted-foreground">
        <FileText class="size-3.5" />
        {{ t('docKind') }}
      </p>
      <div class="grid gap-2 sm:grid-cols-2">
        <RouterLink
          v-for="node in groups.docs"
          :key="node.id"
          :to="hrefOf(node)"
          class="rounded-xl border bg-card p-4 transition-colors hover:bg-accent/60"
        >
          <div class="flex items-center gap-2 text-sm font-medium">
            <FileText class="size-3.5 shrink-0 text-muted-foreground" />
            {{ node.title }}
          </div>
          <p v-if="descOf(node)" class="mt-1 text-xs leading-5 text-muted-foreground">{{ descOf(node) }}</p>
        </RouterLink>
      </div>
    </div>
  </section>
</template>
