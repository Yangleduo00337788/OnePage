<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/composables/useI18n'
import { firePushConfetti } from '@/lib/confetti'
import { packFor, pickPushSuccessLine } from '@/lib/push-copy'
import { pushWorkspaceDocs } from '@/lib/remote-git'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

const { t, locale } = useI18n()
const commitMessage = ref('')
const phase = ref<'form' | 'running' | 'done' | 'error'>('form')
const percent = ref(0)
const current = ref('')
const doneCount = ref(0)
const totalCount = ref(0)
const result = ref('')
const errorText = ref('')
const firstDone = ref(false)

function defaultMessage() {
  const now = new Date()
  const stamp = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
  return locale.value === 'zh' ? `chore: 提交并推送项目 ${stamp}` : `chore: commit and push ${stamp}`
}

function pickSuccessLine() {
  return pickPushSuccessLine(locale.value) || t.value('pushOk')
}

watch(
  () => props.open,
  (open) => {
    if (!open) return
    phase.value = 'form'
    percent.value = 0
    current.value = ''
    result.value = ''
    errorText.value = ''
    firstDone.value = false
    commitMessage.value = defaultMessage()
  },
)

const canClose = computed(() => phase.value !== 'running')

function close() {
  if (!canClose.value) return
  emit('close')
}

async function startPush() {
  const message = commitMessage.value.trim()
  if (!message) return
  phase.value = 'running'
  errorText.value = ''
  try {
    const outcome = await pushWorkspaceDocs(message, (progress) => {
      percent.value = progress.percent
      current.value = progress.current
      doneCount.value = progress.done
      totalCount.value = progress.total
    })
    percent.value = 100
    firstDone.value = outcome.first
    result.value = outcome.first ? packFor(locale.value).firstSuccess : pickSuccessLine()
    phase.value = 'done'
    if (outcome.first) firePushConfetti()
  } catch (error) {
    phase.value = 'error'
    errorText.value = error instanceof Error ? error.message : t.value('pushFail')
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-[80] flex items-center justify-center p-4">
      <button type="button" class="absolute inset-0 bg-background/70 backdrop-blur-sm" :disabled="!canClose" @click="close" />
      <div class="relative w-full max-w-md rounded-2xl border bg-card p-5 shadow-xl">
        <h2 class="text-base font-semibold">{{ t('pushTitle') }}</h2>
        <p class="mt-1 text-sm leading-6 text-muted-foreground">{{ t('pushHint') }}</p>

        <template v-if="phase === 'form'">
          <label class="mt-4 block text-xs text-muted-foreground">{{ t('pushMessage') }}</label>
          <textarea
            v-model="commitMessage"
            class="mt-1.5 min-h-24 w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm outline-none focus-visible:ring-1 focus-visible:ring-ring"
            maxlength="200"
          />
          <div class="mt-4 flex justify-end gap-2">
            <Button size="sm" variant="ghost" @click="close">{{ t('cancelEdit') }}</Button>
            <Button size="sm" :disabled="!commitMessage.trim()" @click="startPush">{{ t('pushStart') }}</Button>
          </div>
        </template>

        <template v-else-if="phase === 'running'">
          <p class="mt-4 text-sm">{{ t('pushProgress') }} {{ percent }}%</p>
          <div class="mt-2 h-2 overflow-hidden rounded-full bg-muted">
            <div class="h-full bg-foreground transition-[width] duration-300" :style="{ width: `${percent}%` }" />
          </div>
          <p class="mt-2 text-xs text-muted-foreground">
            {{ current }}
            <span v-if="totalCount > 1"> · {{ doneCount }} / {{ totalCount }}</span>
          </p>
        </template>

        <template v-else-if="phase === 'done'">
          <p class="mt-4 text-sm font-medium">{{ result }}</p>
          <p v-if="firstDone" class="mt-1 text-xs text-muted-foreground">{{ packFor(locale).firstHint }}</p>
          <div class="mt-4 flex justify-end">
            <Button size="sm" @click="close">{{ t('pushClose') }}</Button>
          </div>
        </template>

        <template v-else>
          <p class="mt-4 text-sm text-destructive">{{ errorText }}</p>
          <div class="mt-4 flex justify-end gap-2">
            <Button size="sm" variant="ghost" @click="close">{{ t('pushClose') }}</Button>
            <Button size="sm" @click="phase = 'form'">{{ t('pushRetry') }}</Button>
          </div>
        </template>
      </div>
    </div>
  </Teleport>
</template>
