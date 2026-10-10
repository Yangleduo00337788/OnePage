import MarkdownIt from 'markdown-it'
import markdownItAnchor from 'markdown-it-anchor'
import markdownItCjkFriendly from 'markdown-it-cjk-friendly'
import markdownItContainer from 'markdown-it-container'
import markdownItFootnote from 'markdown-it-footnote'
import MarkdownItGitHubAlerts from 'markdown-it-github-alerts'
import { tasklist } from '@mdit/plugin-tasklist'
import { createHighlighterCore } from 'shiki/core'
import { createJavaScriptRegexEngine } from 'shiki/engine/javascript'
import bash from '@shikijs/langs/bash'
import css from '@shikijs/langs/css'
import html from '@shikijs/langs/html'
import java from '@shikijs/langs/java'
import javascript from '@shikijs/langs/javascript'
import json from '@shikijs/langs/json'
import markdown from '@shikijs/langs/markdown'
import python from '@shikijs/langs/python'
import sql from '@shikijs/langs/sql'
import tsx from '@shikijs/langs/tsx'
import typescript from '@shikijs/langs/typescript'
import vue from '@shikijs/langs/vue'
import xml from '@shikijs/langs/xml'
import yaml from '@shikijs/langs/yaml'
import githubDark from '@shikijs/themes/github-dark'
import githubLight from '@shikijs/themes/github-light'

export interface TocItem {
  id: string
  title: string
  level: number
}

const langLoaders = {
  bash,
  css,
  html,
  java,
  javascript,
  js: javascript,
  json,
  markdown,
  md: markdown,
  python,
  py: python,
  sql,
  tsx,
  typescript,
  ts: typescript,
  vue,
  xml,
  yaml,
  yml: yaml,
} as const

type SupportedLang = keyof typeof langLoaders
type Highlighter = Awaited<ReturnType<typeof createHighlighterCore>>

let highlighterPromise: Promise<Highlighter> | null = null

function getHighlighter() {
  highlighterPromise ??= createHighlighterCore({
    themes: [githubLight, githubDark],
    langs: [
      bash,
      css,
      html,
      java,
      javascript,
      json,
      markdown,
      python,
      sql,
      tsx,
      typescript,
      vue,
      xml,
      yaml,
    ],
    engine: createJavaScriptRegexEngine(),
  })
  return highlighterPromise
}

function slugify(text: string): string {
  return text
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, '-')
    .replace(/^-+|-+$/g, '')
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
}

function resolveLang(lang: string): string {
  const resolved = lang in langLoaders ? (lang as SupportedLang) : ''
  if (!resolved) return ''
  if (resolved === 'js') return 'javascript'
  if (resolved === 'ts') return 'typescript'
  if (resolved === 'py') return 'python'
  if (resolved === 'yml') return 'yaml'
  if (resolved === 'md') return 'markdown'
  return resolved
}

export function joinPath(dir: string, rel: string): string {
  const parts = [...(dir ? dir.split('/') : []), ...rel.split('/')]
  const stack: string[] = []
  for (const part of parts) {
    if (!part || part === '.') continue
    if (part === '..') stack.pop()
    else stack.push(part)
  }
  return stack.join('/')
}

