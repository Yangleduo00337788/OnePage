/** 耶耶回答：先转义再只放出段落、换行、粗体和 [n] 角标。 */
export function renderYehAnswer(text: string): string {
  const escaped = escapeHtml((text || '').trim())
  const withRefs = escaped.replace(
    /\[(\d+)\]/g,
    '<button type="button" class="yeh-ref" data-ref="$1">$1</button>',
  )
  const withBold = withRefs.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  const blocks = withBold.split(/\n{2,}/).filter(Boolean)
  if (!blocks.length) return ''
  return blocks
    .map((block) => {
      const lines = block.split('\n').filter((line) => line.trim())
      const listed = lines.length > 1 && lines.every((line) => /^\s*([-•]|\d+\.)\s+/.test(line))
      if (listed) {
        const items = lines
          .map((line) => `<li>${line.replace(/^\s*([-•]|\d+\.)\s+/, '')}</li>`)
          .join('')
        return `<ul>${items}</ul>`
      }
      return `<p>${lines.join('<br>')}</p>`
    })
    .join('')
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}
