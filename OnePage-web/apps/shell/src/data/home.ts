/** 瀑布流卡片。首页已接 Feed；搜索仍可能用下面占位 NOTES。 */
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

export const INTERESTS = [
  { to: '/?cat=旅行', label: '旅行', icon: 'plane' },
  { to: '/?cat=美食', label: '美食', icon: 'food' },
  { to: '/?cat=时尚', label: '时尚', icon: 'shirt' },
  { to: '/?cat=摄影', label: '摄影', icon: 'camera' },
  { to: '/?cat=运动', label: '运动', icon: 'sport' },
  { to: '/?cat=生活', label: '生活', icon: 'life' },
  { to: '/?cat=游戏', label: '游戏', icon: 'game' },
] as const

export const NOTES: NoteCard[] = [
  {
    id: '1',
    title: '周末宅家 | 记录一个人的治愈时光',
    author: '小鹿不迷路',
    likes: '3.2w',
    image: 'https://picsum.photos/seed/op-home-1/640/800',
    video: true,
    duration: '02:38',
  },
  {
    id: '2',
    title: '希腊之旅 | 蓝白小镇的浪漫与日常',
    author: '旅行的阿宁',
    likes: '1.8w',
    image: 'https://picsum.photos/seed/op-home-2/640/800',
  },
  {
    id: '3',
    title: '秋冬穿搭 | 简约又有质感的日常搭配',
    author: 'Fashion_小U',
    likes: '2.6w',
    image: 'https://picsum.photos/seed/op-home-3/640/800',
  },
  {
    id: '4',
    title: '东京美食探店 | 这家拉面店真的太吃了！',
    author: '吃货小分队',
    likes: '4.1w',
    image: 'https://picsum.photos/seed/op-home-4/640/800',
    video: true,
    duration: '03:12',
  },
  {
    id: '5',
    title: '和喜欢的人一起看日落是件幸福的事',
    author: '小丸子',
    likes: '5.7w',
    image: 'https://picsum.photos/seed/op-home-5/640/800',
    video: true,
    duration: '01:45',
  },
  {
    id: '6',
    title: '城市咖啡馆 | 和朋友的午后时光',
    author: '咖啡不加糖',
    likes: '2.3w',
    image: 'https://picsum.photos/seed/op-home-6/640/800',
  },
  {
    id: '7',
    title: '城步雪山 | 走进大自然的治愈之旅',
    author: '户外小哥',
    likes: '9.1w',
    image: 'https://picsum.photos/seed/op-home-7/640/800',
  },
  {
    id: '8',
    title: '我的小猫日常 | 一只治愈系喵星人',
    author: '喵了个球',
    likes: '6.8w',
    image: 'https://picsum.photos/seed/op-home-8/640/800',
    video: true,
    duration: '00:56',
  },
  {
    id: '9',
    title: '城市夜色 | 从江边看一座城醒来',
    author: '夜行笔记',
    likes: '4.4w',
    image: 'https://picsum.photos/seed/op-home-9/640/800',
    video: true,
  },
  {
    id: '10',
    title: '海边风很大，但今天我想被拍下来',
    author: '对页',
    likes: '3.1w',
    image: 'https://picsum.photos/seed/op-home-10/640/800',
  },
  {
    id: '11',
    title: '耳机里的城市，帽檐下的光',
    author: '页页',
    likes: '2.9w',
    image: 'https://picsum.photos/seed/op-home-11/640/800',
  },
  {
    id: '12',
    title: '下班后一碗热的，日子就过得去',
    author: '晚风食堂',
    likes: '5.0w',
    image: 'https://picsum.photos/seed/op-home-12/640/800',
  },
]

