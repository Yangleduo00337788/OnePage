<!-- 品牌锁：透明底设计稿 PNG，深色主题切 logo-dark。 -->
<script setup lang="ts">
import { computed } from 'vue'
import { useTheme } from '../stores/theme'

const props = withDefaults(
  defineProps<{
    mini?: boolean
    size?: 'nav' | 'auth' | 'hero'
  }>(),
  { mini: false, size: 'nav' },
)

const theme = useTheme()
const src = computed(() => {
  if (props.mini) return '/favicon-32.png?v=19'
  return theme.resolved === 'dark' ? '/logo-dark.png?v=19' : '/logo-light.png?v=19'
})
</script>

<template>
  <span class="brand-logo" :class="[`is-${size}`, { 'is-mini': mini }]" role="img" aria-label="ONEPAGE">
    <img class="mark" :class="{ 'mark-mini': mini }" :src="src" alt="" />
  </span>
</template>

<style scoped>
.brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.mark {
  display: block;
  height: 30px;
  width: auto;
  max-width: 168px;
  object-fit: contain;
}

.mark-mini {
  width: 28px;
  height: 28px;
}

.is-auth .mark {
  height: 36px;
  max-width: 240px;
}

.is-hero .mark {
  height: 42px;
  max-width: 280px;
}
</style>
