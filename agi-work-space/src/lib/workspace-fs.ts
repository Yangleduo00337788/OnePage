import { computed, ref } from 'vue'
import { openDb, STORE_HANDLES } from '@/lib/idb'
import type { DocEntry } from '@/lib/content'

const HANDLE_KEY = 'workspace-root'

export type FsStatus = 'unsupported' | 'disconnected' | 'need-permission' | 'ready'

const status = ref<FsStatus>('disconnected')
const folderName = ref('')
const fsError = ref('')

function isPickerAvailable(): boolean {
  return typeof window !== 'undefined' && typeof window.showDirectoryPicker === 'function'
}

async function queryWrite(handle: FileSystemDirectoryHandle): Promise<PermissionState> {
  if (typeof handle.queryPermission !== 'function') return 'granted'
  return handle.queryPermission({ mode: 'readwrite' })
}

async function requestWrite(handle: FileSystemDirectoryHandle): Promise<PermissionState> {
  if (typeof handle.requestPermission !== 'function') return 'granted'
  return handle.requestPermission({ mode: 'readwrite' })
}

async function loadHandle(): Promise<FileSystemDirectoryHandle | null> {
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_HANDLES, 'readonly')
    const req = tx.objectStore(STORE_HANDLES).get(HANDLE_KEY)
    req.onsuccess = () => resolve((req.result as FileSystemDirectoryHandle | undefined) ?? null)
    req.onerror = () => reject(req.error)
  })
}

async function persistHandle(handle: FileSystemDirectoryHandle | null): Promise<void> {
  const db = await openDb()
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_HANDLES, 'readwrite')
    const store = tx.objectStore(STORE_HANDLES)
    if (handle) store.put(handle, HANDLE_KEY)
    else store.delete(HANDLE_KEY)
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

async function getChildDir(
  parent: FileSystemDirectoryHandle,
  name: string,
): Promise<FileSystemDirectoryHandle | null> {
  try {
    return await parent.getDirectoryHandle(name)
  } catch {
    return null
  }
}

async function resolveContentDir(root: FileSystemDirectoryHandle): Promise<FileSystemDirectoryHandle> {
  const src = await getChildDir(root, 'src')
  const nested = src ? await getChildDir(src, 'content') : null
  if (nested) return nested
  const direct = await getChildDir(root, 'content')
  if (direct) return direct
  throw new Error('FOLDER_INVALID')
}

let contentDir: FileSystemDirectoryHandle | null = null
let rootHandle: FileSystemDirectoryHandle | null = null
const readyListeners = new Set<() => void>()

export function onWorkspaceReady(listener: () => void) {
  readyListeners.add(listener)
  return () => readyListeners.delete(listener)
}

function notifyReady() {
  for (const listener of readyListeners) listener()
}

async function applyHandle(handle: FileSystemDirectoryHandle, request = false): Promise<void> {
  const perm = request ? await requestWrite(handle) : await queryWrite(handle)
  if (perm !== 'granted') {
    rootHandle = handle
    contentDir = null
    folderName.value = handle.name
    status.value = 'need-permission'
    return
  }
  contentDir = await resolveContentDir(handle)
  rootHandle = handle
  folderName.value = handle.name
  status.value = 'ready'
  fsError.value = ''
  notifyReady()
}

export async function hydrateWorkspaceFs(): Promise<void> {
  if (!isPickerAvailable()) {
    status.value = 'unsupported'
    return
  }
  try {
    const handle = await loadHandle()
    if (!handle) {
      status.value = 'disconnected'
      folderName.value = ''
      return
    }
    await applyHandle(handle, false)
  } catch (error) {
    console.warn(error)
    status.value = 'disconnected'
    folderName.value = ''
  }
}

