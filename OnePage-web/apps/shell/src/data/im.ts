/** 私信页 P0 占位。P2 接 message-service / WS。 */
export type ImSide = 'me' | 'them'
export type ImMsg = { id: string; side: ImSide; text: string; time: string }
export type ImThread = {
  id: string
  name: string
  avatar: string
  preview: string
  time: string
  unread: number
  messages: ImMsg[]
}

export const IM_THREADS: ImThread[] = [
  {
    id: 't1',
    name: '小鹿不迷路',
    avatar: 'https://picsum.photos/seed/op-im-a1/80/80',
    preview: '那组光圈你后来怎么调的？',
    time: '刚刚',
    unread: 2,
    messages: [
      { id: 'm1', side: 'them', text: '周末那组拍得真干净。', time: '昨天 21:02' },
      { id: 'm2', side: 'me', text: '谢谢，傍晚那一会儿光线刚好。', time: '昨天 21:08' },
      { id: 'm3', side: 'them', text: '那组光圈你后来怎么调的？', time: '刚刚' },
    ],
  },
  {
    id: 't2',
    name: '旅行的阿宁',
    avatar: 'https://picsum.photos/seed/op-im-a2/80/80',
    preview: '川西那条线我下周出发',
    time: '18:20',
    unread: 0,
    messages: [
      { id: 'm1', side: 'me', text: '上次说的路线图发你了。', time: '今天 12:10' },
      { id: 'm2', side: 'them', text: '收到，川西那条线我下周出发。', time: '18:20' },
    ],
  },
  {
    id: 't3',
    name: 'Fashion_小U',
    avatar: 'https://picsum.photos/seed/op-im-a3/80/80',
    preview: '这件外套链接还在吗',
    time: '昨天',
    unread: 1,
    messages: [
      { id: 'm1', side: 'them', text: '笔记里那件外套，这件外套链接还在吗', time: '昨天 16:40' },
    ],
  },
  {
    id: 't4',
    name: '页页',
    avatar: 'https://picsum.photos/seed/op-im-a4/80/80',
    preview: '晚上一起看下改版稿？',
    time: '周一',
    unread: 0,
    messages: [
      { id: 'm1', side: 'me', text: '首页瀑布流那版我先发你。', time: '周一 11:02' },
      { id: 'm2', side: 'them', text: '晚上一起看下改版稿？', time: '周一 19:14' },
    ],
  },
]
