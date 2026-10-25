import {
  ApiError,
  type AiChatResult,
  type CreatorStats,
  type LiveCreatorStats,
  type LiveStart,
  type OpsAiConfig,
  type PostDetail,
  type StatsRange,
  type UserProfile,
} from '../../OnePage-web/packages/api-client/src/index.ts'
import { emitIm } from './bus'
import { coverUrl } from './images'
import { videoForNote } from './media'
import {
  brief,
  currentUser,
  getDb,
  nid,
  nowIso,
  pageOf,
  postBrief,
  profileOf,
  receivedFrom,
  saveDb,
  withFollow,
  type Account,
  type PostRow,
} from './store'

function sleep() {
  return new Promise((r) => setTimeout(r, 70 + Math.floor(Math.random() * 90)))
}

function jsonBody(init: RequestInit) {
  if (!init.body || typeof init.body !== 'string') return {} as Record<string, unknown>
  try {
    return JSON.parse(init.body) as Record<string, unknown>
  } catch {
    return {}
  }
}

function match(path: string, pattern: string) {
  const a = path.split('/').filter(Boolean)
  const b = pattern.split('/').filter(Boolean)
  if (a.length !== b.length) return null
  const out: Record<string, string> = {}
  for (let i = 0; i < a.length; i++) {
    if (b[i].startsWith(':')) out[b[i].slice(1)] = decodeURIComponent(a[i])
    else if (a[i] !== b[i]) return null
  }
  return out
}

function need(u: Account | null): Account {
  if (!u) throw new ApiError(401, 'UNAUTHORIZED', '请先登录')
  if (u.frozen) throw new ApiError(403, 'FROZEN', '账号已冻结')
  return u
}

function staff(u: Account | null): Account {
  const me = need(u)
  if (!me.staff) throw new ApiError(403, 'FORBIDDEN', '没有权限')
  return me
}

function findUser(idOrPage: string) {
  const db = getDb()
  return db.users.find((u) => u.userId === idOrPage || u.pageId === idOrPage)
}

function visiblePost(p: PostRow, me: Account | null) {
  if (p.takenDown) return me?.userId === p.authorId
  if (p.reviewStatus === 'draft' || p.reviewStatus === 'pending' || p.reviewStatus === 'rejected') {
    return me?.userId === p.authorId
  }
  if (p.visibility === 'private') return me?.userId === p.authorId
  if (p.visibility === 'friends') {
    if (!me) return false
    if (me.userId === p.authorId) return true
    return getDb().follows.some((f) => f.followerId === me.userId && f.followeeId === p.authorId)
  }
  return true
}

function hydratePost(p: PostRow, me: Account | null): PostDetail {
  const db = getDb()
  return {
    ...postBrief(p, me),
    body: p.body,
    media: p.media,
    likedByMe: Boolean(me && db.likes.some((l) => l.userId === me.userId && l.postId === p.postId)),
    favoritedByMe: Boolean(me && db.favorites.some((l) => l.userId === me.userId && l.postId === p.postId)),
  }
}

function loginAs(u: Account, audience?: 'ops') {
  const db = getDb()
  if (audience === 'ops') {
    if (!u.staff) throw new ApiError(403, 'FORBIDDEN', '没有运营权限')
    db.opsUserId = u.userId
  } else {
    db.sessionUserId = u.userId
  }
  saveDb()
  return brief(u)
}

function metric(value: number, delta = 0.08) {
  return { value, delta }
}

function days(n: number) {
  const out: { date: string; value: number }[] = []
  for (let i = n - 1; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    out.push({ date: d.toISOString().slice(0, 10), value: 80 + ((i * 37) % 120) })
  }
  return out
}

function creatorStats(me: Account, range: StatsRange): CreatorStats {
  const db = getDb()
  const mine = db.posts.filter((p) => p.authorId === me.userId)
  const views = mine.reduce((s, p) => s + p.viewCount, 0)
  const likes = mine.reduce((s, p) => s + p.likeCount, 0)
  const comments = mine.reduce((s, p) => s + p.commentCount, 0)
  const favorites = mine.reduce((s, p) => s + p.favoriteCount, 0)
  const n = range === '1' ? 1 : range === '30' ? 30 : 7
  return {
    range,
    from: days(n)[0]?.date || '',
    to: days(n)[n - 1]?.date || '',
    account: {
      impressions: metric(views * 3),
      views: metric(views),
      likes: metric(likes),
      comments: metric(comments),
      favorites: metric(favorites),
      shares: metric(36),
      followerNet: metric(12),
      followerNew: metric(18),
      coverCtr: metric(21, 0.04),
      videoCompleteRate: metric(44, 0.03),
      mix: {
        image: mine.filter((p) => p.postType === 'image').length,
        video: mine.filter((p) => p.postType === 'video').length,
        article: mine.filter((p) => p.postType === 'text').length,
      },
      viewTrend: days(n),
    },
    pending: {
      draft: mine.filter((p) => p.reviewStatus === 'draft').length,
      review: mine.filter((p) => p.reviewStatus === 'pending').length,
    },
    content: {
      avgViews: mine.length ? Math.round(views / mine.length) : 0,
      avgLikes: mine.length ? Math.round(likes / mine.length) : 0,
      avgComments: mine.length ? Math.round(comments / mine.length) : 0,
      postCount: mine.length,
      posts: mine.map((p) => ({
        postId: p.postId,
        title: p.title,
        postType: p.postType,
        views: p.viewCount,
        likes: p.likeCount,
        comments: p.commentCount,
        favorites: p.favoriteCount,
      })),
    },
    fans: {
      followerTotal: metric(db.follows.filter((f) => f.followeeId === me.userId).length),
      followerNet: metric(12),
      followerNew: metric(18),
      unfollow: 6,
      activeRate: 0.38,
      reciprocalRate: 0.41,
      viewerFans: 64,
      engagedFans: 29,
      retention7d: 0.62,
      change: { followNew: 18, unfollow: 6, net: 12 },
      trend: days(n),
    },
  }
}

function liveStats(range: StatsRange): LiveCreatorStats {
  return {
    range,
    sessions: metric(3),
    viewers: metric(1640),
    peakOnline: metric(1284),
    followerNew: metric(11),
    gifters: metric(0, 0),
  }
}

function notifyGroup(type: string) {
  if (type === 'comment' || type === 'reply' || type === 'mention') return 'comment'
  if (type.startsWith('like') || type.startsWith('favorite')) return 'reaction'
  if (type === 'follow') return 'follow'
  return 'review'
}