export async function authorizeWorkspace(): Promise<void> {
  if (!isPickerAvailable()) {
    status.value = 'unsupported'
    throw new Error('UNSUPPORTED')
  }
  fsError.value = ''
  const picker = window.showDirectoryPicker
  if (!picker) {
    status.value = 'unsupported'
    throw new Error('UNSUPPORTED')
  }
  const handle = await picker({
    id: 'agi-workspace',
    mode: 'readwrite',
  })
  await persistHandle(handle)
  try {
    await applyHandle(handle, true)
  } catch (error) {
    fsError.value = error instanceof Error && error.message === 'FOLDER_INVALID' ? 'invalid' : 'unknown'
    status.value = 'disconnected'
    throw error
  }
}

export async function reconnectWorkspace(): Promise<void> {
  if (!rootHandle) {
    await authorizeWorkspace()
    return
  }
  await applyHandle(rootHandle, true)
  if (status.value !== 'ready') throw new Error('PERMISSION_DENIED')
}

export async function disconnectWorkspace(): Promise<void> {
  await persistHandle(null)
  rootHandle = null
  contentDir = null
  folderName.value = ''
  status.value = isPickerAvailable() ? 'disconnected' : 'unsupported'
}

async function fileHandle(relPath: string, create: boolean): Promise<FileSystemFileHandle> {
  if (!contentDir || status.value !== 'ready') throw new Error('NOT_READY')
  const parts = relPath.split('/').filter(Boolean)
  const fileName = parts.pop()
  if (!fileName) throw new Error('BAD_PATH')
  let dir = contentDir
  for (const part of parts) {
    dir = await dir.getDirectoryHandle(part, { create })
  }
  return dir.getFileHandle(fileName, { create })
}

export async function writeWorkspaceFile(relPath: string, text: string): Promise<void> {
  const file = await fileHandle(relPath, true)
  const writable = await file.createWritable()
  await writable.write(text)
  await writable.close()
}

export async function writeWorkspaceBytes(relPath: string, data: BufferSource): Promise<void> {
  const file = await fileHandle(relPath, true)
  const writable = await file.createWritable()
  await writable.write(data)
  await writable.close()
}

export async function readWorkspaceBlob(relPath: string): Promise<Blob | null> {
  try {
    const file = await fileHandle(relPath, false)
    return file.getFile()
  } catch {
    return null
  }
}

export async function workspaceFileExists(relPath: string): Promise<boolean> {
  try {
    await fileHandle(relPath, false)
    return true
  } catch {
    return false
  }
}

export async function deleteWorkspaceFile(relPath: string): Promise<void> {
  if (!contentDir || status.value !== 'ready') throw new Error('NOT_READY')
  const parts = relPath.split('/').filter(Boolean)
  const fileName = parts.pop()
  if (!fileName) throw new Error('BAD_PATH')
  let dir = contentDir
  for (const part of parts) {
    dir = await dir.getDirectoryHandle(part)
  }
  await dir.removeEntry(fileName)
}

async function contentDirHandle(relDir: string, create = false): Promise<FileSystemDirectoryHandle> {
  if (!contentDir || status.value !== 'ready') throw new Error('NOT_READY')
  let dir = contentDir
  for (const part of relDir.split('/').filter(Boolean)) {
    dir = await dir.getDirectoryHandle(part, { create })
  }
  return dir
}

async function copyDirectory(
  source: FileSystemDirectoryHandle,
  destParent: FileSystemDirectoryHandle,
  destName: string,
) {
  const dest = await destParent.getDirectoryHandle(destName, { create: true })
  for await (const [name, handle] of source.entries()) {
    if (handle.kind === 'file') {
      const file = await (handle as FileSystemFileHandle).getFile()
      const out = await dest.getFileHandle(name, { create: true })
      const writable = await out.createWritable()
      await writable.write(await file.arrayBuffer())
      await writable.close()
    } else {
      await copyDirectory(handle as FileSystemDirectoryHandle, dest, name)
    }
  }
}

