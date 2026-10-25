import type {
  CommentView,
  CommunityPolicy,
  ConversationItem,
  ImMessage,
  LiveChatLine,
  LiveSession,
  LiveStart,
  NotificationItem,
  OpsAiConfig,
  OpsCampaign,
  OpsCategory,
  OpsConversationRow,
  OpsFaq,
  OpsImMessageRow,
  OpsMediaRow,
  OpsNotice,
  OpsReport,
  OpsSupport,
  OpsTopic,
  PostDetail,
  ReceivedItem,
  UserBrief,
  UserProfile,
} from '../../OnePage-web/packages/api-client/src/index.ts'
import { createSeed } from './seed'

const KEY = 'onepage.web.db'
export const SEED_VER = 11

export type Account = UserProfile & {
  phone: string
  email: string
  password: string
  hasPassword: boolean
  githubBound: boolean
  googleBound: boolean
  frozen: boolean
  staff: boolean
}

export type PostRow = PostDetail & {
  authorId: string
  takenDown: boolean
  scheduledAt?: string
  aiReview?: string | null
  aiReviewReason?: string | null
  aiReviewScore?: number | null
  aiReviewLabels?: string | null
}

export type MediaRow = OpsMediaRow & { blobUrl?: string }

export type Db = {
  users: Account[]
  follows: { followerId: string; followeeId: string }[]
  posts: PostRow[]
  comments: (CommentView & { postId: string; hidden: boolean })[]
  likes: { userId: string; postId: string; createdAt: string }[]
  favorites: { userId: string; postId: string; createdAt: string }[]
  commentLikes: { userId: string; commentId: string }[]
  history: { userId: string; postId: string; createdAt: string }[]
  notifications: (NotificationItem & { userId: string; hidden?: boolean })[]
  conversations: ConversationItem[]
  messages: (ImMessage & { conversationId: string; hidden?: boolean })[]
  opsConversations: OpsConversationRow[]
  opsMessages: OpsImMessageRow[]
  lives: (LiveSession & { notice: string; ended: boolean })[]
  liveChats: Record<string, LiveChatLine[]>
  liveHost: LiveStart | null
  reports: OpsReport[]
  tickets: OpsSupport[]
  categories: OpsCategory[]
  campaigns: OpsCampaign[]
  topics: OpsTopic[]
  notices: OpsNotice[]
  faqs: OpsFaq[]
  media: MediaRow[]
  policy: CommunityPolicy
  ai: OpsAiConfig
  blobs: Record<string, string>
  sessionUserId: string | null
  opsUserId: string | null
  seedVer: number
  /** 首访是否已按标题补全封面（localStorage 持久化） */
  mediaEnriched?: boolean
}

let mem: Db | null = null

export function nid(prefix = 'id') {
  return `${prefix}_${Math.random().toString(36).slice(2, 10)}`
}

export function nowIso() {
  return new Date().toISOString()
}

export function getDb(): Db {
  if (mem) return mem
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as Db
      if (parsed.seedVer === SEED_VER) {
        parsed.blobs = {}
        mem = parsed
        return mem
      }
    }
  } catch {
    /* 损坏则重种 */
  }
  mem = createSeed()
  saveDb()
  return mem
}

export function saveDb() {
  if (!mem) return
  const { blobs: _blobs, ...rest } = mem
  try {
    localStorage.setItem(KEY, JSON.stringify(rest))
  } catch {
    /* 配额满时仍留内存 */
  }
}

export function resetDb() {
  mem = createSeed()
  saveDb()
  return mem
}

export function brief(u: Account): UserBrief {
  return {
    userId: u.userId,
    pageId: u.pageId,
    nickname: u.nickname,
    avatarUrl: u.avatarUrl,
    liveHost: u.liveHost,
    verified: u.verified,
    official: u.official,
  }
}

export function withFollow(me: Account | null, u: Account): UserBrief {
  const b = brief(u)
  if (!me) return b
  b.followedByMe = getDb().follows.some((f) => f.followerId === me.userId && f.followeeId === u.userId)
  return b
}

export function profileOf(u: Account, me: Account | null): UserProfile {
  const db = getDb()
  const followingCount = db.follows.filter((f) => f.followerId === u.userId).length
  const followerCount = db.follows.filter((f) => f.followeeId === u.userId).length
  const likeReceivedCount = db.likes.filter((l) => {
    const p = db.posts.find((x) => x.postId === l.postId)
    return p?.authorId === u.userId
  }).length
  const favoriteReceivedCount = db.favorites.filter((l) => {
    const p = db.posts.find((x) => x.postId === l.postId)
    return p?.authorId === u.userId
  }).length
  return {
    ...brief(u),
    coverUrl: u.coverUrl,
    bio: u.bio,
    gender: u.gender,
    followedByMe: me ? db.follows.some((f) => f.followerId === me.userId && f.followeeId === u.userId) : false,
    stats: { followingCount, followerCount, likeReceivedCount, favoriteReceivedCount },
  }
}

export function currentUser(audience?: 'ops') {
  const db = getDb()
  const id = audience === 'ops' ? db.opsUserId : db.sessionUserId
  if (!id) return null
  return db.users.find((u) => u.userId === id) ?? null
}

export function pageOf<T>(items: T[], cursor?: string | null, limit = 20) {
  const start = cursor ? Number(cursor) || 0 : 0
  const slice = items.slice(start, start + limit)
  const next = start + limit
  return {
    items: slice,
    nextCursor: next < items.length ? String(next) : null,
    hasMore: next < items.length,
  }
}

export function postBrief(p: PostRow, me: Account | null) {
  const db = getDb()
  const author = db.users.find((u) => u.userId === p.authorId)
  return {
    postId: p.postId,
    author: author ? withFollow(me, author) : p.author,
    title: p.title,
    body: p.body,
    coverUrl: p.coverUrl,
    postType: p.postType,
    category: p.category,
    original: p.original,
    contentKind: p.contentKind,
    eventId: p.eventId,
    eventTitle: p.eventTitle,
    visibility: p.visibility,
    likeCount: p.likeCount,
    favoriteCount: p.favoriteCount,
    commentCount: p.commentCount,
    viewCount: p.viewCount,
    publishedAt: p.publishedAt,
    reviewStatus: p.reviewStatus,
    rejectReason: p.rejectReason,
  }
}

export function receivedFrom(
  rows: { userId: string; postId: string; createdAt: string }[],
): ReceivedItem[] {
  const db = getDb()
  return rows
    .map((r) => {
      const actor = db.users.find((u) => u.userId === r.userId)
      const post = db.posts.find((p) => p.postId === r.postId)
      if (!actor || !post) return null
      return {
        actor: brief(actor),
        postId: post.postId,
        postTitle: post.title,
        createdAt: r.createdAt,
      }
    })
    .filter((x): x is ReceivedItem => Boolean(x))
}
