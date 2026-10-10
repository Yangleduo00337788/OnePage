<!-- 误进 /create 或 /creator：新开创作者站，社区回到首页。 -->
<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { openCreatorSite } from '../creatorSite'

const router = useRouter()

onMounted(() => {
  openCreatorSite()
  router.replace('/')
})
</script>

<template>
  <div />
</template>
