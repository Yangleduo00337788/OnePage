import { defineConfig } from 'vite'
import { siteConfig } from './shared'

export default defineConfig(
  siteConfig({ app: 'shell', port: 5274, outName: 'community', tailwind: true, deployBase: '/' }),
)
