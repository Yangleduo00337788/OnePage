const DB_NAME = 'agi-kb'
const DB_VERSION = 4

export const STORE_OVERRIDES = 'md-overrides'
export const STORE_HANDLES = 'handles'
export const STORE_REMOTE = 'remote-git'
export const STORE_DRAFTS = 'md-drafts'

const REQUIRED_STORES = [STORE_OVERRIDES, STORE_HANDLES, STORE_REMOTE, STORE_DRAFTS] as const

function ensureStores(db: IDBDatabase) {
  for (const name of REQUIRED_STORES) {
    if (!db.objectStoreNames.contains(name)) db.createObjectStore(name)
  }
}

function openOnce(name: string, version: number): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(name, version)
    req.onupgradeneeded = () => ensureStores(req.result)
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

function deleteDb(name: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.deleteDatabase(name)
    req.onsuccess = () => resolve()
    req.onblocked = () => resolve()
    req.onerror = () => reject(req.error)
  })
}

function missingStores(db: IDBDatabase) {
  return REQUIRED_STORES.some((name) => !db.objectStoreNames.contains(name))
}

export async function openDb(): Promise<IDBDatabase> {
  try {
    const db = await openOnce(DB_NAME, DB_VERSION)
    if (!missingStores(db)) return db
    db.close()
    await deleteDb(DB_NAME)
    return openOnce(DB_NAME, DB_VERSION)
  } catch (error) {
    const versionError = error instanceof DOMException && error.name === 'VersionError'
    if (!versionError) throw error
    await deleteDb(DB_NAME)
    return openOnce(DB_NAME, DB_VERSION)
  }
}
