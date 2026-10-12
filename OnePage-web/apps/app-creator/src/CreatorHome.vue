<!-- 创作者后台主区。页面由左栏 page 决定。P0 数据占位。 -->
<script setup lang="ts">
import { computed } from 'vue'
import type { UserProfile } from '@onepage/api-client'
import CreatorDash from './CreatorDash.vue'
import CreatorDict from './CreatorDict.vue'
import CreatorEvents from './CreatorEvents.vue'
import CreatorNotes from './CreatorNotes.vue'
import CreatorStats from './CreatorStats.vue'
import PublishComposer, { type PublishKind } from './PublishComposer.vue'

export type PageId = 'home' | 'notes' | 'account' | 'content' | 'fans' | 'events' | 'dict' | 'publish'
export type { PublishKind }

const props = defineProps<{
  page: PageId
  publishKind: PublishKind
  me: UserProfile | null
  editPostId?: string
}>()
const emit = defineEmits<{
  'update:publishKind': [PublishKind]
  publish: [PublishKind]
  'open-stats': []
  'open-notes': []
  'open-events': []
  edit: [string]
}>()

const statsKind = computed(() => {
  if (props.page === 'account' || props.page === 'content' || props.page === 'fans') return props.page
  return null
})
</script>

<template>
  <div class="cc-root">
    <PublishComposer
      v-if="props.page === 'publish'"
      :key="props.editPostId || 'new'"
      :kind="props.publishKind"
      :edit-post-id="props.editPostId"
      @update:kind="emit('update:publishKind', $event)"
      @close="emit('open-notes')"
    />

    <CreatorDash
      v-else-if="props.page === 'home'"
      :me="props.me"
      @publish="emit('publish', $event)"
      @open-stats="emit('open-stats')"
      @open-notes="emit('open-notes')"
      @open-events="emit('open-events')"
    />

    <CreatorNotes v-else-if="props.page === 'notes'" @edit="emit('edit', $event)" />

    <CreatorStats v-else-if="statsKind" :kind="statsKind" @publish="emit('publish', $event)" />

    <CreatorEvents v-else-if="props.page === 'events'" />

    <CreatorDict v-else />
  </div>
</template>

<style scoped>
.cc-root {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}
</style>