export async function demoRequest<T>(path: string, init: RequestInit = {}, audience?: 'ops'): Promise<T> {
  await sleep()
  const url = new URL(path, 'http://onepage.local')
  const p = url.pathname
  const q = url.searchParams
  const method = (init.method || 'GET').toUpperCase()
  const body = jsonBody(init)
  const db = getDb()
  const me = currentUser(audience)
  const cursor = q.get('cursor') || undefined
  const limit = q.get('limit') ? Number(q.get('limit')) : 20

  const go = (value: unknown) => value as T

  if (match(p, '/api/v1/auth/sms/send') && method === 'POST') {
    return go({ cooldownSec: 60 })
  }
  if (match(p, '/api/v1/auth/sms/login') && method === 'POST') {
    const phone = String(body.phone || '')
    const code = String(body.code || '')
    if (!/^\d{4,8}$/.test(code)) throw new ApiError(400, 'CODE', '验证码不正确')
    let u = db.users.find((x) => x.phone === phone)
    if (!u) {
      u = {
        userId: nid('u'),
        pageId: `u${phone.slice(-4)}`,
        nickname: `用户${phone.slice(-4)}`,
        avatarUrl: coverUrl(phone, phone.slice(-4)),
        coverUrl: coverUrl(phone + 'b', ''),
        bio: '',
        gender: 0,
        phone,
        email: '',
        password: '',
        hasPassword: false,
        githubBound: false,
        googleBound: false,
        frozen: false,
        staff: false,
        liveHost: false,
        verified: false,
        official: false,
        followedByMe: false,
        stats: { followingCount: 0, followerCount: 0, likeReceivedCount: 0, favoriteReceivedCount: 0 },
      }
      db.users.push(u)
    }
    const ops = url.searchParams.get('audience') === 'ops' || audience === 'ops'
    return go(loginAs(u, ops ? 'ops' : undefined))
  }
  if (match(p, '/api/v1/auth/login') && method === 'POST') {
    const account = String(body.account || '')
    const password = String(body.password || '')
    const u = db.users.find(
      (x) => x.phone === account || x.email === account || x.pageId === account || x.nickname === account,
    )
    if (!u || (u.password && u.password !== password)) throw new ApiError(400, 'LOGIN', '账号或密码不对')
    const ops = url.searchParams.get('audience') === 'ops' || audience === 'ops'
    return go(loginAs(u, ops ? 'ops' : undefined))
  }
  if (match(p, '/api/v1/auth/register') && method === 'POST') {
    const phone = String(body.phone || '')
    const email = String(body.email || '')
    const nickname = String(body.nickname || '新用户')
    const password = String(body.password || '')
    const u: Account = {
      userId: nid('u'),
      pageId: `p${Date.now().toString(36).slice(-6)}`,
      nickname,
      avatarUrl: coverUrl(nickname, nickname),
      coverUrl: coverUrl(nickname + 'c', ''),
      bio: '',
      gender: 0,
      phone,
      email,
      password,
      hasPassword: Boolean(password),
      githubBound: false,
      googleBound: false,
      frozen: false,
      staff: false,
      liveHost: false,
      verified: false,
      official: false,
      followedByMe: false,
      stats: { followingCount: 0, followerCount: 0, likeReceivedCount: 0, favoriteReceivedCount: 0 },
    }
    db.users.push(u)
    return go(loginAs(u))
  }
  if (match(p, '/api/v1/auth/logout') && method === 'POST') {
    if (url.searchParams.get('audience') === 'ops' || audience === 'ops') db.opsUserId = null
    else db.sessionUserId = null
    saveDb()
    return go(undefined)
  }
  if (match(p, '/api/v1/auth/account') && method === 'GET') {
    const u = need(me)
    return go({
      phone: u.phone,
      email: u.email,
      hasPassword: u.hasPassword,
      githubBound: u.githubBound,
      googleBound: u.googleBound,
    })
  }
  if (match(p, '/api/v1/auth/password') && method === 'POST') {
    const u = need(me)
    u.password = String(body.password || u.password)
    u.hasPassword = true
    saveDb()
    return go({ phone: u.phone, email: u.email, hasPassword: true })
  }
  if (match(p, '/api/v1/auth/password/reset') && method === 'POST') {
    const u = db.users.find((x) => x.phone === String(body.phone || ''))
    if (!u) throw new ApiError(400, 'PHONE', '该手机号尚未注册')
    u.password = String(body.password || '')
    u.hasPassword = true
    saveDb()
    return go(loginAs(u))
  }

  if (match(p, '/api/v1/users/me') && method === 'GET') {
    return go(profileOf(need(me), me) as UserProfile)
  }
  if (match(p, '/api/v1/users/me') && method === 'PATCH') {
    const u = need(me)
    if (typeof body.nickname === 'string') u.nickname = body.nickname
    if (typeof body.bio === 'string') u.bio = body.bio
    if (typeof body.gender === 'number') u.gender = body.gender
    if (typeof body.avatarMediaId === 'string' && db.blobs[body.avatarMediaId]) {
      u.avatarUrl = db.blobs[body.avatarMediaId]
    }
    if (typeof body.coverMediaId === 'string' && db.blobs[body.coverMediaId]) {
      u.coverUrl = db.blobs[body.coverMediaId]
    }
    saveDb()
    return go(profileOf(u, u))
  }
  if (match(p, '/api/v1/users/me/suggest') && method === 'GET') {
    const s = (q.get('q') || '').trim()
    const list = db.users
      .filter((u) => !s || u.nickname.includes(s) || u.pageId.includes(s))
      .slice(0, 8)
      .map((u) => brief(u))
    return go(list)
  }
  {
    const m = match(p, '/api/v1/users/:pageId/posts')
    if (m && method === 'GET') {
      const u = findUser(m.pageId)
      if (!u) throw new ApiError(404, 'NOT_FOUND', '用户不存在')
      const items = db.posts
        .filter((x) => x.authorId === u.userId && visiblePost(x, me) && x.reviewStatus === 'published')
        .map((x) => postBrief(x, me))
      return go(pageOf(items, cursor, limit))
    }
  }
  {
    const m = match(p, '/api/v1/users/:pageId/following')
    if (m && method === 'GET') {
      const u = findUser(m.pageId)
      if (!u) throw new ApiError(404, 'NOT_FOUND', '用户不存在')
      const items = db.follows
        .filter((f) => f.followerId === u.userId)
        .map((f) => db.users.find((x) => x.userId === f.followeeId))
        .filter((x): x is Account => Boolean(x))
        .map((x) => withFollow(me, x))
      return go(pageOf(items, cursor, limit))
    }
  }
  {
    const m = match(p, '/api/v1/users/:pageId/followers')
    if (m && method === 'GET') {
      const u = findUser(m.pageId)
      if (!u) throw new ApiError(404, 'NOT_FOUND', '用户不存在')
      const items = db.follows
        .filter((f) => f.followeeId === u.userId)
        .map((f) => db.users.find((x) => x.userId === f.followerId))
        .filter((x): x is Account => Boolean(x))
        .map((x) => withFollow(me, x))
      return go(pageOf(items, cursor, limit))
    }
  }
  {
    const m = match(p, '/api/v1/users/:pageId/follow')
    if (m && method === 'POST') {
      const u = need(me)
      const peer = findUser(m.pageId)
      if (!peer) throw new ApiError(404, 'NOT_FOUND', '用户不存在')
      if (!db.follows.some((f) => f.followerId === u.userId && f.followeeId === peer.userId)) {
        db.follows.push({ followerId: u.userId, followeeId: peer.userId })
        db.notifications.unshift({
          id: nid('n'),
          userId: peer.userId,
          type: 'follow',
          actor: brief(u),
          read: false,
          createdAt: nowIso(),
        })
      }
      saveDb()
      return go({ followed: true })
    }
    if (m && method === 'DELETE') {
      const u = need(me)
      const peer = findUser(m.pageId)
      if (peer) db.follows = db.follows.filter((f) => !(f.followerId === u.userId && f.followeeId === peer.userId))
      saveDb()
      return go(undefined)
    }
  }
  {
    const m = match(p, '/api/v1/users/:pageId')
    if (m && method === 'GET' && m.pageId !== 'me') {
      const u = findUser(m.pageId)
      if (!u) throw new ApiError(404, 'NOT_FOUND', '用户不存在')
      return go(profileOf(u, me))
    }
  }

  if (match(p, '/api/v1/users/me/favorites') && method === 'GET') {
    const u = need(me)
    const items = db.favorites
      .filter((x) => x.userId === u.userId)
      .map((x) => db.posts.find((p0) => p0.postId === x.postId))
      .filter((x): x is PostRow => Boolean(x))
      .map((x) => postBrief(x, me))
    return go(pageOf(items, cursor, limit))
  }
  if (match(p, '/api/v1/users/me/likes') && method === 'GET') {
    const u = need(me)
    const items = db.likes
      .filter((x) => x.userId === u.userId)
      .map((x) => db.posts.find((p0) => p0.postId === x.postId))
      .filter((x): x is PostRow => Boolean(x))
      .map((x) => postBrief(x, me))
    return go(pageOf(items, cursor, limit))
  }
  if (match(p, '/api/v1/users/me/history') && method === 'DELETE') {
    const u = need(me)
    db.history = db.history.filter((h) => h.userId !== u.userId)
    saveDb()
    return go(undefined)
  }
  if (match(p, '/api/v1/users/me/history') && method === 'GET') {
    const u = need(me)
    const items = db.history
      .filter((x) => x.userId === u.userId)
      .map((x) => db.posts.find((p0) => p0.postId === x.postId))
      .filter((x): x is PostRow => Boolean(x))
      .map((x) => postBrief(x, me))
    return go(pageOf(items, cursor, limit))
  }
  {
    const m = match(p, '/api/v1/users/me/history/:postId')
    if (m && method === 'DELETE') {
      const u = need(me)
      db.history = db.history.filter((h) => !(h.userId === u.userId && h.postId === m.postId))
      saveDb()
      return go(undefined)
    }
  }
  if (match(p, '/api/v1/users/me/likes-received') && method === 'GET') {
    const u = need(me)
    const mine = new Set(db.posts.filter((p0) => p0.authorId === u.userId).map((p0) => p0.postId))
    return go(pageOf(receivedFrom(db.likes.filter((l) => mine.has(l.postId))), cursor, limit))
  }
  if (match(p, '/api/v1/users/me/favorites-received') && method === 'GET') {
    const u = need(me)
    const mine = new Set(db.posts.filter((p0) => p0.authorId === u.userId).map((p0) => p0.postId))
    return go(pageOf(receivedFrom(db.favorites.filter((l) => mine.has(l.postId))), cursor, limit))
  }

  if (match(p, '/api/v1/uploads/presign') && method === 'POST') {
    const mediaId = nid('m')
    return go({
      mediaId,
      uploadUrl: `/onepage-media/${mediaId}`,
      objectKey: mediaId,
      expiresAt: new Date(Date.now() + 3600_000).toISOString(),
    })
  }
  if (match(p, '/api/v1/uploads/complete') && method === 'POST') {
    const mediaId = String(body.mediaId || '')
    const url = db.blobs[mediaId] || coverUrl(mediaId, '图')
    db.media.unshift({
      mediaId,
      owner: me ? brief(me) : undefined,
      purpose: 'post',
      mimeType: 'image/jpeg',
      sizeBytes: 120000,
      status: 'ready',
      url,
      postId: '',
      hidden: false,
      createdAt: nowIso(),
    })
    saveDb()
    return go({ mediaId, status: 'ready', url })
  }

  if (match(p, '/api/v1/posts') && method === 'POST') {
    const u = need(me)
    const draft = Boolean(body.draft)
    const visibility = String(body.visibility || 'public')
    const title = String(body.title || '未命名')
    const mediaIds = (body.mediaIds as string[]) || []
    const media = mediaIds.map((id) => ({
      mediaId: id,
      url: db.blobs[id] || coverUrl(id, title),
      mimeType: 'image/jpeg',
      role: 'cover',
    }))
    const postType = String(body.postType || 'image')
    const row: PostRow = {
      postId: nid('p'),
      authorId: u.userId,
      author: brief(u),
      title,
      body: String(body.body || ''),
      coverUrl: media[0]?.url || (postType === 'text' ? '' : coverUrl(title, title)),
      postType,
      category: String(body.category || '生活'),
      original: body.original !== false,
      contentKind: String(body.contentKind || 'note'),
      eventId: String(body.eventId || ''),
      eventTitle: db.campaigns.find((c) => c.eventId === body.eventId)?.title || '',
      visibility,
      likeCount: 0,
      favoriteCount: 0,
      commentCount: 0,
      viewCount: 0,
      publishedAt: nowIso(),
      reviewStatus: draft ? 'draft' : visibility === 'public' ? 'published' : 'published',
      likedByMe: false,
      favoritedByMe: false,
      media,
      takenDown: false,
      scheduledAt: body.scheduledAt ? String(body.scheduledAt) : undefined,
    }
    db.posts.unshift(row)
    saveDb()
    return go({ postId: row.postId, publishedAt: row.publishedAt, reviewStatus: row.reviewStatus })
  }
  if (match(p, '/api/v1/posts/mine') && method === 'GET') {
    const u = need(me)
    const items = db.posts.filter((x) => x.authorId === u.userId).map((x) => postBrief(x, me))
    return go(pageOf(items, cursor, limit))
  }
  {
    const m = match(p, '/api/v1/posts/:postId/visibility')
    if (m && method === 'PATCH') {
      const u = need(me)
      const row = db.posts.find((x) => x.postId === m.postId)
      if (!row || row.authorId !== u.userId) throw new ApiError(404, 'NOT_FOUND', '笔记不存在')
      row.visibility = String(body.visibility || row.visibility)
      saveDb()
      return go(postBrief(row, me))
    }
  }
  {
    const m = match(p, '/api/v1/posts/:postId/view')
    if (m && method === 'POST') {
      const row = db.posts.find((x) => x.postId === m.postId)
      if (row) {
        row.viewCount += 1
        if (me) {
          db.history = [{ userId: me.userId, postId: row.postId, createdAt: nowIso() }, ...db.history.filter((h) => !(h.userId === me.userId && h.postId === row.postId))]
        }
        saveDb()
      }
      return go(undefined)
    }
  }
  {
    const m = match(p, '/api/v1/posts/:postId/play-complete')
    if (m && method === 'POST') return go(undefined)
  }
  {
    const m = match(p, '/api/v1/posts/:postId/share/link')
    if (m && method === 'POST') {
      return go({ shareUrl: `${window.location.origin}/?post=${encodeURIComponent(m.postId)}` })
    }
  }
  {
    const m = match(p, '/api/v1/posts/:postId/share/im')
    if (m && method === 'POST') {
      const u = need(me)
      const post = db.posts.find((x) => x.postId === m.postId)
      if (!post) throw new ApiError(404, 'NOT_FOUND', '笔记不存在')
      let convId = String(body.conversationId || '')
      if (!convId && body.targetUserId) {
        const opened = openConv(u.userId, String(body.targetUserId))
        convId = opened
      }
      const msg = {
        conversationId: convId,
        messageId: nid('im'),
        senderId: u.userId,
        msgType: 'post_card',
        content: {
          postId: post.postId,
          title: post.title,
          coverUrl: post.coverUrl,
          authorNickname: post.author.nickname,
        },
        createdAt: nowIso(),
      }
      db.messages.push(msg)
      touchConv(convId, msg.messageId, '[笔记]', msg.createdAt)
      emitIm({ op: 'message', conversationId: convId, payload: msg })
      saveDb()
      return go({ messageId: msg.messageId })
    }
  }
  {
    const m = match(p, '/api/v1/posts/:postId/comments')
    if (m && method === 'GET') {
      const rootId = q.get('rootId')
      let items = db.comments.filter((c) => c.postId === m.postId && !c.hidden)
      if (rootId) items = items.filter((c) => c.rootId === rootId && c.commentId !== rootId)
      else items = items.filter((c) => !c.parentId)
      const offset = q.get('offset') ? Number(q.get('offset')) : 0
      const lim = q.get('limit') ? Number(q.get('limit')) : 20
      const slice = items.slice(offset, offset + lim).map((c) => ({
        ...c,
        likedByMe: Boolean(me && db.commentLikes.some((x) => x.userId === me.userId && x.commentId === c.commentId)),
        author: (() => {
          const u = db.users.find((x) => x.userId === c.author.userId)
          return u ? withFollow(me, u) : c.author
        })(),
      }))
      return go({ items: slice, total: items.length })
    }
    if (m && method === 'POST') {
      const u = need(me)
      const parentId = body.parentId ? String(body.parentId) : undefined
      const parent = parentId ? db.comments.find((c) => c.commentId === parentId) : undefined
      const row = {
        commentId: nid('c'),
        postId: m.postId,
        author: brief(u),
        content: String(body.content || ''),
        parentId: parentId || null,
        rootId: parent?.rootId || nid('c'),
        likeCount: 0,
        likedByMe: false,
        replyCount: 0,
        createdAt: nowIso(),
        hidden: false,
      }
      if (!parent) row.rootId = row.commentId
      if (parent) parent.replyCount += 1
      db.comments.push(row)
      const post = db.posts.find((x) => x.postId === m.postId)
      if (post) {
        post.commentCount += 1
        db.notifications.unshift({
          id: nid('n'),
          userId: post.authorId,
          type: parent ? 'reply' : 'comment',
          actor: brief(u),
          postId: post.postId,
          commentId: row.commentId,
          preview: row.content,
          read: false,
          createdAt: row.createdAt,
          coverUrl: post.coverUrl,
        })
      }
      saveDb()
      return go(row)
    }
  }
  {
    const m = match(p, '/api/v1/posts/:postId')
    if (m && method === 'GET') {
      const row = db.posts.find((x) => x.postId === m.postId)
      if (!row || !visiblePost(row, me)) throw new ApiError(404, 'NOT_FOUND', '笔记不存在')
      return go(hydratePost(row, me))
    }
    if (m && method === 'PATCH') {
      const u = need(me)
      const row = db.posts.find((x) => x.postId === m.postId)
      if (!row || row.authorId !== u.userId) throw new ApiError(404, 'NOT_FOUND', '笔记不存在')
      if (typeof body.title === 'string') row.title = body.title
      if (typeof body.body === 'string') row.body = body.body
      if (typeof body.postType === 'string') row.postType = body.postType
      if (typeof body.category === 'string') row.category = body.category
      if (typeof body.visibility === 'string') row.visibility = body.visibility
      if (body.draft) row.reviewStatus = 'draft'
      else if (row.reviewStatus === 'rejected') row.reviewStatus = 'pending'
      saveDb()
      return go(hydratePost(row, me))
    }
    if (m && method === 'DELETE') {
      const u = need(me)
      db.posts = db.posts.filter((x) => !(x.postId === m.postId && x.authorId === u.userId))
      saveDb()
      return go(undefined)
    }
  }

  if (match(p, '/api/v1/creator/stats') && method === 'GET') {
    return go(creatorStats(need(me), (q.get('range') as StatsRange) || '7'))
  }
  if (match(p, '/api/v1/live/stats') && method === 'GET') {
    need(me)
    return go(liveStats((q.get('range') as StatsRange) || '7'))
  }

  if (match(p, '/api/v1/search') && method === 'GET') {
    const s = (q.get('q') || '').trim()
    const type = q.get('type') || 'all'
    const hit = (t: string) => !s || t.toLowerCase().includes(s.toLowerCase())
    const posts = db.posts
      .filter((x) => visiblePost(x, me) && x.reviewStatus === 'published' && (hit(x.title) || hit(x.body)))
      .map((x) => postBrief(x, me))
    const users = db.users.filter((u) => hit(u.nickname) || hit(u.pageId)).map((u) => ({
      userId: u.userId,
      pageId: u.pageId,
      nickname: u.nickname,
      avatarUrl: u.avatarUrl,
      bio: u.bio,
      liveHost: u.liveHost,
      verified: u.verified,
      official: u.official,
    }))
    const topics = db.topics.filter((t) => t.enabled && hit(t.name))
    const lives = db.lives.filter((l) => !l.ended && (hit(l.title) || hit(l.category)))
    return go({
      posts: type === 'user' || type === 'topic' || type === 'live' ? [] : posts,
      users: type === 'post' || type === 'topic' || type === 'live' ? [] : users,
      topics: type === 'post' || type === 'user' || type === 'live' ? [] : topics,
      lives: type === 'post' || type === 'user' || type === 'topic' ? [] : lives,
      nextCursor: null,
      hasMore: false,
    })
  }

  if (match(p, '/api/v1/live/sessions') && method === 'GET') {
    const cat = q.get('category') || ''
    const s = q.get('q') || ''
    const items = db.lives.filter((l) => !l.ended && (!cat || l.category === cat) && (!s || l.title.includes(s)))
    return go({ items })
  }
  if (match(p, '/api/v1/live/mine') && method === 'GET') {
    return go(db.liveHost || undefined)
  }
  if (match(p, '/api/v1/live/rooms') && method === 'POST') {
    const u = need(me)
    const roomId = nid('rm')
    db.liveHost = {
      sessionId: nid('lv'),
      roomId,
      title: String(body.title || '直播'),
      coverUrl: typeof body.coverMediaId === 'string' ? db.blobs[body.coverMediaId] : coverUrl(roomId, String(body.title || '')),
      notice: String(body.notice || ''),
      pushUrl: 'rtmp://live.onepage.local/app',
      playUrlHls: videoForNote(String(body.title || '直播'), String(body.notice || ''), '生活', roomId),
      streamKey: nid('sk'),
    }
    saveDb()
    return go({ roomId })
  }
  {
    const m = match(p, '/api/v1/live/rooms/:roomId/start')
    if (m && method === 'POST') {
      const u = need(me)
      const host = db.liveHost
      if (!host || host.roomId !== m.roomId) throw new ApiError(404, 'NOT_FOUND', '还没有这场直播')
      db.lives.unshift({
        sessionId: host.sessionId,
        roomId: host.roomId,
        title: host.title || '直播',
        category: '生活分享',
        status: 'live',
        coverUrl: host.coverUrl || coverUrl(host.sessionId, host.title || ''),
        notice: host.notice || '',
        playUrlHls: host.playUrlHls,
        viewerCount: 1,
        owner: brief(u),
        startedAt: nowIso(),
        ended: false,
      })
      db.liveChats[host.sessionId] = []
      saveDb()
      return go(host as LiveStart)
    }
  }
  {
    const m = match(p, '/api/v1/live/rooms/:roomId/end')
    if (m && method === 'POST') {
      const live = db.lives.find((l) => l.roomId === m.roomId)
      if (live) {
        live.status = 'ended'
        live.ended = true
      }
      db.liveHost = null
      saveDb()
      return go(undefined)
    }
  }
  {
    const m = match(p, '/api/v1/live/rooms/:roomId/notice')
    if (m && method === 'POST') {
      const live = db.lives.find((l) => l.roomId === m.roomId)
      if (live) live.notice = String(body.notice || '')
      if (db.liveHost && db.liveHost.roomId === m.roomId) db.liveHost.notice = String(body.notice || '')
      saveDb()
      return go({ notice: String(body.notice || '') })
    }
  }
  {
    const m = match(p, '/api/v1/live/sessions/:sessionId/share/link')
    if (m && method === 'POST') return go({ shareUrl: `${window.location.origin}/live/${m.sessionId}` })
  }
  {
    const m = match(p, '/api/v1/live/sessions/:sessionId/share/im')
    if (m && method === 'POST') {
      const u = need(me)
      const live = db.lives.find((l) => l.sessionId === m.sessionId)
      let convId = String(body.conversationId || '')
      if (!convId && body.targetUserId) convId = openConv(u.userId, String(body.targetUserId))
      const msg = {
        conversationId: convId,
        messageId: nid('im'),
        senderId: u.userId,
        msgType: 'live_card',
        content: {
          sessionId: m.sessionId,
          title: live?.title,
          coverUrl: live?.coverUrl,
          authorNickname: live?.owner.nickname,
        },
        createdAt: nowIso(),
      }
      db.messages.push(msg)
      touchConv(convId, msg.messageId, '[直播]', msg.createdAt)
      emitIm({ op: 'message', conversationId: convId, payload: msg })
      saveDb()
      return go({ messageId: msg.messageId })
    }
  }
  {
    const m = match(p, '/api/v1/live/sessions/:sessionId/messages')
    if (m && method === 'GET') {
      const after = q.get('after')
      let items = db.liveChats[m.sessionId] || []
      if (after) {
        const i = items.findIndex((x) => x.messageId === after)
        items = i >= 0 ? items.slice(i + 1) : items
      }
      return go({ items })
    }
    if (m && method === 'POST') {
      const u = need(me)
      const line = {
        messageId: nid('lc'),
        kind: 'text' as const,
        body: String(body.body || ''),
        createdAt: nowIso(),
        author: brief(u),
      }
      db.liveChats[m.sessionId] = [...(db.liveChats[m.sessionId] || []), line]
      saveDb()
      return go(line)
    }
  }
  {
    const m = match(p, '/api/v1/live/sessions/:sessionId/heartbeat')
    if (m && method === 'POST') {
      const live = db.lives.find((l) => l.sessionId === m.sessionId)
      return go({
        status: live?.ended ? 'ended' : 'live',
        viewerCount: live?.viewerCount ?? 0,
        join: null,
      })
    }
  }
  {
    const m = match(p, '/api/v1/live/sessions/:sessionId')
    if (m && method === 'GET') {
      const live = db.lives.find((l) => l.sessionId === m.sessionId)
      if (!live) throw new ApiError(404, 'NOT_FOUND', '直播不存在')
      return go(live)
    }
  }

  if (match(p, '/api/v1/ai/posts/polish') && method === 'POST') {
    const title = String(body.title || '')
    const text = String(body.body || '')
    const mode = body.mode
    if (mode === 'continue') {
      return go({ title, body: `${text}\n\n后来天色更暗，路灯一盏盏亮起来。把这一页合上之前，还是想再走两步。` })
    }
    return go({
      title: title ? `${title.replace(/。$/, '')}` : '这一页',
      body: text.replace(/\s+/g, ' ').trim() + (text ? ' 风停了一会儿，我把相机放下。' : '把想写的话说完整就好。'),
    })
  }
  if (match(p, '/api/v1/ai/chat') && method === 'POST') {
    return go(yehAnswer(String(body.question || '')))
  }
  if (match(p, '/api/v1/ai/bootstrap') && method === 'GET') {
    return go({ yehPreset: db.ai.yehPreset, yehQuestions: db.ai.yehQuestions })
  }

  if (match(p, '/api/v1/feeds/explore') && method === 'GET') {
    const cat = q.get('category') || ''
    const items = db.posts
      .filter((x) => visiblePost(x, me) && x.reviewStatus === 'published' && x.visibility === 'public' && (!cat || x.category === cat))
      .map((x) => postBrief(x, me))
    return go(pageOf(items, cursor, limit))
  }
  if (match(p, '/api/v1/feeds/following') && method === 'GET') {
    const u = need(me)
    const ids = new Set(db.follows.filter((f) => f.followerId === u.userId).map((f) => f.followeeId))
    const items = db.posts
      .filter((x) => ids.has(x.authorId) && visiblePost(x, me) && x.reviewStatus === 'published')
      .map((x) => postBrief(x, me))
    return go(pageOf(items, cursor, limit))
  }

  {
    const m = match(p, '/api/v1/reactions/posts/:postId/like')
    if (m && (method === 'POST' || method === 'DELETE')) {
      const u = need(me)
      const post = db.posts.find((x) => x.postId === m.postId)
      if (!post) throw new ApiError(404, 'NOT_FOUND', '笔记不存在')
      const has = db.likes.some((l) => l.userId === u.userId && l.postId === post.postId)
      if (method === 'POST' && !has) {
        db.likes.push({ userId: u.userId, postId: post.postId, createdAt: nowIso() })
        post.likeCount += 1
      }
      if (method === 'DELETE' && has) {
        db.likes = db.likes.filter((l) => !(l.userId === u.userId && l.postId === post.postId))
        post.likeCount = Math.max(0, post.likeCount - 1)
      }
      saveDb()
      const liked = db.likes.some((l) => l.userId === u.userId && l.postId === post.postId)
      return go({ liked, likeCount: post.likeCount })
    }
  }
  {
    const m = match(p, '/api/v1/reactions/posts/:postId/favorite')
    if (m && (method === 'POST' || method === 'DELETE')) {
      const u = need(me)
      const post = db.posts.find((x) => x.postId === m.postId)
      if (!post) throw new ApiError(404, 'NOT_FOUND', '笔记不存在')
      const has = db.favorites.some((l) => l.userId === u.userId && l.postId === post.postId)
      if (method === 'POST' && !has) {
        db.favorites.push({ userId: u.userId, postId: post.postId, createdAt: nowIso() })
        post.favoriteCount += 1
      }
      if (method === 'DELETE' && has) {
        db.favorites = db.favorites.filter((l) => !(l.userId === u.userId && l.postId === post.postId))
        post.favoriteCount = Math.max(0, post.favoriteCount - 1)
      }
      saveDb()
      const favorited = db.favorites.some((l) => l.userId === u.userId && l.postId === post.postId)
      return go({ favorited, favoriteCount: post.favoriteCount })
    }
  }
  {
    const m = match(p, '/api/v1/reactions/comments/:commentId/like')
    if (m && (method === 'POST' || method === 'DELETE')) {
      const u = need(me)
      const c = db.comments.find((x) => x.commentId === m.commentId)
      if (!c) throw new ApiError(404, 'NOT_FOUND', '评论不存在')
      const has = db.commentLikes.some((l) => l.userId === u.userId && l.commentId === c.commentId)
      if (method === 'POST' && !has) {
        db.commentLikes.push({ userId: u.userId, commentId: c.commentId })
        c.likeCount += 1
      }
      if (method === 'DELETE' && has) {
        db.commentLikes = db.commentLikes.filter((l) => !(l.userId === u.userId && l.commentId === c.commentId))
        c.likeCount = Math.max(0, c.likeCount - 1)
      }
      saveDb()
      return go({
        liked: db.commentLikes.some((l) => l.userId === u.userId && l.commentId === c.commentId),
        likeCount: c.likeCount,
      })
    }
  }
  {
    const m = match(p, '/api/v1/comments/:commentId')
    if (m && method === 'DELETE') {
      const u = need(me)
      db.comments = db.comments.filter((c) => !(c.commentId === m.commentId && c.author.userId === u.userId))
      saveDb()
      return go(undefined)
    }
  }

  if (match(p, '/api/v1/notifications') && method === 'GET') {
    const u = need(me)
    const group = q.get('group') || ''
    const items = db.notifications.filter(
      (n) => n.userId === u.userId && !n.hidden && (!group || notifyGroup(n.type) === group),
    )
    return go(pageOf(items, cursor, limit))
  }
  if (match(p, '/api/v1/notifications/read') && method === 'POST') {
    const u = need(me)
    const ids = (body.ids as string[]) || []
    db.notifications.forEach((n) => {
      if (n.userId !== u.userId) return
      if (body.all || ids.includes(n.id)) n.read = true
    })
    saveDb()
    return go(undefined)
  }
  if (match(p, '/api/v1/notifications/unread-count') && method === 'GET') {
    const u = need(me)
    const mine = db.notifications.filter((n) => n.userId === u.userId && !n.read && !n.hidden)
    const countOf = (g: string) => mine.filter((n) => notifyGroup(n.type) === g).length
    return go({
      count: mine.length,
      comment: countOf('comment'),
      reaction: countOf('reaction'),
      follow: countOf('follow'),
      review: countOf('review'),
    })
  }

  if (match(p, '/api/v1/conversations') && method === 'GET') {
    need(me)
    return go({ items: db.conversations })
  }
  if (match(p, '/api/v1/conversations') && method === 'POST') {
    const u = need(me)
    const id = openConv(u.userId, String(body.peerUserId || ''))
    return go({ conversationId: id })
  }
  if (match(p, '/api/v1/conversations/unread-count') && method === 'GET') {
    need(me)
    return go({ count: db.conversations.reduce((s, c) => s + c.unreadCount, 0) })
  }
  {
    const m = match(p, '/api/v1/conversations/:id/messages')
    if (m && method === 'GET') {
      const before = q.get('beforeId')
      let items = db.messages.filter((x) => x.conversationId === m.id && !x.hidden)
      if (before) {
        const i = items.findIndex((x) => x.messageId === before)
        items = i > 0 ? items.slice(0, i) : items
      }
      return go({ messages: items })
    }
    if (m && method === 'POST') {
      const u = need(me)
      const msgType = String(body.msgType || 'text')
      const content = (body.content as Record<string, string>) || {}
      if (msgType === 'image' && content.mediaId && db.blobs[content.mediaId]) {
        content.url = db.blobs[content.mediaId]
      }
      const msg = {
        conversationId: m.id,
        messageId: nid('im'),
        senderId: u.userId,
        msgType,
        content,
        createdAt: nowIso(),
      }
      db.messages.push(msg)
      touchConv(m.id, msg.messageId, content.text || '[图片]', msg.createdAt)
      emitIm({ op: 'message', conversationId: m.id, payload: msg })
      saveDb()
      return go(msg)
    }
  }

  if (match(p, '/api/v1/reports') && method === 'POST') {
    const u = need(me)
    const report = {
      reportId: nid('rp'),
      targetType: (body.targetType as 'post') || 'post',
      targetId: String(body.targetId || ''),
      reasonCode: String(body.reasonCode || 'other'),
      detail: String(body.detail || ''),
      status: 'pending' as const,
      reporter: brief(u),
      createdAt: nowIso(),
    }
    db.reports.unshift(report)
    saveDb()
    return go({ reportId: report.reportId })
  }
  if (match(p, '/api/v1/support') && method === 'POST') {
    const u = need(me)
    const ticket = {
      ticketId: nid('tk'),
      kind: (body.kind as 'feedback') || 'feedback',
      title: String(body.title || '帮助'),
      contact: String(body.contact || ''),
      body: String(body.body || ''),
      mediaUrls: (body.mediaUrls as string[]) || [],
      status: 'pending' as const,
      user: brief(u),
      handleNote: null,
      createdAt: nowIso(),
      handledAt: null,
    }
    db.tickets.unshift(ticket)
    saveDb()
    return go({ ticketId: ticket.ticketId })
  }
  if (match(p, '/api/v1/catalog/faq') && method === 'GET') return go(db.faqs.filter((f) => f.enabled))
  if (match(p, '/api/v1/catalog') && method === 'GET') {
    return go({
      categories: db.categories.filter((x) => x.enabled),
      campaigns: db.campaigns.filter((x) => x.enabled),
      topics: db.topics.filter((x) => x.enabled),
      notices: db.notices.filter((x) => x.enabled),
    })
  }
  if (match(p, '/api/v1/catalog/topics') && method === 'GET') {
    const s = q.get('q') || ''
    return go(db.topics.filter((t) => t.enabled && (!s || t.name.includes(s))))
  }

  if (p.startsWith('//api/v1/ops') || p.startsWith('/api/v1/ops')) {
    return go(handleOps(p, method, q, body, me, cursor, limit))
  }

  throw new ApiError(404, 'NOT_FOUND', `未实现 ${method} ${p}`)
}

