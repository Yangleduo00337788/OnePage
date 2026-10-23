/**
 * 耶耶会话：对话落 localStorage；回复走 POST /ai/chat/stream。
 * 历史、当前会话、新建 / 删除都走这一份状态。
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { AiCitation } from '@onepage/api-client'

export type YehRole = 'user' | 'ai'
export type YehStage = 'retrieve' | 'notes' | 'write'
export type YehProcess = {
  question: string
  noteCount: number
  open: boolean
  openStep: YehStage | ''
}
export type YehNoteChip = { postId: string; title: string; coverUrl?: string }
export type YehMsg = {
  role: YehRole
  text: string
  imageUrls?: string[]
  notes?: YehNoteChip[]
  citations?: AiCitation[]
  process?: YehProcess
}
export type YehSession = {
  id: string
  title: string
  messages: YehMsg[]
  updatedAt: number
}

const KEY = 'onepage.yehyeh.sessions'

function load(): YehSession[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(KEY) || '[]') as unknown
    if (!Array.isArray(parsed)) return []
    return parsed.filter(
      (item): item is YehSession =>
        Boolean(item) &&
        typeof item === 'object' &&
        typeof (item as YehSession).id === 'string' &&
        Array.isArray((item as YehSession).messages),
    )
  } catch {
    return []
  }
}

function nid() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`
}

export const useYehYeh = defineStore('yehyeh', () => {
  const sessions = ref<YehSession[]>(load())
  const currentId = ref<string | null>(null)

  const current = computed(() => sessions.value.find((s) => s.id === currentId.value) ?? null)

  function persist() {
    localStorage.setItem(KEY, JSON.stringify(sessions.value))
  }

  function open(id: string) {
    currentId.value = id
  }

  function startFresh() {
    currentId.value = null
  }

  function remove(id: string) {
    sessions.value = sessions.value.filter((s) => s.id !== id)
    if (currentId.value === id) currentId.value = null
    persist()
  }

  function touch(session: YehSession) {
    session.updatedAt = Date.now()
    sessions.value = [session, ...sessions.value.filter((s) => s.id !== session.id)]
    persist()
  }

  function sessionTitle(userText: string, hasImage: boolean) {
    const title = userText.replace(/\s+/g, ' ').trim().slice(0, 24)
    if (title) return title
    return hasImage ? '图片' : '新会话'
  }

  function ensureSession(userText: string, hasImage = false): YehSession {
    let session = current.value
    if (!session) {
      session = { id: nid(), title: sessionTitle(userText, hasImage), messages: [], updatedAt: Date.now() }
      sessions.value = [session, ...sessions.value]
      currentId.value = session.id
    }
    return session
  }

  function appendUser(userText: string, imageUrls?: string[], notes?: YehNoteChip[]) {
    const hasImage = Boolean(imageUrls?.length)
    const session = ensureSession(userText, hasImage)
    session.messages.push({
      role: 'user',
      text: userText,
      imageUrls: hasImage ? imageUrls : undefined,
      notes: notes?.length ? notes : undefined,
    })
    if (session.messages.filter((m) => m.role === 'user').length === 1) {
      session.title = sessionTitle(userText, hasImage)
    }
    touch(session)
  }

  function appendAi(aiText: string, citations?: AiCitation[]) {
    const session = current.value
    if (!session) return
    session.messages.push({
      role: 'ai',
      text: aiText,
      citations: citations?.length ? citations : undefined,
    })
    touch(session)
  }

  function dropLastAi() {
    const session = current.value
    if (!session) return
    const last = session.messages[session.messages.length - 1]
    if (!last || last.role !== 'ai') return
    session.messages.pop()
    persist()
  }

  function beginAi(question?: string) {
    appendAi('')
    const session = current.value
    const last = session?.messages[session.messages.length - 1]
    if (!last || last.role !== 'ai') return
    last.process = {
      question: (question || '').trim(),
      noteCount: 0,
      open: false,
      openStep: '',
    }
    persist()
  }

  function patchLastAi(delta: string) {
    const session = current.value
    if (!session) return
    const last = session.messages[session.messages.length - 1]
    if (!last || last.role !== 'ai') return
    last.text += delta
    persist()
  }

  function finishLastAi(citations?: AiCitation[], fallback?: string) {
    const session = current.value
    if (!session) return
    const last = session.messages[session.messages.length - 1]
    if (!last || last.role !== 'ai') return
    if (!last.text.trim() && fallback) last.text = fallback
    last.citations = citations?.length ? citations : undefined
    last.process = {
      question: last.process?.question || '',
      noteCount: citations?.length ?? last.process?.noteCount ?? 0,
      open: false,
      openStep: '',
    }
    touch(session)
  }

  function patchProcess(index: number, patch: Partial<YehProcess>) {
    const session = current.value
    if (!session) return
    const msg = session.messages[index]
    if (!msg || msg.role !== 'ai') return
    msg.process = {
      question: msg.process?.question || '',
      noteCount: msg.process?.noteCount || 0,
      open: false,
      openStep: '',
      ...msg.process,
      ...patch,
    }
    persist()
  }

  function append(userText: string, aiText: string, citations?: AiCitation[]) {
    appendUser(userText)
    appendAi(aiText, citations)
  }

  return {
    sessions,
    currentId,
    current,
    open,
    startFresh,
    remove,
    append,
    appendUser,
    appendAi,
    beginAi,
    dropLastAi,
    patchLastAi,
    finishLastAi,
    patchProcess,
  }
})
