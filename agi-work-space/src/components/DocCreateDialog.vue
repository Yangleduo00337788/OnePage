<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useI18n } from '@/composables/useI18n'
import { childRelPath, fileStemFromTitle, nextOrderInFolder, toSlug } from '@/lib/content'

const props = defineProps<{
  open: boolean
  parentSlug: string
  asFolderDefault?: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  create: [payload: { title: string; description: string; relPath: string; slug: string; order: number }]
}>()

const { t } = useI18n()
const title = ref('')
const description = ref('')
const stem = ref('')
const asFolder = ref(false)
const error = ref('')

watch(
  () => props.open,
  (open) => {
    if (!open) return
    title.value = ''
    description.value = ''
    stem.value = ''
    asFolder.value = Boolean(props.asFolderDefault)
    error.value = ''
  },
)

watch(title, (value) => {
  stem.value = fileStemFromTitle(value)
})

const previewPath = computed(() => {
  try {
    return childRelPath(props.parentSlug, stem.value || 'doc', asFolder.value)
  } catch {
    return ''
  }
})

function close() {
  emit('update:open', false)
}

function submit() {
  error.value = ''
  const name = (stem.value || fileStemFromTitle(title.value)).trim()
  if (!title.value.trim()) {
    error.value = t.value('docTitleRequired')
    return
  }
  try {
    const relPath = childRelPath(props.parentSlug, name, asFolder.value)
    const slug = toSlug(relPath)
    emit('create', {
      title: title.value.trim(),
      description: description.value.trim(),
      relPath,
      slug,
      order: nextOrderInFolder(props.parentSlug),
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
        <h2 class="text-base font-semibold">{{ asFolder ? t('newFolder') : t('newDoc') }}</h2>
        <p class="mt-1 text-xs text-muted-foreground">{{ asFolder ? t('newFolderHint') : t('newDocHint') }}</p>
        <label class="mt-4 block text-xs text-muted-foreground">{{ t('newDocTitle') }}</label>
        <Input v-model="title" class="mt-1" @keydown.enter.prevent="submit" />
        <label class="mt-3 block text-xs text-muted-foreground">{{ t('newDocDesc') }}</label>
        <Input v-model="description" class="mt-1" />
        <label class="mt-3 block text-xs text-muted-foreground">{{ t('newDocFile') }}</label>
        <Input v-model="stem" class="mt-1 font-mono text-xs" />
        <label class="mt-3 flex items-center gap-2 text-sm">
          <input v-model="asFolder" type="checkbox" class="size-4 accent-foreground" />
          {{ t('newDocAsFolder') }}
        </label>
        <p v-if="previewPath" class="mt-2 font-mono text-[11px] text-muted-foreground">{{ previewPath }}</p>
        <p v-if="error" class="mt-2 text-xs text-destructive">{{ error }}</p>
        <div class="mt-4 flex justify-end gap-2">
          <Button type="button" size="sm" variant="ghost" @click="close">{{ t('cancelEdit') }}</Button>
          <Button type="button" size="sm" @click="submit">{{ t('createDoc') }}</Button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
