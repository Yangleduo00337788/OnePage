import { bundledContentFiles, replaceCatalog } from '@/lib/content'
import { reindexAll } from '@/lib/search'
import { listContentMarkdown, useWorkspaceFs } from '@/lib/workspace-fs'

export async function syncCatalogFromDisk(): Promise<boolean> {
  const files = await listContentMarkdown()
  if (!files.length) return false
  replaceCatalog(files)
  reindexAll()
  return true
}

export function restoreBundledCatalog() {
  replaceCatalog(bundledContentFiles())
  reindexAll()
}

export function bindCatalogFocusSync() {
  if (typeof window === 'undefined') return () => {}
  const { ready } = useWorkspaceFs()
  const onFocus = () => {
    if (ready.value) void syncCatalogFromDisk()
  }
  window.addEventListener('focus', onFocus)
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') onFocus()
  })
  return () => {
    window.removeEventListener('focus', onFocus)
  }
}
