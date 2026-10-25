import { resolveMediaCategory } from './media'

export function buildImageQuery(title: string, body: string, category: string) {
  const cat = String(category || '生活')
    .replace(/生活分享/g, '生活')
    .replace(/才艺直播|兴趣爱好|科技新闻|运动户外/g, '')
    .trim() || '生活'
  const t = String(title || '')
    .replace(/[^\u4e00-\u9fa5a-zA-Z0-9]/g, '')
    .slice(0, 14)
  const b = String(body || '')
    .replace(/[^\u4e00-\u9fa5]/g, '')
    .slice(0, 10)
  const q = `${cat} ${t} ${b}`.trim().replace(/\s+/g, ' ').slice(0, 28)
  return q || cat
}

/** 开发环境经 Vite 代理调百度 acjson；静态部署依赖预生成的 media-index.json */
export async function fetchCoverByText(title: string, body: string, category: string, salt: string) {
  const word = buildImageQuery(title, body, category)
  const pick = resolveMediaCategory(title, body, category)
  const pn = Math.abs(salt.length + pick.length) % 3
  const q = new URLSearchParams({ word, salt, pn: String(pn) })
  try {
    const r = await fetch(`/demo-api/baidu-image?${q}`)
    if (!r.ok) return ''
    const j = (await r.json()) as { url?: string }
    return j.url || ''
  } catch {
    return ''
  }
}
