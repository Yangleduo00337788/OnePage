/** 根据 public/media/covers 已有文件重建 src/media-index.json */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer, resolveConfig } from 'vite'
import { safeMediaFileKey } from './baidu-image-core.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const demoRoot = path.resolve(here, '..')
const coversDir = path.join(demoRoot, 'public/media/covers')
const outFile = path.join(demoRoot, 'src/media-index.json')

function pathForKey(key) {
  const file = `${safeMediaFileKey(key)}.jpg`
  const abs = path.join(coversDir, file)
  if (!fs.existsSync(abs)) return ''
  return `/media/covers/${file}`
}

const config = await resolveConfig(
  { configFile: path.join(demoRoot, 'vite/community.ts') },
  'serve',
)
const server = await createServer(config)
const seedMod = await server.ssrLoadModule(path.join(demoRoot, 'src/seed.ts'))
await server.close()

const db = seedMod.createSeed()
const index = {}

for (const p of db.posts) {
  if (p.postType === 'video') {
    const u = pathForKey(`${p.postId}:cover`)
    if (u) index[`${p.postId}:cover`] = u
  } else if (p.postType !== 'text') {
    const u = pathForKey(p.postId)
    if (u) index[p.postId] = u
  }
}
for (const live of db.lives) {
  const u = pathForKey(live.sessionId)
  if (u) index[live.sessionId] = u
}

fs.writeFileSync(outFile, `${JSON.stringify(index, null, 2)}\n`, 'utf8')
console.log(`index: ${Object.keys(index).length} entries → ${outFile}`)
