<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import Input from '@/components/ui/input/Input.vue'
import { useI18n } from '@/composables/useI18n'
import { searchDocs, type SearchHit } from '@/lib/search'
import { cn } from '@/lib/utils'

const { t } = useI18n()
const router = useRouter()
const isApple = computed(() => {
  if (typeof navigator === 'undefined') return false
  return /Mac|iPhone|iPad|iPod/i.test(navigator.platform || navigator.userAgent)
})
const open = ref(false)
const query = ref('')
const active = ref(0)
const inputEl = ref<{ focus: () => void } | null>(null)

const hits = computed<SearchHit[]>(() => searchDocs(query.value))

function close() {
  open.value = false
  query.value = ''
}

function show() {
  open.value = true
  nextTick(() => inputEl.value?.focus())
}

function go(hit: SearchHit) {
  router.push(hit.slug ? `/${hit.slug}` : '/')
  close()
}

function onKey(event: KeyboardEvent) {
  const meta = event.metaKey || event.ctrlKey
  if (meta && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    if (open.value) close()
    else show()
    return
  }
  if (!open.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
  }
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    active.value = Math.min(active.value + 1, Math.max(hits.value.length - 1, 0))
  }
  if (event.key === 'ArrowUp') {
    event.preventDefault()
    active.value = Math.max(active.value - 1, 0)
  }
  if (event.key === 'Enter' && hits.value[active.value]) {
    event.preventDefault()
    go(hits.value[active.value]!)
  }
}

watch(hits, () => {
  active.value = 0
})

onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => window.removeEventListener('keydown', onKey))

defineExpose({ show })
</script>

<template>
  <Button
    variant="outline"
    class="h-8 w-9 justify-center px-0 text-muted-foreground shadow-none sm:w-56 sm:justify-start sm:gap-2 sm:px-2.5"
    @click="show"
  >
    <Search class="size-3.5" />
    <span class="hidden flex-1 text-left text-xs sm:inline">{{ t('searchPlaceholder') }}</span>
    <kbd class="hidden rounded border bg-muted px-1.5 py-0.5 font-mono text-[10px] text-muted-foreground md:inline">
      {{ isApple ? '⌘K' : 'Ctrl K' }}
    </kbd>
  </Button>

  <Teleport to="body">
    <Transition name="search">
      <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-start justify-center bg-background/70 px-4 pt-[14vh] backdrop-blur-sm"
        @click.self="close"
      >
        <div class="search-panel w-full max-w-xl overflow-hidden rounded-xl border bg-popover shadow-lg">
          <div class="border-b p-3">
            <Input ref="inputEl" v-model="query" :placeholder="t('searchPlaceholder')" class="h-10 border-0 shadow-none focus-visible:ring-0" />
          </div>
          <div class="max-h-80 overflow-y-auto p-2 scrollbar-thin">
            <p v-if="!query.trim()" class="px-2 py-6 text-center text-sm text-muted-foreground">
              {{ t('searchHint') }}
            </p>
            <p v-else-if="!hits.length" class="px-2 py-6 text-center text-sm text-muted-foreground">
              {{ t('noResults') }}
            </p>
            <button
              v-for="(hit, index) in hits"
              :key="hit.slug || 'index'"
              type="button"
              :class="
                cn(
                  'flex w-full flex-col rounded-lg px-3 py-2 text-left transition-colors',
                  index === active ? 'bg-accent' : 'hover:bg-muted/80',
                )
              "
              @mouseenter="active = index"
              @click="go(hit)"
            >
              <span class="text-sm font-medium">{{ hit.title }}</span>
              <span v-if="hit.description" class="truncate text-xs text-muted-foreground">{{ hit.description }}</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
