<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/composables/useI18n'
import {
  extensionForImage,
  hydrateContentImages,
  objectUrlForContentPath,
  relativeFromDoc,
} from '@/lib/content-images'
import { editableHtmlToMarkdown, markdownToEditableHtml } from '@/lib/html-markdown'
import { extractMermaidBlocks, renderMermaidSources } from '@/lib/mermaid-render'
import { useWorkspaceFs, writeWorkspaceBytes } from '@/lib/workspace-fs'

const props = defineProps<{
  modelValue: string
  docDir?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  save: []
}>()

const { t } = useI18n()
const { ready: fsReady } = useWorkspaceFs()
const editor = ref<HTMLElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const imageMessage = ref('')
const mermaidHost = ref<HTMLElement | null>(null)
let mermaidTimer = 0

function syncFromMarkdown(source: string) {
  if (!editor.value) return
  editor.value.innerHTML = markdownToEditableHtml(source) || '<p></p>'
  void hydrateContentImages(editor.value, props.docDir ?? '')
}

function currentMarkdown() {
  return editor.value ? editableHtmlToMarkdown(editor.value.innerHTML) : props.modelValue
}

function emitMarkdown() {
  emit('update:modelValue', currentMarkdown())
  scheduleMermaidPreview()
}

function run(command: string, value?: string) {
  editor.value?.focus()
  document.execCommand(command, false, value)
  emitMarkdown()
}

function setBlock(tag: string) {
  run('formatBlock', tag)
}

function insertTable() {
  run(
    'insertHTML',
    '<div class="table-wrap"><table><thead><tr><th>列 1</th><th>列 2</th></tr></thead><tbody><tr><td></td><td></td></tr></tbody></table></div><p></p>',
  )
}

function insertLink() {
  const href = window.prompt(t.value('fmtLinkPrompt'), 'https://')
  if (!href) return
  run('createLink', href)
}

function onEditorClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  if (target?.closest('a')) event.preventDefault()
}

async function insertImageFile(file: File) {
  imageMessage.value = ''
  if (!file.type.startsWith('image/')) return
  if (file.size > 8 * 1024 * 1024) {
    imageMessage.value = t.value('imageTooLarge')
    return
  }
  if (!fsReady.value) {
    imageMessage.value = t.value('needWorkspace')
    return
  }
  const ext = extensionForImage(file)
  const name = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}.${ext}`
  const dir = props.docDir ?? ''
  const rel = dir ? `${dir}/assets/${name}` : `assets/${name}`
  await writeWorkspaceBytes(rel, await file.arrayBuffer())
  const url = (await objectUrlForContentPath(rel)) ?? ''
  const mdSrc = relativeFromDoc(dir, rel)
  const alt = file.name.replace(/\.[^.]+$/, '')
  editor.value?.focus()
  document.execCommand(
    'insertHTML',
    false,
    `<img src="${url}" alt="${alt.replaceAll('"', '')}" data-content-path="${rel}" data-rel="${mdSrc}">`,
  )
  emitMarkdown()
}

function onPaste(event: ClipboardEvent) {
  const items = [...(event.clipboardData?.items ?? [])]
  const files = items
    .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
    .map((item) => item.getAsFile())
    .filter((file): file is File => Boolean(file))
  if (!files.length) return
  event.preventDefault()
  void Promise.all(files.map((file) => insertImageFile(file)))
}

function onDrop(event: DragEvent) {
  const files = [...(event.dataTransfer?.files ?? [])].filter((file) => file.type.startsWith('image/'))
  if (!files.length) return
  event.preventDefault()
  void Promise.all(files.map((file) => insertImageFile(file)))
}

function pickImage() {
  fileInput.value?.click()
}

function insertMermaid() {
  editor.value?.focus()
  document.execCommand(
    'insertHTML',
    false,
    '<pre class="mermaid-source"><code class="language-mermaid">flowchart TD\n  A[开始] --&gt; B[结束]</code></pre><p></p>',
  )
  emitMarkdown()
}

