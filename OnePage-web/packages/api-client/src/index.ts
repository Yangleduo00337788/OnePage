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

export type UserProfile = UserSummary & {
  coverUrl: string
  bio: string
  gender: number
  stats: {
    followingCount: number
    followerCount: number
    likeReceivedCount: number
    favoriteReceivedCount: number
  }
}

/** 业务/HTTP 失败。组件 catch 后用 err.message 展示，用 err.code 分支。 */
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
}
