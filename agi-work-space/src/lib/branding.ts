import { computed, ref } from 'vue'
import bundled from '@/config/branding.json'
import type { Locale } from '@/i18n/messages'
import { getWorkspaceRoot, readRepoFile, rewriteRepoTextFiles, writeRepoBytes, writeRepoFile } from '@/lib/workspace-fs'

export const BRANDING_FILE = 'src/config/branding.json'
export const LOGO_LIGHT_FILE = 'public/logo-light.png'
export const LOGO_DARK_FILE = 'public/logo-dark.png'
const MAX_LOGO_BYTES = 2 * 1024 * 1024

export interface Branding {
  name: string
  sloganZh: string
  sloganEn: string
  introZh: string
  introEn: string
  logoRev: number
}

const branding = ref<Branding>(normalize(bundled))

function normalize(raw: unknown): Branding {
  const item = raw && typeof raw === 'object' ? (raw as Partial<Branding>) : {}
  const fallback = bundled as Branding
  const name = String(item.name ?? fallback.name).trim() || fallback.name
  const logoRev = Number(item.logoRev)
  return {
    name,
    sloganZh: String(item.sloganZh ?? fallback.sloganZh).trim() || fallback.sloganZh,
    sloganEn: String(item.sloganEn ?? fallback.sloganEn).trim() || fallback.sloganEn,
    introZh: String(item.introZh ?? fallback.introZh).trim() || fallback.introZh,
    introEn: String(item.introEn ?? fallback.introEn).trim() || fallback.introEn,
    logoRev: Number.isFinite(logoRev) && logoRev > 0 ? Math.floor(logoRev) : fallback.logoRev || 1,
  }
}

export function workspaceName() {
  return branding.value.name
}

export function logoLightSrc() {
  return `/logo-light.png?v=${branding.value.logoRev}`
}

export function logoDarkSrc() {
  return `/logo-dark.png?v=${branding.value.logoRev}`
}

export function sloganFor(locale: Locale) {
  return locale === 'en' ? branding.value.sloganEn : branding.value.sloganZh
}

export function introFor(locale: Locale) {
  return locale === 'en' ? branding.value.introEn : branding.value.introZh
}

export async function hydrateBranding() {
  if (!getWorkspaceRoot()) return
  const text = await readRepoFile(BRANDING_FILE)
  if (!text) return
  try {
    branding.value = normalize(JSON.parse(text) as unknown)
  } catch {
    /* keep bundled */
  }
}

function escapeHtml(value: string) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
}

async function patchIndexTitle(name: string) {
  const html = await readRepoFile('index.html')
  if (!html) return
  const next = html.includes('<title>')
    ? html.replace(/<title>[\s\S]*?<\/title>/, `<title>${escapeHtml(name)}</title>`)
    : html
  if (next !== html) await writeRepoFile('index.html', next)
}

function replacementPairs(from: Branding, to: Omit<Branding, 'logoRev'>): [string, string][] {
  const pairs: [string, string][] = [
    [from.introZh, to.introZh],
    [from.introEn, to.introEn],
    [from.sloganZh, to.sloganZh],
    [from.sloganEn, to.sloganEn],
    [from.name, to.name.trim()],
  ]
  const spaced = from.name.replaceAll('-', ' ').trim()
  const nextName = to.name.trim()
  if (spaced && spaced !== from.name && nextName) pairs.push([spaced, nextName])
  const seen = new Set<string>()
  return pairs
    .map(([oldValue, next]) => [oldValue.trim(), next.trim()] as [string, string])
    .filter(([oldValue, next]) => oldValue.length >= 2 && next && oldValue !== next)
    .sort((a, b) => b[0].length - a[0].length)
    .filter(([oldValue]) => {
      if (seen.has(oldValue)) return false
      seen.add(oldValue)
      return true
    })
}

function applyPairs(text: string, pairs: [string, string][]) {
  let next = text
  for (const [from, to] of pairs) next = next.split(from).join(to)
  return next
}

export async function saveBranding(
  next: Omit<Branding, 'logoRev'> & { light?: ArrayBuffer; dark?: ArrayBuffer },
): Promise<number> {
  const name = next.name.trim()
  if (!name) throw new Error('NAME_REQUIRED')
  if (next.light && next.light.byteLength > MAX_LOGO_BYTES) throw new Error('LOGO_TOO_LARGE')
  if (next.dark && next.dark.byteLength > MAX_LOGO_BYTES) throw new Error('LOGO_TOO_LARGE')

  const previous = branding.value
  const pairs = replacementPairs(previous, { ...next, name })

  let logoRev = previous.logoRev
  if (next.light) {
    await writeRepoBytes(LOGO_LIGHT_FILE, next.light)
    logoRev += 1
  }
  if (next.dark) {
    await writeRepoBytes(LOGO_DARK_FILE, next.dark)
    if (!next.light) logoRev += 1
  }

  let rewritten = 0
  if (pairs.length) {
    rewritten = await rewriteRepoTextFiles((_relPath, text) => applyPairs(text, pairs))
  }

  const normalized = normalize({
    name,
    sloganZh: next.sloganZh,
    sloganEn: next.sloganEn,
    introZh: next.introZh,
    introEn: next.introEn,
    logoRev,
  })
  branding.value = normalized
  await writeRepoFile(BRANDING_FILE, `${JSON.stringify(normalized, null, 2)}\n`)
  await patchIndexTitle(normalized.name)
  return rewritten
}

export function useBranding() {
  return {
    branding: computed(() => branding.value),
    file: BRANDING_FILE,
    logoLightSrc: computed(() => logoLightSrc()),
    logoDarkSrc: computed(() => logoDarkSrc()),
  }
}
