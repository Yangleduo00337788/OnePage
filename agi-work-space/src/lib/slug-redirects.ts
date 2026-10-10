/** 模板改成多层目录后，旧 slug 仍可打开。 */
export const SLUG_REDIRECTS: Record<string, string> = {
  'project/overview': 'project/charter/overview',
  'project/goals': 'project/charter/goals',
  'project/roles': 'project/governance/roles',
  'project/status': 'project/governance/status',
  'product/users': 'product/discovery/users',
  'product/prd': 'product/spec/prd',
  'product/prototypes': 'product/spec/prototypes',
  'engineering/architecture': 'engineering/tech/architecture',
  'engineering/domain': 'engineering/tech/domain',
  'engineering/contracts': 'engineering/process/contracts',
  'engineering/conventions': 'engineering/process/conventions',
  'engineering/collab': 'engineering/process/collab',
  'quality/testing': 'quality/plan/testing',
  'quality/cases': 'quality/run/cases',
  'quality/evidence': 'quality/run/evidence',
  'quality/defects': 'quality/run/defects',
  'design/experience': 'design/system/experience',
  'design/specs': 'design/system/specs',
}

export function redirectedSlug(slug: string): string | undefined {
  return SLUG_REDIRECTS[slug]
}
