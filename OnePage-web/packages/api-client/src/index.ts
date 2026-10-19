/**
 * 浏览器调后端的唯一入口。所有方法走 fetch，credentials: 'include' 才会带上 Session Cookie。
 * 运营站须 setApiAudience('ops')，请求带 X-OnePage-Audience，走 ONEPAGE_OPS_SESSION。
 * 不要在组件里直接 fetch，否则容易漏 Cookie 或漏统一错误处理。
 */
export type UserSummary = {
  userId: string
  pageId: string
  nickname: string
  avatarUrl: string
  liveHost?: boolean
  verified?: boolean
  official?: boolean
}

export type UserBrief = {
  userId: string
  pageId: string
  nickname: string
  avatarUrl: string
  followedByMe?: boolean
  liveHost?: boolean
  verified?: boolean
  official?: boolean
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
  reviewStatus?: string
  rejectReason?: string | null
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

export type StatsRange = '1' | '7' | '30'

export type StatsMetric = { value: number; delta: number | null }

export type StatsDayPoint = { date: string; value: number }

export type CreatorStats = {
  range: string
  from: string
  to: string
  account: {
    impressions: StatsMetric
    views: StatsMetric
    likes: StatsMetric
    comments: StatsMetric
    favorites: StatsMetric
    shares: StatsMetric
    followerNet: StatsMetric
    followerNew: StatsMetric
    coverCtr: StatsMetric
    videoCompleteRate: StatsMetric
    mix: { image: number; video: number; article: number }
    viewTrend: StatsDayPoint[]
  }
  pending: { draft: number; review: number }
  content: {
    avgViews: number
    avgLikes: number
    avgComments: number
    postCount: number
    posts: {
      postId: string
      title: string
      postType: string
      views: number
      likes: number
      comments: number
      favorites: number
    }[]
  }
  fans: {
    followerTotal: StatsMetric
    followerNet: StatsMetric
    followerNew: StatsMetric
    unfollow: number | null
    activeRate: number | null
    reciprocalRate: number | null
    viewerFans: number
    engagedFans: number
    retention7d: number | null
    change: { followNew: number; unfollow: number | null; net: number }
    trend: StatsDayPoint[]
  }
}

export type LiveCreatorStats = {
  range: string
  sessions: StatsMetric
  viewers: StatsMetric
  peakOnline: StatsMetric
  followerNew: StatsMetric
  gifters: StatsMetric
}

export type SearchUserHit = {
  userId: string
  pageId: string
  nickname: string
  avatarUrl: string
  bio?: string
  liveHost?: boolean
  verified?: boolean
  official?: boolean
}

export type SearchTopicHit = {
  topicId: string
  name: string
  official: boolean
  sortOrder: number
}

export type LiveSession = {
  sessionId: string
  roomId: string
  title: string
  category: string
  status?: string
  coverUrl: string
  notice?: string
  playUrlHls: string
  viewerCount: number
  owner: UserBrief
  startedAt?: string
}

export type SearchResult = {
  posts: PostBrief[]
  users: SearchUserHit[]
  topics?: SearchTopicHit[]
  lives?: LiveSession[]
  nextCursor: string | null
  hasMore: boolean
}

export type LiveChatLine = {
  messageId: string
  kind?: 'text' | 'join' | string
  body: string
  createdAt: string
  author: UserBrief
}

export type LivePresence = {
  status: string
  viewerCount: number
  join?: LiveChatLine | null
}

export type LiveStart = {
  sessionId: string
  roomId: string
  title?: string
  coverUrl?: string
  notice?: string
  pushUrl: string
  playUrlHls: string
  streamKey: string
}

export type PolishResult = {
  title: string
  body: string
}

export type AiCitation = {
  postId: string
  snippet: string
  title?: string
  coverUrl?: string
  nickname?: string
  pageId?: string
}

export type AiChatResult = {
  answer: string
  citations: AiCitation[]
  stopped?: boolean
}

export type AiBootstrap = {
  yehPreset: string
  yehQuestions: string[]
}

export type OpsAiConfig = {
  llmBaseUrl: string
  llmModel: string
  llmKeySet: boolean
  llmKeyMasked: string
  yehPreset: string
  yehQuestions: string[]
  chatEnabled: boolean
  polishEnabled: boolean
  persisted: boolean
  chatSystem: string
  polishSystem: string
  continueSystem: string
  retrieveLimit: number
  contextBodyMax: number
  temperature: number
  maxTokens: number
  streamEnabled: boolean
}

export type OpsAiProbe = {
  ok: boolean
  message: string
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
  coverUrl?: string | null
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
    sessionId?: string
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
  review?: number
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
  targetType: 'post' | 'user' | 'comment' | 'live'
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

export type OpsLiveView = {
  sessionId: string
  roomId: string
  title: string
  status: string
  coverUrl: string
  notice: string
  owner?: UserBrief
  ended: boolean
}

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
  reviewStatus?: string
  rejectReason?: string | null
}

export type OpsUser = {
  userId: string
  pageId: string
  nickname: string
  avatarUrl: string
  liveHost?: boolean
  verified?: boolean
  official?: boolean
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
  reviewStatus?: string
  rejectReason?: string | null
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
  postCount?: number
}

export type OpsNotice = {
  noticeId: string
  title: string
  body: string
  sortOrder: number
  enabled: boolean
}

export type OpsFaq = {
  faqId: string
  groupId: string
  groupTitle: string
  question: string
  answer: string
  sortOrder: number
  enabled: boolean
}

export type SupportKind = 'feedback' | 'live' | 'verify'

export type OpsSupport = {
  ticketId: string
  kind: SupportKind
  title: string
  contact: string
  body: string
  mediaUrls: string[]
  status: 'pending' | 'done'
  user?: UserBrief
  handler?: UserBrief
  handleNote?: string | null
  createdAt: string
  handledAt?: string | null
}

export type OpsImPeer = {
  userId: string
  pageId: string
  nickname: string
  avatarUrl: string
}

export type OpsConversationRow = {
  conversationId: string
  left: OpsImPeer
  right: OpsImPeer
  preview: string
  updatedAt: string
}

export type OpsImMessageRow = {
  messageId: string
  conversationId: string
  sender: OpsImPeer
  msgType: string
  preview: string
  imageUrl: string
  postId: string
  hidden: boolean
  createdAt: string
}

export type OpsMediaRow = {
  mediaId: string
  owner?: UserBrief
  purpose: string
  mimeType: string
  sizeBytes: number
  status: string
  url: string
  postId: string
  hidden: boolean
  createdAt: string
}

export type OpsCatalog = {
  categories: OpsCategory[]
  campaigns: OpsCampaign[]
  topics: OpsTopic[]
  notices: OpsNotice[]
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

/** 运营站入口调用一次。之后请求带 X-OnePage-Audience: ops，不覆盖社区 Cookie。 */
let apiAudience: 'ops' | undefined

export function setApiAudience(audience?: 'ops') {
  apiAudience = audience
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
  if (apiAudience === 'ops' && !headers.has('X-OnePage-Audience')) {
    headers.set('X-OnePage-Audience', 'ops')
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

async function chatStream(
  body: { question: string; sessionId?: string },
  onDelta: (text: string) => void,
  onStage?: (stage: string, n: number) => void,
  signal?: AbortSignal,
): Promise<AiChatResult> {
  const headers = new Headers()
  headers.set('Content-Type', 'application/json')
  if (apiAudience === 'ops') {
    headers.set('X-OnePage-Audience', 'ops')
  }
  let res: Response
  try {
    res = await fetch('/api/v1/ai/chat/stream', {
      method: 'POST',
      headers,
      credentials: 'include',
      body: JSON.stringify(body),
      signal,
    })
  } catch (ex) {
    if (signal?.aborted) return { answer: '', citations: [], stopped: true }
    throw ex
  }
  if (!res.ok) {
    const text = await res.text()
    let data: { code?: string; message?: string } | null = null
    try {
      data = text ? JSON.parse(text) : null
    } catch {
      data = null
    }
    throw new ApiError(res.status, data?.code ?? 'ERROR', data?.message ?? `请求失败（${res.status}）`)
  }
  if (!res.body) {
    throw new ApiError(503, 'ERROR', '暂时不能回答')
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let answer = ''
  let citations: AiCitation[] = []
  const takeFrame = (frame: string) => {
    let event = 'message'
    const dataLines: string[] = []
    for (const line of frame.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
    }
    if (!dataLines.length) return
    const parsed = JSON.parse(dataLines.join('\n')) as {
      t?: string
      citations?: AiCitation[]
      message?: string
      s?: string
      n?: number
    }
    if (event === 'stage' && parsed.s) {
      onStage?.(parsed.s, parsed.n ?? 0)
    } else if (event === 'delta' && parsed.t) {
      answer += parsed.t
      onDelta(parsed.t)
    } else if (event === 'done') {
      citations = parsed.citations ?? []
    } else if (event === 'error') {
      throw new ApiError(503, 'CONFIG_MISSING', parsed.message || '暂时不能回答')
    }
  }
  try {
    while (true) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
      const frames = buffer.split('\n\n')
      buffer = frames.pop() ?? ''
      for (const frame of frames) {
        if (frame.trim()) takeFrame(frame)
      }
      if (done) break
    }
  } catch (ex) {
    if (signal?.aborted) return { answer, citations, stopped: true }
    throw ex
  }
  if (buffer.trim()) takeFrame(buffer)
  return { answer, citations }
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
  logout: (audience?: 'ops') =>
    request<void>(
      `/api/v1/auth/logout${audience === 'ops' || apiAudience === 'ops' ? '?audience=ops' : ''}`,
      { method: 'POST' },
    ),
  account: () =>
    request<{
      phone: string
      email: string
      hasPassword: boolean
      githubBound: boolean
      googleBound: boolean
    }>('/api/v1/auth/account'),
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
  deleteHistory: (postId: string) =>
    request<void>(`/api/v1/users/me/history/${encodeURIComponent(postId)}`, { method: 'DELETE' }),
  clearHistory: () => request<void>('/api/v1/users/me/history', { method: 'DELETE' }),
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
    body?: string
    mediaIds?: string[]
    postType: string
    category?: string
    original?: boolean
    contentKind?: string
    eventId?: string
    visibility?: string
    draft?: boolean
    scheduledAt?: string
  }) =>
    request<{ postId: string; publishedAt: string; reviewStatus?: string }>('/api/v1/posts', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  post: (postId: string) => request<PostDetail>(`/api/v1/posts/${encodeURIComponent(postId)}`),
  patchPost: (
    postId: string,
    body: {
      title?: string
      body?: string
      mediaIds?: string[]
      postType: string
      category?: string
      original?: boolean
      contentKind?: string
      eventId?: string
      visibility?: string
      draft?: boolean
      scheduledAt?: string
    },
  ) =>
    request<PostDetail>(`/api/v1/posts/${encodeURIComponent(postId)}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),
  deletePost: (postId: string) => request<void>(`/api/v1/posts/${postId}`, { method: 'DELETE' }),
  creatorStats: (range: StatsRange = '7') =>
    request<CreatorStats>(`/api/v1/creator/stats?range=${encodeURIComponent(range)}`),
  liveStats: (range: StatsRange = '7') =>
    request<LiveCreatorStats>(`/api/v1/live/stats?range=${encodeURIComponent(range)}`),
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
  playComplete: (postId: string) =>
    request<void>(`/api/v1/posts/${postId}/play-complete`, { method: 'POST' }),
  shareLink: (postId: string) =>
    request<{ shareUrl: string }>(`/api/v1/posts/${postId}/share/link`, { method: 'POST' }),
  shareIm: (postId: string, body: { conversationId?: string; targetUserId?: string }) =>
    request<{ messageId: string }>(`/api/v1/posts/${postId}/share/im`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  search: (
    q: string,
    type?: 'post' | 'user' | 'all' | 'topic' | 'live',
    cursor?: string,
    limit?: number,
    extra?: { postType?: string; sort?: string; time?: string; scope?: string },
  ) => {
    const p = new URLSearchParams()
    if (q) p.set('q', q)
    if (type) p.set('type', type)
    if (cursor) p.set('cursor', cursor)
    if (limit) p.set('limit', String(limit))
    if (extra?.postType) p.set('postType', extra.postType)
    if (extra?.sort) p.set('sort', extra.sort)
    if (extra?.time) p.set('time', extra.time)
    if (extra?.scope) p.set('scope', extra.scope)
    const suffix = p.toString() ? `?${p}` : ''
    return request<SearchResult>(`/api/v1/search${suffix}`)
  },
  liveSessions: (opts?: { q?: string; category?: string }) => {
    const p = new URLSearchParams()
    if (opts?.q) p.set('q', opts.q)
    if (opts?.category) p.set('category', opts.category)
    const suffix = p.toString() ? `?${p}` : ''
    return request<{ items: LiveSession[] }>(`/api/v1/live/sessions${suffix}`)
  },
  myLive: () => request<LiveStart | undefined>('/api/v1/live/mine'),
  liveSession: (sessionId: string, opts?: { peek?: boolean }) => {
    const suffix = opts?.peek ? '?peek=true' : ''
    return request<LiveSession>(`/api/v1/live/sessions/${encodeURIComponent(sessionId)}${suffix}`)
  },
  createLiveRoom: (body: { title: string; category?: string; coverMediaId?: string; notice?: string }) =>
    request<{ roomId: string }>('/api/v1/live/rooms', { method: 'POST', body: JSON.stringify(body) }),
  startLive: (roomId: string) =>
    request<LiveStart>(`/api/v1/live/rooms/${encodeURIComponent(roomId)}/start`, { method: 'POST' }),
  endLive: (roomId: string) =>
    request<void>(`/api/v1/live/rooms/${encodeURIComponent(roomId)}/end`, { method: 'POST' }),
  updateLiveNotice: (roomId: string, notice: string) =>
    request<{ notice: string }>(`/api/v1/live/rooms/${encodeURIComponent(roomId)}/notice`, {
      method: 'POST',
      body: JSON.stringify({ notice }),
    }),
  shareLiveLink: (sessionId: string) =>
    request<{ shareUrl: string }>(
      `/api/v1/live/sessions/${encodeURIComponent(sessionId)}/share/link`,
      { method: 'POST' },
    ),
  shareLiveIm: (sessionId: string, body: { conversationId?: string; targetUserId?: string }) =>
    request<{ messageId: string }>(
      `/api/v1/live/sessions/${encodeURIComponent(sessionId)}/share/im`,
      { method: 'POST', body: JSON.stringify(body) },
    ),
  liveMessages: (sessionId: string, after?: string) => {
    const suffix = after ? `?after=${encodeURIComponent(after)}` : ''
    return request<{ items: LiveChatLine[] }>(
      `/api/v1/live/sessions/${encodeURIComponent(sessionId)}/messages${suffix}`,
    )
  },
  sendLiveMessage: (sessionId: string, body: string) =>
    request<LiveChatLine>(`/api/v1/live/sessions/${encodeURIComponent(sessionId)}/messages`, {
      method: 'POST',
      body: JSON.stringify({ body }),
    }),
  liveHeartbeat: (sessionId: string, presenceId: string) =>
    request<LivePresence>(`/api/v1/live/sessions/${encodeURIComponent(sessionId)}/heartbeat`, {
      method: 'POST',
      body: JSON.stringify({ presenceId }),
    }),
  polishPost: (body: { title?: string; body?: string; mode?: 'optimize' | 'continue' }) =>
    request<PolishResult>('/api/v1/ai/posts/polish', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  aiChat: (body: { question: string; sessionId?: string }) =>
    request<AiChatResult>('/api/v1/ai/chat', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  aiChatStream: (
    body: { question: string; sessionId?: string },
    onDelta: (text: string) => void,
    onStage?: (stage: string, n: number) => void,
    signal?: AbortSignal,
  ) => chatStream(body, onDelta, onStage, signal),
  aiBootstrap: () => request<AiBootstrap>('/api/v1/ai/bootstrap'),
  opsAiConfig: () => request<OpsAiConfig>('/api/v1/ops/ai'),
  opsSaveAiConfig: (body: {
    llmBaseUrl?: string
    llmApiKey?: string
    llmModel?: string
    yehPreset?: string
    yehQuestions?: string[]
    chatEnabled?: boolean
    polishEnabled?: boolean
    clearLlmKey?: boolean
    chatSystem?: string
    polishSystem?: string
    continueSystem?: string
    retrieveLimit?: number
    contextBodyMax?: number
    temperature?: number
    maxTokens?: number
    streamEnabled?: boolean
  }) => request<OpsAiConfig>('/api/v1/ops/ai', { method: 'POST', body: JSON.stringify(body) }),
  opsResetAiConfig: () => request<OpsAiConfig>('/api/v1/ops/ai/reset', { method: 'POST' }),
  opsProbeAi: (body: { llmBaseUrl?: string; llmApiKey?: string; llmModel?: string }) =>
    request<OpsAiProbe>('/api/v1/ops/ai/probe', { method: 'POST', body: JSON.stringify(body) }),
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
  unlikeComment: (commentId: string) =>
    request<{ liked: boolean; likeCount: number }>(`/api/v1/reactions/comments/${commentId}/like`, {
      method: 'DELETE',
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
    targetType: 'post' | 'user' | 'comment' | 'live'
    targetId: string
    reasonCode: ReportReasonCode
    detail?: string
  }) =>
    request<{ reportId: string }>('/api/v1/reports', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  createSupport: (body: {
    kind: SupportKind
    title?: string
    contact?: string
    body?: string
    mediaUrls?: string[]
  }) =>
    request<{ ticketId: string }>('/api/v1/support', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  catalogFaq: () => request<OpsFaq[]>('/api/v1/catalog/faq'),
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
  opsLive: (sessionId: string) =>
    request<OpsLiveView>(`/api/v1/ops/lives/${encodeURIComponent(sessionId)}`),
  opsPosts: (opts?: {
    q?: string
    taken?: 'live' | 'down' | 'pending' | 'rejected'
    cursor?: string
    limit?: number
  }) => {
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
  opsApprovePost: (postId: string) =>
    request<OpsPostView>(`/api/v1/ops/posts/${encodeURIComponent(postId)}/approve`, { method: 'POST' }),
  opsRejectPost: (postId: string, reason?: string) => {
    const q = new URLSearchParams()
    if (reason) q.set('reason', reason)
    const suffix = q.toString() ? `?${q}` : ''
    return request<OpsPostView>(
      `/api/v1/ops/posts/${encodeURIComponent(postId)}/reject${suffix}`,
      { method: 'POST' },
    )
  },
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
  opsCreateNotice: (body: { title: string; body?: string; sortOrder?: number; enabled?: boolean }) =>
    request<OpsNotice>('/api/v1/ops/notices', { method: 'POST', body: JSON.stringify(body) }),
  opsPatchNotice: (id: string, body: { title?: string; body?: string; sortOrder?: number; enabled?: boolean }) =>
    request<OpsNotice>(`/api/v1/ops/notices/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),
  opsNoticeEnabled: (id: string, value: boolean) =>
    request<void>(`/api/v1/ops/notices/${encodeURIComponent(id)}/enabled?value=${value}`, {
      method: 'POST',
    }),
  opsSupport: (opts?: { kind?: SupportKind; status?: 'pending' | 'done'; cursor?: string; limit?: number }) => {
    const q = new URLSearchParams()
    if (opts?.kind) q.set('kind', opts.kind)
    if (opts?.status) q.set('status', opts.status)
    if (opts?.cursor) q.set('cursor', opts.cursor)
    if (opts?.limit) q.set('limit', String(opts.limit))
    const suffix = q.toString() ? `?${q}` : ''
    return request<CursorPage<OpsSupport>>(`/api/v1/ops/support${suffix}`)
  },
  opsResolveSupport: (ticketId: string, body: { action: 'reply' | 'approve' | 'reject'; note: string }) =>
    request<{ reportId: string; status: string }>(
      `/api/v1/ops/support/${encodeURIComponent(ticketId)}`,
      { method: 'PATCH', body: JSON.stringify(body || {}) },
    ),
  opsFaqs: () => request<OpsFaq[]>('/api/v1/ops/faqs'),
  opsCreateFaq: (body: {
    groupId: string
    question: string
    answer: string
    sortOrder?: number
    enabled?: boolean
  }) => request<OpsFaq>('/api/v1/ops/faqs', { method: 'POST', body: JSON.stringify(body) }),
  opsPatchFaq: (
    id: string,
    body: { groupId?: string; question?: string; answer?: string; sortOrder?: number; enabled?: boolean },
  ) =>
    request<OpsFaq>(`/api/v1/ops/faqs/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),
  opsFaqEnabled: (id: string, value: boolean) =>
    request<void>(`/api/v1/ops/faqs/${encodeURIComponent(id)}/enabled?value=${value}`, {
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
  opsSetUserBadges: (userId: string, body: { liveHost?: boolean; verified?: boolean; official?: boolean }) =>
    request<OpsUser>(`/api/v1/ops/users/${encodeURIComponent(userId)}/badges`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  opsUserFollowing: (userId: string, opts?: { cursor?: string; limit?: number }) => {
    const q = new URLSearchParams()
    if (opts?.cursor) q.set('cursor', opts.cursor)
    if (opts?.limit) q.set('limit', String(opts.limit))
    const suffix = q.toString() ? `?${q}` : ''
    return request<CursorPage<UserBrief>>(`/api/v1/ops/users/${encodeURIComponent(userId)}/following${suffix}`)
  },
  opsUserFollowers: (userId: string, opts?: { cursor?: string; limit?: number }) => {
    const q = new URLSearchParams()
    if (opts?.cursor) q.set('cursor', opts.cursor)
    if (opts?.limit) q.set('limit', String(opts.limit))
    const suffix = q.toString() ? `?${q}` : ''
    return request<CursorPage<UserBrief>>(`/api/v1/ops/users/${encodeURIComponent(userId)}/followers${suffix}`)
  },
  opsRemoveFollowing: (userId: string, peerId: string) =>
    request<void>(
      `/api/v1/ops/users/${encodeURIComponent(userId)}/following/${encodeURIComponent(peerId)}/remove`,
      { method: 'POST' },
    ),
  opsRemoveFollower: (userId: string, peerId: string) =>
    request<void>(
      `/api/v1/ops/users/${encodeURIComponent(userId)}/followers/${encodeURIComponent(peerId)}/remove`,
      { method: 'POST' },
    ),
  opsUserNotifications: (
    userId: string,
    opts?: { group?: string; cursor?: string; limit?: number },
  ) => {
    const q = new URLSearchParams()
    if (opts?.group) q.set('group', opts.group)
    if (opts?.cursor) q.set('cursor', opts.cursor)
    if (opts?.limit) q.set('limit', String(opts.limit))
    const suffix = q.toString() ? `?${q}` : ''
    return request<CursorPage<NotificationItem>>(
      `/api/v1/ops/users/${encodeURIComponent(userId)}/notifications${suffix}`,
    )
  },
  opsHideNotification: (id: string) =>
    request<void>(`/api/v1/ops/notifications/${encodeURIComponent(id)}/hide`, { method: 'POST' }),
  opsMedia: (opts?: {
    q?: string
    purpose?: string
    status?: 'pending' | 'ready' | 'fail'
    hidden?: 'yes' | 'no'
    cursor?: string
    limit?: number
  }) => {
    const q = new URLSearchParams()
    if (opts?.q) q.set('q', opts.q)
    if (opts?.purpose) q.set('purpose', opts.purpose)
    if (opts?.status) q.set('status', opts.status)
    if (opts?.hidden) q.set('hidden', opts.hidden)
    if (opts?.cursor) q.set('cursor', opts.cursor)
    if (opts?.limit) q.set('limit', String(opts.limit))
    const suffix = q.toString() ? `?${q}` : ''
    return request<CursorPage<OpsMediaRow>>(`/api/v1/ops/media${suffix}`)
  },
  opsHideMedia: (mediaId: string) =>
    request<void>(`/api/v1/ops/media/${encodeURIComponent(mediaId)}/hide`, { method: 'POST' }),
  opsRestoreMedia: (mediaId: string) =>
    request<void>(`/api/v1/ops/media/${encodeURIComponent(mediaId)}/restore`, { method: 'POST' }),
  opsConversations: (opts?: { q?: string; cursor?: string; limit?: number }) => {
    const q = new URLSearchParams()
    if (opts?.q) q.set('q', opts.q)
    if (opts?.cursor) q.set('cursor', opts.cursor)
    if (opts?.limit) q.set('limit', String(opts.limit))
    const suffix = q.toString() ? `?${q}` : ''
    return request<CursorPage<OpsConversationRow>>(`/api/v1/ops/conversations${suffix}`)
  },
  opsConversationMessages: (
    conversationId: string,
    opts?: { hidden?: 'yes' | 'no'; cursor?: string; limit?: number },
  ) => {
    const q = new URLSearchParams()
    if (opts?.hidden) q.set('hidden', opts.hidden)
    if (opts?.cursor) q.set('cursor', opts.cursor)
    if (opts?.limit) q.set('limit', String(opts.limit))
    const suffix = q.toString() ? `?${q}` : ''
    return request<CursorPage<OpsImMessageRow>>(
      `/api/v1/ops/conversations/${encodeURIComponent(conversationId)}/messages${suffix}`,
    )
  },
  opsHideImMessage: (messageId: string) =>
    request<void>(`/api/v1/ops/messages/${encodeURIComponent(messageId)}/hide`, { method: 'POST' }),
  opsRestoreImMessage: (messageId: string) =>
    request<void>(`/api/v1/ops/messages/${encodeURIComponent(messageId)}/restore`, { method: 'POST' }),
}
