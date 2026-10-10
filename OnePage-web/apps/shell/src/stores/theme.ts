/**
 * 主题：偏好存在 localStorage，真正生效写在 html[data-theme]，CSS 变量在 style.css。
 * computed：由 pref + 系统深色推导出当前实际 light/dark。
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export type ThemePref = 'system' | 'light' | 'dark'

const KEY = 'onepage-theme'

function readPref(): ThemePref {
  const raw = localStorage.getItem(KEY)
  if (raw === 'light' || raw === 'dark' || raw === 'system') return raw
  return 'system'
}

function systemDark() {
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

export const useTheme = defineStore('theme', () => {
  const pref = ref<ThemePref>(readPref())
  const resolved = computed(() => (pref.value === 'system' ? (systemDark() ? 'dark' : 'light') : pref.value))

  function apply() {
    document.documentElement.dataset.theme = resolved.value
    document.documentElement.style.colorScheme = resolved.value
  }

  function setPref(next: ThemePref) {
    pref.value = next
    localStorage.setItem(KEY, next)
    apply()
  }

  function init() {
    apply()
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
      if (pref.value === 'system') apply()
    })
  }

  return { pref, resolved, setPref, init }
})
