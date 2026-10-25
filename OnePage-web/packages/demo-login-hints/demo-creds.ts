export type DemoSite = 'community' | 'creator' | 'ops'

export type DemoRow = { label: string; text: string; copy: string }
export type DemoGroup = { title: string; rows: DemoRow[] }

export function demoGroupsFor(site: DemoSite): DemoGroup[] {
  const communityPassword = [
    { label: '账号', text: 'linzhou', copy: 'linzhou' },
    { label: '密码', text: 'Linzhou8', copy: 'Linzhou8' },
  ]
  const opsPassword = [
    { label: '账号', text: 'suwan', copy: 'suwan' },
    { label: '密码', text: 'Suwan8x', copy: 'Suwan8x' },
  ]

  if (site === 'community') {
    return [{ title: '社区站', rows: communityPassword }]
  }
  if (site === 'creator') {
    return [{ title: '创作者中心', rows: communityPassword }]
  }
  return [{ title: '运营后台', rows: opsPassword }]
}
