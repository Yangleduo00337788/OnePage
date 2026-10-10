import { computed, ref } from 'vue'
import bundled from '@/config/push-success.json'
import type { Locale } from '@/i18n/messages'
import { getWorkspaceRoot, readRepoFile, writeRepoFile } from '@/lib/workspace-fs'

export const PUSH_COPY_FILE = 'src/config/push-success.json'

export interface PushCopyPack {
  firstSuccess: string
  firstHint: string
  lines: string[]
}

export interface PushCopyBundle {
  zh: PushCopyPack
  en: PushCopyPack
}

const bundle = ref<PushCopyBundle>(normalizeBundle(bundled))

function cleanLines(lines: unknown): string[] {
  if (!Array.isArray(lines)) return []
  return lines.map((item) => String(item).trim()).filter(Boolean)
}

function normalizePack(raw: unknown, fallback: PushCopyPack): PushCopyPack {
  const item = raw && typeof raw === 'object' ? (raw as Partial<PushCopyPack>) : {}
  return {
    firstSuccess: String(item.firstSuccess ?? fallback.firstSuccess).trim() || fallback.firstSuccess,
    firstHint: String(item.firstHint ?? fallback.firstHint).trim() || fallback.firstHint,
    lines: cleanLines(item.lines).length ? cleanLines(item.lines) : [...fallback.lines],
  }
}

function normalizeBundle(raw: unknown): PushCopyBundle {
  const item = raw && typeof raw === 'object' ? (raw as Partial<PushCopyBundle>) : {}
  return {
    zh: normalizePack(item.zh, bundled.zh),
    en: normalizePack(item.en, bundled.en),
  }
}

export async function hydratePushCopy() {
  if (!getWorkspaceRoot()) return
  const text = await readRepoFile(PUSH_COPY_FILE)
  if (!text) return
  try {
    bundle.value = normalizeBundle(JSON.parse(text) as unknown)
  } catch {
    /* keep bundled defaults */
  }
}

export async function savePushCopy(next: PushCopyBundle) {
  const normalized = normalizeBundle(next)
  bundle.value = normalized
  await writeRepoFile(PUSH_COPY_FILE, `${JSON.stringify(normalized, null, 2)}\n`)
}

export function packFor(locale: Locale): PushCopyPack {
  return bundle.value[locale] ?? bundle.value.zh
}

export function pickPushSuccessLine(locale: Locale) {
  const lines = packFor(locale).lines
  if (!lines.length) return packFor(locale).firstSuccess
  return lines[Math.floor(Math.random() * lines.length)] ?? packFor(locale).firstSuccess
}

export function usePushCopy() {
  return {
    bundle: computed(() => bundle.value),
    file: PUSH_COPY_FILE,
  }
}
