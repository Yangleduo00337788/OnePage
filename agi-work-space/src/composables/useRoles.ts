import { computed, ref } from 'vue'
import { slugsForRoles, type RoleId } from '@/lib/roles'
import {
  normalizeRoleIds,
  readWorkspaceRolesFromDisk,
  writeWorkspaceRolesToDisk,
} from '@/lib/workspace-roles'
import { getWorkspaceRoot } from '@/lib/workspace-fs'

const STORAGE_KEY = 'agi-roles'

function readCachedRoles(): RoleId[] {
  if (typeof localStorage === 'undefined') return []
  try {
    return normalizeRoleIds(JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]') as unknown)
  } catch {
    return []
  }
}

const selected = ref<RoleId[]>(readCachedRoles())
let writeChain: Promise<void> = Promise.resolve()

function persistCache() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(selected.value))
}

function persistDisk() {
  if (!getWorkspaceRoot()) return
  const snapshot = [...selected.value]
  writeChain = writeChain
    .then(() => writeWorkspaceRolesToDisk(snapshot))
    .catch(() => {
      /* keep cache; next toggle or hydrate retries */
    })
}

export function applySelectedRoles(ids: RoleId[]) {
  selected.value = normalizeRoleIds(ids)
  persistCache()
}

export async function hydrateRoles() {
  const fromDisk = await readWorkspaceRolesFromDisk()
  if (fromDisk === null) {
    persistDisk()
    return
  }
  if (fromDisk.length === 0 && selected.value.length > 0) {
    persistDisk()
    return
  }
  applySelectedRoles(fromDisk)
}

export function useRoles() {
  const ownedSlugs = computed(() => slugsForRoles(selected.value))

  function toggle(id: RoleId) {
    if (selected.value.includes(id)) {
      selected.value = selected.value.filter((item) => item !== id)
    } else {
      selected.value = [...selected.value, id]
    }
    persistCache()
    persistDisk()
  }

  return { selected, ownedSlugs, toggle }
}