function openConv(myId: string, peerId: string) {
  const db = getDb()
  const peer = db.users.find((u) => u.userId === peerId)
  if (!peer) throw new ApiError(404, 'NOT_FOUND', '用户不存在')
  const exist = db.conversations.find((c) => c.peer.userId === peerId)
  if (exist) return exist.conversationId
  const id = nid('cv')
  db.conversations.unshift({
    conversationId: id,
    peer: brief(peer),
    lastMessage: null,
    unreadCount: 0,
    updatedAt: nowIso(),
  })
  saveDb()
  return id
}

function touchConv(id: string, messageId: string, text: string, at: string) {
  const db = getDb()
  const c = db.conversations.find((x) => x.conversationId === id)
  if (!c) return
  c.lastMessage = { messageId, msgType: 'text', text, createdAt: at }
  c.updatedAt = at
}

function yehAnswer(question: string): AiChatResult {
  const db = getDb()
  const posts = db.posts.filter((p) => p.reviewStatus === 'published' && p.visibility === 'public').slice(0, 3)
  const citations = posts.map((p) => ({
    postId: p.postId,
    snippet: p.body.slice(0, 48),
    title: p.title,
    coverUrl: p.coverUrl,
    nickname: p.author.nickname,
    pageId: p.author.pageId,
  }))
  const answer = question.includes('吃')
    ? '想吃热的就按陈月那碗担担面来：辣椒油要热、花生粗砸，汤底用筒骨。分量只做一人份，吃完才不会后悔。'
    : question.includes('走') || question.includes('去哪')
      ? '傍晚去江边最合适，风会把衬衫吹起来。何山写过雨天的石板路，不想远行的话沿河散步也够。'
      : `关于「${question || '这一页'}」——先把眼前的事写清楚。社区里已经有人把晚霞、窗边的光和一碗面记下了，你可以点下面的来源看看。`
  return { answer, citations }
}

