import type { DocEntry, NavNode } from '@/lib/content'
import { catalog, childRelPath, getDoc, isFolderNode, isProtectedDoc, parentSlugOf, titleFromSegment, toSlug } from '@/lib/content'
import { isOwnedSlug, isSharedWritableSlug, type RoleId } from '@/lib/roles'
import { syncCatalogFromDisk } from '@/lib/catalog-sync'
import { moveDocDraft } from '@/lib/doc-drafts'
import { moveDocOverride } from '@/lib/doc-store'
import {
  deleteWorkspaceFile,
  moveWorkspaceDir,
  reconnectWorkspace,
  serializeMarkdown,
  useWorkspaceFs,
  workspaceFileExists,
  writeRepoFile,
  writeWorkspaceFile,
} from '@/lib/workspace-fs'

export function canEditSlug(slug: string, selected: RoleId[], owned: Set<string>) {
  if (!selected.length) return false
  const doc = getDoc(slug)
  if (!doc) return false
  if (doc.segments[0] === 'space' || isSharedWritableSlug(doc.slug)) return true
  return isOwnedSlug(doc.slug, owned)
}

export function canCreateIn(parentSlug: string, selected: RoleId[], owned: Set<string>) {
  if (!selected.length || !parentSlug) return false
  if (parentSlug === 'space' || parentSlug.startsWith('space/')) return true
  if (isSharedWritableSlug(parentSlug)) return true
  return isOwnedSlug(`${parentSlug}/__new__`, owned)
}

export function createParentForNode(node: NavNode, selected: RoleId[], owned: Set<string>) {
  if (node.slug === undefined) return ''
  if (node.slug === '') return canCreateIn('start', selected, owned) ? 'start' : ''
  if (isFolderNode(node) && canCreateIn(node.slug, selected, owned)) return node.slug
  if (!isFolderNode(node)) {
    const slash = node.slug.lastIndexOf('/')
    const parent = slash === -1 ? '' : node.slug.slice(0, slash)
    if (parent && canCreateIn(parent, selected, owned)) return parent
  }
  return ''
}

export function docParentSlug(doc: DocEntry): string {
  if (doc.isIndex) {
    const i = doc.slug.lastIndexOf('/')
    return i === -1 ? '' : doc.slug.slice(0, i)
  }
  return parentSlugOf(doc)
}

export function docFileStem(doc: DocEntry): string {
  if (doc.isIndex) return doc.segments[doc.segments.length - 1] ?? ''
  const name = doc.relPath.split('/').pop() ?? ''
  return name.replace(/\.md$/i, '')
}

export function canMoveDoc(doc: DocEntry, selected: RoleId[], owned: Set<string>) {
  if (isProtectedDoc(doc)) return false
  return canEditSlug(doc.slug, selected, owned)
}

export function moveTargetFolders(doc: DocEntry, selected: RoleId[], owned: Set<string>) {
  const currentParent = docParentSlug(doc)
  const options: { slug: string; title: string }[] = []
  const seen = new Set<string>()
  const spaceOnly = doc.segments[0] === 'space'
  const push = (slug: string, title: string) => {
    if (seen.has(slug)) return
    seen.add(slug)
    options.push({ slug, title })
  }
  if (!currentParent && !spaceOnly) push('', '—')
  for (const item of catalog.docs) {
    if (!item.isIndex || !item.slug) continue
    if (spaceOnly && !item.slug.startsWith('space')) continue
    if (!spaceOnly && item.slug.startsWith('space')) continue
    if (doc.isIndex && (item.slug === doc.slug || item.slug.startsWith(`${doc.slug}/`))) continue
    const sameParent = item.slug === currentParent
    if (!sameParent && !canCreateIn(item.slug, selected, owned)) continue
    push(item.slug, item.title)
  }
  return options
}

async function ensureParentIndexFiles(relPath: string) {
  const parts = relPath.replaceAll('\\', '/').split('/').filter(Boolean)
  if (!parts.length) return
  const last = parts[parts.length - 1]!.toLowerCase()
  const dirs = last === 'index.md' ? parts.slice(0, -1) : parts.slice(0, -1)
  let acc = ''
  for (const part of dirs) {
    acc = acc ? `${acc}/${part}` : part
    const indexRel = `${acc}/index.md`
    if (await workspaceFileExists(indexRel)) continue
    const stub = {
      title: titleFromSegment(part),
      description: '',
      order: 50,
      slug: acc,
      content: '',
      segments: acc.split('/'),
      isIndex: true,
      relPath: indexRel,
    }
    await writeWorkspaceFile(indexRel, serializeMarkdown(stub, `# ${stub.title}\n\n`))
  }
}

