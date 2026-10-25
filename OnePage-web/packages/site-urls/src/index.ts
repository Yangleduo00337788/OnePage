/** 侧栏 / 底栏版权与源码仓库（三端 SiteFooter 共用） */
export const PROJECT_COPYRIGHT_LINE = 'Copyright © 2026 Yangleduo. All Rights Reserved.'
export const PROJECT_GITHUB_URL = 'https://github.com/Yangleduo00337788/OnePage'
export const PROJECT_GITEE_URL = 'https://gitee.com/yangleduo7788/one-page'

/** 多站点跳转：开发用完整 origin，Cloudflare 单域用路径前缀。 */
function abs(origin: string) {
  if (!origin) return window.location.origin
  if (origin.startsWith('http')) return origin.replace(/\/$/, '')
  const path = origin.startsWith('/') ? origin : `/${origin}`
  return `${window.location.origin}${path}`.replace(/\/$/, '')
}

export function communityOrigin() {
  return abs(import.meta.env.VITE_COMMUNITY_ORIGIN || '')
}

export function creatorOrigin() {
  return abs(import.meta.env.VITE_CREATOR_ORIGIN || 'http://localhost:5175')
}

export function opsOrigin() {
  const raw = import.meta.env.VITE_OPS_ORIGIN || ''
  if (!raw) return ''
  return abs(raw)
}

/** 新标签打开创作者站；失败时不跳转当前页（避免单域演示站把社区壳顶掉） */
export function openCreatorSite(): boolean {
  const url = creatorOrigin()
  const tab = window.open(url, '_blank', 'noopener,noreferrer')
  return tab != null
}

export function openOpsSite() {
  const url = opsOrigin()
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

export function showOpsEntry() {
  return Boolean(import.meta.env.VITE_OPS_ORIGIN)
}
