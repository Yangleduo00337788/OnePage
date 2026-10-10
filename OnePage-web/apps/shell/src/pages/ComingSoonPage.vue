<!-- 视频/直播等 P0 占位。title 由路由 props 传入。 -->
<script setup lang="ts">
defineProps<{ title: string }>()
</script>

<template>
  <section class="flex min-h-[50vh] flex-col items-center justify-center text-center">
    <p class="text-lg font-medium">{{ title }}</p>
    <p class="mt-2 text-sm text-[#999]">这一页还在排版，先去首页逛逛笔记。</p>
  </section>
</template>