export const LIVES = [
  { title: '海边日落 · 和你一起看海', author: '林小夏', viewers: '12.8w', image: 'https://picsum.photos/seed/op-live-1/200/200' },
  { title: '国服打野 | 上分教学', author: '阿杰不是阿杰', viewers: '8.3w', image: 'https://picsum.photos/seed/op-live-2/200/200' },
  { title: '川西自驾 | 沿途风景分享', author: '在路上的小北', viewers: '5.6w', image: 'https://picsum.photos/seed/op-live-3/200/200' },
  { title: '晚安电台 | 让音乐治愈你', author: '星辰_Stereo', viewers: '4.2w', image: 'https://picsum.photos/seed/op-live-4/200/200' },
]

export const LIVE_TABS = ['全部', '游戏', '才艺直播', '生活分享', '兴趣爱好', '科技新闻', '运动户外'] as const

export type LiveRoom = {
  id: string
  title: string
  author: string
  viewers: string
  cat: (typeof LIVE_TABS)[number]
  cover: string
  avatar: string
}

export const LIVE_ROOMS: LiveRoom[] = [
  { id: 'l1', title: '国服打野 | 上分教学', author: '阿杰不是阿杰', viewers: '8.3w', cat: '游戏', cover: 'https://picsum.photos/seed/op-live-c1/640/800', avatar: 'https://picsum.photos/seed/op-live-a1/80/80' },
  { id: 'l2', title: '今晚排位冲分，来个队友', author: '夜雨不打野', viewers: '3.6w', cat: '游戏', cover: 'https://picsum.photos/seed/op-live-c2/640/800', avatar: 'https://picsum.photos/seed/op-live-a2/80/80' },
  { id: 'l3', title: '民谣弹唱 | 把今天过成歌', author: '星辰_Stereo', viewers: '4.2w', cat: '才艺直播', cover: 'https://picsum.photos/seed/op-live-c3/640/800', avatar: 'https://picsum.photos/seed/op-live-a3/80/80' },
  { id: 'l4', title: '练舞室随拍，跟我跳一段', author: '小鹿不迷路', viewers: '2.1w', cat: '才艺直播', cover: 'https://picsum.photos/seed/op-live-c4/640/800', avatar: 'https://picsum.photos/seed/op-live-a4/80/80' },
  { id: 'l5', title: '海边日落 · 和你一起看海', author: '林小夏', viewers: '12.8w', cat: '生活分享', cover: 'https://picsum.photos/seed/op-live-c5/640/800', avatar: 'https://picsum.photos/seed/op-live-a5/80/80' },
  { id: 'l6', title: '下班后一碗热的，边煮边聊', author: '晚风食堂', viewers: '5.0w', cat: '生活分享', cover: 'https://picsum.photos/seed/op-live-c6/640/800', avatar: 'https://picsum.photos/seed/op-live-a6/80/80' },
  { id: 'l7', title: '胶片相机开箱，今晚修图', author: '夜行笔记', viewers: '1.9w', cat: '兴趣爱好', cover: 'https://picsum.photos/seed/op-live-c7/640/800', avatar: 'https://picsum.photos/seed/op-live-a7/80/80' },
  { id: 'l8', title: '手作灯串，把房间点亮', author: '对页', viewers: '1.4w', cat: '兴趣爱好', cover: 'https://picsum.photos/seed/op-live-c8/640/800', avatar: 'https://picsum.photos/seed/op-live-a8/80/80' },
  { id: 'l9', title: '聊聊本周科技圈发生了什么', author: '页页', viewers: '6.7w', cat: '科技新闻', cover: 'https://picsum.photos/seed/op-live-c9/640/800', avatar: 'https://picsum.photos/seed/op-live-a9/80/80' },
  { id: 'l10', title: '新手机开箱直播，边拆边测', author: 'Fashion_小U', viewers: '2.8w', cat: '科技新闻', cover: 'https://picsum.photos/seed/op-live-c10/640/800', avatar: 'https://picsum.photos/seed/op-live-a10/80/80' },
  { id: 'l11', title: '川西自驾 | 沿途风景分享', author: '在路上的小北', viewers: '5.6w', cat: '运动户外', cover: 'https://picsum.photos/seed/op-live-c11/640/800', avatar: 'https://picsum.photos/seed/op-live-a11/80/80' },
  { id: 'l12', title: '城步雪山夜聊，装备怎么选', author: '户外小哥', viewers: '9.1w', cat: '运动户外', cover: 'https://picsum.photos/seed/op-live-c12/640/800', avatar: 'https://picsum.photos/seed/op-live-a12/80/80' },
]

