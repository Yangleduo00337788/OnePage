import { defineConfig } from 'vite'
import { siteConfig } from './shared'

export default defineConfig(siteConfig({ app: 'app-ops', port: 5276, outName: 'ops', deployBase: '/ops/' }))
