/**
 * 按种子标题百度搜图并下载到 public/media/covers，写入 src/media-index.json
 * 用法：node scripts/fetch-local-media.mjs
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer, resolveConfig } from 'vite'
import { buildImageQuery, fetchLocalCover, safeMediaFileKey } from './baidu-image-core.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const demoRoot = path.resolve(here, '..')
const coversDir = path.join(demoRoot, 'public/media/covers')
const outFile = path.join(demoRoot, 'src/media-index.json')

fs.mkdirSync(coversDir, { recursive: true })

const config = await resolveConfig(
  { configFile: path.join(demoRoot, 'vite/community.ts') },
  'serve',
)
const server = await createServer(config)
const seedMod = await server.ssrLoadModule(path.join(demoRoot, 'src/seed.ts'))
await server.close()

const db = seedMod.createSeed()
let index = {}
if (fs.existsSync(outFile)) {
  try {
    index = JSON.parse(fs.readFileSync(outFile, 'utf8'))
  } catch {
    index = {}
  }
}
const delay = (ms) => new Promise((r) => setTimeout(r, ms))

function existingPath(key) {
  const file = path.join(coversDir, `${safeMediaFileKey(key)}.jpg`)
  if (!fs.existsSync(file)) return ''
  return `/media/covers/${safeMediaFileKey(key)}.jpg`
}

function flushIndex() {
  fs.writeFileSync(outFile, `${JSON.stringify(index, null, 2)}\n`, 'utf8')
}

async function add(key, title, body, category) {
  const cached = index[key] || existingPath(key)
  if (cached) {
    index[key] = cached
    process.stdout.write(`hit ${key}\n`)
    return
  }
  const word = buildImageQuery(title, body, category)
  const webPath = await fetchLocalCover(word, key, coversDir)
  if (webPath) index[key] = webPath
  process.stdout.write(webPath ? `ok ${key}\n` : `skip ${key}\n`)
  flushIndex()
  await delay(180)
}

for (const p of db.posts) {
  if (p.postType === 'video') {
    await add(`${p.postId}:cover`, p.title, p.body, p.category)
  } else if (p.postType !== 'text') {
    await add(p.postId, p.title, p.body, p.category)
  }
}
for (const live of db.lives) {
  await add(live.sessionId, live.title, live.notice, live.category)
}

fs.writeFileSync(outFile, `${JSON.stringify(index, null, 2)}\n`, 'utf8')
console.log(`Done: ${Object.keys(index).length} files in public/media/covers, index → ${outFile}`)