export const SEARCH_HISTORY_SEED = ['秋冬穿搭指南', '希腊旅行', '东京美食', '海边日落', '记录日常']

export const SEARCH_USERS = [
  { id: 'u1', name: '小鹿不迷路', pageId: 'xiaolu', fans: '12.4w', bio: '记录生活里的光' },
  { id: 'u2', name: '旅行的阿宁', pageId: 'aning', fans: '8.6w', bio: '下一站，未知' },
  { id: 'u3', name: 'Fashion_小U', pageId: 'xiaou', fans: '21.0w', bio: '穿搭与日常' },
  { id: 'u4', name: '页页', pageId: 'yeye', fans: '3.2w', bio: 'Every Day, A New Page' },
]

export function seedHash(s: string) {
  let n = 0
  for (const c of s) n = (n * 31 + c.charCodeAt(0)) >>> 0
  return n
}

export function pageIdOf(author: string) {
  const hit = SEARCH_USERS.find((u) => u.name === author)
  if (hit) return hit.pageId
  return `u${String(100000 + (seedHash(author) % 900000))}`
}

export function authorOfPageId(pageId: string) {
  const known = SEARCH_USERS.find((u) => u.pageId === pageId)
  if (known) return known.name
  const names = [...new Set(NOTES.map((n) => n.author))]
  return names.find((name) => pageIdOf(name) === pageId) || ''
}

export function authorBioOf(name: string) {
  const known = SEARCH_USERS.find((u) => u.name === name)
  if (known?.bio) return known.bio
  if (name.includes('旅行') || name.includes('户外')) return '把路过的风景，慢慢记成一页。'
  if (name.includes('吃') || name.includes('咖啡')) return '认真吃饭，认真过一小段日子。'
  return '记录生活里刚好的光，也想被温柔地看见。'
}

/** 0 未设置不展示；1 男 mars；2 女 venus。P0 占位作者给确定值。 */
export function authorGenderOf(name: string): 0 | 1 | 2 {
  if (/小哥|阿杰|打野|小北/.test(name)) return 1
  if (/小鹿|阿宁|小U|小丸|页页|对页|林小夏|Fashion|咖啡/.test(name)) return 2
  return ((seedHash(name) % 2) + 1) as 1 | 2
}

export function authorStatsOf(name: string) {
  const n = seedHash(name || 'a')
  const known = SEARCH_USERS.find((u) => u.name === name)
  return {
    following: String(80 + (n % 420)),
    fans: known?.fans || `${(1.2 + (n % 80) / 10).toFixed(1)}w`,
    likes: `${(3.4 + (n % 90) / 10).toFixed(1)}w`,
  }
}

export function avatarOf(name: string) {
  return `https://picsum.photos/seed/op-av-${encodeURIComponent(name)}/160/160`
}

export function coverOf(name: string) {
  return `https://picsum.photos/seed/op-cv-${encodeURIComponent(name)}/1600/480`
}

export function postsByAuthor(author: string) {
  return NOTES.filter((item) => item.author === author)
}

export const TOPICS = [
  { rank: 1, name: '#秋冬穿搭指南', heat: '248.6w' },
  { rank: 2, name: '#旅行的意义', heat: '186.4w' },
  { rank: 3, name: '#美食治愈时刻', heat: '152.8w' },
  { rank: 4, name: '#生活中的小确幸', heat: '123.7w' },
  { rank: 5, name: '#记录我的日常', heat: '98.2w' },
  { rank: 6, name: '#今日份快乐', heat: '76.5w' },
]
