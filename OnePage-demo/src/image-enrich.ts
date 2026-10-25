import mediaIndex from './media-index.json'
import { getDb, saveDb } from './store'

function indexEntries() {
  return Object.entries(mediaIndex as Record<string, string>)
}

/** 将已下载的本地封面路径写进内存库（无需再请求百度） */
export function applyLocalMediaIndex() {
  const entries = indexEntries()
  if (!entries.length) return
  const db = getDb()
  if (db.mediaEnriched) return
  let changed = false

  for (const p of db.posts) {
    const k = p.postType === 'video' ? `${p.postId}:cover` : p.postId
    const url = (mediaIndex as Record<string, string>)[k]
    if (!url || !url.startsWith('/media/')) continue
    p.coverUrl = url
    if (p.media[0] && p.postType !== 'video') p.media[0].url = url
    changed = true
  }

  for (const live of db.lives) {
    const url = (mediaIndex as Record<string, string>)[live.sessionId]
    if (url?.startsWith('/media/')) {
      live.coverUrl = url
      changed = true
    }
  }

  db.mediaEnriched = true
  if (changed) saveDb()
}
