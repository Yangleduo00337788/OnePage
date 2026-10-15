<!-- 尚未接入的运营页：用户处置等。 -->
<script setup lang="ts">
import EmptyState from './EmptyState.vue'

defineProps<{ title: string; desc: string }>()
</script>

<template>
  <div class="page">
    <section class="card">
      <EmptyState src="/empty-people.png" :title="title" :desc="desc" />
    </section>
  </div>
</template>

<style scoped>
.page {
  flex: 1;
  min-height: 0;
  padding: 0 20px 20px 0;
}

.card {
  min-height: calc(100vh - 92px);
  border-radius: 20px;
  background: #fff;
}
</style>
