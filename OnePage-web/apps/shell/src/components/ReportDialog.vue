<!-- 举报弹层：原因胶囊 + 可选说明。提交 POST /reports。 -->
<script setup lang="ts">
import { ref, watch } from 'vue'
import { api, ApiError, REPORT_REASONS, type ReportReasonCode } from '@onepage/api-client'
import { useAuthModal } from '../stores/authModal'

const props = defineProps<{
  open: boolean
  targetType: 'post' | 'user' | 'comment'
  targetId: string
}>()

const emit = defineEmits<{ close: []; done: [] }>()

const auth = useAuthModal()
const reason = ref<ReportReasonCode>('spam')
const detail = ref('')
const error = ref('')
const loading = ref(false)

watch(
  () => props.open,
  (on) => {
    if (on) {
      reason.value = 'spam'
      detail.value = ''
      error.value = ''
    }
  },
)

async function submit() {
  error.value = ''
  loading.value = true
  try {
    await api.createReport({
      targetType: props.targetType,
      targetId: props.targetId,
      reasonCode: reason.value,
      detail: detail.value.trim() || undefined,
    })
    emit('done')
    emit('close')
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      auth.show('login')
      emit('close')
      return
    }
    error.value = e instanceof ApiError ? e.message : '提交失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-[80] flex items-center justify-center bg-black/25 px-4"
      @click.self="emit('close')"
    >
      <form class="card" @submit.prevent="submit">
        <h2>举报</h2>
        <p class="sub">{{ targetType === 'post' ? '举报这条笔记' : '举报这个用户' }}</p>
        <div class="chips">
          <button
            v-for="item in REPORT_REASONS"
            :key="item.code"
            type="button"
            class="gender-pill"
            :class="{ 'is-on': reason === item.code }"
            @click="reason = item.code"
          >
            {{ item.label }}
          </button>
        </div>
        <textarea v-model="detail" maxlength="1000" rows="3" placeholder="补充说明（可选）" />
        <p v-if="error" class="err">{{ error }}</p>
        <div class="acts">
          <button type="button" class="report-ghost" @click="emit('close')">取消</button>
          <button class="report-cta" :disabled="loading">{{ loading ? '提交中…' : '提交' }}</button>
        </div>
      </form>
    </div>
  </Teleport>
</template>

<style scoped>
/* 全局 button { background: none } 会压过 Tailwind bg-*，选中必须用非 @layer 样式。 */
.card {
  width: 360px;
  padding: 24px;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.16);
}

h2 {
  margin: 0;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
}

.sub {
  margin: 8px 0 0;
  text-align: center;
  color: #8a8a8a;
  font-size: 13px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

textarea {
  display: block;
  box-sizing: border-box;
  width: 100%;
  margin-top: 16px;
  padding: 10px 12px;
  border: 0;
  border-radius: 12px;
  background: #f7f7f7;
  color: #1f1f1f;
  font: inherit;
  font-size: 13px;
  resize: none;
  outline: none;
}

.err {
  margin: 8px 0 0;
  color: #ff2442;
  font-size: 13px;
}

.acts {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}

.report-cta:disabled {
  opacity: 0.7;
}
</style>
