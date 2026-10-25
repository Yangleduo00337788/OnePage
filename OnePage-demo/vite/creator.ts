import { defineConfig } from 'vite'
import { siteConfig } from './shared'

export default defineConfig(
  siteConfig({ app: 'app-creator', port: 5275, outName: 'creator', deployBase: '/creator/' }),
)
