<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { isDemoMode } from '@onepage/api-client'
import { demoGroupsFor, type DemoSite } from './demo-creds'

const props = withDefaults(
  defineProps<{
    site: DemoSite
    /** auth-left：社区弹窗原二维码位；form：创作者/运营登录表单上方 */
    layout?: 'auth-left' | 'form'
  }>(),
  { layout: 'auth-left' },
)

const copyTip = ref('')
let copyTipTimer: ReturnType<typeof setTimeout> | undefined

const groups = demoGroupsFor(props.site)

async function copyDemo(text: string) {
  copyTip.value = ''
  try {
    await navigator.clipboard.writeText(text)
    copyTip.value = '已复制'
  } catch {
    copyTip.value = '复制失败'
  }
  if (copyTipTimer) clearTimeout(copyTipTimer)
  copyTipTimer = setTimeout(() => {
    copyTip.value = ''
  }, 2000)
}

onUnmounted(() => {
  if (copyTipTimer) clearTimeout(copyTipTimer)
})
</script>

<template>
  <div v-if="isDemoMode()" class="demo-creds" :class="`is-${layout}`">
    <div class="head">
      <p class="title">演示账号</p>
      <span v-if="copyTip" class="tip tip-ok">{{ copyTip }}</span>
      <span v-else class="tip">点击复制</span>
    </div>
    <div class="groups">
      <section v-for="group in groups" :key="group.title">
        <p class="group-title">{{ group.title }}</p>
        <ul class="rows">
          <li v-for="row in group.rows" :key="`${group.title}-${row.label}`">
            <button type="button" class="row" @click="copyDemo(row.copy)">
              <span class="label">{{ row.label }}</span>
              <span class="value">{{ row.text }}</span>
            </button>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style scoped>
.demo-creds {
  width: 100%;
  padding: 0;
  background: transparent;
  border: 0;
  box-shadow: none;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.title {
  margin: 0;
  font-size: 11px;
  font-weight: 600;
  color: var(--demo-creds-ink, #1f1f1f);
}

.tip {
  font-size: 10px;
  color: #bbb;
  white-space: nowrap;
}

.tip-ok {
  color: #ff2442;
}

.demo-creds.is-auth-left {
  width: auto;
  min-width: 152px;
  max-width: 176px;
  margin-top: 32px;
  text-align: left;
}

.demo-creds.is-form {
  width: 100%;
  max-width: none;
  margin: 0;
}

.groups {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.group-title {
  margin: 0 0 2px;
  font-size: 10px;
  font-weight: 500;
  color: #8a8a8a;
}

.rows {
  margin: 0;
  padding: 0;
  list-style: none;
}

.row {
  display: flex;
  width: 100%;
  gap: 8px;
  margin: 0;
  padding: 4px 0;
  border: 0;
  border-radius: 4px;
  font: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  color: #666;
}

.row:hover {
  color: #1f1f1f;
}

.row:hover .value {
  color: #ff2442;
}

.label {
  flex-shrink: 0;
  width: 2.5em;
  font-size: 11px;
  color: #aaa;
}

.value {
  min-width: 0;
  font-size: 11px;
  line-height: 1.4;
  color: #555;
}
</style>
