import '@/lib/buffer-polyfill'
import git from 'isomorphic-git'
import { request as gitRequest } from 'isomorphic-git/http/web'
import { createHandleFs } from '@/lib/handle-fs'
import { REMOTE_CREDENTIALS_FILE, type GitProvider } from '@/lib/remote-git'
import { getWorkspaceRoot } from '@/lib/workspace-fs'

export interface GitSyncProgress {
  percent: number
  done: number
  total: number
  current: string
}

function rewriteGitHttpUrl(url: string) {
  if (typeof window === 'undefined') return url
  const host = window.location.hostname
  if (host !== 'localhost' && host !== '127.0.0.1') return url
  const origin = window.location.origin
  return url
    .replace(/^https:\/\/gitee\.com/i, `${origin}/__git-remote/gitee`)
    .replace(/^https:\/\/github\.com/i, `${origin}/__git-remote/github`)
    .replace(/^https:\/\/gitlab\.com/i, `${origin}/__git-remote/gitlab`)
}

function gitAuth(provider: GitProvider, owner: string, token: string) {
  if (provider === 'github') return { username: 'x-access-token', password: token }
  if (provider === 'gitlab') return { username: 'oauth2', password: token }
  return { username: owner, password: token }
}

function basicAuthHeader(username: string, password: string) {
  const bytes = new TextEncoder().encode(`${username}:${password}`)
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return `Basic ${btoa(binary)}`
}

function createGitHttp(username: string, password: string) {
  const authorization = basicAuthHeader(username, password)
  return {
    request: async (args: Parameters<typeof gitRequest>[0]) => {
      return gitRequest({
        ...args,
        url: rewriteGitHttpUrl(args.url),
        headers: { ...args.headers, Authorization: authorization },
        fetchOptions: { ...args.fetchOptions, credentials: 'omit' },
      })
    },
  }
}

function remoteUrl(provider: GitProvider, owner: string, repo: string, host: string) {
  if (provider === 'gitee') return `https://gitee.com/${owner}/${repo}.git`
  if (provider === 'github') return `https://github.com/${owner}/${repo}.git`
  const origin = host.replace(/\/+$/, '') || 'https://gitlab.com'
  return `${origin}/${owner}/${repo}.git`
}

function isSecretPath(filepath: string) {
  return filepath === REMOTE_CREDENTIALS_FILE || filepath.endsWith('.local.json')
}

export async function commitAndPushProject(options: {
  message: string
  provider: GitProvider
  owner: string
  repo: string
  branch: string
  token: string
  host: string
  filepaths?: string[]
  onProgress?: (progress: GitSyncProgress) => void
}): Promise<{ staged: number; committed: boolean }> {
  const root = getWorkspaceRoot()
  if (!root) throw new Error('NEED_WORKSPACE')
  const fs = createHandleFs(root)
  const dir = '.'
  const cache = {}
  const auth = gitAuth(options.provider, options.owner, options.token)
  const http = createGitHttp(auth.username, auth.password)
  const report = (percent: number, current: string, done = 0, total = 1) => {
    options.onProgress?.({ percent, current, done, total })
  }

  async function runPhase<T>(from: number, to: number, label: string, task: () => Promise<T>) {
    let tick = from
    report(from, label)
    const timer = window.setInterval(() => {
      tick = Math.min(to - 1, tick + 1)
      report(tick, label)
    }, 350)
    try {
      return await task()
    } finally {
      window.clearInterval(timer)
      report(to, label)
    }
  }

  report(6, '读取工作区')
  try {
    await fs.promises.stat('.git')
  } catch {
    await git.init({ fs, dir, defaultBranch: options.branch || 'master' })
  }

  report(18, '对照 .gitignore 扫描变更')
  const matrix = await git.statusMatrix({ fs, dir, cache })
  const allow = options.filepaths ? new Set(options.filepaths.map((item) => item.replace(/^\/+/, ''))) : null
  const toAdd: string[] = []
  const toRemove: string[] = []
  for (const [filepath, head, workdir, stage] of matrix) {
    if (isSecretPath(filepath)) {
      if (head === 1) toRemove.push(filepath)
      continue
    }
    if (allow && !allow.has(filepath)) continue
    if (head === 1 && workdir === 1 && stage === 1) continue
    if (workdir === 0) toRemove.push(filepath)
    else toAdd.push(filepath)
  }

  const staged = toAdd.length + toRemove.length
  report(36, staged ? `并行暂存 ${staged} 个文件` : '没有新的文件变更', 0, Math.max(staged, 1))

  for (const filepath of toRemove) {
    await git.remove({ fs, dir, filepath, cache })
  }
  if (toAdd.length) {
    await git.add({ fs, dir, filepath: toAdd, parallel: true, cache })
  }

  let committed = false
  if (staged) {
    await runPhase(58, 74, `提交 ${staged} 个文件`, async () => {
      await git.commit({
        fs,
        dir,
        cache,
        message: options.message,
        author: {
          name: options.owner,
          email: `${options.owner}@users.noreply.${options.provider}.com`,
        },
      })
      committed = true
    })
  }

  await runPhase(76, 98, '上传到远程仓库', async () => {
    await git.push({
      fs,
      http,
      dir,
      cache,
      url: remoteUrl(options.provider, options.owner, options.repo, options.host),
      ref: options.branch || 'master',
      headers: { Authorization: basicAuthHeader(auth.username, auth.password) },
      onProgress: (event) => {
        const loaded = event.loaded ?? 0
        const total = event.total || 0
        const slice = total > 0 ? Math.round((loaded / total) * 20) : 8
        report(Math.min(97, 76 + slice), event.phase || '上传到远程仓库', loaded, total || staged)
      },
      onAuth: () => auth,
    })
  })
  report(100, '完成', staged, staged)
  return { staged, committed }
}
