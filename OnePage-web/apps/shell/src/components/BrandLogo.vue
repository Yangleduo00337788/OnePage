<!-- 社区字标：纯字、同号、单色，不加图标。 -->
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
    <span v-if="mini">P</span>
    <span v-else class="word">ONEPAGE</span>
  </span>
</template>

<style scoped>
.brand-logo {
  display: inline-flex;
  align-items: center;
  color: #ff2442;
  font-family: 'Plus Jakarta Sans', 'Avenir Next', 'PingFang SC', sans-serif;
  font-size: 18px;
  font-style: normal;
  font-weight: 700;
  letter-spacing: 0.06em;
  line-height: 1;
}

.word {
  font-size: 1em;
  font-weight: inherit;
  letter-spacing: inherit;
}

.is-auth {
  font-size: 24px;
}

.is-hero {
  font-size: 28px;
}

.is-mini {
  font-size: 20px;
  letter-spacing: 0;
}
</style>
