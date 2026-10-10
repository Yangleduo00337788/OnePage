import { computed, ref } from 'vue'
import { openDb, STORE_REMOTE } from '@/lib/idb'
import { getWorkspaceRoot, readProjectFile, removeProjectFile, writeProjectFile } from '@/lib/workspace-fs'

const CONFIG_KEY = 'binding'
export const REMOTE_CREDENTIALS_FILE = '.agi-workspace.local.json'

export type GitProvider = 'github' | 'gitee' | 'gitlab'

export interface RemoteGitConfig {
  provider: GitProvider
  owner: string
  repo: string
  branch: string
  token: string
  contentRoot: string
  autoPush: boolean
  host: string
  firstPushDone: boolean
}

export type RemoteGitStatus = 'disconnected' | 'ready' | 'error'

const status = ref<RemoteGitStatus>('disconnected')
const config = ref<RemoteGitConfig | null>(null)
const lastError = ref('')
const lastPushAt = ref('')

const PROVIDERS: Record<
  GitProvider,
  { label: string; api: string; page: string; tokenHelp: string }
> = {
  github: {
    label: 'GitHub',
    api: 'https://api.github.com',
    page: 'https://github.com',
    tokenHelp: 'https://github.com/settings/tokens',
  },
  gitee: {
    label: 'Gitee',
    api: 'https://gitee.com/api/v5',
    page: 'https://gitee.com',
    tokenHelp: 'https://gitee.com/personal_access_tokens',
  },
  gitlab: {
    label: 'GitLab',
    api: 'https://gitlab.com/api/v4',
    page: 'https://gitlab.com',
    tokenHelp: 'https://gitlab.com/-/user_settings/personal_access_tokens',
  },
}

export function gitProviders() {
  return PROVIDERS
}

export function detectProviderFromRepo(input: string): GitProvider | null {
  const text = input.trim().toLowerCase()
  if (!text) return null
  if (text.includes('gitee.com')) return 'gitee'
  if (text.includes('github.com')) return 'github'
  if (text.includes('gitlab.com')) return 'gitlab'
  return null
}

function normalizeRoot(root: string) {
  return root.replace(/^\/+|\/+$/g, '') || 'src/content'
}

function remotePath(relPath: string, root: string) {
  const clean = relPath.replace(/^\/+/, '')
  return `${normalizeRoot(root)}/${clean}`
}

export function parseRepoInput(input: string, provider: GitProvider): { owner: string; repo: string; host: string } {
  let trimmed = input.trim()
  const fallbackHost = PROVIDERS[provider].page
  if (!trimmed) return { owner: '', repo: '', host: fallbackHost }

  trimmed = trimmed.replace(/\.git$/i, '')

  const ssh = trimmed.match(/^(?:git@|ssh:\/\/git@)([^:/]+)[:/]+(.+)$/i)
  if (ssh) {
    const hostName = ssh[1] ?? ''
    const parts = (ssh[2] ?? '').replace(/^\/+|\/+$/g, '').split('/').filter(Boolean)
    return {
      owner: parts[0] ?? '',
      repo: parts.slice(1).join('/'),
      host: `https://${hostName}`,
    }
  }

  try {
    if (/^https?:\/\//i.test(trimmed)) {
      const url = new URL(trimmed)
      const parts = url.pathname.replace(/^\/+|\/+$/g, '').split('/').filter(Boolean)
      return {
        owner: parts[0] ?? '',
        repo: parts.slice(1).join('/'),
        host: `${url.protocol}//${url.host}`,
      }
    }
  } catch {
    /* fall through */
  }

  const parts = trimmed.replace(/^\/+|\/+$/g, '').split('/').filter(Boolean)
  return {
    owner: parts[0] ?? '',
    repo: parts.slice(1).join('/'),
    host: fallbackHost,
  }
}

function giteeHeaders(token: string): HeadersInit {
  return {
    Accept: 'application/json',
    Authorization: `token ${token}`,
  }
}

