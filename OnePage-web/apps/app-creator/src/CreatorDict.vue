<!-- 创作字典：分类 Tab + 问答。 -->
<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from './Icon.vue'
import { DICT } from './dict'

const tab = ref(DICT[0].id)
const query = ref('')

const tabs = DICT.map((block) => ({ id: block.id, label: block.title }))

const items = computed(() => {
  const q = query.value.trim().toLowerCase()
  const source = DICT.find((b) => b.id === tab.value)?.items ?? []
  if (!q) return source
  return source.filter((item) => item.q.toLowerCase().includes(q) || item.a.toLowerCase().includes(q))
})
</script>

<template>
  <div class="page">
    <div class="bar">
      <div class="tabs">
        <button
          v-for="item in tabs"
          :key="item.id"
          type="button"
          class="tab"
          :class="{ 'is-on': tab === item.id }"
          @click="tab = item.id"
        >
          {{ item.label }}
        </button>
      </div>
      <label class="search">
        <Icon name="search" :size="16" />
        <input v-model="query" type="search" placeholder="搜索问题" />
      </label>
    </div>

    <div v-if="items.length" class="list">
      <article v-for="item in items" :key="item.q" class="faq">
        <h3>{{ item.q }}</h3>
        <p>{{ item.a }}</p>
      </article>
    </div>
    <p v-else class="empty">没有找到相关问题</p>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  margin: 0 20px 20px 0;
  padding: 16px 24px 24px;
  box-sizing: border-box;
  overflow: auto;
  border-radius: 20px;
  background: #fff;
}

.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tab {
  height: 36px;
  padding: 0 14px;
  border-radius: 20px;
  color: #555;
  font-size: 14px;
}

.tab:hover {
  background: rgb(248, 248, 248);
}

.tab.is-on {
  color: #1f1f1f;
  font-weight: 600;
  background: rgb(248, 248, 248);
}

.search {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 8px;
  width: 220px;
  height: 36px;
  padding: 0 14px;
  border-radius: 20px;
  background: rgb(248, 248, 248);
  color: #bbb;
}

.search input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  color: #1f1f1f;
  font: inherit;
  font-size: 14px;
  outline: none;
}

.search input::placeholder {
  color: #bbb;
}

.list {
  margin-top: 8px;
}

.faq {
  padding: 16px 4px;
  border-bottom: 1px solid #f2f2f2;
}

.faq h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.faq p {
  margin: 8px 0 0;
  color: #555;
  font-size: 13px;
  line-height: 1.7;
}

.empty {
  margin: 80px 0 0;
  color: #8a8a8a;
  font-size: 14px;
  text-align: center;
}
</style>
