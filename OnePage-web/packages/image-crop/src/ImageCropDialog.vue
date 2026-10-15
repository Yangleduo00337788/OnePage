<!--
  本地裁剪后再走 presign 直传。不依赖第三方库。
  滚轮缩放、拖动平移；完成导出 JPEG。
-->
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

const props = defineProps<{
  src: string
  title?: string
  /** 宽/高。头像 1，封面 3，笔记 0.75 */
  aspect: number
  round?: boolean
  fileName?: string
  outputWidth?: number
}>()

const emit = defineEmits<{
  confirm: [file: File]
  cancel: []
}>()

const STAGE_W = 560
const STAGE_H = 400
const PAD = 40

const img = ref<HTMLImageElement | null>(null)
const stageEl = ref<HTMLElement | null>(null)
const ready = ref(false)
const natW = ref(0)
const natH = ref(0)
const busy = ref(false)
const failed = ref('')
const scale = ref(1)
const ox = ref(0)
const oy = ref(0)
const dragging = ref(false)
const lastX = ref(0)
const lastY = ref(0)

const frame = computed(() => {
  const maxW = STAGE_W - PAD * 2
  const maxH = STAGE_H - PAD * 2
  let w = maxW
  let h = w / props.aspect
  if (h > maxH) {
    h = maxH
    w = h * props.aspect
  }
  return {
    w,
    h,
    x: (STAGE_W - w) / 2,
    y: (STAGE_H - h) / 2,
  }
})

const minScale = computed(() => {
  const el = img.value
  if (!el || !el.naturalWidth) return 1
  const f = frame.value
  return Math.max(f.w / el.naturalWidth, f.h / el.naturalHeight)
})

function clampPos() {
  const el = img.value
  if (!el) return
  const f = frame.value
  const w = el.naturalWidth * scale.value
  const h = el.naturalHeight * scale.value
  const minX = f.x + f.w - w
  const minY = f.y + f.h - h
  ox.value = Math.min(f.x, Math.max(minX, ox.value))
  oy.value = Math.min(f.y, Math.max(minY, oy.value))
}

function fit() {
  const el = img.value
  if (!el) return
  natW.value = el.naturalWidth
  natH.value = el.naturalHeight
  const f = frame.value
  scale.value = minScale.value
  ox.value = f.x - (el.naturalWidth * scale.value - f.w) / 2
  oy.value = f.y - (el.naturalHeight * scale.value - f.h) / 2
  clampPos()
  ready.value = true
}

function onImgLoad() {
  failed.value = ''
  fit()
}

function onImgError() {
  failed.value = '图片无法预览'
}

function zoomAt(next: number, cx: number, cy: number) {
  const el = img.value
  if (!el) return
  const lo = minScale.value
  const hi = lo * 6
  const clamped = Math.min(hi, Math.max(lo, next))
  const imgX = (cx - ox.value) / scale.value
  const imgY = (cy - oy.value) / scale.value
  scale.value = clamped
  ox.value = cx - imgX * scale.value
  oy.value = cy - imgY * scale.value
  clampPos()
}

function onWheel(event: WheelEvent) {
  event.preventDefault()
  if (!ready.value) return
  const f = frame.value
  zoomAt(scale.value * (event.deltaY > 0 ? 0.92 : 1.08), f.x + f.w / 2, f.y + f.h / 2)
}

function onPointerDown(event: PointerEvent) {
  if (!ready.value || event.button !== 0) return
  dragging.value = true
  lastX.value = event.clientX
  lastY.value = event.clientY
  stageEl.value?.setPointerCapture(event.pointerId)
}

function onPointerMove(event: PointerEvent) {
  if (!dragging.value) return
  ox.value += event.clientX - lastX.value
  oy.value += event.clientY - lastY.value
  lastX.value = event.clientX
  lastY.value = event.clientY
  clampPos()
}

function onPointerUp() {
  dragging.value = false
}

