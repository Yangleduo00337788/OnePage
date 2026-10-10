import { ref } from 'vue'

const blocked = ref(false)
const leaveMessage = ref('')
let flushDraft: (() => void) | null = null

export function setUnsavedLeave(active: boolean, message = '') {
  blocked.value = active
  if (message) leaveMessage.value = message
}

export function setUnsavedFlush(fn: (() => void) | null) {
  flushDraft = fn
}

export function confirmLeaveIfUnsaved(): boolean {
  flushDraft?.()
  if (!blocked.value) return true
  return window.confirm(leaveMessage.value || 'Leave without saving?')
}

export function bindUnsavedBeforeUnload() {
  if (typeof window === 'undefined') return () => {}
  const onBeforeUnload = (event: BeforeUnloadEvent) => {
    if (!blocked.value) return
    event.preventDefault()
    event.returnValue = ''
  }
  window.addEventListener('beforeunload', onBeforeUnload)
  return () => window.removeEventListener('beforeunload', onBeforeUnload)
}
