/**
 * 耶耶会话：对话落 localStorage；回复走 POST /ai/chat。
 * 历史、当前会话、新建 / 删除都走这一份状态。
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { AiCitation } from '@onepage/api-client'

export type YehRole = 'user' | 'ai'
export type YehMsg = { role: YehRole; text: string; citations?: AiCitation[] }
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

  function ensureSession(userText: string): YehSession {
    const title = userText.replace(/\s+/g, ' ').trim().slice(0, 24)
    let session = current.value
    if (!session) {
      session = { id: nid(), title, messages: [], updatedAt: Date.now() }
      sessions.value = [session, ...sessions.value]
      currentId.value = session.id
    }
    return session
  }

  function appendUser(userText: string) {
    const session = ensureSession(userText)
    const title = userText.replace(/\s+/g, ' ').trim().slice(0, 24)
    session.messages.push({ role: 'user', text: userText })
    if (session.messages.filter((m) => m.role === 'user').length === 1) session.title = title
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

  function append(userText: string, aiText: string, citations?: AiCitation[]) {
    appendUser(userText)
    appendAi(aiText, citations)
  }

  return { sessions, currentId, current, open, startFresh, remove, append, appendUser, appendAi }
})
