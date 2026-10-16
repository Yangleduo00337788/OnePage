/** 笔记管理：列表类型。数据来自 GET /posts/mine。 */
import type { PostBrief } from '@onepage/api-client'

export type NoteStatus = '已发布' | '审核中' | '未通过' | '草稿'

export type Note = {
  id: string
  title: string
  status: NoteStatus
  rejectReason?: string
  views: string
  cover: string
  original: boolean
  category: string
  contentKind: string
  eventTitle: string
  visibility: string
  postType: string
  body: string
}

export function formatCount(n: number) {
  if (n >= 10000) return `${(n / 10000).toFixed(1).replace(/\.0$/, '')}w`
  return String(n)
}

export function coverVideoSrc(url: string) {
  if (!url) return ''
  return url.includes('#') ? url : `${url}#t=0.1`
}

export function isVideoNote(item: Pick<Note, 'postType' | 'cover'>) {
  return item.postType === 'video' || /\.(mp4|webm|mov)(\?|#|$)/i.test(item.cover)
}

export function isTextNote(item: Pick<Note, 'postType' | 'cover'>) {
  return item.postType === 'text' || (!item.cover && !isVideoNote(item))
}

function reviewLabel(status?: string): NoteStatus {
  if (status === 'draft') return '草稿'
  if (status === 'pending') return '审核中'
  if (status === 'rejected') return '未通过'
  return '已发布'
}

export function noteFromMine(p: PostBrief): Note {
  const title =
    p.title?.trim() || (p.postType === 'video' ? '视频笔记' : p.postType === 'text' ? '笔记' : '笔记')
  return {
    id: p.postId,
    title,
    status: reviewLabel(p.reviewStatus),
    rejectReason: p.rejectReason || '',
    views: formatCount(p.viewCount ?? 0),
    cover: p.coverUrl || '',
    original: Boolean(p.original),
    category: p.category || '',
    contentKind: p.contentKind || '',
    eventTitle: p.eventTitle || '',
    visibility: p.visibility || 'public',
    postType: p.postType,
    body: p.body || '',
  }
}