function apiBase(cfg: RemoteGitConfig) {
  if (useLocalGitProxy()) {
    if (cfg.provider === 'gitee') return '/__git/gitee'
    if (cfg.provider === 'github') return '/__git/github'
    if (cfg.provider === 'gitlab') {
      const host = cfg.host.replace(/\/+$/, '') || 'https://gitlab.com'
      if (/gitlab\.com$/i.test(host.replace(/^https?:\/\//, ''))) return '/__git/gitlab'
      return `${host}/api/v4`
    }
  }
  if (cfg.provider === 'gitlab') {
    const host = cfg.host.replace(/\/+$/, '') || 'https://gitlab.com'
    return `${host}/api/v4`
  }
  return PROVIDERS[cfg.provider].api
}

function gitlabProject(cfg: RemoteGitConfig) {
  return encodeURIComponent(`${cfg.owner}/${cfg.repo}`)
}

async function readJson(response: Response) {
  const text = await response.text()
  if (!text) return {}
  try {
    return JSON.parse(text) as Record<string, unknown>
  } catch {
    return { message: text }
  }
}

function apiMessage(body: Record<string, unknown>, fallback: string) {
  if (typeof body.message === 'string' && body.message) {
    if (/bad credentials/i.test(body.message)) {
      return '令牌无效，或平台选错了。Gitee 仓库必须选 Gitee，并使用 Gitee 私人令牌，不能用 GitHub 令牌。'
    }
    return body.message
  }
  if (Array.isArray(body.message)) return body.message.map(String).join('; ')
  if (typeof body.error === 'string' && body.error) return body.error
  return fallback
}

function describeNetworkError(error: unknown, fallback: string) {
  const message = error instanceof Error ? error.message : ''
  if (/failed to fetch|networkerror|load failed/i.test(message)) {
    return '浏览器无法访问该平台接口（跨域或网络）。请用 npm run dev 打开后再绑，或检查令牌权限。'
  }
  return message || fallback
}

function useLocalGitProxy() {
  if (typeof window === 'undefined') return false
  const host = window.location.hostname
  return host === 'localhost' || host === '127.0.0.1'
}

async function persistIdb(next: RemoteGitConfig | null) {
  const db = await openDb()
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_REMOTE, 'readwrite')
    const store = tx.objectStore(STORE_REMOTE)
    if (next) store.put(next, CONFIG_KEY)
    else store.delete(CONFIG_KEY)
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

function parseSavedConfig(raw: unknown): RemoteGitConfig | null {
  if (!raw || typeof raw !== 'object') return null
  const item = raw as Partial<RemoteGitConfig>
  if (!item.token || !item.owner || !item.repo) return null
  if (item.provider !== 'github' && item.provider !== 'gitee' && item.provider !== 'gitlab') return null
  return {
    provider: item.provider,
    owner: String(item.owner),
    repo: String(item.repo).replace(/\.git$/i, ''),
    branch: String(item.branch || 'main'),
    token: String(item.token),
    contentRoot: normalizeRoot(String(item.contentRoot || 'src/content')),
    autoPush: item.autoPush !== false,
    host: String(item.host || PROVIDERS[item.provider].page),
    firstPushDone: Boolean(item.firstPushDone),
  }
}

async function persistDisk(next: RemoteGitConfig | null) {
  if (!getWorkspaceRoot()) return false
  if (!next) {
    await removeProjectFile(REMOTE_CREDENTIALS_FILE)
    return true
  }
  await writeProjectFile(REMOTE_CREDENTIALS_FILE, `${JSON.stringify(next, null, 2)}\n`)
  return true
}

async function persistConfig(next: RemoteGitConfig | null) {
  await persistIdb(next)
  try {
    await persistDisk(next)
  } catch (error) {
    console.warn(error)
  }
}

async function loadIdb(): Promise<RemoteGitConfig | null> {
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_REMOTE, 'readonly')
    const req = tx.objectStore(STORE_REMOTE).get(CONFIG_KEY)
    req.onsuccess = () => resolve(parseSavedConfig(req.result))
    req.onerror = () => reject(req.error)
  })
}

async function loadDisk(): Promise<RemoteGitConfig | null> {
  const text = await readProjectFile(REMOTE_CREDENTIALS_FILE)
  if (!text) return null
  try {
    return parseSavedConfig(JSON.parse(text) as unknown)
  } catch {
    return null
  }
}