export async function demoChatStream(
  body: { question: string; sessionId?: string; imageUrls?: string[] },
  onDelta: (text: string) => void,
  onStage?: (stage: string, n: number) => void,
  signal?: AbortSignal,
): Promise<AiChatResult> {
  const stages = ['search', 'read', 'write']
  for (let i = 0; i < stages.length; i++) {
    if (signal?.aborted) return { answer: '', citations: [], stopped: true }
    onStage?.(stages[i], i + 1)
    await new Promise((r) => setTimeout(r, 220))
  }
  const result = yehAnswer(body.question)
  for (const ch of result.answer) {
    if (signal?.aborted) return { answer: result.answer, citations: result.citations, stopped: true }
    onDelta(ch)
    await new Promise((r) => setTimeout(r, 12))
  }
  return result
}

export async function demoPutObject(uploadUrl: string, body: Blob, _mime: string) {
  const id = uploadUrl.split('/').pop() || nid('m')
  getDb().blobs[id] = URL.createObjectURL(body)
}

function handleOps(
  p: string,
  method: string,
  q: URLSearchParams,
  body: Record<string, unknown>,
  me: Account | null,
  cursor?: string,
  limit = 20,
) {
  const db = getDb()
  const u = staff(me)
  if (match(p, '/api/v1/ops/me') && method === 'GET') return brief(u)
  if (match(p, '/api/v1/ops/overview') && method === 'GET') {
    return {
      communityUsers: db.users.length,
      usersToday: 2,
      postsLive: db.posts.filter((x) => x.reviewStatus === 'published' && !x.takenDown).length,
      postsDown: db.posts.filter((x) => x.takenDown).length,
      postsPending: db.posts.filter((x) => x.reviewStatus === 'pending').length,
      postsRejected: db.posts.filter((x) => x.reviewStatus === 'rejected').length,
      postsToday: 3,
      comments: db.comments.filter((c) => !c.hidden).length,
      commentsToday: 2,
      reportsPending: db.reports.filter((r) => r.status === 'pending').length,
      reportsDone: db.reports.filter((r) => r.status === 'done').length,
      reportsToday: 1,
      supportPending: db.tickets.filter((t) => t.status === 'pending').length,
      categories: db.categories.length,
      campaigns: db.campaigns.length,
      topics: db.topics.length,
    }
  }
  if (match(p, '/api/v1/ops/ai') && method === 'GET') return db.ai
  if (match(p, '/api/v1/ops/ai') && method === 'POST') {
    Object.assign(db.ai, body)
    if (body.llmApiKey) {
      db.ai.llmKeySet = true
      db.ai.llmKeyMasked = `sk-••••${String(body.llmApiKey).slice(-4)}`
    }
    if (body.clearLlmKey) {
      db.ai.llmKeySet = false
      db.ai.llmKeyMasked = ''
    }
    if (body.visionLlmApiKey) {
      db.ai.visionLlmKeySet = true
      db.ai.visionLlmKeyMasked = `sk-••••${String(body.visionLlmApiKey).slice(-4)}`
    }
    saveDb()
    return db.ai as OpsAiConfig
  }
  if (match(p, '/api/v1/ops/ai/reset') && method === 'POST') {
    saveDb()
    return db.ai
  }
  if (match(p, '/api/v1/ops/ai/probe') && method === 'POST') return { ok: true, message: '已连通' }
  if (match(p, '/api/v1/ops/ai/vision/probe') && method === 'POST') return { ok: true, message: '已连通' }
  if (match(p, '/api/v1/ops/ai/review/try') && method === 'POST') {
    return { verdict: 'pass', reason: '未见明显敏感内容', score: 8, labels: ['normal'] }
  }
  if (match(p, '/api/v1/ops/policy') && method === 'GET') return db.policy
  if (match(p, '/api/v1/ops/policy') && method === 'POST') {
    db.policy = body as typeof db.policy
    saveDb()
    return db.policy
  }
  if (match(p, '/api/v1/ops/reports') && method === 'GET') {
    const status = q.get('status')
    const items = db.reports.filter((r) => !status || r.status === status)
    return pageOf(items, cursor, limit)
  }
  {
    const m = match(p, '/api/v1/ops/reports/:id')
    if (m && method === 'PATCH') {
      const r = db.reports.find((x) => x.reportId === m.id)
      if (!r) throw new ApiError(404, 'NOT_FOUND', '工单不存在')
      r.status = 'done'
      r.handler = brief(u)
      r.handleAction = String(body.action || '')
      r.handleNote = String(body.note || '')
      r.handledAt = nowIso()
      if (body.action === 'takedown') {
        const post = db.posts.find((x) => x.postId === r.targetId)
        if (post) post.takenDown = true
      }
      if (body.action === 'ban') {
        const target = db.users.find((x) => x.userId === r.authorId)
        if (target) target.frozen = true
      }
      saveDb()
      return { reportId: r.reportId, status: r.status }
    }
  }
  if (match(p, '/api/v1/ops/posts') && method === 'GET') {
    const taken = q.get('taken')
    const s = q.get('q') || ''
    let items = db.posts.filter((x) => !s || x.title.includes(s) || x.postId.includes(s))
    if (taken === 'live') items = items.filter((x) => !x.takenDown && x.reviewStatus === 'published')
    if (taken === 'down') items = items.filter((x) => x.takenDown)
    if (taken === 'pending') items = items.filter((x) => x.reviewStatus === 'pending')
    if (taken === 'rejected') items = items.filter((x) => x.reviewStatus === 'rejected')
    return pageOf(
      items.map((x) => ({
        postId: x.postId,
        author: x.author,
        title: x.title,
        postType: x.postType,
        category: x.category,
        takenDown: x.takenDown,
        publishedAt: x.publishedAt,
        reviewStatus: x.reviewStatus,
        rejectReason: x.rejectReason,
        aiReview: x.aiReview,
        aiReviewReason: x.aiReviewReason,
        aiReviewScore: x.aiReviewScore,
        aiReviewLabels: x.aiReviewLabels,
      })),
      cursor,
      limit,
    )
  }
  {
    const m = match(p, '/api/v1/ops/posts/:id/takedown')
    if (m && method === 'POST') {
      const row = db.posts.find((x) => x.postId === m.id)
      if (!row) throw new ApiError(404, 'NOT_FOUND', '笔记不存在')
      row.takenDown = true
      saveDb()
      return opsPostView(row)
    }
  }
  {
    const m = match(p, '/api/v1/ops/posts/:id/restore')
    if (m && method === 'POST') {
      const row = db.posts.find((x) => x.postId === m.id)
      if (!row) throw new ApiError(404, 'NOT_FOUND', '笔记不存在')
      row.takenDown = false
      saveDb()
      return opsPostView(row)
    }
  }
  {
    const m = match(p, '/api/v1/ops/posts/:id/approve')
    if (m && method === 'POST') {
      const row = db.posts.find((x) => x.postId === m.id)
      if (!row) throw new ApiError(404, 'NOT_FOUND', '笔记不存在')
      row.reviewStatus = 'published'
      row.rejectReason = null
      saveDb()
      return opsPostView(row)
    }
  }
  {
    const m = match(p, '/api/v1/ops/posts/:id/reject')
    if (m && method === 'POST') {
      const row = db.posts.find((x) => x.postId === m.id)
      if (!row) throw new ApiError(404, 'NOT_FOUND', '笔记不存在')
      row.reviewStatus = 'rejected'
      row.rejectReason = q.get('reason') || '未通过审核'
      saveDb()
      return opsPostView(row)
    }
  }
  {
    const m = match(p, '/api/v1/ops/posts/:id')
    if (m && method === 'GET') {
      const row = db.posts.find((x) => x.postId === m.id)
      if (!row) throw new ApiError(404, 'NOT_FOUND', '笔记不存在')
      return opsPostView(row)
    }
  }
  {
    const m = match(p, '/api/v1/ops/lives/:id')
    if (m && method === 'GET') {
      const live = db.lives.find((x) => x.sessionId === m.id)
      if (!live) throw new ApiError(404, 'NOT_FOUND', '直播不存在')
      return {
        sessionId: live.sessionId,
        roomId: live.roomId,
        title: live.title,
        status: live.status,
        coverUrl: live.coverUrl,
        notice: live.notice,
        owner: live.owner,
        ended: live.ended,
      }
    }
  }
  if (match(p, '/api/v1/ops/comments') && method === 'GET') {
    const hidden = q.get('hidden')
    const s = q.get('q') || ''
    let items = db.comments.filter((c) => !s || c.content.includes(s))
    if (hidden === 'yes') items = items.filter((c) => c.hidden)
    if (hidden === 'no') items = items.filter((c) => !c.hidden)
    return pageOf(
      items.map((c) => ({
        commentId: c.commentId,
        postId: c.postId,
        author: c.author,
        content: c.content,
        hidden: c.hidden,
        createdAt: c.createdAt,
      })),
      cursor,
      limit,
    )
  }
  {
    const m = match(p, '/api/v1/ops/comments/:id/hide')
    if (m && method === 'POST') {
      const c = db.comments.find((x) => x.commentId === m.id)
      if (c) c.hidden = true
      saveDb()
      return undefined
    }
  }
  {
    const m = match(p, '/api/v1/ops/comments/:id/restore')
    if (m && method === 'POST') {
      const c = db.comments.find((x) => x.commentId === m.id)
      if (c) c.hidden = false
      saveDb()
      return undefined
    }
  }
  if (match(p, '/api/v1/ops/catalog') && method === 'GET') {
    return { categories: db.categories, campaigns: db.campaigns, topics: db.topics, notices: db.notices }
  }
  if (match(p, '/api/v1/ops/categories') && method === 'POST') {
    const row = {
      categoryId: String(body.name || nid('cat')),
      name: String(body.name || ''),
      sortOrder: Number(body.sortOrder || db.categories.length + 1),
      enabled: body.enabled !== false,
    }
    db.categories.push(row)
    saveDb()
    return row
  }
  {
    const m = match(p, '/api/v1/ops/categories/:id/enabled')
    if (m && method === 'POST') {
      const row = db.categories.find((x) => x.categoryId === m.id)
      if (row) row.enabled = q.get('value') === 'true'
      saveDb()
      return undefined
    }
  }
  {
    const m = match(p, '/api/v1/ops/categories/:id')
    if (m && method === 'PATCH') {
      const row = db.categories.find((x) => x.categoryId === m.id)
      if (!row) throw new ApiError(404, 'NOT_FOUND', '分类不存在')
      Object.assign(row, body)
      saveDb()
      return row
    }
  }
  if (match(p, '/api/v1/ops/campaigns') && method === 'POST') {
    const row = {
      eventId: String(body.eventId || nid('ev')),
      title: String(body.title || ''),
      intro: String(body.intro || ''),
      coverUrl: String(body.coverUrl || coverUrl(String(body.title || '活动'), String(body.title || ''))),
      status: String(body.status || 'online'),
      sortOrder: Number(body.sortOrder || 0),
      enabled: body.enabled !== false,
    }
    db.campaigns.push(row)
    saveDb()
    return row
  }
  {
    const m = match(p, '/api/v1/ops/campaigns/:id/enabled')
    if (m && method === 'POST') {
      const row = db.campaigns.find((x) => x.eventId === m.id)
      if (row) row.enabled = q.get('value') === 'true'
      saveDb()
      return undefined
    }
  }
  {
    const m = match(p, '/api/v1/ops/campaigns/:id')
    if (m && method === 'PATCH') {
      const row = db.campaigns.find((x) => x.eventId === m.id)
      if (!row) throw new ApiError(404, 'NOT_FOUND', '活动不存在')
      Object.assign(row, body)
      saveDb()
      return row
    }
  }
  if (match(p, '/api/v1/ops/topics') && method === 'POST') {
    const row = {
      topicId: nid('tp'),
      name: String(body.name || ''),
      official: Boolean(body.official),
      sortOrder: Number(body.sortOrder || 0),
      enabled: body.enabled !== false,
      postCount: 0,
    }
    db.topics.push(row)
    saveDb()
    return row
  }
  {
    const m = match(p, '/api/v1/ops/topics/:id/enabled')
    if (m && method === 'POST') {
      const row = db.topics.find((x) => x.topicId === m.id)
      if (row) row.enabled = q.get('value') === 'true'
      saveDb()
      return undefined
    }
  }
  {
    const m = match(p, '/api/v1/ops/topics/:id')
    if (m && method === 'PATCH') {
      const row = db.topics.find((x) => x.topicId === m.id)
      if (!row) throw new ApiError(404, 'NOT_FOUND', '话题不存在')
      Object.assign(row, body)
      saveDb()
      return row
    }
  }
  if (match(p, '/api/v1/ops/notices') && method === 'POST') {
    const row = {
      noticeId: nid('nt'),
      title: String(body.title || ''),
      body: String(body.body || ''),
      sortOrder: Number(body.sortOrder || 0),
      enabled: body.enabled !== false,
    }
    db.notices.push(row)
    saveDb()
    return row
  }
  {
    const m = match(p, '/api/v1/ops/notices/:id/enabled')
    if (m && method === 'POST') {
      const row = db.notices.find((x) => x.noticeId === m.id)
      if (row) row.enabled = q.get('value') === 'true'
      saveDb()
      return undefined
    }
  }
  {
    const m = match(p, '/api/v1/ops/notices/:id')
    if (m && method === 'PATCH') {
      const row = db.notices.find((x) => x.noticeId === m.id)
      if (!row) throw new ApiError(404, 'NOT_FOUND', '公告不存在')
      Object.assign(row, body)
      saveDb()
      return row
    }
  }
  if (match(p, '/api/v1/ops/support') && method === 'GET') {
    const kind = q.get('kind')
    const status = q.get('status')
    const items = db.tickets.filter((t) => (!kind || t.kind === kind) && (!status || t.status === status))
    return pageOf(items, cursor, limit)
  }
  {
    const m = match(p, '/api/v1/ops/support/:id')
    if (m && method === 'PATCH') {
      const t = db.tickets.find((x) => x.ticketId === m.id)
      if (!t) throw new ApiError(404, 'NOT_FOUND', '申请不存在')
      t.status = 'done'
      t.handler = brief(u)
      t.handleNote = String(body.note || '')
      t.handledAt = nowIso()
      saveDb()
      return { reportId: t.ticketId, status: t.status }
    }
  }
  if (match(p, '/api/v1/ops/faqs') && method === 'GET') return db.faqs
  if (match(p, '/api/v1/ops/faqs') && method === 'POST') {
    const g = String(body.groupId || 'faq')
    const titles: Record<string, string> = {
      account: '用户账号管理',
      create: '创作功能',
      data: '数据中心',
      faq: '常见问题',
    }
    const row = {
      faqId: nid('f'),
      groupId: g,
      groupTitle: titles[g] || g,
      question: String(body.question || ''),
      answer: String(body.answer || ''),
      sortOrder: Number(body.sortOrder || 0),
      enabled: body.enabled !== false,
    }
    db.faqs.push(row)
    saveDb()
    return row
  }
  {
    const m = match(p, '/api/v1/ops/faqs/:id/enabled')
    if (m && method === 'POST') {
      const row = db.faqs.find((x) => x.faqId === m.id)
      if (row) row.enabled = q.get('value') === 'true'
      saveDb()
      return undefined
    }
  }
  {
    const m = match(p, '/api/v1/ops/faqs/:id')
    if (m && method === 'PATCH') {
      const row = db.faqs.find((x) => x.faqId === m.id)
      if (!row) throw new ApiError(404, 'NOT_FOUND', '词条不存在')
      Object.assign(row, body)
      saveDb()
      return row
    }
  }
  if (match(p, '/api/v1/ops/users') && method === 'GET') {
    const s = q.get('q') || ''
    const items = db.users
      .filter((x) => !s || x.nickname.includes(s) || x.pageId.includes(s) || x.phone.includes(s))
      .map((x) => ({
        userId: x.userId,
        pageId: x.pageId,
        nickname: x.nickname,
        avatarUrl: x.avatarUrl,
        liveHost: x.liveHost,
        verified: x.verified,
        official: x.official,
        frozen: x.frozen,
      }))
    return pageOf(items, cursor, limit)
  }
  {
    const m = match(p, '/api/v1/ops/users/:id/freeze')
    if (m && method === 'POST') {
      const row = db.users.find((x) => x.userId === m.id)
      if (!row) throw new ApiError(404, 'NOT_FOUND', '用户不存在')
      row.frozen = true
      saveDb()
      return opsUser(row)
    }
  }
  {
    const m = match(p, '/api/v1/ops/users/:id/unfreeze')
    if (m && method === 'POST') {
      const row = db.users.find((x) => x.userId === m.id)
      if (!row) throw new ApiError(404, 'NOT_FOUND', '用户不存在')
      row.frozen = false
      saveDb()
      return opsUser(row)
    }
  }
  {
    const m = match(p, '/api/v1/ops/users/:id/badges')
    if (m && method === 'POST') {
      const row = db.users.find((x) => x.userId === m.id)
      if (!row) throw new ApiError(404, 'NOT_FOUND', '用户不存在')
      if (typeof body.liveHost === 'boolean') row.liveHost = body.liveHost
      if (typeof body.verified === 'boolean') row.verified = body.verified
      if (typeof body.official === 'boolean') row.official = body.official
      saveDb()
      return opsUser(row)
    }
  }
  {
    const m = match(p, '/api/v1/ops/users/:id/following/:peerId/remove')
    if (m && method === 'POST') {
      db.follows = db.follows.filter((f) => !(f.followerId === m.id && f.followeeId === m.peerId))
      saveDb()
      return undefined
    }
  }
  {
    const m = match(p, '/api/v1/ops/users/:id/followers/:peerId/remove')
    if (m && method === 'POST') {
      db.follows = db.follows.filter((f) => !(f.followeeId === m.id && f.followerId === m.peerId))
      saveDb()
      return undefined
    }
  }
  {
    const m = match(p, '/api/v1/ops/users/:id/following')
    if (m && method === 'GET') {
      const items = db.follows
        .filter((f) => f.followerId === m.id)
        .map((f) => db.users.find((x) => x.userId === f.followeeId))
        .filter((x): x is Account => Boolean(x))
        .map((x) => brief(x))
      return pageOf(items, cursor, limit)
    }
  }
  {
    const m = match(p, '/api/v1/ops/users/:id/followers')
    if (m && method === 'GET') {
      const items = db.follows
        .filter((f) => f.followeeId === m.id)
        .map((f) => db.users.find((x) => x.userId === f.followerId))
        .filter((x): x is Account => Boolean(x))
        .map((x) => brief(x))
      return pageOf(items, cursor, limit)
    }
  }
  {
    const m = match(p, '/api/v1/ops/users/:id/notifications')
    if (m && method === 'GET') {
      const group = q.get('group') || ''
      const items = db.notifications.filter(
        (n) => n.userId === m.id && (!group || notifyGroup(n.type) === group),
      )
      return pageOf(items, cursor, limit)
    }
  }
  {
    const m = match(p, '/api/v1/ops/users/:id')
    if (m && method === 'GET') {
      const row = db.users.find((x) => x.userId === m.id)
      if (!row) throw new ApiError(404, 'NOT_FOUND', '用户不存在')
      return opsUser(row)
    }
  }
  {
    const m = match(p, '/api/v1/ops/notifications/:id/hide')
    if (m && method === 'POST') {
      const n = db.notifications.find((x) => x.id === m.id)
      if (n) n.hidden = true
      saveDb()
      return undefined
    }
  }
  if (match(p, '/api/v1/ops/media') && method === 'GET') {
    const hidden = q.get('hidden')
    let items = db.media
    if (hidden === 'yes') items = items.filter((x) => x.hidden)
    if (hidden === 'no') items = items.filter((x) => !x.hidden)
    return pageOf(items, cursor, limit)
  }
  {
    const m = match(p, '/api/v1/ops/media/:id/hide')
    if (m && method === 'POST') {
      const row = db.media.find((x) => x.mediaId === m.id)
      if (row) row.hidden = true
      saveDb()
      return undefined
    }
  }
  {
    const m = match(p, '/api/v1/ops/media/:id/restore')
    if (m && method === 'POST') {
      const row = db.media.find((x) => x.mediaId === m.id)
      if (row) row.hidden = false
      saveDb()
      return undefined
    }
  }
  if (match(p, '/api/v1/ops/conversations') && method === 'GET') {
    return pageOf(db.opsConversations, cursor, limit)
  }
  {
    const m = match(p, '/api/v1/ops/conversations/:id/messages')
    if (m && method === 'GET') {
      const hidden = q.get('hidden')
      let items = db.opsMessages.filter((x) => x.conversationId === m.id)
      if (hidden === 'yes') items = items.filter((x) => x.hidden)
      if (hidden === 'no') items = items.filter((x) => !x.hidden)
      return pageOf(items, cursor, limit)
    }
  }
  {
    const m = match(p, '/api/v1/ops/messages/:id/hide')
    if (m && method === 'POST') {
      const row = db.opsMessages.find((x) => x.messageId === m.id)
      if (row) row.hidden = true
      const im = db.messages.find((x) => x.messageId === m.id)
      if (im) im.hidden = true
      saveDb()
      return undefined
    }
  }
  {
    const m = match(p, '/api/v1/ops/messages/:id/restore')
    if (m && method === 'POST') {
      const row = db.opsMessages.find((x) => x.messageId === m.id)
      if (row) row.hidden = false
      const im = db.messages.find((x) => x.messageId === m.id)
      if (im) im.hidden = false
      saveDb()
      return undefined
    }
  }
  throw new ApiError(404, 'NOT_FOUND', `未实现 ${method} ${p}`)
}

function opsPostView(x: PostRow) {
  return {
    postId: x.postId,
    author: x.author,
    title: x.title,
    body: x.body,
    coverUrl: x.coverUrl,
    postType: x.postType,
    category: x.category,
    original: x.original,
    contentKind: x.contentKind,
    eventTitle: x.eventTitle,
    visibility: x.visibility,
    media: x.media,
    takenDown: x.takenDown,
    publishedAt: x.publishedAt,
    reviewStatus: x.reviewStatus,
    rejectReason: x.rejectReason,
    aiReview: x.aiReview,
    aiReviewReason: x.aiReviewReason,
    aiReviewScore: x.aiReviewScore,
    aiReviewLabels: x.aiReviewLabels,
  }
}

function opsUser(x: Account) {
  return {
    userId: x.userId,
    pageId: x.pageId,
    nickname: x.nickname,
    avatarUrl: x.avatarUrl,
    liveHost: x.liveHost,
    verified: x.verified,
    official: x.official,
    frozen: x.frozen,
  }
}
