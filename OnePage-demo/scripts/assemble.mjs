import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const demoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const dist = path.join(demoRoot, 'dist')
const site = path.join(dist, 'site')

function copyDir(from, to) {
  fs.mkdirSync(to, { recursive: true })
  for (const name of fs.readdirSync(from)) {
    const src = path.join(from, name)
    const dest = path.join(to, name)
    if (fs.statSync(src).isDirectory()) copyDir(src, dest)
    else fs.copyFileSync(src, dest)
  }
}

fs.rmSync(site, { recursive: true, force: true })
fs.mkdirSync(site, { recursive: true })
copyDir(path.join(dist, 'community'), site)
const bundledMedia = path.join(dist, 'community', 'media')
const demoMedia = path.join(demoRoot, 'public', 'media')
if (fs.existsSync(bundledMedia)) copyDir(bundledMedia, path.join(site, 'media'))
else if (fs.existsSync(demoMedia)) copyDir(demoMedia, path.join(site, 'media'))
copyDir(path.join(dist, 'creator'), path.join(site, 'creator'))
copyDir(path.join(dist, 'ops'), path.join(site, 'ops'))

const redirects = `/creator/*  /creator/index.html  200
/ops/*  /ops/index.html  200
/*  /index.html  200
`
fs.writeFileSync(path.join(site, '_redirects'), redirects, 'utf8')
console.log('assembled', site)
