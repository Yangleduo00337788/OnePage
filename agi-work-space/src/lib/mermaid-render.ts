import mermaid from 'mermaid'

let mermaidReady = false
let lastTheme: 'default' | 'dark' | '' = ''
let renderSeq = 0

function mermaidTheme(): 'default' | 'dark' {
  return document.documentElement.classList.contains('dark') ? 'dark' : 'default'
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
}

function ensureMermaid() {
  const theme = mermaidTheme()
  if (mermaidReady && lastTheme === theme) return
  mermaid.initialize({
    startOnLoad: false,
    securityLevel: 'strict',
    theme,
    fontFamily: 'inherit',
  })
  mermaidReady = true
  lastTheme = theme
}

if (typeof window !== 'undefined') {
  window.addEventListener('agi-theme', () => {
    mermaidReady = false
    lastTheme = ''
  })
}

export function extractMermaidBlocks(source: string): string[] {
  const blocks: string[] = []
  const re = /```(?:mermaid|mmd)\s*\n([\s\S]*?)```/gi
  let match: RegExpExecArray | null
  while ((match = re.exec(source))) {
    const code = match[1]?.trim()
    if (code) blocks.push(code)
  }
  return blocks
}

export async function renderMermaidIn(root: ParentNode | null): Promise<void> {
  if (!root || typeof document === 'undefined') return
  const wraps = [...root.querySelectorAll<HTMLElement>('.mermaid-wrap')]
  if (!wraps.length) return
  ensureMermaid()
  const gen = ++renderSeq
  const nodes: HTMLElement[] = []
  for (const wrap of wraps) {
    const encoded = wrap.getAttribute('data-source')
    const code = encoded ? decodeURIComponent(encoded) : wrap.textContent?.trim() || ''
    if (!code) continue
    wrap.innerHTML = `<pre class="mermaid">${escapeHtml(code)}</pre>`
    const pre = wrap.querySelector<HTMLElement>('pre.mermaid')
    if (pre) nodes.push(pre)
  }
  if (!nodes.length || gen !== renderSeq) return
  try {
    await mermaid.run({ nodes })
  } catch {
    if (gen !== renderSeq) return
  }
}

export async function renderMermaidSources(codes: string[], host: HTMLElement | null): Promise<void> {
  if (!host) return
  host.replaceChildren()
  if (!codes.length) return
  ensureMermaid()
  const gen = ++renderSeq
  for (const [index, code] of codes.entries()) {
    if (gen !== renderSeq) return
    const wrap = document.createElement('div')
    wrap.className = 'mermaid-preview-item rounded-xl border bg-card p-4'
    try {
      const id = `draft-mmd-${Date.now().toString(36)}-${index}`
      const { svg } = await mermaid.render(id, code)
      wrap.innerHTML = svg
    } catch (error) {
      wrap.classList.add('text-sm', 'text-destructive')
      wrap.textContent = error instanceof Error ? error.message : 'Mermaid error'
    }
    host.append(wrap)
  }
}
