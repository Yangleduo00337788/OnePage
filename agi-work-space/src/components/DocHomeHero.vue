<script setup lang="ts">
import { useI18n } from '@/composables/useI18n'
import { useBranding } from '@/lib/branding'

const { t } = useI18n()
const { logoLightSrc, logoDarkSrc } = useBranding()
</script>

<template>
  <section class="rounded-2xl border bg-card px-6 py-8 sm:px-10 sm:py-10">
    <div class="flex justify-center">
      <img :src="logoLightSrc" :alt="t('product')" class="h-12 w-auto dark:hidden sm:h-14" />
      <img :src="logoDarkSrc" :alt="t('product')" class="hidden h-12 w-auto dark:block sm:h-14" />
    </div>
    <p class="mt-5 text-center text-xl font-semibold tracking-tight text-balance sm:text-2xl">
      {{ t('homeSlogan') }}
    </p>
    <p class="mx-auto mt-3 max-w-2xl text-center text-sm leading-7 text-muted-foreground sm:text-[15px]">
      {{ t('homeIntro') }}
    </p>
  </section>
</template>
