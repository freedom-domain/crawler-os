<template>
  <div class="image-preview-page">
    <!-- 工具栏触发按钮 -->
    <button 
      type="button" 
      class="toolbar-trigger"
      :style="triggerStyle"
      @click="showToolbar = !showToolbar"
      @mousedown="startDrag"
    >
      <el-icon><Setting /></el-icon>
    </button>
    
    <!-- 工具栏 -->
    <div 
      v-show="showToolbar" 
      class="toolbar-header"
      :style="toolbarStyle"
    >
      <div class="toolbar-section">
        <span class="section-label">图片</span>
        <span class="img-count">{{ images.length }}</span>
      </div>
      <div class="toolbar-divider"></div>
      <div class="toolbar-section">
        <span class="section-label">每行</span>
        <input
          v-model.number="colsInput"
          type="number"
          min="1"
          max="20"
          class="cols-input"
          @change="onColsChange"
        />
      </div>
      <div class="toolbar-divider"></div>
      <div class="toolbar-section zoom-section">
        <button type="button" class="tool-btn" @click="gridZoom(-0.1)" title="缩小">−</button>
        <span class="zoom-label">{{ Math.round(gridScale * 100) }}%</span>
        <button type="button" class="tool-btn" @click="gridZoom(0.1)" title="放大">＋</button>
      </div>
      <button type="button" class="reset-btn" @click="gridScale = 1" title="重置">重置</button>
    </div>

    <!-- 图片列表：直接显示图片，无卡片容器，每行张数可输入 -->
    <div class="grid-wrapper">
      <div class="grid" :style="gridStyle">
        <img
          v-for="(img, idx) in images"
          :key="idx"
          class="thumb"
          :src="img"
          :alt="`图片 ${idx + 1}`"
          loading="lazy"
          @click="openViewer(idx)"
        />
        <div v-if="!images.length" class="empty-state">暂无图片</div>
      </div>
    </div>

    <!-- 全屏查看器 -->
    <Teleport to="body">
      <div v-if="viewerOpen" class="viewer-overlay" @click.self="closeViewer">
        <div class="viewer-stage" ref="stageRef">
          <img
            ref="imgRef"
            :src="images[viewerIndex]"
            :alt="`图片 ${viewerIndex + 1}`"
            :style="{ transform: `scale(${viewerScale})` }"
          />
        </div>

        <!-- 顶部工具栏 -->
        <div class="viewer-topbar">
          <button type="button" class="tool-btn" @click="viewerZoom(-0.15)" title="缩小">−</button>
          <span class="zoom-label">{{ Math.round(viewerScale * 100) }}%</span>
          <button type="button" class="tool-btn" @click="viewerZoom(0.15)" title="放大">＋</button>
          <button type="button" class="tool-btn" @click="viewerScale = 1" title="重置缩放">重置</button>
        </div>

        <!-- 关闭按钮 -->
        <button class="viewer-close" type="button" @click="closeViewer" aria-label="关闭">&times;</button>

        <!-- 左右导航 -->
        <button v-if="images.length > 1" class="viewer-nav prev" type="button" @click="prevImage" aria-label="上一张">&#8249;</button>
        <button v-if="images.length > 1" class="viewer-nav next" type="button" @click="nextImage" aria-label="下一张">&#8250;</button>

        <!-- 底部位置指示 -->
        <div class="viewer-position">{{ viewerIndex + 1 }} / {{ images.length }}</div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { Setting } from '@element-plus/icons-vue'

const route = useRoute()

const title = ref('图片预览')
const images = ref<string[]>([])
const gridScale = ref(1)
// 每行显示的图片张数（可在页面输入）
const cols = ref(4)
const colsInput = ref(4)
const viewerOpen = ref(false)
const viewerIndex = ref(0)
const viewerScale = ref(1)
const stageRef = ref<HTMLElement | null>(null)
const imgRef = ref<HTMLImageElement | null>(null)
const showToolbar = ref(false)

