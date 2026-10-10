import { onUnmounted, ref } from 'vue'

const WIDTH_KEY = 'agi-sidebar-width'
const COLLAPSED_KEY = 'agi-sidebar-collapsed'
const MIN_WIDTH = 200
const MAX_WIDTH = 420
const DEFAULT_WIDTH = 256

function readWidth(): number {
  if (typeof localStorage === 'undefined') return DEFAULT_WIDTH
  const raw = Number(localStorage.getItem(WIDTH_KEY))
  if (!Number.isFinite(raw)) return DEFAULT_WIDTH
  return Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, raw))
}

function readCollapsed(): boolean {
  if (typeof localStorage === 'undefined') return false
  return localStorage.getItem(COLLAPSED_KEY) === '1'
}

export function useResizableSidebar() {
  const width = ref(readWidth())
  const collapsed = ref(readCollapsed())
  const resizing = ref(false)

  function persistWidth() {
    localStorage.setItem(WIDTH_KEY, String(width.value))
  }

  function persistCollapsed() {
    localStorage.setItem(COLLAPSED_KEY, collapsed.value ? '1' : '0')
  }

  function toggle() {
    collapsed.value = !collapsed.value
    persistCollapsed()
  }

  function startResize(event: MouseEvent) {
    if (collapsed.value) return
    event.preventDefault()
    resizing.value = true
    const startX = event.clientX
    const startWidth = width.value

    const onMove = (move: MouseEvent) => {
      const next = startWidth + (move.clientX - startX)
      width.value = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, next))
    }

    const onUp = () => {
      resizing.value = false
      persistWidth()
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
    }

    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
  }

  onUnmounted(() => {
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  })

  return { width, collapsed, resizing, toggle, startResize }
}
