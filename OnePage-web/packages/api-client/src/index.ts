/**
 * 浏览器调后端的唯一入口。所有方法走 fetch，credentials: 'include' 才会带上 ONEPAGE_SESSION。
 * 不要在组件里直接 fetch，否则容易漏 Cookie 或漏统一错误处理。
 */
export type UserSummary = {
  userId: string
  pageId: string
  nickname: string
  avatarUrl: string
}

export type UserBrief = {
  userId: string
  pageId: string
  nickname: string
  avatarUrl: string
  followedByMe?: boolean
}

export const FEED_CATEGORIES = ['旅行', '美食', '时尚', '摄影', '生活', '运动', '游戏', '音乐'] as const

export type FeedCategory = (typeof FEED_CATEGORIES)[number]

export type PostBrief = {
  postId: string
  author: UserBrief
  title: string
  body: string
  coverUrl: string
  postType: string
  category: string
  original: boolean
  contentKind: string
  eventId: string
  eventTitle: string
  visibility: string
  likeCount: number
  favoriteCount: number
  commentCount: number
  viewCount: number
  publishedAt: string
}

export type PostDetail = PostBrief & {
  body: string
  media: { mediaId: string; url: string; mimeType: string; role: string }[]
  likedByMe: boolean
  favoritedByMe: boolean
}

export type CursorPage<T> = {
  items: T[]
  nextCursor: string | null
  hasMore: boolean
}

export type NotificationItem = {
  id: string
  type: string
  actor: UserBrief
  postId?: string | null
  commentId?: string | null
  preview?: string | null
  read: boolean
  createdAt: string
}

export type ConversationItem = {
  conversationId: string
  peer: UserBrief
  lastMessage: { messageId: string; msgType: string; text: string; createdAt: string } | null
  unreadCount: number
  updatedAt: string
}

export type ImMessage = {
  messageId: string
  senderId: string
  msgType: string
  content: { text?: string; postId?: string; title?: string; coverUrl?: string; authorNickname?: string }
  createdAt: string
}

export type ReceivedItem = {
  actor: UserBrief
  postId: string
  postTitle: string
  createdAt: string
}

export type NotificationUnread = {
  count: number
  comment?: number
  reaction?: number
  follow?: number
}

export type CommentView = {
  commentId: string
  author: UserBrief
  content: string
  parentId: string | null
  rootId: string
  likeCount: number
  likedByMe: boolean
  replyCount: number
  createdAt: string
}

export type UserProfile = UserSummary & {
  coverUrl: string
  bio: string
  gender: number
  followedByMe?: boolean
  stats: {
    followingCount: number
    followerCount: number
    likeReceivedCount: number
    favoriteReceivedCount: number
  }
}

/** 业务/HTTP 失败。组件 catch 后用 err.message 展示，用 err.code 分支。 */
function cursorQuery(cursor?: string, limit?: number) {
  const q = new URLSearchParams()
  if (cursor) q.set('cursor', cursor)
  if (limit) q.set('limit', String(limit))
  const suffix = q.toString() ? `?${q}` : ''
  return suffix
}

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message)
  }
}

/**
 * 相对路径请求。开发时由 Vite 代理到网关。
 * 204 无 body；其余当 JSON。HTTP 非 2xx 抛 ApiError，不要当成功。
 */
async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const res = await fetch(path, { ...init, headers, credentials: 'include' })
  if (res.status === 204) {
    return undefined as T
  }
  const text = await res.text()
  let data: { code?: string; message?: string } | null = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      throw new ApiError(res.status, 'ERROR', res.ok ? '响应不是 JSON' : `请求失败（${res.status}）`)
    }
  }
  if (!res.ok) {
    throw new ApiError(res.status, data?.code ?? 'ERROR', data?.message ?? res.statusText)
  }
  return data as T
}

