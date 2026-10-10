import { reactive } from 'vue'
import { openDb, STORE_OVERRIDES } from '@/lib/idb'

export const docOverrides = reactive(new Map<string, string>())

export async function hydrateDocOverrides(): Promise<void> {
  if (typeof indexedDB === 'undefined') return
  const db = await openDb()
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_OVERRIDES, 'readonly')
    const req = tx.objectStore(STORE_OVERRIDES).openCursor()
    req.onsuccess = () => {
      const cursor = req.result
      if (!cursor) return
      docOverrides.set(String(cursor.key), String(cursor.value))
      cursor.continue()
    }
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

export async function saveDocOverride(slug: string, content: string): Promise<void> {
  docOverrides.set(slug, content)
  const db = await openDb()
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_OVERRIDES, 'readwrite')
    tx.objectStore(STORE_OVERRIDES).put(content, slug)
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

export async function removeDocOverride(slug: string): Promise<void> {
  docOverrides.delete(slug)
  const db = await openDb()
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_OVERRIDES, 'readwrite')
    tx.objectStore(STORE_OVERRIDES).delete(slug)
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

export async function moveDocOverride(fromSlug: string, toSlug: string): Promise<void> {
  if (fromSlug === toSlug) return
  const body = docOverrides.get(fromSlug)
  if (body === undefined) return
  await saveDocOverride(toSlug, body)
  await removeDocOverride(fromSlug)
}
