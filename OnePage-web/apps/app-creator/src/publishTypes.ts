export type PublishKind = 'image' | 'video' | 'article'

export type LocalFile = { name: string; url: string; file?: File; mediaId?: string }

export type PublishSeed = {
  title: string
  body: string
  category: string
  original: boolean
  contentKind: string
  eventId: string
  visibility: 'public' | 'self'
}
