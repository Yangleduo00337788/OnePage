import { BANNER_POOL, IMAGE_POOL, PORTRAIT_POOL, VIDEO_POOL } from './media-assets'
import mediaIndex from './media-index.json'

export const FEED_CATS = ['旅行', '美食', '时尚', '摄影', '生活', '运动', '游戏', '音乐'] as const
export type FeedCat = (typeof FEED_CATS)[number]

const KEYWORD_RULES: { re: RegExp; cat: FeedCat }[] = [
  { re: /海|沙滩|岛|古城|码头|机场|高铁|山|森林|樱花|京都|大阪|旅行|路|云|湖|草原|沙漠|帐篷/, cat: '旅行' },
  { re: /面|汤|火锅|烤|煮|菜|蛋|咖啡|茶|饺|厨|饭|串|糕|糖|醋|排骨|拉面|市|吃/, cat: '美食' },
  { re: /跑|球|滑|健身|马拉松|骑行|泳|配速|篮|滑板|登山|步道/, cat: '运动' },
  { re: /相机|镜头|焦段|胶片|曝光|晚霞|光影|街拍|构图|快门/, cat: '摄影' },
  { re: /大衣|衬衫|裙|穿|搭|针织|风衣|牛仔|帽|围巾|时尚/, cat: '时尚' },
  { re: /吉他|钢琴|弹|唱|排练|音响|和弦|黑胶|唱片/, cat: '音乐' },
  { re: /排位|打野|游戏|直播.*局|翻车|麦/, cat: '游戏' },
]

function hashSeed(s: string) {
  let n = 0
  for (let i = 0; i < s.length; i++) n = (n * 31 + s.charCodeAt(i)) | 0
  return Math.abs(n)
}

function pick(pool: readonly string[], salt: string) {
  if (!pool.length) return IMAGE_POOL.生活[0]
  return pool[hashSeed(salt) % pool.length]
}

export function resolveMediaCategory(title: string, body: string, category: string): FeedCat {
  const text = `${title} ${body}`
  for (const rule of KEYWORD_RULES) {
    if (rule.re.test(text)) return rule.cat
  }
  if (category.includes('游戏')) return '游戏'
  if (category.includes('生活')) return '生活'
  if (category.includes('才艺') || category.includes('兴趣')) return '摄影'
  if (category.includes('科技')) return '生活'
  if ((FEED_CATS as readonly string[]).includes(category)) return category as FeedCat
  return '生活'
}

export function coverForNote(title: string, body: string, category: string, salt: string) {
  const indexed = (mediaIndex as Record<string, string>)[salt]
  if (indexed) return indexed
  const cat = resolveMediaCategory(title, body, category)
  const pool = IMAGE_POOL[cat] ?? IMAGE_POOL.生活
  return pick(pool, `${cat}:${salt}:${title}`)
}

export function videoForNote(title: string, body: string, category: string, salt: string) {
  const cat = resolveMediaCategory(title, body, category)
  const pool = VIDEO_POOL[cat] ?? VIDEO_POOL.生活
  return pick(pool, `${cat}:v:${salt}:${title}`)
}

export function portraitForUser(userId: string, nickname: string) {
  return pick(PORTRAIT_POOL, `ava:${userId}:${nickname}`)
}

export function bannerForUser(userId: string) {
  return pick(BANNER_POOL, `ban:${userId}`)
}

/** @deprecated 仅兼容旧调用，请用 coverForNote */
export function photo(i: number) {
  return pick(IMAGE_POOL.生活, `legacy:${i}`)
}

export function video(i: number) {
  return pick(VIDEO_POOL.生活, `legacy:v:${i}`)
}

export function hls(i: number) {
  return video(i)
}

export function portrait(seed: string, _size = 160) {
  return portraitForUser(seed, seed)
}

export function wideBanner(seed: string) {
  return bannerForUser(seed)
}

export const PHOTOS = IMAGE_POOL.生活
export const VIDEOS = VIDEO_POOL.生活
export const HLS = VIDEOS
