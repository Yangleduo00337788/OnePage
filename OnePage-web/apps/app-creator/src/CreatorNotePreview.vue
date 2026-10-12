<!-- 创作者中心预览笔记：本页弹层，不跳社区。 -->
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { PostDetail } from '@onepage/api-client'
import Icon from './Icon.vue'
import TextNoteCover from './TextNoteCover.vue'
import { coverVideoSrc, formatCount } from './notes'

const props = defineProps<{
  detail: PostDetail | null
  loading: boolean
  error: string
}>()

const emit = defineEmits<{ close: [] }>()

const images = computed(() => {
  const media = props.detail?.media || []
  return media.filter((m) => !m.mimeType.startsWith('video/')).map((m) => m.url)
})

const imgIndex = ref(0)

const videoUrl = computed(() => {
  const media = props.detail?.media || []
  const hit = media.find((m) => m.mimeType.startsWith('video/'))
  if (hit?.url) return hit.url
  if (props.detail?.postType === 'video' && props.detail.coverUrl) {
    return coverVideoSrc(props.detail.coverUrl)
  }
  return ''
})

const mediaRatio = ref(0)

watch(images, () => {
  imgIndex.value = 0
  mediaRatio.value = 0
})

watch(
  () => props.detail?.postId,
  () => {
    mediaRatio.value = 0
  },
)

function onImgLoad(e: Event) {
  const el = e.target as HTMLImageElement
  if (el.naturalWidth > 0 && el.naturalHeight > 0) {
    mediaRatio.value = el.naturalWidth / el.naturalHeight
  }
}

const stageWidth = computed(() => {
  if (videoUrl.value || !mediaRatio.value) return ''
  const maxH = Math.min(780, window.innerHeight - 48)
  const maxSheet = Math.min(920, window.innerWidth - 48)
  const copyMin = 280
  return `${Math.round(Math.min(maxSheet - copyMin, maxH * mediaRatio.value))}px`
})

const chips = computed(() => {
  const p = props.detail
  if (!p) return [] as string[]
  const list: string[] = []
  if (p.original) list.push('原创')
  if (p.category) list.push(p.category)
  if (p.contentKind) list.push(p.contentKind)
  if (p.eventTitle) list.push(p.eventTitle)
  if (p.visibility === 'self') list.push('仅自己可见')
  return list
})

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}

onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <Teleport to="body">
    <div class="mask" @click.self="emit('close')">
      <div class="sheet" role="dialog" aria-modal="true" aria-label="预览笔记">
        <button type="button" class="close" aria-label="关闭" @click="emit('close')">
          <Icon name="x" :size="16" />
        </button>
        <p v-if="loading" class="hint">加载中…</p>
        <p v-else-if="error" class="hint">{{ error }}</p>
        <template v-else-if="detail">
          <div class="stage" :style="stageWidth ? { width: stageWidth, flex: 'none' } : undefined">
            <video
              v-if="videoUrl"
              :src="videoUrl"
              class="media"
              controls
              playsinline
            />
            <div v-else-if="images.length" class="gallery">
              <img
                :key="images[imgIndex]"
                :src="images[imgIndex]"
                :alt="detail.title"
                class="media"
                @load="onImgLoad"
              />
              <button
                v-if="images.length > 1 && imgIndex > 0"
                type="button"
                class="nav prev"
                aria-label="上一张"
                @click="imgIndex -= 1"
              >
                ‹
              </button>
              <button
                v-if="images.length > 1 && imgIndex < images.length - 1"
                type="button"
                class="nav next"
                aria-label="下一张"
                @click="imgIndex += 1"
              >
                ›
              </button>
              <span v-if="images.length > 1" class="pager">{{ imgIndex + 1 }}/{{ images.length }}</span>
            </div>
            <TextNoteCover v-else size="feed" :title="detail.title" :body="detail.body" />
          </div>
          <div class="copy">
            <h2>{{ detail.title || '笔记' }}</h2>
            <p v-if="chips.length" class="chips">
              <span v-for="chip in chips" :key="chip">{{ chip }}</span>
            </p>
            <p v-if="detail.body" class="body">{{ detail.body }}</p>
            <p class="meta">
              浏览 {{ formatCount(detail.viewCount ?? 0) }} · 赞 {{ formatCount(detail.likeCount ?? 0) }} · 收藏
              {{ formatCount(detail.favoriteCount ?? 0) }}
            </p>
          </div>
        </template>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.4);
}

.sheet {
  position: relative;
  display: flex;
  width: min(920px, 100%);
  max-height: min(780px, calc(100vh - 48px));
  overflow: hidden;
  border-radius: 20px;
  background: #fff;
}

.close {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #1f1f1f;
  background: rgb(248, 248, 248);
}

.close:hover {
  background: #f2f2f2;
}

.hint {
  margin: 80px auto;
  color: #8a8a8a;
  font-size: 14px;
}

.stage {
  width: 52%;
  min-width: 0;
  background: #f7f7f7;
}

.gallery {
  position: relative;
  height: 100%;
  min-height: 420px;
}

.media,
.stage :deep(.tnc) {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 420px;
  object-fit: contain;
}

.nav {
  position: absolute;
  top: 50%;
  z-index: 1;
  width: 32px;
  height: 32px;
  margin-top: -16px;
  border-radius: 50%;
  color: #fff;
  background: rgba(0, 0, 0, 0.35);
  font-size: 20px;
  line-height: 32px;
}

.nav.prev {
  left: 12px;
}

.nav.next {
  right: 12px;
}

.pager {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  color: #fff;
  background: rgba(0, 0, 0, 0.4);
  font-size: 12px;
}

.copy {
  flex: 1;
  min-width: 0;
  overflow: auto;
  padding: 28px 24px 24px;
}

.copy h2 {
  margin: 0 28px 0 0;
  font-size: 18px;
  font-weight: 650;
  line-height: 1.4;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 12px 0 0;
}

.chips span {
  padding: 2px 8px;
  border-radius: 10px;
  background: rgb(248, 248, 248);
  color: #555;
  font-size: 12px;
}

.body {
  margin: 16px 0 0;
  color: #555;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.meta {
  margin: 20px 0 0;
  color: #8a8a8a;
  font-size: 12px;
}

@media (max-width: 800px) {
  .sheet {
    flex-direction: column;
  }

  .stage {
    width: 100%;
    height: 220px;
  }

  .media,
  .stage :deep(.tnc) {
    min-height: 220px;
  }
}
</style>