export const api = {
  sendSms: (phone: string, scene: 'login' | 'register' | 'reset') =>
    request<{ cooldownSec: number }>('/api/v1/auth/sms/send', {
      method: 'POST',
      body: JSON.stringify({ phone, scene }),
    }),
  smsLogin: (phone: string, code: string) =>
    request<UserSummary>('/api/v1/auth/sms/login', {
      method: 'POST',
      body: JSON.stringify({ phone, code }),
    }),
  login: (account: string, password: string) =>
    request<UserSummary>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ account, password }),
    }),
  register: (body: { phone?: string; email?: string; password: string; nickname?: string }) =>
    request<UserSummary>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  logout: () => request<void>('/api/v1/auth/logout', { method: 'POST' }),
  account: () =>
    request<{ phone: string; email: string; hasPassword: boolean }>('/api/v1/auth/account'),
  setPassword: (body: { oldPassword?: string; password: string }) =>
    request<{ phone: string; email: string; hasPassword: boolean }>('/api/v1/auth/password', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  resetPassword: (body: { phone: string; code: string; password: string }) =>
    request<UserSummary>('/api/v1/auth/password/reset', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  me: () => request<UserProfile>('/api/v1/users/me'),
  userByPageId: (pageId: string) => request<UserProfile>(`/api/v1/users/${encodeURIComponent(pageId)}`),
  userPosts: (pageId: string, cursor?: string, limit?: number) => {
    const q = new URLSearchParams()
    if (cursor) q.set('cursor', cursor)
    if (limit) q.set('limit', String(limit))
    const suffix = q.toString() ? `?${q}` : ''
    return request<CursorPage<PostBrief>>(`/api/v1/users/${encodeURIComponent(pageId)}/posts${suffix}`)
  },
  myFavorites: (cursor?: string, limit?: number) =>
    request<CursorPage<PostBrief>>(`/api/v1/users/me/favorites${cursorQuery(cursor, limit)}`),
  myLikes: (cursor?: string, limit?: number) =>
    request<CursorPage<PostBrief>>(`/api/v1/users/me/likes${cursorQuery(cursor, limit)}`),
  myHistory: (cursor?: string, limit?: number) =>
    request<CursorPage<PostBrief>>(`/api/v1/users/me/history${cursorQuery(cursor, limit)}`),
  likesReceived: (cursor?: string, limit?: number) =>
    request<CursorPage<ReceivedItem>>(`/api/v1/users/me/likes-received${cursorQuery(cursor, limit)}`),
  favoritesReceived: (cursor?: string, limit?: number) =>
    request<CursorPage<ReceivedItem>>(
      `/api/v1/users/me/favorites-received${cursorQuery(cursor, limit)}`,
    ),
  userFollowing: (pageId: string, cursor?: string, limit?: number) =>
    request<CursorPage<UserBrief>>(`/api/v1/users/${encodeURIComponent(pageId)}/following${cursorQuery(cursor, limit)}`),
  userFollowers: (pageId: string, cursor?: string, limit?: number) =>
    request<CursorPage<UserBrief>>(`/api/v1/users/${encodeURIComponent(pageId)}/followers${cursorQuery(cursor, limit)}`),
  followUser: (pageId: string) =>
    request<{ followed: boolean }>(`/api/v1/users/${encodeURIComponent(pageId)}/follow`, { method: 'POST' }),
  unfollowUser: (pageId: string) =>
    request<void>(`/api/v1/users/${encodeURIComponent(pageId)}/follow`, { method: 'DELETE' }),
  patchMe: (body: { nickname?: string; bio?: string; gender?: number; avatarMediaId?: string; coverMediaId?: string }) =>
    request<UserProfile>('/api/v1/users/me', { method: 'PATCH', body: JSON.stringify(body) }),
  presign: (mimeType: string, sizeBytes: number, purpose: string) =>
    request<{ mediaId: string; uploadUrl: string; objectKey: string; expiresAt: string }>(
      '/api/v1/uploads/presign',
      { method: 'POST', body: JSON.stringify({ mimeType, sizeBytes, purpose }) },
    ),
  completeUpload: (mediaId: string) =>
    request<{ mediaId: string; status: string }>('/api/v1/uploads/complete', {
      method: 'POST',
      body: JSON.stringify({ mediaId }),
    }),
  createPost: (body: {
    title?: string
    body: string
    mediaIds?: string[]
    postType: string
    category: string
    original?: boolean
    contentKind?: string
    eventId?: string
    visibility?: string
  }) =>
    request<{ postId: string; publishedAt: string }>('/api/v1/posts', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  post: (postId: string) => request<PostDetail>(`/api/v1/posts/${encodeURIComponent(postId)}`),
  patchPost: (
    postId: string,
    body: {
      title?: string
      body: string
      mediaIds?: string[]
      postType: string
      category: string
      original?: boolean
      contentKind?: string
      eventId?: string
      visibility?: string
    },
  ) =>
    request<PostDetail>(`/api/v1/posts/${encodeURIComponent(postId)}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),
  deletePost: (postId: string) => request<void>(`/api/v1/posts/${postId}`, { method: 'DELETE' }),
  myPosts: (cursor?: string, limit?: number) => {
    const q = new URLSearchParams()
    if (cursor) q.set('cursor', cursor)
    if (limit) q.set('limit', String(limit))
    const suffix = q.toString() ? `?${q}` : ''
    return request<CursorPage<PostBrief>>(`/api/v1/posts/mine${suffix}`)
  },
  patchPostVisibility: (postId: string, visibility: string) =>
    request<PostBrief>(`/api/v1/posts/${postId}/visibility`, {
      method: 'PATCH',
      body: JSON.stringify({ visibility }),
    }),
  viewPost: (postId: string) => request<void>(`/api/v1/posts/${postId}/view`, { method: 'POST' }),
  shareLink: (postId: string) =>
    request<{ shareUrl: string }>(`/api/v1/posts/${postId}/share/link`, { method: 'POST' }),
  shareIm: (postId: string, body: { conversationId?: string; targetUserId?: string }) =>
    request<{ messageId: string }>(`/api/v1/posts/${postId}/share/im`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  exploreFeed: (cursor?: string, limit?: number, category?: string) => {
    const q = new URLSearchParams()
    if (cursor) q.set('cursor', cursor)
    if (limit) q.set('limit', String(limit))
    if (category) q.set('category', category)
    const suffix = q.toString() ? `?${q}` : ''
    return request<CursorPage<PostBrief>>(`/api/v1/feeds/explore${suffix}`)
  },
  followingFeed: (cursor?: string, limit?: number) => {
    const q = new URLSearchParams()
    if (cursor) q.set('cursor', cursor)
    if (limit) q.set('limit', String(limit))
    const suffix = q.toString() ? `?${q}` : ''
    return request<CursorPage<PostBrief>>(`/api/v1/feeds/following${suffix}`)
  },
  likePost: (postId: string) =>
    request<{ liked: boolean; likeCount: number }>(`/api/v1/reactions/posts/${postId}/like`, {
      method: 'POST',
    }),
  notifications: (group?: string, cursor?: string, limit?: number) => {
    const q = new URLSearchParams()
    if (group) q.set('group', group)
    if (cursor) q.set('cursor', cursor)
    if (limit) q.set('limit', String(limit))
    const suffix = q.toString() ? `?${q}` : ''
    return request<CursorPage<NotificationItem>>(`/api/v1/notifications${suffix}`)
  },
  markNotificationsRead: (body: { ids?: string[]; all?: boolean }) =>
    request<void>('/api/v1/notifications/read', { method: 'POST', body: JSON.stringify(body) }),
  notificationUnread: () => request<NotificationUnread>('/api/v1/notifications/unread-count'),
  unlikePost: (postId: string) =>
    request<{ liked: boolean; likeCount: number }>(`/api/v1/reactions/posts/${postId}/like`, {
      method: 'DELETE',
    }),
  favoritePost: (postId: string) =>
    request<{ favorited: boolean; favoriteCount: number }>(
      `/api/v1/reactions/posts/${postId}/favorite`,
      { method: 'POST' },
    ),
  unfavoritePost: (postId: string) =>
    request<{ favorited: boolean; favoriteCount: number }>(
      `/api/v1/reactions/posts/${postId}/favorite`,
      { method: 'DELETE' },
    ),
  comments: (postId: string, rootId?: string, offset?: number, limit?: number) => {
    const q = new URLSearchParams()
    if (rootId) q.set('rootId', rootId)
    if (offset != null) q.set('offset', String(offset))
    if (limit != null) q.set('limit', String(limit))
    const suffix = q.toString() ? `?${q}` : ''
    return request<{ items: CommentView[]; total: number }>(`/api/v1/posts/${postId}/comments${suffix}`)
  },
  createComment: (postId: string, body: { content: string; parentId?: string }) =>
    request<CommentView>(`/api/v1/posts/${postId}/comments`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  deleteComment: (commentId: string) =>
    request<void>(`/api/v1/comments/${commentId}`, { method: 'DELETE' }),
  likeComment: (commentId: string) =>
    request<{ liked: boolean; likeCount: number }>(`/api/v1/reactions/comments/${commentId}/like`, {
      method: 'POST',
    }),
  conversations: () => request<{ items: ConversationItem[] }>('/api/v1/conversations'),
  openConversation: (peerUserId: string) =>
    request<{ conversationId: string }>('/api/v1/conversations', {
      method: 'POST',
      body: JSON.stringify({ peerUserId }),
    }),
  conversationMessages: (conversationId: string, beforeId?: string, limit?: number) => {
    const q = new URLSearchParams()
    if (beforeId) q.set('beforeId', beforeId)
    if (limit) q.set('limit', String(limit))
    const suffix = q.toString() ? `?${q}` : ''
    return request<{ messages: ImMessage[] }>(
      `/api/v1/conversations/${encodeURIComponent(conversationId)}/messages${suffix}`,
    )
  },
  sendMessage: (conversationId: string, text: string) =>
    request<ImMessage>(`/api/v1/conversations/${encodeURIComponent(conversationId)}/messages`, {
      method: 'POST',
      body: JSON.stringify({ msgType: 'text', content: { text } }),
    }),
  conversationUnread: () => request<{ count: number }>('/api/v1/conversations/unread-count'),
}
