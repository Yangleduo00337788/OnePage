import { computed, onUnmounted, ref } from 'vue'

export type SpeechStatus = 'idle' | 'speaking' | 'paused' | 'unsupported'

const CHUNK = 220

function splitText(text: string): string[] {
  const clean = text.replace(/\s+/g, ' ').trim()
  if (!clean) return []
  const parts: string[] = []
  let rest = clean
  while (rest.length > CHUNK) {
    const slice = rest.slice(0, CHUNK)
    const cut = Math.max(
      slice.lastIndexOf('。'),
      slice.lastIndexOf('！'),
      slice.lastIndexOf('？'),
      slice.lastIndexOf('. '),
      slice.lastIndexOf('，'),
      slice.lastIndexOf(', '),
    )
    const at = cut > 40 ? cut + 1 : CHUNK
    parts.push(rest.slice(0, at).trim())
    rest = rest.slice(at).trim()
  }
  if (rest) parts.push(rest)
  return parts
}

function pickVoice(lang: string): SpeechSynthesisVoice | undefined {
  const voices = window.speechSynthesis.getVoices()
  const prefix = lang.slice(0, 2).toLowerCase()
  return (
    voices.find((voice) => voice.lang.toLowerCase().startsWith(lang.toLowerCase())) ||
    voices.find((voice) => voice.lang.toLowerCase().startsWith(prefix))
  )
}

export function speechSupported() {
  return typeof window !== 'undefined' && 'speechSynthesis' in window && 'SpeechSynthesisUtterance' in window
}

export function useDocSpeech() {
  const supported = computed(() => speechSupported())
  const status = ref<SpeechStatus>(speechSupported() ? 'idle' : 'unsupported')
  let queue: SpeechSynthesisUtterance[] = []
  let index = 0
  let lang = 'zh-CN'
  let startTimer = 0
  let gen = 0

  function clearQueue() {
    queue = []
    index = 0
  }

  function speakNext() {
    if (!speechSupported()) return
    const item = queue[index]
    if (!item) {
      status.value = 'idle'
      clearQueue()
      return
    }
    const voice = pickVoice(lang)
    if (voice) item.voice = voice
    item.lang = lang
    status.value = 'speaking'
    window.speechSynthesis.speak(item)
  }

  function stop() {
    if (!speechSupported()) return
    gen += 1
    window.clearTimeout(startTimer)
    window.speechSynthesis.cancel()
    clearQueue()
    status.value = supported.value ? 'idle' : 'unsupported'
  }

  function pause() {
    if (status.value !== 'speaking' || !speechSupported()) return
    window.speechSynthesis.pause()
    status.value = 'paused'
  }

  function resume() {
    if (status.value !== 'paused' || !speechSupported()) return
    window.speechSynthesis.resume()
    status.value = 'speaking'
  }

  function speak(text: string, locale: 'zh' | 'en') {
    if (!speechSupported()) {
      status.value = 'unsupported'
      return false
    }
    const parts = splitText(text)
    if (!parts.length) return false
    stop()
    lang = locale === 'en' ? 'en-US' : 'zh-CN'
    const run = ++gen
    queue = parts.map((chunk) => {
      const utter = new SpeechSynthesisUtterance(chunk)
      utter.lang = lang
      utter.rate = 1
      utter.onend = () => {
        if (run !== gen) return
        index += 1
        speakNext()
      }
      utter.onerror = () => {
        if (run !== gen) return
        status.value = 'idle'
        clearQueue()
      }
      return utter
    })
    index = 0
    startTimer = window.setTimeout(() => speakNext(), 80)
    return true
  }

  onUnmounted(() => stop())

  if (typeof window !== 'undefined' && speechSupported()) {
    window.speechSynthesis.getVoices()
  }

  return { status, speak, pause, resume, stop, supported }
}