// 工具栏拖拽
const triggerPos = ref({ x: 0, y: 0 })
const isDragging = ref(false)
const dragOffset = ref({ x: 0, y: 0 })

const triggerStyle = computed(() => ({
  position: 'fixed',
  left: `${triggerPos.value.x}px`,
  top: `${triggerPos.value.y}px`,
  transform: 'none',
  right: 'auto',
  zIndex: 20
}))

const toolbarStyle = computed(() => {
  const { x, y } = triggerPos.value
  const vw = window.innerWidth
  // 判断按钮在左边还是右边
  const isLeft = x < vw / 2
  return {
    position: 'fixed',
    left: isLeft ? `${x + 45}px` : `${x - 150}px`,
    top: `${y - 80}px`,
    right: 'auto',
    transform: 'none',
    zIndex: 20
  }
})

const startDrag = (e: MouseEvent) => {
  isDragging.value = true
  dragOffset.value = {
    x: e.clientX - triggerPos.value.x,
    y: e.clientY - triggerPos.value.y
  }
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', endDrag)
}

const onDrag = (e: MouseEvent) => {
  if (!isDragging.value) return
  triggerPos.value = {
    x: e.clientX - dragOffset.value.x,
    y: e.clientY - dragOffset.value.y
  }
}

const endDrag = () => {
  isDragging.value = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', endDrag)
  // 吸附到最近的边
  const { x, y } = triggerPos.value
  const vw = window.innerWidth
  const vh = window.innerHeight
  const snapX = x < vw / 2 ? 0 : vw - 40
  triggerPos.value = { x: snapX, y }
}

// 初始化位置：右侧居中
onMounted(() => {
  triggerPos.value = {
    x: window.innerWidth - 40,
    y: window.innerHeight / 2 - 20
  }
  // 点击其他地方隐藏工具栏
  document.addEventListener('click', onDocumentClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
})

const onDocumentClick = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  // 如果点击的不是触发按钮或工具栏，则隐藏
  if (!target.closest('.toolbar-trigger') && !target.closest('.toolbar-header')) {
    showToolbar.value = false
  }
}

// 窗口标题跟随内容标题
watch(title, (t) => {
  document.title = t || '图片预览'
})

// 从路由参数解析图片列表
onMounted(() => {
  const t = route.query.title as string
  if (t) title.value = t

  const srcs = route.query.srcs as string
  if (srcs) {
    try {
      images.value = JSON.parse(decodeURIComponent(srcs))
    } catch {
      images.value = []
    }
  }

  // 绑定键盘事件
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.title = '图片预览'
})

// ===== 每行张数 =====
const onColsChange = () => {
  const n = Math.round(Number(colsInput.value))
  cols.value = Number.isFinite(n) ? Math.min(20, Math.max(1, n)) : 4
  colsInput.value = cols.value
}

// 列宽 = (100% - 所有列间 gap) / 每行张数；不足一行的图片按实际数量铺满
const gridStyle = computed(() => ({
  zoom: gridScale.value,
  gridTemplateColumns: `repeat(auto-fit, minmax(min(calc(100% / ${cols.value} - ${cols.value > 1 ? (cols.value - 1) * 14 / cols.value : 0}px), 100%), 1fr))`
}))

// ===== 网格缩放 =====
const gridZoom = (delta: number) => {
  gridScale.value = Math.min(3, Math.max(0.4, Number((gridScale.value + delta).toFixed(2))))
}

// ===== 查看器 =====
const openViewer = (index: number) => {
  viewerIndex.value = index
  viewerScale.value = 1
  viewerOpen.value = true
  // 打开时确保居中
  requestAnimationFrame(resetStageScroll)
}

const closeViewer = () => {
  viewerOpen.value = false
}

