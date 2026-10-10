/**
 * 产品路由表。createWebHistory：URL 是 /me 这种真实路径，不是 #/me。
 * /login、/register 不渲染独立页：beforeEach 打开 AuthModal 后 replace 回首页。
 * /create 渲染首页内容并让侧栏发布选中；点击时另开创作者站。
 * /creator 新开创作者站后回到首页。
 */
import { createRouter, createWebHistory } from 'vue-router'
import HomePage from './pages/HomePage.vue'
import SearchPage from './pages/SearchPage.vue'
import MePage from './pages/MePage.vue'
import UserPage from './pages/UserPage.vue'
import YehYehPage from './pages/YehYehPage.vue'
import VideoPage from './pages/VideoPage.vue'
import LivePage from './pages/LivePage.vue'
import NotifyPage from './pages/NotifyPage.vue'
import MessagesPage from './pages/MessagesPage.vue'
import AboutPage from './pages/AboutPage.vue'
import LegalPage from './pages/LegalPage.vue'
import HelpPage from './pages/HelpPage.vue'
import CreatorPage from './pages/CreatorPage.vue'
import PartnerPage from './pages/PartnerPage.vue'
import ShortcutsPage from './pages/ShortcutsPage.vue'
import { useAuthModal } from './stores/authModal'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: HomePage },
    { path: '/search', component: SearchPage },
    { path: '/me', component: MePage },
    { path: '/u/:pageId', component: UserPage },
    { path: '/yehyeh', component: YehYehPage },
    { path: '/video', component: VideoPage },
    { path: '/live', component: LivePage },
    { path: '/create', component: HomePage },
    { path: '/notify', component: NotifyPage },
    { path: '/messages', component: MessagesPage },
    { path: '/about', component: AboutPage },
    { path: '/privacy', component: LegalPage },
    { path: '/terms', component: LegalPage },
    { path: '/help', component: HelpPage },
    { path: '/creator', component: CreatorPage },
    { path: '/partner/live', component: PartnerPage },
    { path: '/partner/verify', component: PartnerPage },
    { path: '/shortcuts', component: ShortcutsPage },
    { path: '/login', component: HomePage },
    { path: '/register', component: HomePage },
  ],
})

/** 全局前置守卫：进某页之前改道。return true 放行；return { path } 改去别处。 */
router.beforeEach((to) => {
  if (to.path !== '/login' && to.path !== '/register') return true
  const auth = useAuthModal()
  auth.show(to.path === '/register' ? 'register' : 'login')
  return { path: '/', replace: true }
})