export async function moveWorkspaceDir(fromDir: string, toDir: string): Promise<void> {
  if (!contentDir || status.value !== 'ready') throw new Error('NOT_READY')
  const from = fromDir.replaceAll('\\', '/').replace(/^\/+|\/+$/g, '')
  const to = toDir.replaceAll('\\', '/').replace(/^\/+|\/+$/g, '')
  if (!from || !to || from === to) throw new Error('BAD_PATH')
  if (to.startsWith(`${from}/`)) throw new Error('NESTED')
  if (await workspaceFileExists(`${to}/index.md`)) throw new Error('EXISTS')
  const fromParts = from.split('/')
  const fromName = fromParts.pop()!
  const fromParent = fromParts.length ? await contentDirHandle(fromParts.join('/')) : contentDir
  const source = await fromParent.getDirectoryHandle(fromName)
  const toParts = to.split('/')
  const toName = toParts.pop()!
  const toParent = toParts.length ? await contentDirHandle(toParts.join('/'), true) : contentDir
  await copyDirectory(source, toParent, toName)
  await fromParent.removeEntry(fromName, { recursive: true })
}

export async function moveWorkspaceFile(fromRel: string, toRel: string): Promise<void> {
  if (fromRel === toRel) return
  if (await workspaceFileExists(toRel)) throw new Error('EXISTS')
  const text = await readWorkspaceFile(fromRel)
  if (text === null) throw new Error('MISSING')
  await writeWorkspaceFile(toRel, text)
  await deleteWorkspaceFile(fromRel)
}

export async function listContentMarkdown(): Promise<{ relPath: string; raw: string; updatedAt?: number }[]> {
  if (!contentDir || status.value !== 'ready') return []
  const out: { relPath: string; raw: string; updatedAt?: number }[] = []

  async function walk(dir: FileSystemDirectoryHandle, prefix: string) {
    for await (const [name, handle] of dir.entries()) {
      if (name.startsWith('.')) continue
      const rel = prefix ? `${prefix}/${name}` : name
      if (handle.kind === 'directory') {
        await walk(handle as FileSystemDirectoryHandle, rel)
        continue
      }
      if (!name.toLowerCase().endsWith('.md')) continue
      const file = await (handle as FileSystemFileHandle).getFile()
      out.push({
        relPath: rel.replaceAll('\\', '/'),
        raw: await file.text(),
        updatedAt: file.lastModified,
      })
    }
  }

  await walk(contentDir, '')
  return out
}

export async function readWorkspaceFile(relPath: string): Promise<string | null> {
  try {
    const file = await fileHandle(relPath, false)
    const blob = await file.getFile()
    return blob.text()
  } catch {
    return null
  }
}

