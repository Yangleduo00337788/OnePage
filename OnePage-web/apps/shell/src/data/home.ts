/** 瀑布流卡片类型，以及 Feed 转卡片。假列表已删，数据只来自接口。 */
export type NoteCard = {
  id: string
  title: string
  author: string
  likes: string
  image: string
  video?: boolean
  duration?: string
  pageId?: string
  avatarUrl?: string
  body?: string
  postType?: string
  original?: boolean
}

export function formatCount(n: number) {
  if (n >= 10000) return `${(n / 10000).toFixed(1).replace(/\.0$/, '')}w`
  return String(n)
}

export function noteFromPost(p: {
  postId: string
  title: string
  body?: string
  coverUrl: string
  postType: string
  likeCount: number
  original?: boolean
  author: { nickname: string; pageId: string; avatarUrl: string }
}): NoteCard {
  return {
    id: p.postId,
    title: p.title?.trim() || (p.postType === 'video' ? '视频笔记' : p.postType === 'text' ? '笔记' : '笔记'),
    author: p.author.nickname,
    likes: formatCount(p.likeCount),
    image: p.coverUrl || '',
    video: p.postType === 'video',
    pageId: p.author.pageId,
    avatarUrl: p.author.avatarUrl,
    body: p.body || '',
    postType: p.postType,
    original: Boolean(p.original),
  }
}

export const TABS = [
  '推荐',
  '关注',
  '旅行',
  '美食',
  '时尚',
  '摄影',
  '生活',
  '运动',
  '游戏',
  '音乐',
] as const

export const LIVE_TABS = ['全部', '游戏', '才艺直播', '生活分享', '兴趣爱好', '科技新闻', '运动户外'] as const
