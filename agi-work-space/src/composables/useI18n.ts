import { computed, ref } from 'vue'
import { messages, type Locale, type MessageKey } from '@/i18n/messages'
import { introFor, sloganFor, workspaceName } from '@/lib/branding'

const STORAGE_KEY = 'agi-locale'

function readLocale(): Locale {
  if (typeof localStorage === 'undefined') return 'zh'
  const stored = localStorage.getItem(STORAGE_KEY)
  return stored === 'en' ? 'en' : 'zh'
}

const locale = ref<Locale>(readLocale())

export function useI18n() {
  const t = computed(() => {
    const name = workspaceName()
    const slogan = sloganFor(locale.value)
    const intro = introFor(locale.value)
    const table = messages[locale.value]
    return (key: MessageKey) => {
      if (key === 'product') return name || table.product
      if (key === 'homeSlogan') return slogan || table.homeSlogan
      if (key === 'homeIntro') return intro || table.homeIntro
      return table[key]
    }
  })

  function setLocale(next: Locale) {
    locale.value = next
    localStorage.setItem(STORAGE_KEY, next)
    document.documentElement.lang = next === 'zh' ? 'zh-CN' : 'en'
  }

  return { locale, t, setLocale }
}
