import MarkdownIt from 'markdown-it'
import markdownItCjkFriendly from 'markdown-it-cjk-friendly'
import markdownItContainer from 'markdown-it-container'
import MarkdownItGitHubAlerts from 'markdown-it-github-alerts'
import { tasklist } from '@mdit/plugin-tasklist'
import TurndownService from 'turndown'

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
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

export function markdownToEditableHtml(source: string): string {
  const md = new MarkdownIt({
    html: false,
    linkify: true,
    typographer: true,
    breaks: false,
  })
  md.use(markdownItCjkFriendly)
  md.use(MarkdownItGitHubAlerts)
  md.use(tasklist, { disabled: false, label: true })
  createContainer(md, 'tip', 'TIP')
  createContainer(md, 'info', 'INFO')
  createContainer(md, 'warning', 'WARNING')
  createContainer(md, 'danger', 'DANGER')

  const defaultFence = md.renderer.rules.fence?.bind(md.renderer)
  md.renderer.rules.fence = (tokens, idx, options, env, self) => {
    const token = tokens[idx]!
    const lang = token.info.trim().split(/\s+/)[0] ?? ''
    if (lang === 'mermaid' || lang === 'mmd') {
      return `<pre class="mermaid-source"><code class="language-mermaid">${escapeHtml(token.content)}</code></pre>\n`
    }
    return defaultFence ? defaultFence(tokens, idx, options, env, self) : self.renderToken(tokens, idx, options)
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

  return md.render(source)
}

function cellText(cell: Element): string {
  return (cell.textContent ?? '').replace(/\s+/g, ' ').trim().replace(/\|/g, '\\|')
}

function tableToMarkdown(table: HTMLTableElement): string {
  const rows = [...table.querySelectorAll('tr')].map((row) =>
    [...row.querySelectorAll('th, td')].map((cell) => cellText(cell)),
  )
  if (!rows.length) return ''
  const width = Math.max(...rows.map((row) => row.length), 1)
  const padded = rows.map((row) => {
    const next = [...row]
    while (next.length < width) next.push('')
    return next
  })
  const header = padded[0] ?? Array.from({ length: width }, () => '')
  const body = padded.slice(1)
  const line = (cells: string[]) => `| ${cells.join(' | ')} |`
  const sep = `| ${header.map(() => '---').join(' | ')} |`
  return [line(header), sep, ...body.map(line)].join('\n')
}

function quoteBlock(kind: string, inner: string): string {
  const body = inner
    .trim()
    .split('\n')
    .filter((line) => line.trim() && !/^(TIP|INFO|WARNING|DANGER|NOTE|IMPORTANT|CAUTION)$/i.test(line.trim()))
    .join('\n')
  const quoted = body
    .split('\n')
    .map((line) => `> ${line}`)
    .join('\n')
  return `> [!${kind}]\n${quoted || '> '}`
}

export function editableHtmlToMarkdown(html: string): string {
  const turndown = new TurndownService({
    headingStyle: 'atx',
    codeBlockStyle: 'fenced',
    bulletListMarker: '-',
    emDelimiter: '*',
    hr: '---',
  })

  turndown.addRule('mermaidSource', {
    filter: (node) => {
      if (!(node instanceof HTMLElement)) return false
      if (node.classList.contains('mermaid-source') || node.classList.contains('mermaid')) return true
      return node.tagName === 'PRE' && Boolean(node.querySelector('code.language-mermaid, code.language-mmd'))
    },
    replacement(_content, node) {
      const el = node as HTMLElement
      const code = (el.querySelector('code')?.textContent ?? el.textContent ?? '').trim()
      return `\n\n\`\`\`mermaid\n${code}\n\`\`\`\n\n`
    },
  })

  turndown.addRule('contentImage', {
    filter: 'img',
    replacement(_content, node) {
      const el = node as HTMLImageElement
      const alt = el.getAttribute('alt') || ''
      const rel = el.getAttribute('data-rel') || ''
      const src = rel || el.getAttribute('src') || ''
      if (!src || src.startsWith('blob:') || src.startsWith('data:')) return ''
      return `![${alt}](${src})`
    },
  })

  turndown.addRule('tableWrap', {
    filter: (node) => node instanceof HTMLElement && node.classList.contains('table-wrap'),
    replacement(_content, node) {
      const table = (node as HTMLElement).querySelector('table')
      return table ? `\n\n${tableToMarkdown(table)}\n\n` : ''
    },
  })

  turndown.addRule('table', {
    filter: 'table',
    replacement(_content, node) {
      return `\n\n${tableToMarkdown(node as HTMLTableElement)}\n\n`
    },
  })

  turndown.addRule('taskItem', {
    filter: (node) => node instanceof HTMLElement && node.classList.contains('task-list-item'),
    replacement(content, node) {
      const input = (node as HTMLElement).querySelector('input[type="checkbox"]') as HTMLInputElement | null
      const mark = input?.checked ? 'x' : ' '
      const text = content.replace(/^\s*\[[ xX]\]\s*/, '').trim()
      return `- [${mark}] ${text}\n`
    },
  })

  turndown.addRule('githubAlert', {
    filter: (node) => node instanceof HTMLElement && node.classList.contains('markdown-alert'),
    replacement(content, node) {
      const el = node as HTMLElement
      const typeClass = [...el.classList].find((name) => name.startsWith('markdown-alert-') && name !== 'markdown-alert')
      const kind = (typeClass?.replace('markdown-alert-', '') ?? 'NOTE').toUpperCase()
      return `\n\n${quoteBlock(kind, content)}\n\n`
    },
  })

  turndown.addRule('customBlock', {
    filter: (node) => node instanceof HTMLElement && node.classList.contains('custom-block'),
    replacement(content, node) {
      const el = node as HTMLElement
      const kind = ['tip', 'info', 'warning', 'danger'].find((name) => el.classList.contains(name)) ?? 'tip'
      return `\n\n${quoteBlock(kind.toUpperCase(), content)}\n\n`
    },
  })

  const markdown = turndown.turndown(html)
  return markdown.replace(/\n{3,}/g, '\n\n').trim()
}
