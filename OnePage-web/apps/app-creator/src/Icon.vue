<!-- 创作者后台用的线框图标，不依赖社区壳。 -->
<script setup lang="ts">
withDefaults(defineProps<{ name: string; size?: number; stroke?: number }>(), {
  size: 20,
  stroke: 1.75,
})

type IconNode =
  | { tag: 'path'; d: string }
  | { tag: 'circle'; cx: number; cy: number; r: number }
  | { tag: 'rect'; x: number; y: number; width: number; height: number; rx?: number }
  | { tag: 'line'; x1: number; y1: number; x2: number; y2: number }

const icons: Record<string, IconNode[]> = {
  house: [
    { tag: 'path', d: 'M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8' },
    { tag: 'path', d: 'M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z' },
  ],
  'sticky-note': [
    { tag: 'path', d: 'M16 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V8Z' },
    { tag: 'path', d: 'M15 3v4a2 2 0 0 0 2 2h4' },
  ],
  'chart-column': [
    { tag: 'path', d: 'M3 3v16a2 2 0 0 0 2 2h16' },
    { tag: 'path', d: 'M18 17V9' },
    { tag: 'path', d: 'M13 17V5' },
    { tag: 'path', d: 'M8 17v-3' },
  ],
  calendar: [
    { tag: 'path', d: 'M8 2v4' },
    { tag: 'path', d: 'M16 2v4' },
    { tag: 'rect', x: 3, y: 4, width: 18, height: 18, rx: 2 },
    { tag: 'path', d: 'M3 10h18' },
  ],
  'book-open': [
    { tag: 'path', d: 'M12 7v14' },
    { tag: 'path', d: 'M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-6a3 3 0 0 0-3 3 3 3 0 0 0-3-3z' },
  ],
  'panel-left': [
    { tag: 'rect', x: 3, y: 3, width: 18, height: 18, rx: 2 },
    { tag: 'path', d: 'M9 3v18' },
  ],
  'chevron-down': [{ tag: 'path', d: 'M6 9l6 6 6-6' }],
  'chevron-right': [{ tag: 'path', d: 'M9 18l6-6-6-6' }],
  'heart-plus': [
    { tag: 'path', d: 'M13.5 19.5 12 21l-7-6c-1.5-1.4-3-3.9-3-6.5A5.5 5.5 0 0 1 12 3a5.5 5.5 0 0 1 10 4c0 .3 0 .6-.1.9' },
    { tag: 'path', d: 'M15 15h6' },
    { tag: 'path', d: 'M18 12v6' },
  ],
  plus: [
    { tag: 'path', d: 'M5 12h14' },
    { tag: 'path', d: 'M12 5v14' },
  ],
  image: [
    { tag: 'rect', x: 3, y: 3, width: 18, height: 18, rx: 2 },
    { tag: 'circle', cx: 9, cy: 9, r: 2 },
    { tag: 'path', d: 'm21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21' },
  ],
  'circle-play': [
    { tag: 'path', d: 'M9 9.003a1 1 0 0 1 1.517-.859l4.997 2.997a1 1 0 0 1 0 1.718l-4.997 2.997A1 1 0 0 1 9 14.996z' },
    { tag: 'circle', cx: 12, cy: 12, r: 10 },
  ],
  'file-text': [
    { tag: 'path', d: 'M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z' },
    { tag: 'path', d: 'M14 2v4a2 2 0 0 0 2 2h4' },
    { tag: 'path', d: 'M10 9H8' },
    { tag: 'path', d: 'M16 13H8' },
    { tag: 'path', d: 'M16 17H8' },
  ],
  pencil: [
    { tag: 'path', d: 'M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z' },
    { tag: 'path', d: 'm15 5 4 4' },
  ],
  headphones: [
    { tag: 'path', d: 'M3 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a9 9 0 0 1 18 0v7a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3' },
  ],
  'cloud-upload': [
    { tag: 'path', d: 'M12 13v8' },
    { tag: 'path', d: 'M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242' },
    { tag: 'path', d: 'm8 17 4-4 4 4' },
  ],
  search: [
    { tag: 'circle', cx: 11, cy: 11, r: 8 },
    { tag: 'path', d: 'm21 21-4.3-4.3' },
  ],
  x: [
    { tag: 'path', d: 'M18 6 6 18' },
    { tag: 'path', d: 'm6 6 12 12' },
  ],
  list: [
    { tag: 'path', d: 'M8 6h13' },
    { tag: 'path', d: 'M8 12h13' },
    { tag: 'path', d: 'M8 18h13' },
    { tag: 'path', d: 'M3 6h.01' },
    { tag: 'path', d: 'M3 12h.01' },
    { tag: 'path', d: 'M3 18h.01' },
  ],
  layout: [
    { tag: 'rect', x: 3, y: 3, width: 7, height: 7, rx: 1 },
    { tag: 'rect', x: 14, y: 3, width: 7, height: 7, rx: 1 },
    { tag: 'rect', x: 14, y: 14, width: 7, height: 7, rx: 1 },
    { tag: 'rect', x: 3, y: 14, width: 7, height: 7, rx: 1 },
  ],
  radio: [
    { tag: 'path', d: 'M4.9 19.1C1 15.2 1 8.8 4.9 4.9' },
    { tag: 'path', d: 'M7.8 16.2c-2.3-2.3-2.3-6.1 0-8.5' },
    { tag: 'circle', cx: 12, cy: 12, r: 2 },
    { tag: 'path', d: 'M16.2 7.8c2.3 2.3 2.3 6.1 0 8.5' },
    { tag: 'path', d: 'M19.1 4.9C23 8.8 23 15.2 19.1 19.1' },
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
      <line v-else-if="n.tag === 'line'" :x1="n.x1" :y1="n.y1" :x2="n.x2" :y2="n.y2" />
    </template>
  </svg>
</template>