const viewerZoom = (delta: number) => {
  viewerScale.value = Math.min(5, Math.max(0.2, Number((viewerScale.value + delta).toFixed(2))))
}

const resetStageScroll = () => {
  if (!stageRef.value) return
  stageRef.value.scrollLeft = 0
  stageRef.value.scrollTop = 0
}

const prevImage = () => {
  if (!images.value.length) return
  viewerIndex.value = (viewerIndex.value - 1 + images.value.length) % images.value.length
  viewerScale.value = 1
  // 回到居中位置
  resetStageScroll()
}

const nextImage = () => {
  if (!images.value.length) return
  viewerIndex.value = (viewerIndex.value + 1) % images.value.length
  viewerScale.value = 1
  resetStageScroll()
}

// ===== 键盘快捷键 =====
const onKeydown = (event: KeyboardEvent) => {
  if (!viewerOpen.value) {
    // 网格模式：仅保留 0 重置缩放（缩放使用 Ctrl+滚轮）
    if (event.key === '0') gridScale.value = 1
    return
  }
  // 查看器模式
  switch (event.key) {
    case 'Escape':
      closeViewer()
      break
    case 'ArrowLeft':
      prevImage()
      break
    case 'ArrowRight':
      nextImage()
      break
    case '0':
      viewerScale.value = 1
      break
  }
}

// ===== Ctrl+滚轮缩放 =====
const onWheel = (event: WheelEvent) => {
  if (!event.ctrlKey) return
  event.preventDefault()
  if (viewerOpen.value) {
    viewerZoom(event.deltaY < 0 ? 0.15 : -0.15)
  } else {
    // 仅在网格区域缩放
    const target = event.target as HTMLElement
    if (target.closest('.grid-wrapper')) {
      gridZoom(event.deltaY < 0 ? 0.1 : -0.1)
    }
  }
}

onMounted(() => {
  document.addEventListener('wheel', onWheel, { passive: false })
})

onBeforeUnmount(() => {
  document.removeEventListener('wheel', onWheel)
})
</script>

<style scoped>
.image-preview-page {
  min-height: 100vh;
  background: #f0f2f5;
  color: #1e293b;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', sans-serif;
}

/* ===== 工具栏 ===== */
.toolbar-header {
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  backdrop-filter: blur(8px);
}
.toolbar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}
.section-label {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 500;
}
.img-count {
  font-size: 16px;
  font-weight: 600;
  color: #334155;
}
.toolbar-divider {
  width: 24px;
  height: 1px;
  background: #e2e8f0;
}
.cols-input {
  width: 40px;
  height: 28px;
  text-align: center;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  font-size: 13px;
  color: #334155;
  outline: none;
  transition: border-color 0.2s;
}
.cols-input:focus {
  border-color: #3b82f6;
}
.zoom-section {
  flex-direction: row;
  gap: 8px;
}
.tool-btn {
  width: 28px;
  height: 28px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  color: #475569;
  font-size: 16px;
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: all 0.2s;
}
.tool-btn:hover {
  background: #f1f5f9;
  border-color: #94a3b8;
}
.zoom-label {
  font-size: 12px;
  color: #64748b;
  min-width: 36px;
  text-align: center;
}
.reset-btn {
  padding: 6px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  color: #475569;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.reset-btn:hover {
  background: #f1f5f9;
  border-color: #94a3b8;
}
.toolbar-trigger {
  width: 40px;
  height: 40px;
  border: 1px solid rgba(226, 232, 240, 0.5);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.6);
  color: #64748b;
  cursor: grab;
  display: grid;
  place-items: center;
  backdrop-filter: blur(4px);
  transition: background 0.2s, color 0.2s;
}
.toolbar-trigger:active {
  cursor: grabbing;
}
.toolbar-trigger:hover {
  background: rgba(255, 255, 255, 0.9);
  color: #3b82f6;
}
.toolbar-trigger:hover {
  background: #fff;
  color: #3b82f6;
  border-color: #94a3b8;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}
.img-count {
  font-size: 13px;
  color: #64748b;
  white-space: nowrap;
}
.cols-control {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #64748b;
  white-space: nowrap;
}
.cols-input {
  width: 56px;
  height: 30px;
  padding: 0 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #f1f5f9;
  color: #1e293b;
  font-size: 13px;
  text-align: center;
  outline: none;
  transition: border-color 0.15s, background 0.15s;
}
.cols-input:focus {
  border-color: #94a3b8;
  background: #fff;
}

/* ===== 工具栏按钮 ===== */
.toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
}
.tool-btn {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 6px 12px;
  background: #f1f5f9;
  color: #1e293b;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.15s;
  white-space: nowrap;
}
.tool-btn:hover {
  background: #e2e8f0;
  border-color: #cbd5e1;
}
.tool-btn:active {
  transform: scale(0.96);
}
.zoom-label {
  min-width: 48px;
  text-align: center;
  font-size: 13px;
  color: #64748b;
  font-variant-numeric: tabular-nums;
}

