import { bannerForUser, coverForNote, portraitForUser, videoForNote } from './media'

export function coverUrl(seed: string, title: string) {
  return coverForNote(title || '笔记', '', '生活', seed)
}

export function avatarUrl(seed: string, name: string) {
  return portraitForUser(seed, name)
}

export function bannerUrl(seed: string) {
  return bannerForUser(seed)
}

export const SAMPLE_VIDEO = videoForNote('样片', '演示', '生活', 'sample')
export const SAMPLE_HLS = SAMPLE_VIDEO
