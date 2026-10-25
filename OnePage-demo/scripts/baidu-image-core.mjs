/** 百度图片搜索 acjson（服务端调用，避免浏览器 CORS） */

export function buildImageQuery(title, body, category) {
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

function hash(s) {
  let n = 0
  for (let i = 0; i < s.length; i++) n = (n * 31 + s.charCodeAt(i)) | 0
  return Math.abs(n)
}

function normalizeUrl(raw) {
  let u = String(raw).replace(/\\u002F/g, '/').replace(/\\\//g, '/')
  if (u.startsWith('http://')) u = `https://${u.slice(7)}`
  return u
}

export async function searchBaiduImageUrls(word, pn = 0) {
  const w = encodeURIComponent(word)
  const url = `https://image.baidu.com/search/acjson?tn=resultjson_com&ipn=rj&ct=201326592&fp=result&word=${w}&queryWord=${w}&pn=${pn}&rn=16`
  const r = await fetch(url, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0',
      Referer: 'https://image.baidu.com/',
    },
  })
  const text = await r.text()
  const urls = []
  for (const m of text.matchAll(/"thumbURL":"([^"]+)"/g)) urls.push(normalizeUrl(m[1]))
  for (const m of text.matchAll(/"middleURL":"([^"]+)"/g)) urls.push(normalizeUrl(m[1]))
  const seen = new Set()
  return urls.filter((u) => {
    if (!u.startsWith('https://') || seen.has(u)) return false
    seen.add(u)
    return true
  })
}

export async function pickBaiduImage(word, salt, pn = 0) {
  const urls = await searchBaiduImageUrls(word, pn)
  if (!urls.length) return ''
  const idx = hash(`${salt}:${word}`) % urls.length
  const chosen = urls[idx]
  try {
    const head = await fetch(chosen, { method: 'HEAD' })
    const len = Number(head.headers.get('content-length') || 0)
    if (head.ok && len > 6000) return chosen
  } catch {
    /* ignore */
  }
  try {
    const g = await fetch(chosen)
    const buf = Buffer.from(await g.arrayBuffer())
    if (g.ok && buf.length > 6000 && buf.length !== 3802) return chosen
  } catch {
    /* ignore */
  }
  return urls[(idx + 1) % urls.length] || chosen
}

export function safeMediaFileKey(key) {
  return String(key).replace(/[^a-zA-Z0-9_-]/g, '_')
}

const FETCH_HEADERS = {
  'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0',
  Referer: 'https://image.baidu.com/',
}

function looksLikeImage(buf) {
  if (buf.length < 6000 || buf.length === 3802) return false
  if (buf[0] === 0xff && buf[1] === 0xd8) return true
  if (buf[0] === 0x89 && buf[1] === 0x50) return true
  if (buf[0] === 0x47 && buf[1] === 0x49) return true
  if (buf[0] === 0x52 && buf[1] === 0x49) return true
  return buf.length > 12000
}

/** 下载远程图到本地；成功返回 true */
export async function downloadImageToFile(remoteUrl, destPath) {
  try {
    const r = await fetch(remoteUrl, { headers: FETCH_HEADERS })
    const buf = Buffer.from(await r.arrayBuffer())
    if (!r.ok || !looksLikeImage(buf)) return false
    const { mkdirSync, writeFileSync } = await import('node:fs')
    const { dirname } = await import('node:path')
    mkdirSync(dirname(destPath), { recursive: true })
    writeFileSync(destPath, buf)
    return true
  } catch {
    return false
  }
}

/** 按关键词搜图并落盘，返回站点路径 `/media/covers/xxx.jpg` */
export async function fetchLocalCover(word, key, coversDir) {
  const pn = Math.abs(key.length) % 3
  const urls = await searchBaiduImageUrls(word, pn)
  if (!urls.length) return ''
  const file = `${safeMediaFileKey(key)}.jpg`
  const dest = `${coversDir}/${file}`
  const start = hash(`${key}:${word}`) % urls.length
  for (let i = 0; i < Math.min(urls.length, 8); i++) {
    const url = urls[(start + i) % urls.length]
    if (await downloadImageToFile(url, dest)) return `/media/covers/${file}`
  }
  const fallback = await pickBaiduImage(word, key, pn)
  if (fallback && await downloadImageToFile(fallback, dest)) return `/media/covers/${file}`
  return ''
}
