<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { ref } from 'vue'
import { cn } from '@/lib/utils'

const props = defineProps<{
  class?: HTMLAttributes['class']
  placeholder?: string
  type?: string
  autocomplete?: string
}>()

const model = defineModel<string>({ default: '' })
const inputRef = ref<HTMLInputElement | null>(null)

defineExpose({
  focus: () => inputRef.value?.focus(),
})
</script>

<template>
  <input
    ref="inputRef"
    v-model="model"
    :type="type ?? 'text'"
    :autocomplete="autocomplete"
    :placeholder="placeholder"
    :class="
      cn(
        'flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring',
        props.class,
      )
    "
  />
</template>
