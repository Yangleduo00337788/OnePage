import path from 'node:path'
import { fileURLToPath } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { defineConfig, type ProxyOptions } from 'vite'

const root = fileURLToPath(new URL('.', import.meta.url))

function stripBrowserAuthPrompt(): Pick<ProxyOptions, 'configure'> {
  return {
    configure(proxy) {
      proxy.on('proxyRes', (proxyRes) => {
        delete proxyRes.headers['www-authenticate']
      })
    },
  }
}

const gitProxy = {
  '/__git/gitee': {
    target: 'https://gitee.com',
    changeOrigin: true,
    rewrite: (urlPath: string) => urlPath.replace(/^\/__git\/gitee/, '/api/v5'),
    ...stripBrowserAuthPrompt(),
  },
  '/__git/github': {
    target: 'https://api.github.com',
    changeOrigin: true,
    rewrite: (urlPath: string) => urlPath.replace(/^\/__git\/github/, ''),
    ...stripBrowserAuthPrompt(),
  },
  '/__git/gitlab': {
    target: 'https://gitlab.com',
    changeOrigin: true,
    rewrite: (urlPath: string) => urlPath.replace(/^\/__git\/gitlab/, '/api/v4'),
    ...stripBrowserAuthPrompt(),
  },
  '/__git-remote/gitee': {
    target: 'https://gitee.com',
    changeOrigin: true,
    rewrite: (urlPath: string) => urlPath.replace(/^\/__git-remote\/gitee/, ''),
    ...stripBrowserAuthPrompt(),
  },
  '/__git-remote/github': {
    target: 'https://github.com',
    changeOrigin: true,
    rewrite: (urlPath: string) => urlPath.replace(/^\/__git-remote\/github/, ''),
    ...stripBrowserAuthPrompt(),
  },
  '/__git-remote/gitlab': {
    target: 'https://gitlab.com',
    changeOrigin: true,
    rewrite: (urlPath: string) => urlPath.replace(/^\/__git-remote\/gitlab/, ''),
    ...stripBrowserAuthPrompt(),
  },
}

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(root, './src'),
      buffer: 'buffer',
    },
  },
  optimizeDeps: {
    include: ['buffer'],
  },
  server: {
    fs: {
      allow: [root],
    },
    proxy: gitProxy,
  },
  preview: {
    proxy: gitProxy,
  },
})