async function loadConfig(): Promise<RemoteGitConfig | null> {
  const fromDisk = await loadDisk()
  if (fromDisk) {
    await persistIdb(fromDisk)
    return fromDisk
  }
  const fromIdb = await loadIdb()
  if (fromIdb) {
    try {
      await persistDisk(fromIdb)
    } catch {
      /* folder not granted yet */
    }
  }
  return fromIdb
}

async function verifyBinding(cfg: RemoteGitConfig) {
  if (cfg.provider === 'github') {
    const response = await fetch(`${apiBase(cfg)}/repos/${cfg.owner}/${cfg.repo}`, {
      headers: {
        Accept: 'application/vnd.github+json',
        Authorization: `Bearer ${cfg.token}`,
      },
    })
    const body = await readJson(response)
    if (!response.ok) throw new Error(apiMessage(body, '无法访问该 GitHub 仓库'))
    return
  }
  if (cfg.provider === 'gitee') {
    const response = await fetch(`${apiBase(cfg)}/repos/${encodeURIComponent(cfg.owner)}/${encodeURIComponent(cfg.repo)}`, {
      headers: giteeHeaders(cfg.token),
    })
    const body = await readJson(response)
    if (!response.ok) throw new Error(apiMessage(body, '无法访问该 Gitee 仓库'))
    const defaultBranch = typeof body.default_branch === 'string' ? body.default_branch : ''
    if (cfg.branch && defaultBranch && cfg.branch !== defaultBranch) {
      const branchRes = await fetch(
        `${apiBase(cfg)}/repos/${encodeURIComponent(cfg.owner)}/${encodeURIComponent(cfg.repo)}/branches/${encodeURIComponent(cfg.branch)}`,
        { headers: giteeHeaders(cfg.token) },
      )
      if (branchRes.status === 404) {
        throw new Error(`仓库默认分支是 ${defaultBranch}，当前填写的是 ${cfg.branch}`)
      }
    }
    return
  }
  const response = await fetch(`${apiBase(cfg)}/projects/${gitlabProject(cfg)}`, {
    headers: { 'PRIVATE-TOKEN': cfg.token },
  })
  const body = await readJson(response)
  if (!response.ok) throw new Error(apiMessage(body, '无法访问该 GitLab 项目'))
}

export async function hydrateRemoteGit() {
  try {
    const saved = await loadConfig()
    if (!saved?.token || !saved.owner || !saved.repo) {
      config.value = null
      status.value = 'disconnected'
      return
    }
    config.value = saved
    status.value = 'ready'
    lastError.value = ''
  } catch (error) {
    console.warn(error)
    status.value = 'disconnected'
    config.value = null
  }
}

export async function bindRemoteGit(input: {
  provider: GitProvider
  repoInput: string
  branch: string
  token: string
  contentRoot: string
  autoPush: boolean
  host?: string
}): Promise<void> {
  lastError.value = ''
  const detected = detectProviderFromRepo(input.repoInput)
  const provider = detected ?? input.provider
  if (detected && detected !== input.provider) {
    lastError.value = `仓库地址是 ${PROVIDERS[detected].label}，已按 ${PROVIDERS[detected].label} 验证。请使用该平台的访问令牌。`
  }
  const parsed = parseRepoInput(input.repoInput, provider)
  if (!parsed.owner || !parsed.repo) throw new Error('REPO_INVALID')
  const token = input.token.trim() || config.value?.token || (await loadConfig())?.token || ''
  if (!token) throw new Error('TOKEN_REQUIRED')
  const next: RemoteGitConfig = {
    provider,
    owner: parsed.owner,
    repo: parsed.repo.replace(/\.git$/i, ''),
    branch: input.branch.trim() || 'main',
    token,
    contentRoot: normalizeRoot(input.contentRoot),
    autoPush: input.autoPush,
    host: (input.host?.trim() || parsed.host || PROVIDERS[provider].page).replace(/\/+$/, ''),
    firstPushDone: Boolean(
      config.value?.firstPushDone &&
        config.value.owner === parsed.owner &&
        config.value.repo === parsed.repo.replace(/\.git$/i, ''),
    ),
  }
  try {
    await verifyBinding(next)
  } catch (error) {
    status.value = 'error'
    lastError.value = describeNetworkError(error, 'BIND_FAILED')
    throw new Error(lastError.value)
  }
  await persistConfig(next)
  config.value = next
  status.value = 'ready'
}

