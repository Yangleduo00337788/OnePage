<script setup lang="ts">
import { ChevronRight } from 'lucide-vue-next'

defineProps<{
  id?: string
  title: string
  hint?: string
}>()

const open = defineModel<boolean>('open', { default: false })
</script>

<template>
  <section :id="id" class="mt-5 overflow-hidden rounded-xl border bg-card">
    <button
      type="button"
      class="flex w-full items-center gap-2 px-5 py-4 text-left hover:bg-accent/30"
      :aria-expanded="open"
      @click="open = !open"
    >
      <ChevronRight class="size-4 shrink-0 text-muted-foreground transition-transform" :class="open ? 'rotate-90' : ''" />
      <span class="text-sm font-semibold">{{ title }}</span>
    </button>
    <div class="grid transition-[grid-template-rows] duration-200" :class="open ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]'">
      <div class="overflow-hidden">
        <div class="border-t px-5 pb-5 pt-4">
          <p v-if="hint" class="text-sm leading-6 text-muted-foreground">{{ hint }}</p>
          <slot />
        </div>
      </div>
    </div>
  </section>
</template>
