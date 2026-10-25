import fs from 'node:fs'
import path from 'node:path'
import { createReadStream } from 'node:fs'
import { fileURLToPath } from 'node:url'
import type { Plugin } from 'vite'
import { pickBaiduImage } from '../scripts/baidu-image-core.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const demoRoot = path.resolve(here, '..')
const demoMediaRoot = path.join(demoRoot, 'public/media')
const bootstrap = path.resolve(demoRoot, 'src/bootstrap.ts').replace(/\\/g, '/')

function copyDir(from: string, to: string) {
  if (!fs.existsSync(from)) return
  fs.mkdirSync(to, { recursive: true })
  for (const name of fs.readdirSync(from)) {
    const src = path.join(from, name)
    const dest = path.join(to, name)
    if (fs.statSync(src).isDirectory()) copyDir(src, dest)
    else fs.copyFileSync(src, dest)
  }
}

const HINTS: [string, string][] = [
  ['验证码已发送，开发环境填 888888', '验证码已发送'],
  ['仅已用该手机号开过户的账号可重置。开发环境验证码 888888', '仅已用该手机号开过户的账号可重置。'],
  ['开发环境验证码固定 888888', ''],
  ['请确认网关已启动后重试', '请再试一次'],
]

export function sitePlugins(): Plugin[] {
  return [
    {
      name: 'onepage-site-entry',
      transform(code, id) {
        const norm = id.replace(/\\/g, '/')
        const isMain =
          /\/apps\/shell\/src\/main\.ts$/.test(norm) ||
          /\/apps\/app-creator\/src\/main\.ts$/.test(norm) ||
          /\/apps\/app-ops\/src\/main\.ts$/.test(norm)
        if (!isMain) return null
        return `import ${JSON.stringify(bootstrap)}\n${code}`
      },
    },
    {
      name: 'onepage-site-copy',
      transform(code, id) {
        const norm = id.replace(/\\/g, '/')
        if (!norm.includes('/OnePage-web/') || !/\.(vue|ts)$/.test(norm)) return null
        let next = code
        for (const [from, to] of HINTS) next = next.split(from).join(to)
        next = next
          .split('window.location.assign(`/api/v1/auth/oauth/${provider}/authorize?intent=bind`)')
          .join('window.__onepageOauth?.(provider, "bind")')
          .split("window.location.assign(`/api/v1/auth/oauth/${provider}/authorize`)")
          .join('window.__onepageOauth?.(provider)')
        return next === code ? null : next
      },
    },
    {
      name: 'onepage-site-referrer',
      transformIndexHtml(html) {
        if (/name=["']referrer["']/i.test(html)) return html
        return html.replace(/<head(\s[^>]*)?>/i, (tag) => `${tag}\n    <meta name="referrer" content="no-referrer" />`)
      },
    },
    {
      name: 'onepage-site-local-media',
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          const raw = (req.url || '').split('?')[0]
          if (!raw.startsWith('/media/')) {
            next()
            return
          }
          const rel = raw.replace(/^\/media\/?/, '')
          const file = path.join(demoMediaRoot, rel)
          if (!file.startsWith(demoMediaRoot) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
            next()
            return
          }
          const ext = path.extname(file).toLowerCase()
          const type =
            ext === '.png'
              ? 'image/png'
              : ext === '.webp'
                ? 'image/webp'
                : ext === '.gif'
                  ? 'image/gif'
                  : 'image/jpeg'
          res.setHeader('Content-Type', type)
          res.setHeader('Cache-Control', 'public, max-age=31536000, immutable')
          createReadStream(file).pipe(res)
        })
      },
      closeBundle() {
        if (!process.env.ONPAGE_SITE_BUILD) return
        const out = process.env.ONPAGE_VITE_OUT_DIR
        if (!out || !fs.existsSync(demoMediaRoot)) return
        copyDir(demoMediaRoot, path.join(out, 'media'))
      },
      configResolved(config) {
        process.env.ONPAGE_VITE_OUT_DIR = config.build.outDir
      },
    },
    {
      name: 'onepage-site-baidu-image',
      configureServer(server) {
        server.middlewares.use(async (req, res, next) => {
          const raw = req.url || ''
          if (!raw.startsWith('/demo-api/baidu-image')) {
            next()
            return
          }
          const u = new URL(raw, 'http://127.0.0.1')
          const word = u.searchParams.get('word') || ''
          const salt = u.searchParams.get('salt') || word
          const pn = Number(u.searchParams.get('pn') || 0)
          try {
            const url = word ? await pickBaiduImage(word, salt, pn) : ''
            res.setHeader('Content-Type', 'application/json; charset=utf-8')
            res.end(JSON.stringify({ url }))
          } catch {
            res.statusCode = 502
            res.end(JSON.stringify({ url: '' }))
          }
        })
      },
    },
    {
      name: 'onepage-site-oauth',
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          const url = req.url || ''
          if (!url.includes('/api/v1/auth/oauth/')) {
            next()
            return
          }
          const bind = url.includes('intent=bind')
          res.statusCode = 302
          res.setHeader('Location', bind ? '/?oauth=bind_ok' : '/')
          res.end()
        })
      },
    },
  ]
}
