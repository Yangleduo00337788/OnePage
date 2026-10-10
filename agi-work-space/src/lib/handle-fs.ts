function normalizePath(filepath: string) {
  let value = filepath.replaceAll('\\', '/')
  if (value === '.' || value === './' || value === '/') return ''
  value = value.replace(/^\.\//, '').replace(/^\/+/, '')
  const parts: string[] = []
  for (const part of value.split('/')) {
    if (!part || part === '.') continue
    if (part === '..') parts.pop()
    else parts.push(part)
  }
  return parts.join('/')
}

function enoent(path: string) {
  const error = new Error(`ENOENT: no such file or directory, '${path}'`) as Error & { code: string }
  error.code = 'ENOENT'
  return error
}

type Stat = {
  mode: number
  size: number
  ino: number
  mtimeMs: number
  ctimeMs: number
  uid: number
  gid: number
  dev: number
  isFile: () => boolean
  isDirectory: () => boolean
  isSymbolicLink: () => boolean
}

function fileStat(size: number, mtimeMs: number): Stat {
  return {
    mode: 0o100644,
    size,
    ino: 0,
    mtimeMs,
    ctimeMs: mtimeMs,
    uid: 1,
    gid: 1,
    dev: 1,
    isFile: () => true,
    isDirectory: () => false,
    isSymbolicLink: () => false,
  }
}

function dirStat(): Stat {
  const now = Date.now()
  return {
    mode: 0o40755,
    size: 0,
    ino: 0,
    mtimeMs: now,
    ctimeMs: now,
    uid: 1,
    gid: 1,
    dev: 1,
    isFile: () => false,
    isDirectory: () => true,
    isSymbolicLink: () => false,
  }
}

export function createHandleFs(root: FileSystemDirectoryHandle) {
  const dirCache = new Map<string, FileSystemDirectoryHandle>()
  dirCache.set('', root)

  function dropDirCache(path: string) {
    const full = normalizePath(path)
    dirCache.delete(full)
    const prefix = full ? `${full}/` : ''
    for (const key of [...dirCache.keys()]) {
      if (prefix && key.startsWith(prefix)) dirCache.delete(key)
    }
  }

  async function dirByParts(parts: string[], create = false): Promise<FileSystemDirectoryHandle> {
    let current = root
    let key = ''
    for (const part of parts) {
      key = key ? `${key}/${part}` : part
      const cached = dirCache.get(key)
      if (cached) {
        current = cached
        continue
      }
      current = await current.getDirectoryHandle(part, { create })
      dirCache.set(key, current)
    }
    return current
  }

  async function locate(filepath: string, createDir = false) {
    const full = normalizePath(filepath)
    const parts = full ? full.split('/') : []
    const name = parts.pop() ?? ''
    const parent = await dirByParts(parts, createDir)
    return { parent, name, full }
  }

  const promises = {
    async readFile(filepath: string, options?: { encoding?: string } | string) {
      const { parent, name, full } = await locate(filepath)
      if (!name) {
        throw enoent(filepath)
      }
      try {
        const file = await (await parent.getFileHandle(name)).getFile()
        const encoding = typeof options === 'string' ? options : options?.encoding
        if (encoding === 'utf8') return await file.text()
        return new Uint8Array(await file.arrayBuffer())
      } catch {
        throw enoent(full)
      }
    },
    async writeFile(filepath: string, data: string | Uint8Array | ArrayBuffer) {
      const { parent, name } = await locate(filepath, true)
      const handle = await parent.getFileHandle(name, { create: true })
      const writable = await handle.createWritable()
      if (typeof data === 'string') {
        await writable.write(data)
      } else {
        const bytes = data instanceof ArrayBuffer ? new Uint8Array(data) : new Uint8Array(data)
        await writable.write(bytes)
      }
      await writable.close()
    },
    async unlink(filepath: string) {
      const { parent, name, full } = await locate(filepath)
      try {
        await parent.removeEntry(name)
        dropDirCache(full)
      } catch {
        throw enoent(full)
      }
    },
    async readdir(filepath: string) {
      try {
        const full = normalizePath(filepath)
        const dir = full ? await dirByParts(full.split('/')) : root
        const names: string[] = []
        for await (const name of dir.keys()) names.push(name)
        return names
      } catch {
        throw enoent(filepath)
      }
    },
    async mkdir(filepath: string) {
      const full = normalizePath(filepath)
      if (!full) return
      await dirByParts(full.split('/'), true)
    },
    async rmdir(filepath: string) {
      const { parent, name, full } = await locate(filepath)
      try {
        await parent.removeEntry(name)
        dropDirCache(full)
      } catch {
        throw enoent(full)
      }
    },
    async stat(filepath: string) {
      return promises.lstat(filepath)
    },
    async lstat(filepath: string) {
      const full = normalizePath(filepath)
      if (!full) return dirStat()
      const parts = full.split('/')
      const name = parts.pop()!
      let parent = root
      try {
        parent = await dirByParts(parts)
      } catch {
        throw enoent(filepath)
      }
      try {
        const file = await (await parent.getFileHandle(name)).getFile()
        return fileStat(file.size, file.lastModified)
      } catch {
        try {
          await parent.getDirectoryHandle(name)
          return dirStat()
        } catch {
          throw enoent(filepath)
        }
      }
    },
    async chmod() {
      return
    },
    async readlink() {
      throw new Error('EPERM: readlink is not supported')
    },
    async symlink() {
      throw new Error('EPERM: symlink is not supported')
    },
  }

  return { promises }
}
