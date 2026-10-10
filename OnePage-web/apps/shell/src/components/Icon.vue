<!--
  内联 SVG 图标。用 name 查路径，避免每个页面引一套 icon 库。
  defineProps / withDefaults：声明入参和默认尺寸。
-->
<script setup lang="ts">
withDefaults(defineProps<{ name: string; size?: number; stroke?: number }>(), {
  size: 24,
  stroke: 1.75,
})

type IconNode =
  | { tag: 'path'; d: string }
  | { tag: 'circle'; cx: number; cy: number; r: number }
  | { tag: 'rect'; x: number; y: number; width: number; height: number; rx?: number }
  | { tag: 'line'; x1: number; y1: number; x2: number; y2: number }

const icons: Record<string, IconNode[]> = {
  'house-heart': [
    { tag: 'path', d: 'M8.62 13.8A2.25 2.25 0 1 1 12 10.836a2.25 2.25 0 1 1 3.38 2.966l-2.626 2.856a.998.998 0 0 1-1.507 0z' },
    { tag: 'path', d: 'M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z' },
  ],
  'message-circle-heart': [
    { tag: 'path', d: 'M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719' },
    { tag: 'path', d: 'M7.828 13.07A3 3 0 0 1 12 8.764a3 3 0 0 1 5.004 2.224 3 3 0 0 1-.832 2.083l-3.447 3.62a1 1 0 0 1-1.45-.001z' },
  ],
  'circle-play': [
    { tag: 'path', d: 'M9 9.003a1 1 0 0 1 1.517-.859l4.997 2.997a1 1 0 0 1 0 1.718l-4.997 2.997A1 1 0 0 1 9 14.996z' },
    { tag: 'circle', cx: 12, cy: 12, r: 10 },
  ],
  'circle-pause': [
    { tag: 'circle', cx: 12, cy: 12, r: 10 },
    { tag: 'line', x1: 10, y1: 9, x2: 10, y2: 15 },
    { tag: 'line', x1: 14, y1: 9, x2: 14, y2: 15 },
  ],
  'tv-minimal-play': [
    { tag: 'path', d: 'M15.033 9.44a.647.647 0 0 1 0 1.12l-4.065 2.352a.645.645 0 0 1-.968-.56V7.648a.645.645 0 0 1 .967-.56z' },
    { tag: 'path', d: 'M7 21h10' },
    { tag: 'rect', x: 2, y: 3, width: 20, height: 14, rx: 2 },
  ],
  'badge-plus': [
    { tag: 'path', d: 'M3.85 8.62a4 4 0 0 1 4.78-4.77 4 4 0 0 1 6.74 0 4 4 0 0 1 4.78 4.78 4 4 0 0 1 0 6.74 4 4 0 0 1-4.77 4.78 4 4 0 0 1-6.75 0 4 4 0 0 1-4.78-4.77 4 4 0 0 1 0-6.76Z' },
    { tag: 'line', x1: 12, y1: 8, x2: 12, y2: 16 },
    { tag: 'line', x1: 8, y1: 12, x2: 16, y2: 12 },
  ],
  'bell-ring': [
    { tag: 'path', d: 'M10.268 21a2 2 0 0 0 3.464 0' },
    { tag: 'path', d: 'M22 8c0-2.3-.8-4.3-2-6' },
    { tag: 'path', d: 'M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326' },
    { tag: 'path', d: 'M4 2C2.8 3.7 2 5.7 2 8' },
  ],
  'message-circle-more': [
    { tag: 'path', d: 'M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719' },
    { tag: 'path', d: 'M8 12h.01' },
    { tag: 'path', d: 'M12 12h.01' },
    { tag: 'path', d: 'M16 12h.01' },
  ],
  smile: [
    { tag: 'circle', cx: 12, cy: 12, r: 10 },
    { tag: 'path', d: 'M8 14s1.5 2 4 2 4-2 4-2' },
    { tag: 'line', x1: 9, y1: 9, x2: 9.01, y2: 9 },
    { tag: 'line', x1: 15, y1: 9, x2: 15.01, y2: 9 },
  ],
  menu: [
    { tag: 'path', d: 'M4 5h16' },
    { tag: 'path', d: 'M4 12h16' },
    { tag: 'path', d: 'M4 19h16' },
  ],
  info: [
    { tag: 'circle', cx: 12, cy: 12, r: 10 },
    { tag: 'path', d: 'M12 16v-4' },
    { tag: 'path', d: 'M12 8h.01' },
  ],
  search: [
    { tag: 'path', d: 'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16zm10-2-4.3-4.3' },
  ],
  spark: [
    { tag: 'path', d: 'M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M18 6l-2.5 2.5M8.5 15.5 6 18' },
    { tag: 'circle', cx: 12, cy: 12, r: 4 },
  ],
  clock: [
    { tag: 'circle', cx: 12, cy: 12, r: 10 },
    { tag: 'path', d: 'M12 6v6l4 2' },
  ],
  x: [
    { tag: 'path', d: 'M18 6 6 18' },
    { tag: 'path', d: 'm6 6 12 12' },
  ],
  heart: [
    { tag: 'path', d: 'M12 19s-7-4.4-7-9a4 4 0 0 1 7-2 4 4 0 0 1 7 2c0 4.6-7 9-7 9z' },
  ],
  play: [
    { tag: 'path', d: 'M9 7.5v9l8-4.5z' },
  ],
  pause: [
    { tag: 'rect', x: 6, y: 4, width: 4, height: 16, rx: 1 },
    { tag: 'rect', x: 14, y: 4, width: 4, height: 16, rx: 1 },
  ],
  chevron: [
    { tag: 'path', d: 'M9 6l6 6-6 6' },
  ],
  sun: [
    { tag: 'circle', cx: 12, cy: 12, r: 4 },
    { tag: 'path', d: 'M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41' },
  ],
  moon: [{ tag: 'path', d: 'M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z' }],
  monitor: [
    { tag: 'rect', x: 2, y: 3, width: 20, height: 14, rx: 2 },
    { tag: 'line', x1: 8, y1: 21, x2: 16, y2: 21 },
    { tag: 'line', x1: 12, y1: 17, x2: 12, y2: 21 },
  ],
  'chevron-left': [{ tag: 'path', d: 'M15 18l-6-6 6-6' }],
  'chevron-up': [{ tag: 'path', d: 'M18 15l-6-6-6 6' }],
  refresh: [
    { tag: 'path', d: 'M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8' },
    { tag: 'path', d: 'M21 3v5h-5' },
    { tag: 'path', d: 'M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16' },
    { tag: 'path', d: 'M8 16H3v5' },
  ],
  pencil: [
    { tag: 'path', d: 'M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z' },
    { tag: 'path', d: 'm15 5 4 4' },
  ],
  mars: [
    { tag: 'path', d: 'M16 3h5v5' },
    { tag: 'path', d: 'm21 3-6.75 6.75' },
    { tag: 'circle', cx: 10, cy: 14, r: 6 },
  ],
  venus: [
    { tag: 'circle', cx: 12, cy: 8, r: 5 },
    { tag: 'path', d: 'M12 13v8' },
    { tag: 'path', d: 'M9 18h6' },
  ],
  camera: [
    { tag: 'path', d: 'M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3z' },
    { tag: 'circle', cx: 12, cy: 13, r: 3 },
  ],
  copy: [
    { tag: 'rect', x: 9, y: 9, width: 13, height: 13, rx: 2 },
    { tag: 'path', d: 'M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1' },
  ],
  bookmark: [
    { tag: 'path', d: 'M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z' },
  ],
  'sticky-note': [
    { tag: 'path', d: 'M16 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V8Z' },
    { tag: 'path', d: 'M15 3v4a2 2 0 0 0 2 2h4' },
  ],
  sliders: [
    { tag: 'line', x1: 4, y1: 21, x2: 4, y2: 14 },
    { tag: 'line', x1: 4, y1: 10, x2: 4, y2: 3 },
    { tag: 'line', x1: 12, y1: 21, x2: 12, y2: 12 },
    { tag: 'line', x1: 12, y1: 8, x2: 12, y2: 3 },
    { tag: 'line', x1: 20, y1: 21, x2: 20, y2: 16 },
    { tag: 'line', x1: 20, y1: 12, x2: 20, y2: 3 },
    { tag: 'line', x1: 1, y1: 14, x2: 7, y2: 14 },
    { tag: 'line', x1: 9, y1: 8, x2: 15, y2: 8 },
    { tag: 'line', x1: 17, y1: 16, x2: 23, y2: 16 },
  ],
  'panel-left': [
    { tag: 'rect', x: 3, y: 3, width: 18, height: 18, rx: 2 },
    { tag: 'path', d: 'M9 3v18' },
  ],
  plus: [
    { tag: 'path', d: 'M5 12h14' },
    { tag: 'path', d: 'M12 5v14' },
  ],
  'trash-2': [
    { tag: 'path', d: 'M3 6h18' },
    { tag: 'path', d: 'M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6' },
    { tag: 'path', d: 'M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2' },
    { tag: 'line', x1: 10, y1: 11, x2: 10, y2: 17 },
    { tag: 'line', x1: 14, y1: 11, x2: 14, y2: 17 },
  ],
  image: [
    { tag: 'rect', x: 3, y: 3, width: 18, height: 18, rx: 2 },
    { tag: 'circle', cx: 9, cy: 9, r: 2 },
    { tag: 'path', d: 'm21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21' },
  ],
  'square-pen': [
    { tag: 'path', d: 'M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7' },
    { tag: 'path', d: 'M18.375 2.625a2.12 2.12 0 1 1 3 3L12 15l-4 1 1-4Z' },
  ],
  'arrow-up': [
    { tag: 'path', d: 'M12 19V5' },
    { tag: 'path', d: 'm5 12 7-7 7 7' },
  ],
  'arrow-right': [
    { tag: 'path', d: 'M5 12h14' },
    { tag: 'path', d: 'm12 5 7 7-7 7' },
  ],
  'arrow-up-right': [
    { tag: 'path', d: 'M7 7h10v10' },
    { tag: 'path', d: 'M7 17 17 7' },
  ],
  plane: [
    { tag: 'path', d: 'M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.1-1.1.5l-.3.5c-.2.5-.1 1 .3 1.3L9 12l-2 3H4l-1 1 3 2 2 3 1-1v-3l3-2 3.5 5.3c.3.4.8.5 1.3.3l.5-.2c.4-.3.6-.7.5-1.2z' },
  ],
  users: [
    { tag: 'path', d: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2' },
    { tag: 'circle', cx: 9, cy: 7, r: 4 },
    { tag: 'path', d: 'M22 21v-2a4 4 0 0 0-3-3.87' },
    { tag: 'path', d: 'M16 3.13a4 4 0 0 1 0 7.75' },
  ],
  'chevron-right': [{ tag: 'path', d: 'M9 18l6-6-6-6' }],
  ellipsis: [
    { tag: 'circle', cx: 12, cy: 12, r: 1 },
    { tag: 'circle', cx: 19, cy: 12, r: 1 },
    { tag: 'circle', cx: 5, cy: 12, r: 1 },
  ],
  send: [
    { tag: 'path', d: 'M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z' },
    { tag: 'path', d: 'm21.854 2.147-10.94 10.939' },
  ],
  link: [
    { tag: 'path', d: 'M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71' },
    { tag: 'path', d: 'M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71' },
  ],
  share: [
    { tag: 'path', d: 'M12 2v13' },
    { tag: 'path', d: 'm8 6 4-4 4 4' },
    { tag: 'path', d: 'M4 12v6a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-6' },
  ],
  'message-circle': [
    { tag: 'path', d: 'M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719' },
  ],
  gauge: [
    { tag: 'path', d: 'm12 14 4-4' },
    { tag: 'path', d: 'M3.34 19a10 10 0 1 1 17.32 0' },
  ],
  'picture-in-picture-2': [
    { tag: 'path', d: 'M21 9V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v10c0 1.1.9 2 2 2h4' },
    { tag: 'rect', x: 12, y: 13, width: 10, height: 7, rx: 2 },
  ],
  maximize: [
    { tag: 'path', d: 'M8 3H5a2 2 0 0 0-2 2v3' },
    { tag: 'path', d: 'M21 8V5a2 2 0 0 0-2-2h-3' },
    { tag: 'path', d: 'M3 16v3a2 2 0 0 0 2 2h3' },
    { tag: 'path', d: 'M16 21h3a2 2 0 0 0 2-2v-3' },
  ],
  minimize: [
    { tag: 'path', d: 'M8 3v3a2 2 0 0 1-2 2H3' },
    { tag: 'path', d: 'M21 8h-3a2 2 0 0 1-2-2V3' },
    { tag: 'path', d: 'M3 16h3a2 2 0 0 1 2 2v3' },
    { tag: 'path', d: 'M16 21v-3a2 2 0 0 1 2-2h3' },
  ],
}

const nodes = (name: string) => icons[name] ?? icons.menu
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
