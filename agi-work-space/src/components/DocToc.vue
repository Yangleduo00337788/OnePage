<script setup lang="ts">
import { computed, watch } from 'vue'
import { cn } from '@/lib/utils'
import type { TocItem } from '@/lib/markdown'

const props = defineProps<{
  items: TocItem[]
  activeId: string
  title: string
}>()

const emit = defineEmits<{
  select: [id: string]
}>()

const display = computed(() => props.items)

watch(
  () => props.activeId,
  (id) => {
    if (!id) return
    const el = document.querySelector<HTMLElement>(`[data-toc-id="${CSS.escape(id)}"]`)
    el?.scrollIntoView({ block: 'nearest' })
  },
)
</script>

<template>
  <nav v-if="display.length" class="space-y-2">
    <p class="text-[11px] font-medium uppercase tracking-[0.14em] text-muted-foreground">
      {{ title }}
    </p>
    <ul class="space-y-1 border-l border-border">
      <li v-for="item in display" :key="item.id">
        <a
          :href="`#${item.id}`"
          :data-toc-id="item.id"
          :class="
            cn(
              '-ml-px block border-l py-1 text-[13px] leading-5 transition-colors',
              item.level === 3 ? 'pl-6' : 'pl-3',
              activeId === item.id
                ? 'border-foreground font-medium text-foreground'
                : 'border-transparent text-muted-foreground hover:border-border hover:text-foreground',
            )
          "
          @click.prevent="emit('select', item.id)"
        >
          {{ item.title }}
        </a>
      </li>
    </ul>
  </nav>
</template>
