import { joinPath } from '@/lib/markdown'
import { readWorkspaceBlob } from '@/lib/workspace-fs'

const urlCache = new Map<string, string>()

export function contentPathFromSrc(src: string, docDir: string): string | null {
  if (!src || /^(https?:|data:|blob:|mailto:)/i.test(src)) return null
  const path = src.startsWith('/') ? src.replace(/^\/+/, '') : joinPath(docDir, src)
  return path.replace(/^src\/content\//, '')
}

export function relativeFromDoc(docDir: string, assetRel: string): string {
  const from = docDir ? docDir.split('/').filter(Boolean) : []
  const toParts = assetRel.replaceAll('\\', '/').split('/').filter(Boolean)
  const file = toParts.pop() ?? ''
  let i = 0
  while (i < from.length && i < toParts.length && from[i] === toParts[i]) i += 1
  const up = from.slice(i).map(() => '..')
  const down = toParts.slice(i)
  const joined = [...up, ...down, file].join('/')
  if (!joined) return './'
  return joined.startsWith('.') ? joined : `./${joined}`
}

export async function objectUrlForContentPath(relPath: string): Promise<string | null> {
  const key = relPath.replaceAll('\\', '/')
  const cached = urlCache.get(key)
  if (cached) return cached
  const blob = await readWorkspaceBlob(key)
  if (!blob) return null
  const url = URL.createObjectURL(blob)
  urlCache.set(key, url)
  return url
}

export async function hydrateContentImages(root: ParentNode, docDir: string): Promise<void> {
  const images = [...root.querySelectorAll('img')]
  await Promise.all(
    images.map(async (img) => {
      const marked = img.getAttribute('data-content-path')
      const src = marked || img.getAttribute('src') || ''
      const rel = marked || contentPathFromSrc(src, docDir)
      if (!rel) return
      img.setAttribute('data-content-path', rel)
      img.setAttribute('data-rel', relativeFromDoc(docDir, rel))
      const url = await objectUrlForContentPath(rel)
      if (url) img.setAttribute('src', url)
    }),
  )
}

export function extensionForImage(file: File): string {
  const fromName = file.name.split('.').pop()?.toLowerCase()
  if (fromName && ['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg'].includes(fromName)) {
    return fromName === 'jpeg' ? 'jpg' : fromName
  }
  if (file.type === 'image/jpeg') return 'jpg'
  if (file.type === 'image/gif') return 'gif'
  if (file.type === 'image/webp') return 'webp'
  if (file.type === 'image/svg+xml') return 'svg'
  return 'png'
}
