/**
 * 社区站开发服务器。本机 5174（5173 留给文档工作空间）。
 * /api 转到网关 8080，浏览器看起来仍是同源，才能带 Cookie。
 */
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue(), tailwindcss()],
    server: {
      port: 5174,
      strictPort: true,
      proxy: {
        '/api': {
          target: env.VITE_DEV_PROXY_TARGET || 'http://127.0.0.1:8080',
          changeOrigin: true,
          configure: (proxy) => {
            proxy.on('proxyRes', (proxyRes) => {
              const cookies = proxyRes.headers['set-cookie']
              if (!cookies) return
              proxyRes.headers['set-cookie'] = cookies.map((c) =>
                c.replace(/;\s*Secure/gi, '').replace(/;\s*Domain=[^;]*/gi, ''),
              )
            })
          },
        },
      },
    },
  }
})
