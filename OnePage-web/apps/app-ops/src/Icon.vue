<!-- 运营后台线框图标，不依赖社区壳。 -->
<script setup lang="ts">
withDefaults(defineProps<{ name: string; size?: number; stroke?: number }>(), {
  size: 20,
  stroke: 1.75,
})

type IconNode =
  | { tag: 'path'; d: string }
  | { tag: 'circle'; cx: number; cy: number; r: number }
  | { tag: 'rect'; x: number; y: number; width: number; height: number; rx?: number }

const icons: Record<string, IconNode[]> = {
  house: [
    { tag: 'path', d: 'M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8' },
    {
      tag: 'path',
      d: 'M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z',
    },
  ],
  flag: [
    { tag: 'path', d: 'M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z' },
    { tag: 'path', d: 'M4 22V2' },
  ],
  users: [
    { tag: 'path', d: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2' },
    { tag: 'circle', cx: 9, cy: 7, r: 4 },
    { tag: 'path', d: 'M22 21v-2a4 4 0 0 0-3-3.87' },
    { tag: 'path', d: 'M16 3.13a4 4 0 0 1 0 7.75' },
  ],
  'panel-left': [
    { tag: 'rect', x: 3, y: 3, width: 18, height: 18, rx: 2 },
    { tag: 'path', d: 'M9 3v18' },
  ],
  'chevron-down': [{ tag: 'path', d: 'M6 9l6 6 6-6' }],
  search: [
    { tag: 'circle', cx: 11, cy: 11, r: 8 },
    { tag: 'path', d: 'm21 21-4.3-4.3' },
  ],
  'sticky-note': [
    { tag: 'path', d: 'M16 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V8Z' },
    { tag: 'path', d: 'M15 3v5h5' },
  ],
  'message-square': [
    { tag: 'path', d: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z' },
  ],
  calendar: [
    { tag: 'rect', x: 3, y: 4, width: 18, height: 18, rx: 2 },
    { tag: 'path', d: 'M16 2v4' },
    { tag: 'path', d: 'M8 2v4' },
    { tag: 'path', d: 'M3 10h18' },
  ],
  hash: [
    { tag: 'path', d: 'M4 9h16' },
    { tag: 'path', d: 'M4 15h16' },
    { tag: 'path', d: 'M10 3 8 21' },
    { tag: 'path', d: 'M16 3l-2 18' },
  ],
  image: [
    { tag: 'rect', x: 3, y: 3, width: 18, height: 18, rx: 2 },
    { tag: 'circle', cx: 9, cy: 9, r: 2 },
    { tag: 'path', d: 'm21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21' },
  ],
  spark: [
    {
      tag: 'path',
      d: 'm12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z',
    },
  ],
  'layout-grid': [
    { tag: 'rect', x: 3, y: 3, width: 7, height: 7, rx: 1 },
    { tag: 'rect', x: 14, y: 3, width: 7, height: 7, rx: 1 },
    { tag: 'rect', x: 14, y: 14, width: 7, height: 7, rx: 1 },
    { tag: 'rect', x: 3, y: 14, width: 7, height: 7, rx: 1 },
  ],
}

const nodes = (name: string) => icons[name] ?? icons.house
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="stroke"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <template v-for="(n, i) in nodes(name)" :key="i">
      <path v-if="n.tag === 'path'" :d="n.d" />
      <circle v-else-if="n.tag === 'circle'" :cx="n.cx" :cy="n.cy" :r="n.r" />
      <rect
        v-else-if="n.tag === 'rect'"
        :x="n.x"
        :y="n.y"
        :width="n.width"
        :height="n.height"
        :rx="n.rx"
      />
    </template>
  </svg>
</template>
