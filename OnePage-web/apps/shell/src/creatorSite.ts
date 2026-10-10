/** 创作者中心是另一个站点。新开标签，不覆盖当前社区页。 */
export const CREATOR_ORIGIN = import.meta.env.VITE_CREATOR_ORIGIN || 'http://localhost:5175'

export function openCreatorSite() {
  window.open(CREATOR_ORIGIN, '_blank', 'noopener,noreferrer')
}
