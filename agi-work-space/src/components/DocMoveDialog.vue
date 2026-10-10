<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useI18n } from '@/composables/useI18n'
import { childRelPath, getDoc, toSlug } from '@/lib/content'
import { docFileStem, docParentSlug, moveTargetFolders } from '@/lib/doc-manage'
import { useRoles } from '@/composables/useRoles'

const props = defineProps<{
  open: boolean
  slug: string
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  move: [payload: { title: string; description: string; parentSlug: string; stem: string }]
}>()

const { t } = useI18n()
const { selected, ownedSlugs } = useRoles()
const title = ref('')
const description = ref('')
const stem = ref('')
const parentSlug = ref('')
const error = ref('')

const doc = computed(() => getDoc(props.slug))
const folders = computed(() => {
  const current = doc.value
  if (!current) return []
  return moveTargetFolders(current, selected.value, ownedSlugs.value)
})

const previewPath = computed(() => {
  const current = doc.value
  if (!current) return ''
  try {
    return childRelPath(parentSlug.value, stem.value || 'doc', current.isIndex)
  } catch {
    return ''
  }
})

watch(
  () => [props.open, props.slug] as const,
  ([open]) => {
    if (!open) return
    const current = getDoc(props.slug)
    error.value = ''
    if (!current) return
    title.value = current.title
    description.value = current.description
    stem.value = docFileStem(current)
    parentSlug.value = docParentSlug(current)
  },
)

function close() {
  emit('update:open', false)
}

function submit() {
  error.value = ''
  const current = doc.value
  if (!current) return
  if (!title.value.trim()) {
    error.value = t.value('docTitleRequired')
    return
  }
  try {
    const relPath = childRelPath(parentSlug.value, stem.value.trim(), current.isIndex)
    toSlug(relPath)
    emit('move', {
      title: title.value.trim(),
      description: description.value.trim(),
      parentSlug: parentSlug.value,
      stem: stem.value.trim(),
    })
  } catch {
    error.value = t.value('docNameInvalid')
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center p-4">
      <button type="button" class="absolute inset-0 bg-background/70 backdrop-blur-sm" @click="close" />
      <div class="relative w-full max-w-md rounded-xl border bg-background p-5 shadow-xl">
        <h2 class="text-base font-semibold">{{ t('moveDoc') }}</h2>
        <p class="mt-1 text-xs text-muted-foreground">{{ t('moveDocHint') }}</p>
        <label class="mt-4 block text-xs text-muted-foreground">{{ t('newDocTitle') }}</label>
        <Input v-model="title" class="mt-1" @keydown.enter.prevent="submit" />
        <label class="mt-3 block text-xs text-muted-foreground">{{ t('newDocDesc') }}</label>
        <Input v-model="description" class="mt-1" />
        <label class="mt-3 block text-xs text-muted-foreground">{{ t('moveDocParent') }}</label>
        <select
          v-model="parentSlug"
          class="mt-1 flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        >
          <option v-for="item in folders" :key="item.slug || 'root'" :value="item.slug">
            {{ item.slug ? item.title : t('moveDocRoot') }}
          </option>
        </select>
        <label class="mt-3 block text-xs text-muted-foreground">{{ t('newDocFile') }}</label>
        <Input v-model="stem" class="mt-1 font-mono text-xs" />
        <p v-if="previewPath" class="mt-2 font-mono text-[11px] text-muted-foreground">{{ previewPath }}</p>
        <p v-if="error" class="mt-2 text-xs text-destructive">{{ error }}</p>
        <div class="mt-4 flex justify-end gap-2">
          <Button type="button" size="sm" variant="ghost" @click="close">{{ t('cancelEdit') }}</Button>
          <Button type="button" size="sm" @click="submit">{{ t('moveDocSave') }}</Button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
