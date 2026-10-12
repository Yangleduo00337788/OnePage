/**
 * 耶耶会话：只落 localStorage，不接模型。页面发送时说明尚未接入。
 * 历史、当前会话、新建 / 删除都走这一份状态。
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export type YehRole = 'user' | 'ai'
export type YehMsg = { role: YehRole; text: string }
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

  function append(userText: string, aiText: string) {
    const title = userText.replace(/\s+/g, ' ').trim().slice(0, 24)
    let session = current.value
    if (!session) {
      session = { id: nid(), title, messages: [], updatedAt: Date.now() }
      sessions.value = [session, ...sessions.value]
      currentId.value = session.id
    }
    session.messages.push({ role: 'user', text: userText }, { role: 'ai', text: aiText })
    if (session.messages.filter((m) => m.role === 'user').length === 1) session.title = title
    session.updatedAt = Date.now()
    sessions.value = [session, ...sessions.value.filter((s) => s.id !== session!.id)]
    persist()
  }

  return { sessions, currentId, current, open, startFresh, remove, append }
})
