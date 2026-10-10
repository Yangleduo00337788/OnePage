export type RoleId = 'rd' | 'fe' | 'pm' | 'qa' | 'op' | 'uiue' | 'pom'

export interface RoleDef {
  id: RoleId
  shortName: string
  titleZh: string
  titleEn: string
  descZh: string
  descEn: string
  /** 该角色负责维护的文档 slug（含分类入口） */
  slugs: string[]
}

export const ROLES: RoleDef[] = [
  {
    id: 'rd',
    shortName: 'RD',
    titleZh: '研发设计',
    titleEn: 'Engineering',
    descZh: '技术方案、接口契约、领域设计和研发协作记录',
    descEn: 'Architecture, API contracts, domain design, and engineering notes',
    slugs: ['engineering', 'engineering/tech', 'engineering/tech/architecture', 'engineering/tech/domain', 'engineering/process', 'engineering/process/conventions', 'engineering/process/contracts', 'engineering/process/collab'],
  },
  {
    id: 'fe',
    shortName: 'FE',
    titleZh: '前端研发',
    titleEn: 'Frontend',
    descZh: '前端结构、交互状态、组件契约和可访问性说明',
    descEn: 'Frontend structure, interaction states, component contracts, a11y',
    slugs: ['engineering/frontend'],
  },
  {
    id: 'pm',
    shortName: 'PM',
    titleZh: '产品设计',
    titleEn: 'Product',
    descZh: '用户场景、PRD、业务流程、思维导图、交互原型、排期图与验收标准',
    descEn: 'Scenarios, PRD, flows, prototypes, schedule, and acceptance',
    slugs: ['product', 'product/definition', 'product/discovery', 'product/discovery/users', 'product/spec', 'product/spec/prd', 'product/spec/prototypes', 'project/charter', 'project/charter/overview', 'project/charter/goals'],
  },
  {
    id: 'qa',
    shortName: 'QA',
    titleZh: '测试',
    titleEn: 'QA',
    descZh: '测试计划、用例、执行证据和缺陷记录',
    descEn: 'Test plans, cases, evidence, and defect logs',
    slugs: ['quality', 'quality/plan', 'quality/plan/testing', 'quality/run', 'quality/run/cases', 'quality/run/evidence', 'quality/run/defects'],
  },
  {
    id: 'op',
    shortName: 'OP',
    titleZh: '运维',
    titleEn: 'Ops',
    descZh: '环境说明、发布流程、监控指标、应急和回滚手册',
    descEn: 'Environments, release, monitoring, incident and rollback',
    slugs: ['delivery/ops'],
  },
  {
    id: 'uiue',
    shortName: 'UIUE',
    titleZh: '视觉与体验设计',
    titleEn: 'Design',
    descZh: '视觉规范、交互稿、体验说明和设计交付',
    descEn: 'Visual specs, interaction drafts, UX notes, and design handoff',
    slugs: ['design', 'design/system', 'design/system/experience', 'design/system/specs', 'design/handoff'],
  },
  {
    id: 'pom',
    shortName: 'POM',
    titleZh: '项目与交付管理',
    titleEn: 'Program',
    descZh: '里程碑、依赖、风险、进度和跨角色交接',
    descEn: 'Milestones, dependencies, risks, progress, and handoff',
    slugs: ['delivery', 'delivery/management', 'project/governance', 'project/governance/roles', 'project/governance/status', 'records', 'records/decisions'],
  },
]

export function slugsForRoles(ids: RoleId[]): Set<string> {
  const next = new Set<string>()
  for (const role of ROLES) {
    if (!ids.includes(role.id)) continue
    for (const slug of role.slugs) next.add(slug)
  }
  return next
}

export function isSharedWritableSlug(slug: string): boolean {
  if (!slug) return true
  return slug === 'start' || slug.startsWith('start/')
}

export function isOwnedSlug(slug: string, owned: Set<string>): boolean {
  if (isSharedWritableSlug(slug)) return true
  if (owned.has(slug)) return true
  for (const item of owned) {
    if (slug.startsWith(`${item}/`) || item.startsWith(`${slug}/`)) return true
  }
  return false
}
