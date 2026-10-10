/** 笔记管理占位数据。P1 换成接口。 */
export type NoteStatus = '已发布' | '审核中' | '未通过'

export type Note = {
  id: string
  title: string
  status: NoteStatus
  views: string
  cover: string
}

export const NOTES: Note[] = [
  { id: 'n1', title: '周末市集手帐', status: '已发布', views: '3.2万', cover: 'https://images.unsplash.com/photo-1519682337058-a94d519337bc?w=400&h=400&fit=crop' },
  { id: 'n2', title: '夜里的便利店', status: '已发布', views: '1.1万', cover: 'https://images.unsplash.com/photo-1441986300917-64674bd600d8?w=400&h=400&fit=crop' },
  { id: 'n3', title: '第一次露营', status: '审核中', views: '—', cover: 'https://images.unsplash.com/photo-1504280390367-361c6d9f38f4?w=400&h=400&fit=crop' },
  { id: 'n4', title: '厨房小实验', status: '已发布', views: '8690', cover: 'https://images.unsplash.com/photo-1495521821757-a1efb6729352?w=400&h=400&fit=crop' },
  { id: 'n5', title: '城市骑行路线', status: '未通过', views: '—', cover: 'https://images.unsplash.com/photo-1485965120184-e220f721d03e?w=400&h=400&fit=crop' },
  { id: 'n6', title: '雨天窗台', status: '已发布', views: '2.4万', cover: 'https://images.unsplash.com/photo-1515694346937-94d85e41e6f0?w=400&h=400&fit=crop' },
]