export async function createWorkspaceDoc(payload: {
  title: string
  description: string
  relPath: string
  slug: string
  order: number
}) {
  const { status, ready } = useWorkspaceFs()
  if (status.value === 'need-permission') await reconnectWorkspace()
  if (!ready.value) throw new Error('NEED_WORKSPACE')
  if (await workspaceFileExists(payload.relPath)) throw new Error('EXISTS')
  await ensureParentIndexFiles(payload.relPath)
  const stub = {
    title: payload.title,
    description: payload.description,
    order: payload.order,
    slug: payload.slug,
    content: '',
    segments: payload.slug.split('/'),
    isIndex: payload.relPath.endsWith('/index.md'),
    relPath: payload.relPath,
  }
  const body = `# ${payload.title}\n\n`
  await writeWorkspaceFile(payload.relPath, serializeMarkdown(stub, body))
  await syncCatalogFromDisk()
}

export async function moveWorkspaceDoc(payload: {
  slug: string
  title: string
  description: string
  parentSlug: string
  stem: string
}) {
  const { status, ready } = useWorkspaceFs()
  if (status.value === 'need-permission') await reconnectWorkspace()
  if (!ready.value) throw new Error('NEED_WORKSPACE')
  const doc = getDoc(payload.slug)
  if (!doc || isProtectedDoc(doc)) throw new Error('FORBIDDEN')
  const nextRel = childRelPath(payload.parentSlug, payload.stem, doc.isIndex)
  const nextSlug = toSlug(nextRel)
  if (doc.isIndex && (nextSlug === doc.slug || nextSlug.startsWith(`${doc.slug}/`))) {
    throw new Error('NESTED')
  }
  const nextDoc = {
    ...doc,
    title: payload.title.trim(),
    description: payload.description.trim(),
    slug: nextSlug,
    relPath: nextRel,
    isIndex: doc.isIndex,
    segments: nextSlug ? nextSlug.split('/') : [],
  }
  const fileText = serializeMarkdown(nextDoc, doc.content)

  if (nextRel === doc.relPath) {
    await writeWorkspaceFile(doc.relPath, fileText)
    if (nextSlug === 'space/harness/AGENTS') {
      await writeRepoFile('AGENTS.md', `${doc.content.replace(/^\n+/, '')}\n`)
    }
    await syncCatalogFromDisk()
    return nextSlug
  }

  if (doc.isIndex) {
    await ensureParentIndexFiles(nextRel)
    await moveWorkspaceDir(doc.slug, nextSlug)
    await writeWorkspaceFile(nextRel, fileText)
  } else {
    if (await workspaceFileExists(nextRel)) throw new Error('EXISTS')
    await ensureParentIndexFiles(nextRel)
    await writeWorkspaceFile(nextRel, fileText)
    await deleteWorkspaceFile(doc.relPath)
  }

  await moveDocDraft(doc.slug, nextSlug)
  await moveDocOverride(doc.slug, nextSlug)
  if (nextSlug === 'space/harness/AGENTS') {
    await writeRepoFile('AGENTS.md', `${doc.content.replace(/^\n+/, '')}\n`)
  }
  await syncCatalogFromDisk()
  return nextSlug
}

export async function promoteDocToFolder(slug: string) {
  const { status, ready } = useWorkspaceFs()
  if (status.value === 'need-permission') await reconnectWorkspace()
  if (!ready.value) throw new Error('NEED_WORKSPACE')
  const doc = getDoc(slug)
  if (!doc || isProtectedDoc(doc)) throw new Error('FORBIDDEN')
  if (doc.isIndex) return slug
  const nextRel = `${doc.slug}/index.md`
  if (await workspaceFileExists(nextRel)) throw new Error('EXISTS')
  const nextDoc = {
    ...doc,
    isIndex: true,
    relPath: nextRel,
  }
  await writeWorkspaceFile(nextRel, serializeMarkdown(nextDoc, doc.content))
  await deleteWorkspaceFile(doc.relPath)
  await syncCatalogFromDisk()
  return slug
}
