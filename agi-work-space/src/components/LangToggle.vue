<script setup lang="ts">
import { Button } from '@/components/ui/button'
import { useI18n } from '@/composables/useI18n'

const { locale, setLocale } = useI18n()

function toggle() {
  setLocale(locale.value === 'zh' ? 'en' : 'zh')
}
</script>

<template>
  <Button variant="ghost" size="sm" class="h-8 px-2 text-xs font-medium tracking-wide" @click="toggle">
    {{ locale === 'zh' ? 'EN' : '中文' }}
  </Button>
</template>
