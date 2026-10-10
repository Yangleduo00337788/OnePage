<script setup lang="ts">
import { computed } from 'vue'
import { catalog } from '@/lib/content'
import NavTree from '@/components/NavTree.vue'
import { useI18n } from '@/composables/useI18n'

const { t } = useI18n()
const nodes = computed(() => {
  void catalog.revision
  return catalog.docsNavTree
})
</script>

<template>
  <section v-if="nodes.length" class="mt-8">
    <h2 class="text-sm font-semibold tracking-tight">{{ t('startReading') }}</h2>
    <p class="mt-1 text-xs text-muted-foreground">{{ t('startReadingHint') }}</p>
    <div class="mt-4 rounded-xl border bg-card px-2 py-2">
      <NavTree :nodes="nodes" :show-actions="false" :initially-open="['start']" />
    </div>
  </section>
</template>
