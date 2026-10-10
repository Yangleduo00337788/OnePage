import type { MessageKey } from '@/i18n/messages'

export const SPACE_NAV_ITEMS = [
  { id: 'center', labelKey: 'settingsCenter', to: '/settings' },
  { id: 'references', labelKey: 'settingsNavRefs', to: '/space/references' },
  { id: 'harness', labelKey: 'settingsNavHarness', to: '/space/harness' },
] as const satisfies readonly { id: string; labelKey: MessageKey; to: string }[]

export type SpaceNavId = (typeof SPACE_NAV_ITEMS)[number]['id']

export function activeSpaceItem(path: string): SpaceNavId | '' {
  if (path.startsWith('/space/harness')) return 'harness'
  if (path.startsWith('/space/references')) return 'references'
  if (path === '/settings' || path.startsWith('/settings')) return 'center'
  return ''
}

export function isSpacePath(path: string) {
  return path === '/settings' || path.startsWith('/settings') || path.startsWith('/space/')
}
