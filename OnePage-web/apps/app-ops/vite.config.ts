/**
 * 运营后台独立站点。本机 5176。/api 转到网关，登录 Cookie 才能带上。
 * /onepage-media 转到 MinIO :9000，预签名 PUT 同源，避开 CORS。
 * 不挂社区导航，用内部地址打开。
 */
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    server: {
      port: 5176,
      strictPort: true,
      proxy: {
        '/onepage-media': {
          target: 'http://localhost:9000',
          changeOrigin: true,
        },
        '/api': {
          target: env.VITE_DEV_PROXY_TARGET || 'http://127.0.0.1:8080',
          changeOrigin: true,
          configure: (proxy) => {
            proxy.on('proxyReq', (proxyReq) => {
              proxyReq.removeHeader('origin')
            })
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
    preview: {
      port: 5176,
      strictPort: true,
    },
  }
})
