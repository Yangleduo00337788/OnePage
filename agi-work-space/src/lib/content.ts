import { reactive } from 'vue'
import { docOverrides } from '@/lib/doc-store'
import { workspaceName } from '@/lib/branding'

export interface DocEntry {
  slug: string
  title: string
  description: string
  order: number
  content: string
  segments: string[]
  isIndex: boolean
  relPath: string
  /** 磁盘文件 mtime；未授权目录、构建打包文件可能为空 */
  updatedAt?: number
}

export interface NavNode {
  id: string
  title: string
  slug?: string
  order: number
  kind: 'folder' | 'doc'
  children?: NavNode[]
}

interface Frontmatter {
  title?: string
  description?: string
  order?: number
  updated?: string
}

export interface ContentFile {
  relPath: string
  raw: string
  updatedAt?: number
}

const bundledFiles = import.meta.glob('../content/**/*.md', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>

export const catalog = reactive({
  revision: 0,
  docs: [] as DocEntry[],
  navTree: [] as NavNode[],
  docsNavTree: [] as NavNode[],
  orderedDocs: [] as DocEntry[],
})

export const docsBySlug = new Map<string, DocEntry>()

export function parseFrontmatter(raw: string): { data: Frontmatter; content: string } {
  if (!raw.startsWith('---')) {
    return { data: {}, content: raw }
  }
  const end = raw.indexOf('\n---', 3)
  if (end === -1) {
    return { data: {}, content: raw }
  }
  const fm = raw.slice(3, end).trim()
  const content = raw.slice(end + 4).replace(/^\r?\n/, '')
  const data: Frontmatter = {}
  for (const line of fm.split('\n')) {
    const i = line.indexOf(':')
    if (i === -1) continue
    const key = line.slice(0, i).trim()
    let value = line.slice(i + 1).trim()
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1)
    }
    if (key === 'order' && /^\d+$/.test(value)) {
      data.order = Number(value)
    } else if (key === 'title') {
      data.title = value
    } else if (key === 'description') {
      data.description = value
    } else if (key === 'updated') {
      data.updated = value
    }
  }
  return { data, content }
}

export function contentRelPath(filePath: string): string {
  const normalized = filePath.replaceAll('\\', '/')
  const marker = '/content/'
  const idx = normalized.lastIndexOf(marker)
  return idx >= 0 ? normalized.slice(idx + marker.length) : normalized.split('/').slice(-1)[0]!
}

export function toSlug(relPath: string): string {
  let rel = relPath.replaceAll('\\', '/').replace(/^\.?\/+/, '')
  rel = rel.replace(/\.md$/, '')
  if (rel.endsWith('/index')) {
    rel = rel.slice(0, -'/index'.length)
  }
  if (rel === 'index') return ''
  return rel
}

function isIndexRel(relPath: string): boolean {
  const normalized = relPath.replaceAll('\\', '/')
  return normalized === 'index.md' || normalized.endsWith('/index.md')
}

