/**
 * 登录/注册弹窗开关。不做成独立路由，是为了浏览中途登录后还能回到原页（redirect）。
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAuthModal = defineStore('authModal', () => {
  const open = ref(false)
  const mode = ref<'login' | 'register'>('login')
  const redirect = ref('/me')
  const notice = ref('')

  function show(next: 'login' | 'register' = 'login', after = '/me', message = '') {
    mode.value = next
    redirect.value = after
    notice.value = message
    open.value = true
  }

  function hide() {
    open.value = false
    notice.value = ''
  }

  return { open, mode, redirect, notice, show, hide }
})
