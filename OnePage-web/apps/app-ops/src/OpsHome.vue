<!-- 运营首页：工单概览。治理服务未上线时计数为 0。 -->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, ApiError } from '@onepage/api-client'

const emit = defineEmits<{ open: [string] }>()

const serviceReady = ref(false)
const note = ref('正在探测治理接口…')

onMounted(async () => {
  try {
    await api.opsReports('pending', undefined, 1)
    serviceReady.value = true
    note.value = '已接到治理服务。去举报工单处理。'
  } catch (e) {
    serviceReady.value = false
    if (e instanceof ApiError && (e.status === 404 || e.status === 501)) {
      note.value = '治理服务尚未接入。列表会空着，不要造假工单。'
    } else if (e instanceof ApiError && e.status === 403) {
      note.value = '当前账号没有运营权限。'
    } else {
      note.value = e instanceof ApiError ? e.message : '治理接口不可用'
    }
  }
})
</script>

<template>
  <div class="home">
    <section class="card">
      <h1>工作台</h1>
      <p class="lead">{{ note }}</p>
      <div class="stats">
        <button type="button" class="stat" @click="emit('open', 'reports')">
          <span class="n">工单</span>
          <span class="l">打开举报列表</span>
        </button>
        <div class="stat is-plain">
          <span class="n">{{ serviceReady ? '通' : '未' }}</span>
          <span class="l">治理接口</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home {
  flex: 1;
  min-height: 0;
  padding: 0 20px 20px 0;
}

.card {
  padding: 24px 28px 28px;
  border-radius: 20px;
  background: #fff;
}

h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.lead {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #8a8a8a;
}

.stats {
  display: flex;
  gap: 12px;
  margin-top: 24px;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  min-width: 120px;
  padding: 16px 18px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
  text-align: left;
}

.stat:not(.is-plain):hover {
  background: #eee;
}

.n {
  font-size: 22px;
  font-weight: 650;
  color: #1f1f1f;
}

.l {
  font-size: 13px;
  color: #8a8a8a;
}
</style>
