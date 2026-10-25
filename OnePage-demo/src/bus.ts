import type { ImSocketFrame } from '../../OnePage-web/packages/api-client/src/index.ts'

type Fn = (frame: ImSocketFrame) => void
const listeners = new Set<Fn>()

export function onImFrame(fn: Fn) {
  listeners.add(fn)
  return () => listeners.delete(fn)
}

export function emitIm(frame: ImSocketFrame) {
  listeners.forEach((fn) => fn(frame))
}
