<!-- 品牌锁：与社区同一套设计稿 PNG。 -->
<script setup lang="ts">
withDefaults(
  defineProps<{
    mini?: boolean
    size?: 'nav' | 'auth' | 'hero'
  }>(),
  { mini: false, size: 'nav' },
)
</script>

<template>
  <span class="brand-logo" :class="[`is-${size}`, { 'is-mini': mini }]" role="img" aria-label="ONEPAGE">
    <img v-if="mini" class="mark-mini" src="/favicon-32.png?v=19" alt="" />
    <img v-else class="mark" src="/logo-light.png?v=19" alt="ONEPAGE" />
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
  display: block;
  width: 28px;
  height: 28px;
  object-fit: contain;
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