function yamlScalar(value: string): string {
  if (value === '') return '""'
  if (/[:#{}[\],&*?!|>'"%@`]|^\s|\s$/.test(value)) return JSON.stringify(value)
  return value
}

export function serializeMarkdown(doc: DocEntry, body: string): string {
  const updated = new Date().toISOString()
  return `---\ntitle: ${yamlScalar(doc.title)}\ndescription: ${yamlScalar(doc.description)}\norder: ${doc.order}\nupdated: ${updated}\n---\n\n${body.replace(/^\n+/, '')}\n`
}

function assertSafeRelPath(relPath: string) {
  const parts = relPath.replaceAll('\\', '/').split('/').filter(Boolean)
  if (!parts.length || parts.some((part) => part === '.' || part === '..')) {
    throw new Error('BAD_PATH')
  }
  return parts
}

export async function writeRepoFile(relPath: string, text: string): Promise<void> {
  await writeRepoBytes(relPath, new TextEncoder().encode(text))
}

export async function writeRepoBytes(relPath: string, data: BufferSource): Promise<void> {
  if (!rootHandle || status.value !== 'ready') throw new Error('NOT_READY')
  const parts = assertSafeRelPath(relPath)
  const fileName = parts.pop()!
  let dir = rootHandle
  for (const part of parts) {
    dir = await dir.getDirectoryHandle(part, { create: true })
  }
  const file = await dir.getFileHandle(fileName, { create: true })
  const writable = await file.createWritable()
  await writable.write(data)
  await writable.close()
}

export async function readRepoFile(relPath: string): Promise<string | null> {
  if (!rootHandle || status.value !== 'ready') return null
  try {
    const parts = assertSafeRelPath(relPath)
    const fileName = parts.pop()!
    let dir = rootHandle
    for (const part of parts) {
      dir = await dir.getDirectoryHandle(part)
    }
    const blob = await (await dir.getFileHandle(fileName)).getFile()
    return blob.text()
  } catch {
    return null
  }
}

function assertSafeFileName(name: string) {
  if (!name || name.includes('/') || name.includes('\\') || name === '.' || name === '..') {
    throw new Error('BAD_PATH')
  }
}

export async function writeProjectFile(name: string, text: string): Promise<void> {
  if (!rootHandle || status.value !== 'ready') throw new Error('NOT_READY')
  assertSafeFileName(name)
  const file = await rootHandle.getFileHandle(name, { create: true })
  const writable = await file.createWritable()
  await writable.write(text)
  await writable.close()
}

export async function readProjectFile(name: string): Promise<string | null> {
  if (!rootHandle || status.value !== 'ready') return null
  assertSafeFileName(name)
  try {
    const blob = await (await rootHandle.getFileHandle(name)).getFile()
    return blob.text()
  } catch {
    return null
  }
}

export async function removeProjectFile(name: string): Promise<void> {
  if (!rootHandle || status.value !== 'ready') return
  assertSafeFileName(name)
  try {
    await rootHandle.removeEntry(name)
  } catch {
    /* missing is fine */
  }
}

export function getWorkspaceRoot(): FileSystemDirectoryHandle | null {
  return status.value === 'ready' ? rootHandle : null
}

const SKIP_REPO_DIRS = new Set([
  'node_modules',
  'dist',
  '.git',
  '.cursor',
  '.vscode',
  '.idea',
  'terminals',
])

const TEXT_FILE_EXT = new Set([
  '.md',
  '.ts',
  '.tsx',
  '.vue',
  '.json',
  '.html',
  '.txt',
  '.css',
  '.svg',
  '.yml',
  '.yaml',
  '.js',
  '.mjs',
  '.cjs',
  '.mdx',
])

const SKIP_REPO_FILES = new Set([
  'package-lock.json',
  '.agi-workspace.local.json',
  'pnpm-lock.yaml',
  'yarn.lock',
  'workspace-roles.json',
])

export async function rewriteRepoTextFiles(
  mutate: (relPath: string, text: string) => string,
): Promise<number> {
  if (!rootHandle || status.value !== 'ready') throw new Error('NOT_READY')
  let changed = 0

  async function walk(dir: FileSystemDirectoryHandle, prefix: string) {
    for await (const [name, handle] of dir.entries()) {
      if (handle.kind === 'directory') {
        if (name.startsWith('.') || SKIP_REPO_DIRS.has(name)) continue
        await walk(handle as FileSystemDirectoryHandle, prefix ? `${prefix}/${name}` : name)
        continue
      }
      if (SKIP_REPO_FILES.has(name) || name.startsWith('.')) continue
      const dot = name.lastIndexOf('.')
      const ext = dot >= 0 ? name.slice(dot).toLowerCase() : ''
      if (!TEXT_FILE_EXT.has(ext)) continue
      const relPath = prefix ? `${prefix}/${name}` : name
      const file = await (handle as FileSystemFileHandle).getFile()
      if (file.size > 1_500_000) continue
      const text = await file.text()
      const next = mutate(relPath, text)
      if (next === text) continue
      const writable = await (handle as FileSystemFileHandle).createWritable()
      await writable.write(next)
      await writable.close()
      changed += 1
    }
  }

  await walk(rootHandle, '')
  return changed
}

export function useWorkspaceFs() {
  return {
    status: computed(() => status.value),
    folderName: computed(() => folderName.value),
    error: computed(() => fsError.value),
    ready: computed(() => status.value === 'ready'),
  }
}