export function titleFromSegment(segment: string): string {
  return segment
    .split(/[-_]/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function compareNodes(a: NavNode, b: NavNode): number {
  if (a.order !== b.order) return a.order - b.order
  return a.title.localeCompare(b.title, 'zh')
}

function sortTree(nodes: NavNode[]): NavNode[] {
  const sorted = [...nodes].sort(compareNodes)
  for (const node of sorted) {
    if (node.children) node.children = sortTree(node.children)
  }
  return sorted
}

function flattenNav(nodes: NavNode[]): DocEntry[] {
  const list: DocEntry[] = []
  const visit = (items: NavNode[]) => {
    for (const item of items) {
      if (item.slug !== undefined) {
        const found = docsBySlug.get(item.slug)
        if (found && isBrowsableDoc(found)) list.push(found)
      }
      if (item.children) visit(item.children)
    }
  }
  visit(nodes)
  return list
}

export function docFromFile(relPath: string, raw: string, updatedAt?: number): DocEntry {
  const { data, content } = parseFrontmatter(raw)
  const slug = toSlug(relPath)
  const segments = slug ? slug.split('/') : []
  const fallback = segments.length ? titleFromSegment(segments[segments.length - 1]!) : workspaceName()
  const fromFrontmatter = data.updated ? Date.parse(data.updated) : Number.NaN
  const stamp = updatedAt ?? (Number.isFinite(fromFrontmatter) ? fromFrontmatter : undefined)
  return {
    slug,
    title: data.title ?? fallback,
    description: data.description ?? '',
    order: data.order ?? 100,
    content,
    segments,
    isIndex: isIndexRel(relPath) || slug === '',
    relPath: relPath.replaceAll('\\', '/'),
    updatedAt: stamp,
  }
}

function hasDescendantDocs(slug: string, docs: DocEntry[]): boolean {
  if (!slug) return docs.some((item) => item.slug !== '')
  const prefix = `${slug}/`
  return docs.some((item) => item.slug.startsWith(prefix))
}

/** 分组目录的 index.md 不是一篇可打开文档；叶子（含空目录收成的文档）才进阅读顺序 / 搜索。 */
export function isBrowsableDoc(doc: DocEntry): boolean {
  if (!doc.isIndex || doc.slug === '') return true
  return !hasDescendantDocs(doc.slug, catalog.docs)
}

function collapseEmptyNavFolders(nodes: NavNode[]): NavNode[] {
  return nodes.map((node) => {
    const children = node.children?.length ? collapseEmptyNavFolders(node.children) : undefined
    if (node.kind === 'folder' && (!children || !children.length) && node.slug !== undefined) {
      return { ...node, kind: 'doc' as const, children: undefined }
    }
    return children ? { ...node, children } : { ...node, children: undefined }
  })
}

function preferLeafOverIndex(docs: DocEntry[]): DocEntry[] {
  const bySlug = new Map<string, DocEntry>()
  for (const doc of docs) {
    const prev = bySlug.get(doc.slug)
    if (!prev) {
      bySlug.set(doc.slug, doc)
      continue
    }
    if (prev.isIndex && !doc.isIndex) bySlug.set(doc.slug, doc)
    else if (!prev.isIndex && doc.isIndex) continue
    else bySlug.set(doc.slug, doc)
  }
  return [...bySlug.values()]
}

function buildNav(docs: DocEntry[]): NavNode[] {
  const root: NavNode[] = []
  const folders = new Map<string, NavNode>()
  const indexSlugs = new Set(docs.filter((item) => item.isIndex && item.slug).map((item) => item.slug))

  const ensureFolder = (segments: string[]): NavNode => {
    let parentChildren = root
    let path = ''
    let node: NavNode | undefined
    for (const segment of segments) {
      path = path ? `${path}/${segment}` : segment
      node = folders.get(path)
      if (!node) {
        node = {
          id: path,
          title: titleFromSegment(segment),
          order: 100,
          kind: 'folder',
          children: [],
        }
        folders.set(path, node)
        parentChildren.push(node)
      }
      parentChildren = node.children!
    }
    return node!
  }

  for (const doc of docs) {
    if (!doc.segments.length) {
      const start = ensureFolder(['start'])
      start.children = start.children ?? []
      start.children.push({
        id: 'home',
        title: doc.title,
        slug: doc.slug,
        order: doc.order,
        kind: 'doc',
      })
      continue
    }

    if (doc.isIndex) {
      const folder = ensureFolder(doc.segments)
      folder.title = doc.title
      folder.slug = doc.slug
      folder.order = doc.order
      continue
    }

    if (indexSlugs.has(doc.slug)) continue

    const folderSegs = doc.segments.slice(0, -1)
    const leaf: NavNode = {
      id: doc.slug,
      title: doc.title,
      slug: doc.slug,
      order: doc.order,
      kind: 'doc',
    }
    if (folderSegs.length) {
      const parent = ensureFolder(folderSegs)
      parent.children = parent.children ?? []
      parent.children.push(leaf)
      parent.order = Math.min(parent.order, doc.order)
    } else {
      root.push(leaf)
    }
  }

  return collapseEmptyNavFolders(sortTree(root))
}

export function bundledContentFiles(): ContentFile[] {
  return Object.entries(bundledFiles).map(([path, raw]) => ({
    relPath: contentRelPath(path),
    raw,
  }))
}

export function replaceCatalog(files: ContentFile[]) {
  const docs = preferLeafOverIndex(
    files
      .filter((file) => file.relPath.replaceAll('\\', '/').toLowerCase().endsWith('.md'))
      .map((file) => docFromFile(file.relPath, file.raw, file.updatedAt)),
  ).sort((a, b) => a.order - b.order || a.slug.localeCompare(b.slug))

  docsBySlug.clear()
  for (const doc of docs) docsBySlug.set(doc.slug, doc)

  const navTree = buildNav(docs)
  catalog.docs = docs
  catalog.navTree = navTree
  catalog.docsNavTree = navTree.filter((node) => node.id !== 'space')
  catalog.orderedDocs = flattenNav(catalog.docsNavTree)
  catalog.revision += 1
}

replaceCatalog(bundledContentFiles())

export function slugFromPath(path: string): string {
  return path.replace(/^\/+|\/+$/g, '')
}

export function getDoc(slug: string): DocEntry | undefined {
  const base = docsBySlug.get(slug)
  if (!base) return undefined
  const overlay = docOverrides.get(slug)
  if (overlay === undefined) return base
  return { ...base, content: overlay }
}

export function docDir(doc: DocEntry): string {
  if (!doc.slug) return ''
  if (doc.isIndex) return doc.slug
  const i = doc.slug.lastIndexOf('/')
  return i === -1 ? '' : doc.slug.slice(0, i)
}

export function parentSlugOf(doc: DocEntry): string {
  return docDir(doc)
}

export function nextOrderInFolder(parentSlug: string): number {
  const prefix = parentSlug ? `${parentSlug}/` : ''
  let max = 0
  for (const doc of catalog.docs) {
    if (parentSlug) {
      if (doc.slug === parentSlug) continue
      if (!doc.slug.startsWith(prefix)) continue
      const rest = doc.slug.slice(prefix.length)
      if (rest.includes('/')) continue
    } else if (doc.segments.length !== 1) {
      continue
    }
    max = Math.max(max, doc.order)
  }
  return max + 10
}

export function fileStemFromTitle(title: string): string {
  const ascii = title
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return ascii || `doc-${Date.now().toString(36)}`
}

export function childRelPath(parentSlug: string, stem: string, asFolder: boolean): string {
  const parts = stem.replace(/\.md$/i, '').replaceAll('\\', '/').split('/').filter(Boolean)
  if (!parts.length || parts.some((part) => part === '.' || part === '..')) throw new Error('BAD_PATH')
  if (!parts.every((part) => /^[a-zA-Z0-9][a-zA-Z0-9_-]*$/.test(part))) throw new Error('BAD_NAME')
  const nested = parts.join('/')
  const file = asFolder ? `${nested}/index.md` : `${nested}.md`
  return parentSlug ? `${parentSlug}/${file}` : file
}

export function getNeighbors(slug: string): { prev?: DocEntry; next?: DocEntry } {
  const index = catalog.orderedDocs.findIndex((doc) => doc.slug === slug)
  if (index === -1) return {}
  return {
    prev: catalog.orderedDocs[index - 1],
    next: catalog.orderedDocs[index + 1],
  }
}

export function breadcrumbs(slug: string): { title: string; slug?: string }[] {
  const doc = getDoc(slug)
  if (!doc || !slug) return []
  const crumbs: { title: string; slug?: string }[] = []
  let acc = ''
  for (const segment of doc.segments) {
    acc = acc ? `${acc}/${segment}` : segment
    const match = docsBySlug.get(acc)
    crumbs.push({
      title: match?.title ?? titleFromSegment(segment),
      slug: match ? acc : undefined,
    })
  }
  return crumbs
}

export function isFolderNode(node: NavNode): boolean {
  return node.kind === 'folder' || Boolean(node.children?.length)
}

/** 侧栏目录只展开，不作为 Markdown 页面。展开后带文档图标的才是正文。 */
export function isExpandableNavFolder(node: NavNode | undefined): boolean {
  return Boolean(node && node.kind === 'folder' && node.children?.length)
}

export function findNavNode(id: string, nodes: NavNode[] = catalog.navTree): NavNode | undefined {
  for (const node of nodes) {
    if (node.id === id || node.slug === id) return node
    if (node.children?.length) {
      const found = findNavNode(id, node.children)
      if (found) return found
    }
  }
}

export function pageChildren(slug: string): { folders: NavNode[]; docs: NavNode[] } {
  const kids = slug ? (findNavNode(slug)?.children ?? []) : catalog.navTree
  const folders: NavNode[] = []
  const docs: NavNode[] = []
  for (const node of kids) {
    if (node.slug === slug) continue
    if (isExpandableNavFolder(node)) folders.push(node)
    else docs.push(node)
  }
  return { folders, docs }
}

export function formatDocUpdatedAt(ms: number | undefined, locale: 'zh' | 'en'): string {
  if (!ms) return ''
  return new Intl.DateTimeFormat(locale === 'zh' ? 'zh-CN' : 'en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(ms)
}

export function isProtectedDoc(doc: DocEntry): boolean {
  return doc.relPath === 'index.md' || doc.slug === ''
}

export function isSpaceRel(relPath: string): boolean {
  return relPath.replaceAll('\\', '/').startsWith('space/')
}

export function countWorkspaceDocs(): { files: number; folders: number } {
  let files = 0
  let folders = 0
  for (const doc of catalog.docs) {
    if (isSpaceRel(doc.relPath)) continue
    if (doc.isIndex && doc.slug && hasDescendantDocs(doc.slug, catalog.docs)) folders += 1
    else files += 1
  }
  return { files, folders }
}
