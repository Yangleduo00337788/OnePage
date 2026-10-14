/**
 * 登录后连 WS /ws/im。消息页订阅推送；侧栏未读在帧到达时刷新。
 * 断线自动重连；连上后停掉消息页 3 秒轮询。
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { imWebSocketUrl, type ImSocketFrame } from '@onepage/api-client'

type Listener = (frame: ImSocketFrame) => void

export const useImSocket = defineStore('imSocket', () => {
  const connected = ref(false)
  const listeners = new Set<Listener>()
  let socket: WebSocket | null = null
  let pingTimer: ReturnType<typeof setInterval> | null = null
  let retryTimer: ReturnType<typeof setTimeout> | null = null
  let retryMs = 1000
  let wantOpen = false

  function subscribe(fn: Listener) {
    listeners.add(fn)
    return () => {
      listeners.delete(fn)
    }
  }

  function emit(frame: ImSocketFrame) {
    listeners.forEach((fn) => fn(frame))
  }

  function clearTimers() {
    if (pingTimer) {
      clearInterval(pingTimer)
      pingTimer = null
    }
    if (retryTimer) {
      clearTimeout(retryTimer)
      retryTimer = null
    }
  }

  function closeSocket() {
    connected.value = false
    if (socket) {
      socket.onopen = null
      socket.onclose = null
      socket.onerror = null
      socket.onmessage = null
      try {
        socket.close()
      } catch {
        /* 已关 */
      }
      socket = null
    }
  }

  function scheduleReconnect() {
    if (!wantOpen) return
    clearTimers()
    retryTimer = setTimeout(() => {
      retryTimer = null
      open()
    }, retryMs)
    retryMs = Math.min(retryMs * 2, 15000)
  }

  function open() {
    if (!wantOpen) return
    closeSocket()
    const ws = new WebSocket(imWebSocketUrl())
    socket = ws
    ws.onopen = () => {
      connected.value = true
      retryMs = 1000
      pingTimer = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ op: 'ping' }))
        }
      }, 20000)
    }
    ws.onmessage = (ev) => {
      try {
        emit(JSON.parse(String(ev.data)) as ImSocketFrame)
      } catch {
        /* 非 JSON 忽略 */
      }
    }
    ws.onerror = () => {
      /* onclose 会重连 */
    }
    ws.onclose = () => {
      connected.value = false
      if (socket === ws) {
        socket = null
      }
      if (pingTimer) {
        clearInterval(pingTimer)
        pingTimer = null
      }
      scheduleReconnect()
    }
  }

  function connect() {
    wantOpen = true
    if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
      return
    }
    open()
  }

  function disconnect() {
    wantOpen = false
    clearTimers()
    closeSocket()
  }

  return { connected, connect, disconnect, subscribe }
})
