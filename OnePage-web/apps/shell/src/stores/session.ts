/**
 * 当前登录用户。defineStore：Pinia 仓库，整个应用一份。
 * ref：响应式变量，改 .value 后用到它的模板会自动更新。
 * 启动时 GET /users/me：有 Cookie 就填 me，401 就把 me 置空（未登录）。
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, type UserProfile } from '@onepage/api-client'

export const useSession = defineStore('session', () => {
  const me = ref<UserProfile | null>(null)
  const loaded = ref(false)

  async function refresh() {
    try {
      me.value = await api.me()
    } catch {
      me.value = null
    } finally {
      loaded.value = true
    }
  }

  function bumpFollowing(delta: number) {
    if (!me.value) return
    me.value.stats.followingCount = Math.max(0, (me.value.stats.followingCount ?? 0) + delta)
  }

  async function logout() {
    try {
      await api.logout()
    } catch {
      /* 网关已清 Cookie 时仍可能失败，本地照样退出 */
    }
    me.value = null
  }

  return { me, loaded, refresh, logout, bumpFollowing }
})
