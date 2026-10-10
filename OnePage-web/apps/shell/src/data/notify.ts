/** 通知页 P0 占位。P2 接 notification-service。 */
export const NOTIFY_TABS = ['评论和@', '点赞和收藏', '新增关注'] as const
export type NotifyTab = (typeof NOTIFY_TABS)[number]

export type NotifyItem = {
  id: string
  tab: NotifyTab
  name: string
  avatar: string
  action: string
  snippet?: string
  time: string
  unread?: boolean
  thumb?: string
}

export const NOTICES: NotifyItem[] = [
  {
    id: 'c1',
    tab: '评论和@',
    name: '小鹿不迷路',
    avatar: 'https://picsum.photos/seed/op-n-a1/80/80',
    action: '评论了你的笔记',
    snippet: '这组光也太干净了，滤镜能分享一下吗？',
    time: '2分钟前',
    unread: true,
    thumb: 'https://picsum.photos/seed/op-n-t1/120/120',
  },
  {
    id: 'c2',
    tab: '评论和@',
    name: '旅行的阿宁',
    avatar: 'https://picsum.photos/seed/op-n-a2/80/80',
    action: '回复了你的评论',
    snippet: '下周同一条线路，要不要一起？',
    time: '18分钟前',
    unread: true,
    thumb: 'https://picsum.photos/seed/op-n-t2/120/120',
  },
  {
    id: 'c3',
    tab: '评论和@',
    name: '页页',
    avatar: 'https://picsum.photos/seed/op-n-a3/80/80',
    action: '在评论里@了你',
    snippet: '@对页 你看这张构图',
    time: '昨天 21:14',
    thumb: 'https://picsum.photos/seed/op-n-t3/120/120',
  },
  {
    id: 'c4',
    tab: '评论和@',
    name: '晚风食堂',
    avatar: 'https://picsum.photos/seed/op-n-a4/80/80',
    action: '评论了你的笔记',
    snippet: '看起来就很暖，求菜谱。',
    time: '周二',
    thumb: 'https://picsum.photos/seed/op-n-t4/120/120',
  },
  {
    id: 'l1',
    tab: '点赞和收藏',
    name: 'Fashion_小U',
    avatar: 'https://picsum.photos/seed/op-n-a5/80/80',
    action: '赞了你的笔记',
    time: '5分钟前',
    unread: true,
    thumb: 'https://picsum.photos/seed/op-n-t5/120/120',
  },
  {
    id: 'l2',
    tab: '点赞和收藏',
    name: '喵了个球',
    avatar: 'https://picsum.photos/seed/op-n-a6/80/80',
    action: '收藏了你的笔记',
    time: '1小时前',
    unread: true,
    thumb: 'https://picsum.photos/seed/op-n-t6/120/120',
  },
  {
    id: 'l3',
    tab: '点赞和收藏',
    name: '咖啡不加糖',
    avatar: 'https://picsum.photos/seed/op-n-a7/80/80',
    action: '赞了你的笔记',
    time: '昨天 09:20',
    thumb: 'https://picsum.photos/seed/op-n-t7/120/120',
  },
  {
    id: 'f1',
    tab: '新增关注',
    name: '户外小哥',
    avatar: 'https://picsum.photos/seed/op-n-a8/80/80',
    action: '开始关注你了',
    time: '刚刚',
    unread: true,
  },
  {
    id: 'f2',
    tab: '新增关注',
    name: '在路上的小北',
    avatar: 'https://picsum.photos/seed/op-n-a9/80/80',
    action: '开始关注你了',
    time: '今天 11:08',
  },
  {
    id: 'f3',
    tab: '新增关注',
    name: '林小夏',
    avatar: 'https://picsum.photos/seed/op-n-a10/80/80',
    action: '开始关注你了',
    time: '周一',
  },
]
