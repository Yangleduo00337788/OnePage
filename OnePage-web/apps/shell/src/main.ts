/**
 * 前端入口。浏览器先执行这个文件，再挂到 index.html 的 #app。
 * createApp：创建 Vue 应用实例。
 * createPinia：全局状态（session / 登录弹窗 / 主题），各组件用 useXxx() 取同一份。
 * router：按 URL 换中间内容，侧栏不卸载。
 * mount：把虚拟 DOM 接到真实 DOM。
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import './style.css'

createApp(App).use(createPinia()).use(router).mount('#app')
