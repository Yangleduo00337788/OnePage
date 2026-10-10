<script setup lang="ts">
import { Moon, Sun } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { persistTheme, readTheme, type Theme } from '@/composables/useTheme'
import { ref } from 'vue'

const theme = ref<Theme>(readTheme())

function toggle() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
  persistTheme(theme.value)
}
</script>

<template>
  <Button variant="ghost" size="icon-sm" :aria-label="theme === 'dark' ? 'Light' : 'Dark'" @click="toggle">
    <Sun v-if="theme === 'dark'" class="size-4" />
    <Moon v-else class="size-4" />
  </Button>
</template>
