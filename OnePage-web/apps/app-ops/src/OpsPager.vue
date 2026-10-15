<!-- 表底翻页。接口是游标时只显示上一页/下一页与当前页；有总数时带总页。 -->
<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    page: number
    pageSize: number
    hasMore: boolean
    loading?: boolean
    total?: number
  }>(),
  { loading: false },
)

const emit = defineEmits<{
  prev: []
  next: []
  'update:pageSize': [number]
}>()

const pages = computed(() => {
  if (props.total == null) return null
  return Math.max(1, Math.ceil(props.total / props.pageSize))
})
</script>

<template>
  <div class="pager">
    <button type="button" :disabled="page <= 1 || loading" @click="emit('prev')">上一页</button>
    <span class="n">第 {{ page }}{{ pages ? ` / ${pages}` : '' }} 页</span>
    <button type="button" :disabled="!hasMore || loading" @click="emit('next')">下一页</button>
    <select
      :value="pageSize"
      :disabled="loading"
      @change="emit('update:pageSize', Number(($event.target as HTMLSelectElement).value))"
    >
      <option :value="10">10 条/页</option>
      <option :value="20">20 条/页</option>
      <option :value="50">50 条/页</option>
    </select>
    <span v-if="total != null" class="sum">共 {{ total }} 条</span>
  </div>
</template>

<style scoped>
.pager {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
  color: #8a8a8a;
  font-size: 13px;
}

button,
select {
  height: 32px;
  padding: 0 12px;
  border: 0;
  border-radius: 16px;
  background: rgb(248, 248, 248);
  color: #555;
  font: inherit;
}

button:disabled,
select:disabled {
  opacity: 0.45;
  cursor: default;
}

.n,
.sum {
  padding: 0 4px;
}
</style>
