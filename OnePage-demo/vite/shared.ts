import path from 'node:path'
import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import type { UserConfig } from 'vite'
import { sitePlugins } from './plugin'

const here = path.dirname(fileURLToPath(import.meta.url))
export const demoRoot = path.resolve(here, '..')
export const webRoot = path.resolve(demoRoot, '../OnePage-web')

const siteBuild = process.env.ONPAGE_SITE_BUILD === '1'

export function siteConfig(opts: {
  app: 'shell' | 'app-creator' | 'app-ops'
  port: number
  outName: string
  tailwind?: boolean
  deployBase?: string
}): UserConfig {
  const appDir = path.join(webRoot, 'apps', opts.app)
  const base = siteBuild && opts.deployBase ? opts.deployBase : '/'
  return {
    root: appDir,
    base,
    envDir: demoRoot,
    publicDir: path.join(appDir, 'public'),
    plugins: [vue(), ...(opts.tailwind ? [tailwindcss()] : []), ...sitePlugins()],
    resolve: {
      alias: {
        '@onepage/api-client': path.join(webRoot, 'packages/api-client/src/index.ts'),
        '@onepage/site-urls': path.join(webRoot, 'packages/site-urls/src/index.ts'),
        '@onepage/site-footer': path.join(webRoot, 'packages/site-footer/SiteFooter.vue'),
        '@onepage/demo-login-hints': path.join(webRoot, 'packages/demo-login-hints/DemoLoginCreds.vue'),
        '@onepage/image-crop': path.join(webRoot, 'packages/image-crop/src/ImageCropDialog.vue'),
        vue: path.join(demoRoot, 'node_modules/vue'),
        pinia: path.join(demoRoot, 'node_modules/pinia'),
        'vue-router': path.join(demoRoot, 'node_modules/vue-router'),
        'hls.js': path.join(demoRoot, 'node_modules/hls.js'),
        echarts: path.join(demoRoot, 'node_modules/echarts'),
      },
      dedupe: ['vue', 'pinia', 'vue-router', 'echarts'],
    },
    optimizeDeps: {
      include: ['vue', 'pinia', 'vue-router', 'hls.js', 'echarts'],
    },
    server: {
      port: opts.port,
      strictPort: true,
      fs: { allow: [webRoot, demoRoot] },
    },
    preview: {
      port: opts.port,
      strictPort: true,
    },
    build: {
      outDir: path.join(demoRoot, 'dist', opts.outName),
      emptyOutDir: true,
    },
  }
}
