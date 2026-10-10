/** 社区帖子详情弹窗。P0 点瀑布流打开，不进独立路由。 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { NoteCard } from '../data/home'

export type NoteOrigin = { x: number; y: number; w: number; h: number }

export const useNoteDetail = defineStore('noteDetail', () => {
  const open = ref(false)
  const note = ref<NoteCard | null>(null)
  const origin = ref<NoteOrigin | null>(null)

  function show(next: NoteCard, from?: EventTarget | null) {
    note.value = next
    if (from instanceof HTMLElement) {
      const cover = from.querySelector('img')
      const r = (cover instanceof HTMLElement ? cover : from).getBoundingClientRect()
      origin.value = { x: r.left, y: r.top, w: r.width, h: r.height }
    } else {
      origin.value = null
    }
    open.value = true
  }

  function hide() {
    open.value = false
  }

  return { open, note, origin, show, hide }
})
