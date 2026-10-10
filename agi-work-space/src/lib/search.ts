import MiniSearch from 'minisearch'
import { catalog, isBrowsableDoc, type DocEntry } from '@/lib/content'
import { stripMarkdown } from '@/lib/markdown'

const engine = new MiniSearch({
  fields: ['title', 'description', 'text'],
  storeFields: ['title', 'description', 'slug'],
  searchOptions: {
    prefix: true,
    fuzzy: 0.2,
    boost: { title: 4, description: 2, text: 1 },
  },
})

function toRecord(doc: DocEntry) {
  return {
    id: doc.slug || 'index',
    slug: doc.slug,
    title: doc.title,
    description: doc.description,
    text: stripMarkdown(doc.content),
  }
}

export function reindexAll() {
  engine.removeAll()
  engine.addAll(catalog.docs.filter(isBrowsableDoc).map(toRecord))
}

reindexAll()

export interface SearchHit {
  slug: string
  title: string
  description: string
}

export function searchDocs(query: string): SearchHit[] {
  const q = query.trim()
  if (!q) return []
  return engine.search(q).map((hit) => ({
    slug: String(hit.slug ?? ''),
    title: String(hit.title ?? ''),
    description: String(hit.description ?? ''),
  }))
}

export function reindexDoc(doc: DocEntry) {
  const id = doc.slug || 'index'
  if (engine.has(id)) engine.discard(id)
  if (!isBrowsableDoc(doc)) return
  engine.add(toRecord(doc))
}

export function removeSearchDoc(slug: string) {
  const id = slug || 'index'
  if (engine.has(id)) engine.discard(id)
}
