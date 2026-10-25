import { spawn } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const demoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const mode = process.argv[2] || 'dev'
const apps = ['community', 'creator', 'ops']

function run(name) {
  const args =
    mode === 'dev'
      ? ['vite', '--config', `vite/${name}.ts`]
      : mode === 'build'
        ? ['vite', 'build', '--config', `vite/${name}.ts`]
        : ['vite', 'preview', '--config', `vite/${name}.ts`]
  const child = spawn('npx', args, {
    cwd: demoRoot,
    stdio: 'inherit',
    shell: true,
    env: process.env,
  })
  return new Promise((resolve, reject) => {
    child.on('exit', (code) => {
      if (code === 0) resolve()
      else reject(new Error(`${name} exited ${code}`))
    })
  })
}

if (mode === 'build') {
  process.env.ONPAGE_SITE_BUILD = '1'
  for (const name of apps) {
    await run(name)
  }
} else {
  await Promise.all(apps.map((name) => run(name)))
}
