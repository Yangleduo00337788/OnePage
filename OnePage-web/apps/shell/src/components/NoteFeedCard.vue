<!-- 瀑布流一张卡：图文/视频均用封面图；纯文字把正文铺在封面上。 -->
<script setup lang="ts">
import { computed } from 'vue'
import Icon from './Icon.vue'
import IdentityBadges from './IdentityBadges.vue'
import TextNoteCover from './TextNoteCover.vue'
import { useNoteDetail } from '../stores/noteDetail'
import type { NoteCard } from '../data/home'

const props = defineProps<{ note: NoteCard }>()
const noteDetail = useNoteDetail()

function open(e: MouseEvent) {
  noteDetail.show(props.note, e.currentTarget)
}

const isText = computed(() => !props.note.image)
</script>

<template>
  <article
    :data-note-id="note.id"
    class="flex w-[280px] cursor-pointer flex-col"
    @click="open"
  >
    <div class="group relative h-[360px] overflow-hidden rounded-2xl bg-[var(--fill)]">
      <img
        v-if="note.image"
        :src="note.image"
        :alt="note.title"
        class="pointer-events-none h-full w-full object-cover"
        referrerpolicy="no-referrer"
      />
      <TextNoteCover v-else size="feed" :title="note.title" :body="note.body" />
      <span
        v-if="!isText"
        class="pointer-events-none absolute inset-0 bg-black/0 group-hover:bg-black/25"
      />
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
        <span class="relative h-7 w-7 shrink-0 overflow-visible">
          <img
            v-if="note.avatarUrl"
            :src="note.avatarUrl"
            alt=""
            class="h-7 w-7 rounded-full object-cover"
            referrerpolicy="no-referrer"
          />
          <span v-else class="block h-7 w-7 rounded-full bg-[#d9d9d9]" />
          <IdentityBadges size="sm" :live-host="note.liveHost" :verified="note.verified" :official="note.official" />
        </span>
        <span class="truncate">{{ note.author }}</span>
      </span>
      <span class="flex items-center gap-1">
        <Icon name="heart" :size="13" />
        {{ note.likes }}
      </span>
    </div>
  </article>
</template>
