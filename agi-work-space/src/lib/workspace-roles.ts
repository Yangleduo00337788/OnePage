import { ROLES, type RoleId } from '@/lib/roles'
import { getWorkspaceRoot, readRepoFile, writeRepoFile } from '@/lib/workspace-fs'

export const WORKSPACE_ROLES_FILE = 'src/config/workspace-roles.json'

export interface WorkspaceRoleRow {
  id: RoleId
  shortName: string
  titleZh: string
  titleEn: string
  enabled: boolean
}

export interface WorkspaceRolesFile {
  selected: RoleId[]
  roles: WorkspaceRoleRow[]
}

function allowedIds() {
  return new Set(ROLES.map((role) => role.id))
}

export function normalizeRoleIds(raw: unknown): RoleId[] {
  if (!Array.isArray(raw)) return []
  const allowed = allowedIds()
  const seen = new Set<RoleId>()
  const next: RoleId[] = []
  for (const id of raw) {
    if (typeof id !== 'string' || !allowed.has(id as RoleId)) continue
    const roleId = id as RoleId
    if (seen.has(roleId)) continue
    seen.add(roleId)
    next.push(roleId)
  }
  return next
}

export function buildWorkspaceRolesFile(selected: RoleId[]): WorkspaceRolesFile {
  const enabled = new Set(selected)
  return {
    selected,
    roles: ROLES.map((role) => ({
      id: role.id,
      shortName: role.shortName,
      titleZh: role.titleZh,
      titleEn: role.titleEn,
      enabled: enabled.has(role.id),
    })),
  }
}

export function parseWorkspaceRoles(text: string): RoleId[] | null {
  try {
    const raw = JSON.parse(text) as Partial<WorkspaceRolesFile>
    if (Array.isArray(raw.selected)) return normalizeRoleIds(raw.selected)
    if (Array.isArray(raw.roles)) {
      return normalizeRoleIds(raw.roles.filter((row) => row?.enabled).map((row) => row.id))
    }
    return null
  } catch {
    return null
  }
}

export async function readWorkspaceRolesFromDisk(): Promise<RoleId[] | null> {
  if (!getWorkspaceRoot()) return null
  const text = await readRepoFile(WORKSPACE_ROLES_FILE)
  if (!text) return null
  return parseWorkspaceRoles(text)
}

export async function writeWorkspaceRolesToDisk(selected: RoleId[]): Promise<void> {
  const payload = buildWorkspaceRolesFile(normalizeRoleIds(selected))
  await writeRepoFile(WORKSPACE_ROLES_FILE, `${JSON.stringify(payload, null, 2)}\n`)
}
