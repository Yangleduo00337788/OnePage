import { reactive } from 'vue'
import { openDb, STORE_DRAFTS } from '@/lib/idb'

export const docDrafts = reactive(new Map<string, string>())

export async function hydrateDocDrafts(): Promise<void> {
  if (typeof indexedDB === 'undefined') return
  const db = await openDb()
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_DRAFTS, 'readonly')
    const req = tx.objectStore(STORE_DRAFTS).openCursor()
    req.onsuccess = () => {
      const cursor = req.result
      if (!cursor) return
      docDrafts.set(String(cursor.key), String(cursor.value))
      cursor.continue()
    }
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

export async function saveDocDraft(slug: string, content: string): Promise<void> {
  docDrafts.set(slug, content)
  const db = await openDb()
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_DRAFTS, 'readwrite')
    tx.objectStore(STORE_DRAFTS).put(content, slug)
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

export async function removeDocDraft(slug: string): Promise<void> {
  docDrafts.delete(slug)
  const db = await openDb()
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_DRAFTS, 'readwrite')
    tx.objectStore(STORE_DRAFTS).delete(slug)
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

export async function moveDocDraft(fromSlug: string, toSlug: string): Promise<void> {
  if (fromSlug === toSlug) return
  const body = docDrafts.get(fromSlug)
  if (body === undefined) return
  await saveDocDraft(toSlug, body)
  await removeDocDraft(fromSlug)
}
