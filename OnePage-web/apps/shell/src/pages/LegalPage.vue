<!-- 协议/隐私独立路由，文案在 data/legal.ts，方便弹窗和本页共用。 -->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '../components/Icon.vue'
import { LEGAL } from '../data/legal'

const route = useRoute()
const router = useRouter()
const kind = computed((): 'privacy' | 'terms' => (route.path === '/privacy' ? 'privacy' : 'terms'))
const doc = computed(() => LEGAL[kind.value])
</script>

<template>
  <article class="w-full py-4">
    <div class="mb-4 flex items-center gap-2">
      <button type="button" class="page-back" aria-label="返回" @click="router.back()">
        <Icon name="chevron-left" :size="20" />
      </button>
      <h1 class="text-[22px] font-semibold">{{ doc.title }}</h1>
    </div>
    <p class="mt-1 text-[13px] text-[#8a8a8a]">更新于 {{ doc.updated }}</p>
    <p class="mt-5 text-[15px] leading-7 text-[#333]">{{ doc.intro }}</p>
    <section v-for="block in doc.sections" :key="block.heading" class="mt-6">
      <h2 class="text-[16px] font-semibold">{{ block.heading }}</h2>
      <p v-for="p in block.paragraphs" :key="p" class="mt-2 text-[15px] leading-7 text-[#555]">{{ p }}</p>
    </section>
  </article>
</template>
