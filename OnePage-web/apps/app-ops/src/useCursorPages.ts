import { ref } from 'vue'

/** 游标接口翻页：记下每一页入口 cursor，才能上一页。 */
export function useCursorPages(defaultSize = 20) {
  const page = ref(1)
  const pageSize = ref(defaultSize)
  const cursors = ref<(string | undefined)[]>([undefined])
  const hasMore = ref(false)

  function resetCursors() {
    page.value = 1
    cursors.value = [undefined]
    hasMore.value = false
  }

  function cursorFor(index: number) {
    return cursors.value[index - 1]
  }

  function applyResult(index: number, nextCursor: string | null, more: boolean) {
    page.value = index
    hasMore.value = more
    if (nextCursor) cursors.value[index] = nextCursor
  }

  return { page, pageSize, hasMore, resetCursors, cursorFor, applyResult }
}
