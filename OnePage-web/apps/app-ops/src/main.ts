/**
 * 运营后台独立站点入口。本机 5176。
 */
import { setApiAudience } from '@onepage/api-client'
import { createApp } from 'vue'
import App from './App.vue'

setApiAudience('ops')
createApp(App).mount('#app')
