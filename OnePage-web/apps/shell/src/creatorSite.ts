export { creatorOrigin as CREATOR_ORIGIN } from '@onepage/site-urls'
import { creatorOrigin, openCreatorSite as openCreatorTab } from '@onepage/site-urls'

export const CREATOR_OPEN_BLOCKED = 'onepage:creator-open-blocked'

/** 社区壳内打开创作者站；弹窗被拦时发事件，由 App 提示 */
export function openCreatorSite(): boolean {
  if (openCreatorTab()) return true
  window.dispatchEvent(
    new CustomEvent(CREATOR_OPEN_BLOCKED, { detail: { url: creatorOrigin() } }),
  )
  return false
}
