<!-- 瀑布流一张卡：图用 img，视频用 video 首帧，纯文字把正文铺在封面上。 -->
<script setup lang="ts">
import { computed } from 'vue'
import Icon from './Icon.vue'
import TextNoteCover from './TextNoteCover.vue'
import { useNoteDetail } from '../stores/noteDetail'
import type { NoteCard } from '../data/home'

const props = defineProps<{ note: NoteCard }>()
const noteDetail = useNoteDetail()

function open(e: MouseEvent) {
  noteDetail.show(props.note, e.currentTarget)
}

const videoSrc = computed(() => {
  const url = props.note.image
  if (!url) return ''
  return url.includes('#') ? url : `${url}#t=0.1`
})
const isText = computed(() => !props.note.video && !props.note.image)
</script>

<template>
  <article
    :data-note-id="note.id"
    class="flex h-[440px] w-[280px] cursor-pointer flex-col"
    @click="open"
  >
    <div class="group relative min-h-0 flex-1 overflow-hidden rounded-2xl bg-[#ececec]">
      <video
        v-if="note.video && note.image"
        :src="videoSrc"
        class="pointer-events-none h-full w-full object-cover"
        muted
        playsinline
        preload="metadata"
      />
      <img
        v-else-if="note.image && !note.video"
        :src="note.image"
        :alt="note.title"
        class="pointer-events-none h-full w-full object-cover"
      />
      <TextNoteCover v-else size="feed" :title="note.title" :body="note.body" />
      <span
        v-if="!isText"
        class="pointer-events-none absolute inset-0 bg-black/0 group-hover:bg-black/25"
      />
      <span
        v-if="note.original"
        class="absolute top-2.5 left-2.5 z-[1] rounded px-1.5 py-0.5 text-[10px] font-medium text-white"
        style="background:#ff2442"
      >
        原创
      </span>
      <span
        v-if="note.video"
        class="absolute top-2.5 right-2.5 z-[1] text-white drop-shadow"
      >
        <svg viewBox="0 0 24 24" width="28" height="28" fill="currentColor" aria-hidden="true">
          <path d="M8 5.5v13l11-6.5z" />
        </svg>
      </span>
    </div>
    <h2 class="mt-2 shrink-0 line-clamp-2 text-[14px] leading-5">{{ note.title }}</h2>
    <div class="mt-2 flex shrink-0 items-center justify-between text-[12px] text-[#8a8a8a]">
      <span class="flex min-w-0 items-center gap-1.5">
        <img
          v-if="note.avatarUrl"
          :src="note.avatarUrl"
          alt=""
          class="h-5 w-5 shrink-0 rounded-full object-cover"
        />
        <span v-else class="h-5 w-5 shrink-0 rounded-full bg-[#d9d9d9]" />
        <span class="truncate">{{ note.author }}</span>
      </span>
      <span class="flex items-center gap-1">
        <Icon name="heart" :size="13" />
        {{ note.likes }}
      </span>
    </div>
  </article>
</template>
