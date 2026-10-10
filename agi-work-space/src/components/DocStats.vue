<script setup lang="ts">
import { computed } from 'vue'
import { catalog, countWorkspaceDocs } from '@/lib/content'
import { useI18n } from '@/composables/useI18n'

const { t } = useI18n()
const stats = computed(() => {
  void catalog.revision
  return countWorkspaceDocs()
})
</script>

<template>
  <section class="mb-8">
    <p class="text-[11px] font-medium uppercase tracking-[0.14em] text-muted-foreground">
      {{ t('docStatsTitle') }}
    </p>
    <p class="mt-2 text-2xl font-semibold tabular-nums leading-none tracking-tight">
      {{ stats.files }}
      <span class="ml-1 text-sm font-medium text-muted-foreground">{{ t('docStatsUnit') }}</span>
    </p>
    <p class="mt-1.5 text-xs text-muted-foreground">
      {{ stats.folders }} {{ t('docStatsFolders') }} · {{ t('docStatsHint') }}
    </p>
  </section>
</template>
