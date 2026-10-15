<!-- 运营工作台：入口对齐已落地能力。 -->
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
    note.value = '治理服务已接通。下面只列当前真实能力，直播 / 搜索 / AI 上线后再进菜单。'
  } catch (e) {
    serviceReady.value = false
    if (e instanceof ApiError && (e.status === 404 || e.status === 501)) {
      note.value = '治理接口不可用。确认 ModerationApplication 已启动。'
    } else if (e instanceof ApiError && e.status === 403) {
      note.value = '当前账号没有运营权限。'
    } else {
      note.value = e instanceof ApiError ? e.message : '治理接口不可用'
    }
  }
})

const cards: { id: string; title: string; desc: string }[] = [
  { id: 'reports', title: '举报工单', desc: '预览、忽略、下架、封禁' },
  { id: 'users', title: '用户', desc: '搜人、冻结、解冻' },
  { id: 'posts', title: '笔记', desc: '全站列表、下架、恢复' },
  { id: 'comments', title: '评论', desc: '隐藏、恢复' },
  { id: 'catalog', title: '分类与活动', desc: '发帖目录只读' },
]
</script>

<template>
  <div class="home">
    <section class="card">
      <h1>工作台</h1>
      <p class="lead">{{ note }}</p>
      <div class="stats">
        <button v-for="item in cards" :key="item.id" type="button" class="stat" @click="emit('open', item.id)">
          <span class="n">{{ item.title }}</span>
          <span class="l">{{ item.desc }}</span>
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
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 24px;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  min-width: 140px;
  padding: 16px 18px;
  border-radius: 16px;
  background: rgb(248, 248, 248);
  text-align: left;
}

.stat:not(.is-plain):hover {
  background: #eee;
}

.n {
  font-size: 16px;
  font-weight: 650;
  color: #1f1f1f;
}

.l {
  font-size: 13px;
  color: #8a8a8a;
}
</style>