async function confirm() {
  const el = img.value
  if (!el || !ready.value || busy.value) return
  busy.value = true
  try {
    const f = frame.value
    const sx = (f.x - ox.value) / scale.value
    const sy = (f.y - oy.value) / scale.value
    const sw = f.w / scale.value
    const sh = f.h / scale.value
    const outW = props.outputWidth || Math.round(Math.min(1920, Math.max(f.w * 2, 640)))
    const outH = Math.round(outW / props.aspect)
    const canvas = document.createElement('canvas')
    canvas.width = outW
    canvas.height = outH
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('无法裁剪')
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, outW, outH)
    ctx.drawImage(el, sx, sy, sw, sh, 0, 0, outW, outH)
    const blob = await new Promise<Blob>((resolve, reject) => {
      canvas.toBlob(
        (b) => (b ? resolve(b) : reject(new Error('导出失败'))),
        'image/jpeg',
        0.92,
      )
    })
    const base = (props.fileName || 'image').replace(/\.[^.]+$/, '')
    emit('confirm', new File([blob], `${base}.jpg`, { type: 'image/jpeg' }))
  } catch {
    failed.value = '裁剪失败'
  } finally {
    busy.value = false
  }
}

watch(
  () => props.src,
  () => {
    ready.value = false
    failed.value = ''
  },
)

watch(frame, () => {
  if (img.value?.naturalWidth) fit()
})

onMounted(() => {
  stageEl.value?.addEventListener('wheel', onWheel, { passive: false })
})

onUnmounted(() => {
  stageEl.value?.removeEventListener('wheel', onWheel)
})
</script>

<template>
  <Teleport to="body">
    <div class="crop-mask" @click.self="emit('cancel')">
      <div class="crop-panel" role="dialog" aria-modal="true">
        <h2 class="crop-title">{{ title || '裁剪图片' }}</h2>
        <div
          ref="stageEl"
          class="crop-stage"
          :class="{ 'is-drag': dragging }"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerUp"
          @pointercancel="onPointerUp"
        >
          <img
            ref="img"
            :src="src"
            alt=""
            class="crop-img"
            :style="{
              width: natW ? `${natW}px` : undefined,
              height: natH ? `${natH}px` : undefined,
              transform: `translate(${ox}px, ${oy}px) scale(${scale})`,
              visibility: ready ? 'visible' : 'hidden',
            }"
            draggable="false"
            @load="onImgLoad"
            @error="onImgError"
          />
          <div
            class="crop-frame"
            :class="{ 'is-round': round }"
            :style="{
              width: `${frame.w}px`,
              height: `${frame.h}px`,
              left: `${frame.x}px`,
              top: `${frame.y}px`,
            }"
          />
        </div>
        <p v-if="failed" class="crop-err">{{ failed }}</p>
        <p v-else class="crop-hint">滚轮缩放，拖动调整画面</p>
        <div class="crop-actions">
          <button type="button" class="crop-ghost" @click="emit('cancel')">取消</button>
          <button type="button" class="crop-ok" :disabled="!ready || busy" @click="confirm">
            {{ busy ? '处理中…' : '完成' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.crop-mask {
  position: fixed;
  inset: 0;
  z-index: 70;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
  padding: 16px;
}

.crop-panel {
  width: 608px;
  max-width: calc(100vw - 32px);
  border-radius: 20px;
  background: #fff;
  padding: 22px 24px 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.16);
}

.crop-title {
  margin: 0 0 14px;
  font-size: 18px;
  font-weight: 600;
  color: #1f1f1f;
}

.crop-stage {
  position: relative;
  width: 560px;
  max-width: 100%;
  height: 400px;
  overflow: hidden;
  border-radius: 12px;
  background: #1f1f1f;
  touch-action: none;
  cursor: grab;
  user-select: none;
}

.crop-stage.is-drag {
  cursor: grabbing;
}

.crop-img {
  position: absolute;
  left: 0;
  top: 0;
  max-width: none;
  transform-origin: 0 0;
  pointer-events: none;
}

.crop-frame {
  position: absolute;
  box-sizing: border-box;
  border: 1px solid rgba(255, 255, 255, 0.92);
  box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0.46);
  pointer-events: none;
}

.crop-frame.is-round {
  border-radius: 50%;
}

.crop-hint,
.crop-err {
  margin: 10px 0 0;
  font-size: 13px;
  color: #8a8a8a;
}

.crop-err {
  color: #ff2442;
}

.crop-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.crop-ghost,
.crop-ok {
  height: 36px;
  border-radius: 20px;
  padding: 0 18px;
  font-size: 13px;
}

.crop-ghost {
  background: #f2f2f2;
  color: #1f1f1f;
}

.crop-ghost:hover {
  background: #ececec;
}

.crop-ok {
  background: #ff2442;
  color: #fff;
}

.crop-ok:disabled {
  cursor: default;
  opacity: 0.5;
}
</style>
