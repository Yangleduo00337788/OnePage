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
  content: {
    text?: string
    mediaId?: string
    url?: string
    postId?: string
    title?: string
    coverUrl?: string
    authorNickname?: string
  }
  createdAt: string
}

export type ImSocketFrame = {
  op: 'connected' | 'pong' | 'message' | 'ack' | 'error'
  conversationId?: string
  payload?: ImMessage
  code?: string
  detail?: string
}

/** 同源 `/ws/im`，开发时由 Vite 转到网关，才能带上 Session Cookie。 */
export function imWebSocketUrl(): string {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${window.location.host}/ws/im`
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

export const REPORT_REASONS = [
  { code: 'spam', label: '垃圾广告' },
  { code: 'porn', label: '色情低俗' },
  { code: 'abuse', label: '辱骂攻击' },
  { code: 'illegal', label: '违法违规' },
  { code: 'other', label: '其他' },
] as const

export type ReportReasonCode = (typeof REPORT_REASONS)[number]['code']

export type OpsReport = {
  reportId: string
  targetType: 'post' | 'user'
  targetId: string
  targetTitle?: string
  targetPageId?: string
  authorId?: string
  takenDown?: boolean
  reasonCode: string
  detail: string
  status: 'pending' | 'done'
  reporter?: UserBrief
  handler?: UserBrief
  handleAction?: string
  handleNote?: string
  createdAt: string
  handledAt?: string
}

export type OpsResolveAction = 'dismiss' | 'takedown' | 'restore' | 'ban' | 'unban'

export type OpsPostView = {
  postId: string
  author?: UserBrief
  title: string
  body: string
  coverUrl: string
  postType: string
  category: string
  original: boolean
  contentKind: string
  eventTitle: string
  visibility: string
  media: { mediaId: string; url: string; mimeType: string; role: string }[]
  takenDown: boolean
  publishedAt: string
}

export type OpsUser = {
  userId: string
  pageId: string
  nickname: string
  avatarUrl: string
  frozen: boolean
}

export type OpsPostRow = {
  postId: string
  author?: UserBrief
  title: string
  postType: string
  category: string
  takenDown: boolean
  publishedAt: string
}

export type OpsCommentRow = {
  commentId: string
  postId: string
  author?: UserBrief
  content: string
  hidden: boolean
  createdAt: string
}

export type OpsCategory = {
  categoryId: string
  name: string
  sortOrder: number
  enabled: boolean
}

export type OpsCampaign = {
  eventId: string
  title: string
  intro: string
  coverUrl: string
  status: string
  sortOrder: number
  enabled: boolean
}

export type OpsTopic = {
  topicId: string
  name: string
  official: boolean
  sortOrder: number
  enabled: boolean
}

export type OpsCatalog = {
  categories: OpsCategory[]
  campaigns: OpsCampaign[]
  topics: OpsTopic[]
}

export type PublicCatalog = OpsCatalog

export type OpsOverview = {
  communityUsers: number
  postsLive: number
  postsDown: number
  comments: number
  reportsPending: number
  reportsDone: number
  categories: number
  campaigns: number
  topics: number
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

/** 本机 :9000 预签名改到当前站点，避免运营/创作者站 CORS。 */
export function localMinioPutUrl(uploadUrl: string): string {
  try {
    const u = new URL(uploadUrl)
    const localMinio =
      (u.hostname === 'localhost' || u.hostname === '127.0.0.1') && u.port === '9000'
    if (localMinio) {
      return `${window.location.origin}${u.pathname}${u.search}`
    }
  } catch {
    /* 非法 URL 仍走原地址 */
  }
  return uploadUrl
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
  smsLogin: (phone: string, code: string, audience?: 'ops') =>
    request<UserSummary>(`/api/v1/auth/sms/login${audience === 'ops' ? '?audience=ops' : ''}`, {
      method: 'POST',
      body: JSON.stringify({ phone, code }),
    }),
  login: (account: string, password: string, audience?: 'ops') =>
    request<UserSummary>(`/api/v1/auth/login${audience === 'ops' ? '?audience=ops' : ''}`, {
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
  suggestUsers: (q: string) =>
    request<UserSummary[]>(`/api/v1/users/me/suggest?q=${encodeURIComponent(q)}`),
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
  /**
   * 预签名 PUT。本机 MinIO 在 :9000，页面在 :517x，直传会被 CORS 拦。
   * 开发时改成当前源路径，由 Vite 把 `/onepage-media` 转到 9000，Host 仍是 localhost:9000，签名对得上。
   */
  putObject: async (uploadUrl: string, body: Blob, mimeType: string) => {
    const res = await fetch(localMinioPutUrl(uploadUrl), {
      method: 'PUT',
      body,
      headers: { 'Content-Type': mimeType },
    })
    if (!res.ok) {
      throw new Error('直传失败')
    }
  },
  completeUpload: (mediaId: string) =>
    request<{ mediaId: string; status: string; url?: string }>('/api/v1/uploads/complete', {
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
  createComment: (postId: string, body: { content: string; parentId?: string; mentionPageIds?: string[] }) =>
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
  sendImageMessage: (conversationId: string, mediaId: string) =>
    request<ImMessage>(`/api/v1/conversations/${encodeURIComponent(conversationId)}/messages`, {
      method: 'POST',
      body: JSON.stringify({ msgType: 'image', content: { mediaId } }),
    }),
  conversationUnread: () => request<{ count: number }>('/api/v1/conversations/unread-count'),
  createReport: (body: {
    targetType: 'post' | 'user'
    targetId: string
    reasonCode: ReportReasonCode
    detail?: string
  }) =>
    request<{ reportId: string }>('/api/v1/reports', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  opsMe: () => request<UserSummary>('/api/v1/ops/me'),
  opsReports: (status?: 'pending' | 'done', cursor?: string, limit?: number) => {
    const q = new URLSearchParams()
    if (status) q.set('status', status)
    if (cursor) q.set('cursor', cursor)
    if (limit) q.set('limit', String(limit))
    const suffix = q.toString() ? `?${q}` : ''
    return request<CursorPage<OpsReport>>(`/api/v1/ops/reports${suffix}`)
  },
  opsResolveReport: (reportId: string, body: { action: OpsResolveAction; note?: string }) =>
    request<{ reportId: string; status: string }>(
      `/api/v1/ops/reports/${encodeURIComponent(reportId)}`,
      { method: 'PATCH', body: JSON.stringify(body) },
    ),
  opsPost: (postId: string) =>
    request<OpsPostView>(`/api/v1/ops/posts/${encodeURIComponent(postId)}`),
  opsPosts: (opts?: { q?: string; taken?: 'live' | 'down'; cursor?: string; limit?: number }) => {
    const q = new URLSearchParams()
    if (opts?.q) q.set('q', opts.q)
    if (opts?.taken) q.set('taken', opts.taken)
    if (opts?.cursor) q.set('cursor', opts.cursor)
    if (opts?.limit) q.set('limit', String(opts.limit))
    const suffix = q.toString() ? `?${q}` : ''
    return request<CursorPage<OpsPostRow>>(`/api/v1/ops/posts${suffix}`)
  },
  opsTakedownPost: (postId: string) =>
    request<OpsPostView>(`/api/v1/ops/posts/${encodeURIComponent(postId)}/takedown`, { method: 'POST' }),
  opsRestorePost: (postId: string) =>
    request<OpsPostView>(`/api/v1/ops/posts/${encodeURIComponent(postId)}/restore`, { method: 'POST' }),
  opsComments: (opts?: { q?: string; hidden?: 'yes' | 'no'; cursor?: string; limit?: number }) => {
    const q = new URLSearchParams()
    if (opts?.q) q.set('q', opts.q)
    if (opts?.hidden) q.set('hidden', opts.hidden)
    if (opts?.cursor) q.set('cursor', opts.cursor)
    if (opts?.limit) q.set('limit', String(opts.limit))
    const suffix = q.toString() ? `?${q}` : ''
    return request<CursorPage<OpsCommentRow>>(`/api/v1/ops/comments${suffix}`)
  },
  opsHideComment: (commentId: string) =>
    request<void>(`/api/v1/ops/comments/${encodeURIComponent(commentId)}/hide`, { method: 'POST' }),
  opsRestoreComment: (commentId: string) =>
    request<void>(`/api/v1/ops/comments/${encodeURIComponent(commentId)}/restore`, { method: 'POST' }),
  catalog: () => request<PublicCatalog>('/api/v1/catalog'),
  suggestTopics: (q?: string) => {
    const suffix = q ? `?q=${encodeURIComponent(q)}` : ''
    return request<OpsTopic[]>(`/api/v1/catalog/topics${suffix}`)
  },
  opsOverview: () => request<OpsOverview>('/api/v1/ops/overview'),
  opsCatalog: () => request<OpsCatalog>('/api/v1/ops/catalog'),
  opsCreateCategory: (body: { name: string; sortOrder?: number; enabled?: boolean }) =>
    request<OpsCategory>('/api/v1/ops/categories', { method: 'POST', body: JSON.stringify(body) }),
  opsPatchCategory: (id: string, body: { name?: string; sortOrder?: number; enabled?: boolean }) =>
    request<OpsCategory>(`/api/v1/ops/categories/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),
  opsCategoryEnabled: (id: string, value: boolean) =>
    request<void>(`/api/v1/ops/categories/${encodeURIComponent(id)}/enabled?value=${value}`, {
      method: 'POST',
    }),
  opsCreateCampaign: (body: {
    eventId: string
    title: string
    intro?: string
    coverUrl?: string
    status?: string
    sortOrder?: number
    enabled?: boolean
  }) => request<OpsCampaign>('/api/v1/ops/campaigns', { method: 'POST', body: JSON.stringify(body) }),
  opsPatchCampaign: (
    eventId: string,
    body: {
      title?: string
      intro?: string
      coverUrl?: string
      status?: string
      sortOrder?: number
      enabled?: boolean
    },
  ) =>
    request<OpsCampaign>(`/api/v1/ops/campaigns/${encodeURIComponent(eventId)}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),
  opsCampaignEnabled: (eventId: string, value: boolean) =>
    request<void>(`/api/v1/ops/campaigns/${encodeURIComponent(eventId)}/enabled?value=${value}`, {
      method: 'POST',
    }),
  opsCreateTopic: (body: { name: string; official?: boolean; sortOrder?: number; enabled?: boolean }) =>
    request<OpsTopic>('/api/v1/ops/topics', { method: 'POST', body: JSON.stringify(body) }),
  opsPatchTopic: (
    id: string,
    body: { name?: string; official?: boolean; sortOrder?: number; enabled?: boolean },
  ) =>
    request<OpsTopic>(`/api/v1/ops/topics/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),
  opsTopicEnabled: (id: string, value: boolean) =>
    request<void>(`/api/v1/ops/topics/${encodeURIComponent(id)}/enabled?value=${value}`, {
      method: 'POST',
    }),
  opsUser: (userId: string) =>
    request<OpsUser>(`/api/v1/ops/users/${encodeURIComponent(userId)}`),
  opsUsers: (opts?: { q?: string; cursor?: string; limit?: number }) => {
    const q = new URLSearchParams()
    if (opts?.q) q.set('q', opts.q)
    if (opts?.cursor) q.set('cursor', opts.cursor)
    if (opts?.limit) q.set('limit', String(opts.limit))
    const suffix = q.toString() ? `?${q}` : ''
    return request<CursorPage<OpsUser>>(`/api/v1/ops/users${suffix}`)
  },
  opsFreezeUser: (userId: string) =>
    request<OpsUser>(`/api/v1/ops/users/${encodeURIComponent(userId)}/freeze`, { method: 'POST' }),
  opsUnfreezeUser: (userId: string) =>
    request<OpsUser>(`/api/v1/ops/users/${encodeURIComponent(userId)}/unfreeze`, { method: 'POST' }),
}
