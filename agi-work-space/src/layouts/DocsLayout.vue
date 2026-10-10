<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterView } from 'vue-router'
import { PanelLeftClose, PanelLeftOpen } from 'lucide-vue-next'
import AppHeader from '@/components/AppHeader.vue'
import AppSidebar from '@/components/AppSidebar.vue'
import { useI18n } from '@/composables/useI18n'
import { useResizableSidebar } from '@/composables/useResizableSidebar'

const { t } = useI18n()
const mobileOpen = ref(false)
const { width, collapsed, resizing, toggle, startResize } = useResizableSidebar()

const sidebarStyle = computed(() => ({
  width: collapsed.value ? '0px' : `${width.value}px`,
}))
</script>

<template>
  <div class="min-h-screen bg-background">
    <AppHeader @menu="mobileOpen = true" />
    <div class="flex w-full">
      <div
        class="relative z-30 hidden h-[calc(100vh-3.5rem)] shrink-0 md:block"
        :class="['sticky top-14 overflow-visible', !resizing && 'transition-[width] duration-200 ease-out']"
        :style="sidebarStyle"
      >
        <div
          class="h-full overflow-hidden bg-sidebar"
          :style="{ width: collapsed ? '0px' : `${width}px` }"
        >
          <div class="h-full border-r" :style="{ width: `${width}px` }">
            <AppSidebar :title="t('docsNav')" />
          </div>
        </div>

        <button
          type="button"
          class="absolute top-5 z-50 flex h-7 w-7 items-center justify-center rounded-md border border-border bg-background text-foreground shadow-sm transition-colors hover:bg-accent"
          :class="collapsed ? 'left-3' : 'right-0 translate-x-1/2'"
          :title="collapsed ? t('expandNav') : t('collapseNav')"
          :aria-label="collapsed ? t('expandNav') : t('collapseNav')"
          @click="toggle"
        >
          <PanelLeftOpen v-if="collapsed" class="size-3.5" />
          <PanelLeftClose v-else class="size-3.5" />
        </button>

        <div
          v-if="!collapsed"
          class="group absolute inset-y-0 right-0 z-20 w-1.5 translate-x-1/2 cursor-col-resize"
          :title="t('resizeNav')"
          @mousedown="startResize"
        >
          <div
            class="mx-auto h-full w-px bg-transparent transition-colors group-hover:bg-ring group-active:bg-ring"
            :class="resizing ? 'bg-ring' : ''"
          />
        </div>
      </div>
      <div class="min-w-0 flex-1">
        <RouterView :key="$route.path" />
      </div>
    </div>

    <Teleport to="body">
      <Transition name="drawer">
        <div v-if="mobileOpen" class="fixed inset-0 z-50 md:hidden">
          <button type="button" class="absolute inset-0 bg-background/70 backdrop-blur-sm" @click="mobileOpen = false" />
          <div class="drawer-panel relative h-full w-72 max-w-[85vw] border-r bg-sidebar shadow-xl">
            <AppSidebar :title="t('docsNav')" @navigate="mobileOpen = false" />
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>
