/**
 * 「更多」弹窗位置。toggleFrom 用按钮的 getBoundingClientRect，让面板贴在按钮上方，而不是写死坐标。
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useMoreMenu = defineStore('moreMenu', () => {
  const open = ref(false)
  const left = ref(32)
  const bottom = ref(96)

  function hide() {
    open.value = false
  }

  function toggleFrom(el: HTMLElement | null) {
    if (open.value) {
      open.value = false
      return
    }
    if (el) {
      const r = el.getBoundingClientRect()
      left.value = Math.round(r.left)
      bottom.value = Math.round(window.innerHeight - r.top + 8)
    }
    open.value = true
  }

  return { open, left, bottom, hide, toggleFrom }
})
