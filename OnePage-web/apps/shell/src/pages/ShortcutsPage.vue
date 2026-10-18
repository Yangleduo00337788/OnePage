<!-- 快捷键说明页。真正监听尚未全部接上，先把约定写在这里。 -->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import Icon from '../components/Icon.vue'

const router = useRouter()
const rows = [
  { keys: '/', action: '聚焦顶栏搜索' },
  { keys: 'Enter', action: '在搜索框中进入搜索页' },
  { keys: 'Esc', action: '关闭弹窗 / 收起面板' },
  { keys: '?', action: '打开本页快捷键说明' },
]
</script>

<template>
  <section class="w-full py-4">
    <div class="mb-4 flex items-center gap-2">
      <button type="button" class="page-back" aria-label="返回" @click="router.back()">
        <Icon name="chevron-left" :size="20" />
      </button>
      <h1 class="text-[22px] font-semibold">键盘快捷键</h1>
    </div>
    <ul class="mt-6 divide-y divide-[#f2f2f2] overflow-hidden rounded-[16px] ring-1 ring-[#eee]">
      <li v-for="row in rows" :key="row.keys" class="flex items-center justify-between px-4 py-3">
        <span class="text-[14px]">{{ row.action }}</span>
        <kbd class="rounded-lg bg-[#f7f7f7] px-2 py-1 text-[13px] text-[#555]">{{ row.keys }}</kbd>
      </li>
    </ul>
  </section>
</template>
