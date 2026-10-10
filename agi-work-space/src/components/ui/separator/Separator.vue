<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<{
    orientation?: 'horizontal' | 'vertical'
    class?: HTMLAttributes['class']
    decorative?: boolean
  }>(),
  {
    orientation: 'horizontal',
    decorative: true,
  },
)
</script>

<template>
  <div
    :role="decorative ? 'none' : 'separator'"
    :aria-orientation="orientation"
    :class="
      cn(
        'shrink-0 bg-border',
        orientation === 'horizontal' ? 'h-px w-full' : 'h-full w-px',
        props.class,
      )
    "
  />
</template>
