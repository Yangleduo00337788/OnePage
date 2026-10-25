import { installDemoBridge } from '../../OnePage-web/packages/api-client/src/index.ts'
import { onImFrame } from './bus'
import { demoChatStream, demoPutObject, demoRequest } from './handle'
import { applyLocalMediaIndex } from './image-enrich'
import { getDb, saveDb } from './store'

installDemoBridge({
  request: demoRequest,
  chatStream: demoChatStream,
  putObject: demoPutObject,
})

applyLocalMediaIndex()

function completeOauth(provider: 'github' | 'google', intent?: string | null) {
  const db = getDb()
  if (intent === 'bind') {
    const me = db.users.find((u) => u.userId === db.sessionUserId)
    if (me) {
      if (provider === 'google') me.googleBound = true
      else me.githubBound = true
      saveDb()
    }
    const next = new URL(window.location.href)
    next.searchParams.set('oauth', 'bind_ok')
    window.location.replace(next.toString())
    return
  }
  if (!db.sessionUserId) {
    db.sessionUserId = db.users[0]?.userId ?? null
    saveDb()
  }
  window.location.replace(`${window.location.origin}/`)
}

;(window as unknown as { __onepageOauth?: typeof completeOauth }).__onepageOauth = completeOauth

const NativeWS = window.WebSocket

class SiteSocket {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSING = 2
  static readonly CLOSED = 3
  readonly url: string
  readyState = SiteSocket.CONNECTING
  onopen: ((ev: Event) => void) | null = null
  onclose: ((ev: CloseEvent) => void) | null = null
  onerror: ((ev: Event) => void) | null = null
  onmessage: ((ev: MessageEvent) => void) | null = null
  private off: (() => void) | null = null

  constructor(url: string) {
    this.url = url
    queueMicrotask(() => {
      this.readyState = SiteSocket.OPEN
      this.onopen?.(new Event('open'))
      this.sendJson({ op: 'connected' })
    })
    this.off = onImFrame((frame) => this.sendJson(frame))
  }

  send(data: string) {
    try {
      const parsed = JSON.parse(String(data)) as { op?: string }
      if (parsed.op === 'ping') this.sendJson({ op: 'pong' })
    } catch {
      /* 忽略非 JSON */
    }
  }

  close() {
    this.readyState = SiteSocket.CLOSED
    this.off?.()
    this.onclose?.(new CloseEvent('close'))
  }

  addEventListener() {}

  removeEventListener() {}

  private sendJson(payload: unknown) {
    this.onmessage?.(new MessageEvent('message', { data: JSON.stringify(payload) }))
  }
}

try {
  window.WebSocket = new Proxy(NativeWS, {
    construct(target, args: unknown[]) {
      const url = String(args[0] || '')
      if (url.includes('/ws/im')) return new SiteSocket(url)
      return Reflect.construct(target, args)
    },
  }) as typeof WebSocket
} catch {
  /* 保留浏览器原生 WebSocket */
}