function scheduleMermaidPreview() {
  window.clearTimeout(mermaidTimer)
  mermaidTimer = window.setTimeout(() => {
    const codes = extractMermaidBlocks(currentMarkdown())
    void renderMermaidSources(codes, mermaidHost.value)
  }, 450)
}

function onFilePicked(event: Event) {
  const input = event.target as HTMLInputElement
  const files = [...(input.files ?? [])]
  input.value = ''
  void Promise.all(files.map((file) => insertImageFile(file)))
}

onMounted(() => {
  syncFromMarkdown(props.modelValue)
  scheduleMermaidPreview()
  window.addEventListener('agi-theme', scheduleMermaidPreview)
})

onUnmounted(() => {
  window.clearTimeout(mermaidTimer)
  window.removeEventListener('agi-theme', scheduleMermaidPreview)
})

watch(
  () => props.modelValue,
  (value) => {
    if (!editor.value) return
    if (document.activeElement === editor.value) return
    if (currentMarkdown() === value) return
    syncFromMarkdown(value)
    scheduleMermaidPreview()
  },
)

defineExpose({
  getMarkdown: currentMarkdown,
  focus: () => editor.value?.focus(),
})
</script>

<template>
  <div class="mt-6">
    <div class="flex flex-wrap gap-1 rounded-xl border bg-muted/40 p-1.5">
      <Button type="button" size="sm" variant="ghost" @click="setBlock('h2')">{{ t('fmtHeading2') }}</Button>
      <Button type="button" size="sm" variant="ghost" @click="setBlock('h3')">{{ t('fmtHeading3') }}</Button>
      <Button type="button" size="sm" variant="ghost" @click="setBlock('p')">{{ t('fmtParagraph') }}</Button>
      <Button type="button" size="sm" variant="ghost" @click="run('bold')">{{ t('fmtBold') }}</Button>
      <Button type="button" size="sm" variant="ghost" @click="run('italic')">{{ t('fmtItalic') }}</Button>
      <Button type="button" size="sm" variant="ghost" @click="run('insertUnorderedList')">{{ t('fmtList') }}</Button>
      <Button type="button" size="sm" variant="ghost" @click="run('insertOrderedList')">{{ t('fmtOrdered') }}</Button>
      <Button type="button" size="sm" variant="ghost" @click="run('formatBlock', 'blockquote')">{{ t('fmtQuote') }}</Button>
      <Button type="button" size="sm" variant="ghost" @click="insertTable">{{ t('fmtTable') }}</Button>
      <Button type="button" size="sm" variant="ghost" @click="insertLink">{{ t('fmtLink') }}</Button>
      <Button type="button" size="sm" variant="ghost" @click="pickImage">{{ t('fmtImage') }}</Button>
      <Button type="button" size="sm" variant="ghost" @click="insertMermaid">{{ t('fmtMermaid') }}</Button>
    </div>
    <p class="mt-2 text-xs text-muted-foreground">{{ t('editHint') }}</p>
    <p v-if="imageMessage" class="mt-1 text-xs text-muted-foreground">{{ imageMessage }}</p>
    <input ref="fileInput" type="file" accept="image/*" class="hidden" @change="onFilePicked" />
    <div
      ref="editor"
      class="prose prose-docs editor-surface mt-4 max-w-none rounded-xl border bg-background px-6 py-5 outline-none prose-headings:scroll-mt-24"
      contenteditable="true"
      spellcheck="false"
      @input="emitMarkdown"
      @click="onEditorClick"
      @paste="onPaste"
      @drop="onDrop"
      @dragover.prevent
      @keydown.ctrl.s.prevent="emit('save')"
      @keydown.meta.s.prevent="emit('save')"
    />
    <p class="mt-4 text-xs text-muted-foreground">{{ t('mermaidPreviewHint') }}</p>
    <div ref="mermaidHost" class="mermaid-live mt-2 space-y-3" />
  </div>
</template>