export async function updateRemoteOptions(patch: Partial<Pick<RemoteGitConfig, 'autoPush' | 'contentRoot' | 'branch'>>) {
  if (!config.value) return
  const next = { ...config.value, ...patch }
  if (patch.contentRoot) next.contentRoot = normalizeRoot(patch.contentRoot)
  await persistConfig(next)
  config.value = next
}

export async function disconnectRemoteGit() {
  await persistConfig(null)
  config.value = null
  status.value = 'disconnected'
  lastError.value = ''
  lastPushAt.value = ''
}

export async function pushWorkspaceFile(relPath: string, _text: string, message?: string) {
  const cfg = config.value
  if (!cfg || status.value !== 'ready') throw new Error('REMOTE_NOT_READY')
  lastError.value = ''
  const path = remotePath(relPath, cfg.contentRoot)
  const commitMessage = message?.trim() || `docs: update ${path}`
  try {
    const { commitAndPushProject } = await import('@/lib/git-sync')
    await commitAndPushProject({
      message: commitMessage,
      provider: cfg.provider,
      owner: cfg.owner,
      repo: cfg.repo,
      branch: cfg.branch,
      token: cfg.token,
      host: cfg.host,
      filepaths: [path],
    })
    lastPushAt.value = new Date().toISOString()
  } catch (error) {
    lastError.value = error instanceof Error ? error.message : 'PUSH_FAILED'
    throw error
  }
}

export interface PushProgress {
  percent: number
  done: number
  total: number
  current: string
}

export async function pushWorkspaceDocs(
  message: string,
  onProgress?: (progress: PushProgress) => void,
): Promise<{ first: boolean; count: number }> {
  const cfg = config.value
  if (!cfg || status.value !== 'ready') throw new Error('REMOTE_NOT_READY')
  lastError.value = ''
  try {
    const { commitAndPushProject } = await import('@/lib/git-sync')
    const result = await commitAndPushProject({
      message,
      provider: cfg.provider,
      owner: cfg.owner,
      repo: cfg.repo,
      branch: cfg.branch,
      token: cfg.token,
      host: cfg.host,
      onProgress,
    })
    lastPushAt.value = new Date().toISOString()
    const first = !cfg.firstPushDone
    if (first) {
      const next = { ...cfg, firstPushDone: true }
      await persistConfig(next)
      config.value = next
    }
    return { first, count: result.staged }
  } catch (error) {
    const text = error instanceof Error ? error.message : 'PUSH_FAILED'
    lastError.value = text === 'NEED_WORKSPACE' ? '请先授权本地工作目录，才能按 .gitignore 提交整个项目。' : text
    throw new Error(lastError.value)
  }
}

export function useRemoteGit() {
  return {
    status: computed(() => status.value),
    ready: computed(() => status.value === 'ready' && Boolean(config.value)),
    autoPush: computed(() => Boolean(config.value?.autoPush)),
    lastError: computed(() => lastError.value),
    lastPushAt: computed(() => lastPushAt.value),
    summary: computed(() => {
      const item = config.value
      if (!item) return ''
      return `${PROVIDERS[item.provider].label} ${item.owner}/${item.repo}#${item.branch}`
    }),
    provider: computed(() => config.value?.provider ?? null),
    contentRoot: computed(() => config.value?.contentRoot ?? 'src/content'),
    repoPath: computed(() => (config.value ? `${config.value.owner}/${config.value.repo}` : '')),
    branch: computed(() => config.value?.branch ?? 'main'),
    host: computed(() => config.value?.host ?? ''),
    firstPushDone: computed(() => Boolean(config.value?.firstPushDone)),
    tokenStored: computed(() => Boolean(config.value?.token)),
    tokenFile: REMOTE_CREDENTIALS_FILE,
  }
}
