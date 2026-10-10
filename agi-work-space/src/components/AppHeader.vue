<script setup lang="ts">
import { LayoutGrid, Menu } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'
import { Button } from '@/components/ui/button'
import DocBreadcrumb from '@/components/DocBreadcrumb.vue'
import DocSearch from '@/components/DocSearch.vue'
import LangToggle from '@/components/LangToggle.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
import { useI18n } from '@/composables/useI18n'
import { useBranding } from '@/lib/branding'

const emit = defineEmits<{
  menu: []
}>()

const { t } = useI18n()
const { logoLightSrc, logoDarkSrc } = useBranding()
</script>

<template>
  <header class="sticky top-0 z-40 flex h-14 items-center gap-3 border-b bg-background/80 px-4 backdrop-blur-md">
    <Button variant="ghost" size="icon-sm" class="md:hidden" :aria-label="t('menu')" @click="emit('menu')">
      <Menu class="size-4" />
    </Button>
    <RouterLink to="/" class="flex shrink-0 items-center" :aria-label="t('product')">
      <img :src="logoLightSrc" :alt="t('product')" class="h-7 w-auto dark:hidden" />
      <img :src="logoDarkSrc" :alt="t('product')" class="hidden h-7 w-auto dark:block" />
    </RouterLink>
    <DocBreadcrumb />
    <div class="ml-auto flex shrink-0 items-center gap-1.5">
      <DocSearch />
      <RouterLink
        to="/settings"
        class="hidden size-8 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground sm:inline-flex"
        :aria-label="t('settingsNav')"
        :title="t('settingsNav')"
      >
        <LayoutGrid class="size-4" />
      </RouterLink>
      <LangToggle />
      <ThemeToggle />
    </div>
  </header>
</template>
