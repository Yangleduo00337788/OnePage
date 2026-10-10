/** 旧占位，首页请用 home.ts。留着避免误引用时类型还能对上。 */
export type NoteCard = {
  id: string
  title: string
  author: string
  likes: number
  image: string
  video?: boolean
}

export const CATEGORIES = [
  '推荐',
  '穿搭',
  '美食',
  '彩妆',
  '影视',
  '职场',
  '情感',
  '家居',
  '游戏',
  '旅行',
  '健身',
  '视频',
] as const

export const NOTES: NoteCard[] = [
  {
    id: '1',
    title: '在人家门口坐下了 1500 的纸袋本秒变功底',
    author: '秋秋不上班',
    likes: 120,
    image: 'https://picsum.photos/seed/onepage-bag/480/640',
  },
  {
    id: '2',
    title: '没什么胃口，想吃你的豆腐',
    author: '李三',
    likes: 244,
    image: 'https://picsum.photos/seed/onepage-night/480/700',
  },
  {
    id: '3',
    title: '富尔 20 层 · 每天都是大晴天',
    author: '秋小彤 mumu',
    likes: 481,
    image: 'https://picsum.photos/seed/onepage-cafe/480/620',
  },
  {
    id: '4',
    title: '梦中隔白 · 自月光的皮草幻想',
    author: '川奈不加班',
    likes: 521,
    image: 'https://picsum.photos/seed/onepage-fur/480/640',
    video: true,
  },
  {
    id: '5',
    title: '早秋气质棕裙穿搭',
    author: '田田秋',
    likes: 63,
    image: 'https://picsum.photos/seed/onepage-skirt/480/720',
    video: true,
  },
  {
    id: '6',
    title: '薄荷绿针织，周末出街刚刚好',
    author: '一页记录',
    likes: 88,
    image: 'https://picsum.photos/seed/onepage-mint/480/600',
  },
  {
    id: '7',
    title: '驼色大衣和一杯美式',
    author: '晚风笔记',
    likes: 156,
    image: 'https://picsum.photos/seed/onepage-coat/480/680',
  },
  {
    id: '8',
    title: '黑高领是冬天的底色',
    author: '页页',
    likes: 201,
    image: 'https://picsum.photos/seed/onepage-black/480/640',
  },
  {
    id: '9',
    title: 'daily look · 两个人的周末',
    author: '对页',
    likes: 312,
    image: 'https://picsum.photos/seed/onepage-daily/480/560',
    video: true,
  },
  {
    id: '10',
    title: '白衬衫叠穿，办公也想被拍',
    author: '新页',
    likes: 74,
    image: 'https://picsum.photos/seed/onepage-white/480/700',
  },
]