export function resolveDocHref(href: string, dir: string): string | undefined {
  const hashIndex = href.indexOf('#')
  const pathPart = hashIndex >= 0 ? href.slice(0, hashIndex) : href
  const hash = hashIndex >= 0 ? href.slice(hashIndex) : ''

  if (!pathPart) return hash || undefined
  if (/^(https?:|mailto:|tel:)/i.test(pathPart)) return undefined
  if (pathPart.startsWith('#')) return href

  let resolved = pathPart.startsWith('/') ? pathPart.replace(/^\//, '') : joinPath(dir, pathPart)
  resolved = resolved.replace(/\\/g, '/')
  if (resolved.endsWith('.md')) resolved = resolved.slice(0, -3)
  if (resolved.endsWith('/index')) resolved = resolved.slice(0, -'/index'.length)
  if (resolved === 'index') resolved = ''
  const pathname = resolved ? `/${resolved}` : '/'
  return `${pathname}${hash}`
}

function createContainer(md: InstanceType<typeof MarkdownIt>, name: string, defaultTitle: string) {
  md.use(markdownItContainer, name, {
    render(tokens: { info: string; nesting: number }[], idx: number) {
      const info = tokens[idx]!.info.trim().slice(name.length).trim()
      const title = info || defaultTitle
      if (tokens[idx]!.nesting === 1) {
        return `<div class="custom-block ${name}"><p class="custom-block-title">${escapeHtml(title)}</p>\n`
      }
      return '</div>\n'
    },
  })
}

export async function renderMarkdown(
  source: string,
  dir = '',
): Promise<{ html: string; toc: TocItem[] }> {
  const highlighter = await getHighlighter()
  const md = new MarkdownIt({
    html: false,
    linkify: true,
    typographer: true,
    breaks: false,
    highlight(code, lang): string {
      const grammar = resolveLang(lang)
      if (!grammar) {
        return `<pre class="shiki"><code>${escapeHtml(code)}</code></pre>`
      }
      try {
        return highlighter.codeToHtml(code, {
          lang: grammar,
          themes: {
            light: 'github-light',
            dark: 'github-dark',
          },
          defaultColor: false,
        })
      } catch {
        return `<pre class="shiki"><code>${escapeHtml(code)}</code></pre>`
      }
    },
  })

  const toc: TocItem[] = []

  md.use(markdownItCjkFriendly)
  md.use(markdownItFootnote)
  md.use(MarkdownItGitHubAlerts)
  md.use(tasklist, { disabled: true, label: true })
  createContainer(md, 'tip', 'TIP')
  createContainer(md, 'info', 'INFO')
  createContainer(md, 'warning', 'WARNING')
  createContainer(md, 'danger', 'DANGER')

  md.use(markdownItAnchor, {
    level: [2, 3, 4],
    slugify,
    permalink: markdownItAnchor.permalink.ariaHidden({
      class: 'header-anchor',
      symbol: '#',
      placement: 'before',
      space: false,
    }),
    callback(token, info) {
      const level = Number(token.tag.slice(1))
      if (level === 2 || level === 3) {
        toc.push({ id: info.slug, title: info.title, level })
      }
    },
  })

  const defaultFence = md.renderer.rules.fence?.bind(md.renderer)
  md.renderer.rules.fence = (tokens, idx, options, env, self) => {
    const token = tokens[idx]!
    const lang = token.info.trim().split(/\s+/)[0] ?? ''
    if (lang === 'mermaid' || lang === 'mmd') {
      const code = token.content.trim()
      return `<div class="mermaid-wrap" data-source="${encodeURIComponent(code)}"><pre class="mermaid">${escapeHtml(code)}</pre></div>\n`
    }
    const raw = defaultFence ? defaultFence(tokens, idx, options, env, self) : escapeHtml(token.content)
    const encoded = encodeURIComponent(token.content)
    const langAttr = lang ? ` data-lang="${escapeHtml(lang)}"` : ''
    return `<div class="code-wrap"${langAttr}><button type="button" class="copy-code" data-copy="${encoded}">Copy</button>${raw}</div>`
  }

  const defaultTableOpen = md.renderer.rules.table_open?.bind(md.renderer)
  const defaultTableClose = md.renderer.rules.table_close?.bind(md.renderer)
  md.renderer.rules.table_open = (tokens, idx, options, env, self) => {
    const open = defaultTableOpen
      ? defaultTableOpen(tokens, idx, options, env, self)
      : self.renderToken(tokens, idx, options)
    return `<div class="table-wrap" tabindex="0">${open}`
  }
  md.renderer.rules.table_close = (tokens, idx, options, env, self) => {
    const close = defaultTableClose
      ? defaultTableClose(tokens, idx, options, env, self)
      : self.renderToken(tokens, idx, options)
    return `${close}</div>`
  }

  const defaultImage = md.renderer.rules.image?.bind(md.renderer)
  md.renderer.rules.image = (tokens, idx, options, env, self) => {
    const token = tokens[idx]!
    const src = String(token.attrGet('src') ?? '')
    if (src && !/^(https?:|data:|blob:)/i.test(src)) {
      const path = src.startsWith('/') ? src.replace(/^\/+/, '') : joinPath(dir, src)
      token.attrSet('data-content-path', path.replace(/^src\/content\//, ''))
    }
    return defaultImage ? defaultImage(tokens, idx, options, env, self) : self.renderToken(tokens, idx, options)
  }

  const defaultLinkOpen = md.renderer.rules.link_open?.bind(md.renderer)
  md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
    const token = tokens[idx]!
    const href = String(token.attrGet('href') ?? '')
    if (href) {
      const internal = resolveDocHref(href, dir)
      if (internal) {
        token.attrSet('href', internal)
        token.attrSet('data-internal', '1')
      } else if (/^https?:/i.test(href)) {
        token.attrSet('target', '_blank')
        token.attrSet('rel', 'noreferrer noopener')
      }
    }
    return defaultLinkOpen ? defaultLinkOpen(tokens, idx, options, env, self) : self.renderToken(tokens, idx, options)
  }

  const html = md.render(source)
  return { html, toc }
}

export function stripMarkdown(source: string): string {
  return source
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`[^`]*`/g, ' ')
    .replace(/[#>*_\-[\]()]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}
