<!-- 运营站内预览笔记/用户/评论/直播，不跳社区。 -->
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { OpsCommentRow, OpsLiveView, OpsPostView, OpsUser } from '@onepage/api-client'

const props = defineProps<{
  kind: 'post' | 'user' | 'comment' | 'live'
  post: OpsPostView | null
  user: OpsUser | null
  comment?: OpsCommentRow | null
  live?: OpsLiveView | null
  loading: boolean
  error: string
}>()

const emit = defineEmits<{ close: [] }>()

const images = computed(() => {
  const media = props.post?.media || []
  return media.filter((item) => item.mimeType && !item.mimeType.startsWith('video/')).map((item) => item.url)
})

const videoUrl = computed(() => {
  const media = props.post?.media || []
  const hit = media.find((item) => item.mimeType?.startsWith('video/'))
  return hit?.url || ''
})

const imgIndex = ref(0)

watch(
  () => props.post?.postId,
  () => {
    imgIndex.value = 0
  },
)

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}

onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="mask" @click.self="emit('close')">
    <section class="sheet" role="dialog" aria-modal="true">
      <button type="button" class="x" aria-label="关闭" @click="emit('close')">×</button>
      <p v-if="loading" class="hint">加载中…</p>
      <p v-else-if="error" class="err">{{ error }}</p>
      <div v-else-if="kind === 'post' && post" class="post">
        <div class="media">
          <video v-if="videoUrl" :src="videoUrl" controls playsinline />
          <img v-else-if="images.length" :src="images[imgIndex]" alt="" />
          <div v-else class="text-cover">{{ post.title || '纯文字' }}</div>
          <div v-if="images.length > 1" class="dots">
            <button
              v-for="(_, i) in images"
              :key="i"
              type="button"
              :class="{ 'is-on': i === imgIndex }"
              @click="imgIndex = i"
            />
          </div>
        </div>
        <div class="copy">
          <p v-if="post.takenDown" class="badge">已下架</p>
          <p v-else-if="post.reviewStatus === 'pending'" class="badge">待审</p>
          <p v-else-if="post.reviewStatus === 'rejected'" class="badge">未通过</p>
          <p v-if="post.aiReviewScore != null" class="sub">风险分 {{ post.aiReviewScore }}{{ post.aiReviewLabels ? ` · ${post.aiReviewLabels}` : '' }}</p>
          <p v-if="post.aiReview === 'reject'" class="sub">机审驳回：{{ post.aiReviewReason || '高置信违规' }}</p>
          <p v-else-if="post.aiReview === 'flag'" class="sub">进人审：{{ post.aiReviewReason || '需人工复核' }}</p>
          <p v-else-if="post.aiReview === 'skip'" class="sub">{{ post.aiReviewReason || '机审未跑，待人审' }}</p>
          <p v-else-if="post.reviewStatus === 'pending' && !post.aiReview" class="sub">机审中</p>
          <p v-if="post.rejectReason" class="sub">{{ post.rejectReason }}</p>
          <p class="author">{{ post.author?.nickname || post.author?.pageId || '作者' }}</p>
          <h2>{{ post.title || '无标题' }}</h2>
          <p class="body">{{ post.body }}</p>
        </div>
      </div>
      <div v-else-if="kind === 'comment' && comment" class="person">
        <div>
          <p v-if="comment.hidden" class="badge">已删除</p>
          <p class="author">{{ comment.author?.nickname || '用户' }}</p>
          <p class="sub">笔记 {{ comment.postId }}</p>
          <p class="body">{{ comment.content }}</p>
        </div>
      </div>
      <div v-else-if="kind === 'user' && user" class="person">
        <img v-if="user.avatarUrl" class="avatar" :src="user.avatarUrl" alt="" />
        <div v-else class="avatar is-empty">{{ (user.nickname || '用').slice(0, 1) }}</div>
        <div>
          <p class="author">{{ user.nickname || '未命名' }}</p>
          <p class="sub">{{ user.pageId || user.userId }}</p>
          <p v-if="user.frozen" class="badge">已冻结</p>
        </div>
      </div>
      <div v-else-if="kind === 'live' && live" class="person">
        <img v-if="live.coverUrl" class="cover" :src="live.coverUrl" alt="" />
        <div v-else class="cover is-empty">直播</div>
        <div>
          <p v-if="live.ended" class="badge">已关播</p>
          <p v-else class="badge is-live">直播中</p>
          <p class="author">{{ live.title || '无标题' }}</p>
          <p class="sub">{{ live.owner?.nickname || live.owner?.pageId || '主播' }}</p>
          <p v-if="live.notice" class="body">{{ live.notice }}</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgb(0 0 0 / 45%);
}

.sheet {
  position: relative;
  display: flex;
  max-width: min(920px, 100%);
  max-height: calc(100vh - 48px);
  overflow: auto;
  border-radius: 20px;
  background: #fff;
}

.x {
  position: absolute;
  top: 10px;
  right: 12px;
  z-index: 1;
  width: 32px;
  height: 32px;
  border-radius: 16px;
  color: #555;
  font-size: 22px;
  line-height: 1;
}

.hint,
.err {
  margin: 48px 56px;
  font-size: 14px;
}

.err {
  color: #ff2442;
}

.post {
  display: flex;
  min-width: 0;
}

.media {
  position: relative;
  flex: 0 0 420px;
  max-height: calc(100vh - 48px);
  background: #111;
}

.media img,
.media video {
  display: block;
  width: 100%;
  height: 100%;
  max-height: calc(100vh - 48px);
  object-fit: contain;
}

.text-cover {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  padding: 24px;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  text-align: center;
}

.dots {
  position: absolute;
  right: 0;
  bottom: 12px;
  left: 0;
  display: flex;
  justify-content: center;
  gap: 6px;
}

.dots button {
  width: 8px;
  height: 8px;
  border-radius: 4px;
  background: rgb(255 255 255 / 45%);
}

.dots .is-on {
  background: #fff;
}

.copy {
  width: 320px;
  padding: 28px 24px 24px;
}

.author {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

h2 {
  margin: 10px 0 0;
  font-size: 18px;
}

.body {
  margin: 12px 0 0;
  color: #555;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.badge {
  display: inline-block;
  margin: 0 0 10px;
  padding: 2px 8px;
  border-radius: 8px;
  background: rgb(255 36 66 / 10%);
  color: #ff2442;
  font-size: 12px;
}

.badge.is-live {
  background: rgb(0 0 0 / 8%);
  color: #555;
}

.cover {
  width: 90px;
  height: 120px;
  flex-shrink: 0;
  border-radius: 12px;
  object-fit: cover;
  background: #111;
}

.cover.is-empty {
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 13px;
}

.person {
  display: flex;
  gap: 16px;
  min-width: 360px;
  padding: 36px 48px 36px 28px;
}

.avatar {
  width: 56px;
  height: 56px;
  border-radius: 28px;
  object-fit: cover;
  background: rgb(248, 248, 248);
}

.avatar.is-empty {
  display: grid;
  place-items: center;
  color: #8a8a8a;
  font-weight: 600;
}

.sub {
  margin: 4px 0 0;
  color: #8a8a8a;
  font-size: 13px;
}
</style>