/* ===== 图片网格 ===== */
.grid-wrapper {
  padding: 20px 24px;
  width: 100%;
}
.grid {
  display: grid;
  /* 列数由页面输入控制（gridTemplateColumns 通过内联样式设置） */
  gap: 14px;
  justify-items: center;
}
.thumb {
  display: block;
  width: 100%;
  height: 100vh;
  object-fit: contain;
  border-radius: 10px;
  cursor: zoom-in;
  transition: transform 0.15s, box-shadow 0.15s;
  background: #f8fafc;
}
.thumb:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
}
.empty-state {
  width: 100%;
  text-align: center;
  padding: 60px 0;
  color: #64748b;
  font-size: 15px;
}

/* ===== 全屏查看器 ===== */
.viewer-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(15, 23, 42, 0.94);
  display: flex;
  align-items: center;
  justify-content: center;
}
.viewer-stage {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: auto;
}
.viewer-stage img {
  display: block;
  max-width: calc(100vw - 120px);
  max-height: calc(100vh - 120px);
  object-fit: contain;
  user-select: none;
  -webkit-user-drag: none;
  border-radius: 4px;
  flex-shrink: 0;
  /* 以图片中心为缩放基准，保证缩放时图片始终居中 */
  transform-origin: center center;
  transition: transform 0.15s ease;
}

/* 查看器顶部工具栏 */
.viewer-topbar {
  position: fixed;
  top: 16px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  z-index: 110;
}

/* 关闭按钮 */
.viewer-close {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 110;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.97);
  color: #1e293b;
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  transition: all 0.15s;
}
.viewer-close:hover {
  background: #e2e8f0;
  transform: scale(1.08);
}

/* 左右导航 */
.viewer-nav {
  position: fixed;
  top: 50%;
  transform: translateY(-50%);
  z-index: 110;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.97);
  color: #1e293b;
  font-size: 26px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  transition: all 0.15s;
}
.viewer-nav:hover {
  background: #e2e8f0;
  transform: translateY(-50%) scale(1.08);
}
.viewer-nav.prev { left: 20px; }
.viewer-nav.next { right: 20px; }

/* 底部位置指示 */
.viewer-position {
  position: fixed;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 110;
  padding: 6px 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.97);
  color: #1e293b;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
}

/* 响应式 */
@media (max-width: 640px) {
  .page-header { padding: 12px 16px; }
  .page-title { max-width: 50%; font-size: 15px; }
  .grid-wrapper { padding: 16px; }
  .grid { gap: 10px; }
  .viewer-stage img { max-width: calc(100vw - 60px); max-height: calc(100vh - 100px); }
  .viewer-nav.prev { left: 10px; }
  .viewer-nav.next { right: 10px; }
  .viewer-nav { width: 38px; height: 38px; font-size: 22px; }
}
</style>
