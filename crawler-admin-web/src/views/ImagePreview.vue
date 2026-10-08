<template>
  <div class="image-preview-page" @click="hideImageMenu">
    <!-- 工具栏触发按钮 -->
    <button 
      type="button" 
      class="toolbar-trigger"
      :class="{ open: showToolbar }"
      :style="triggerStyle"
      @click="showToolbar = !showToolbar"
      @mousedown="startDrag"
    >
      <el-icon><Setting /></el-icon>
    </button>
    
    <!-- 工具栏 -->
    <div 
      v-show="showToolbar" 
      ref="toolbarRef"
      class="toolbar-header"
      :class="{ open: showToolbar }"
      :style="toolbarStyle"
    >
      <div class="toolbar-controls">
        <!-- 图片数量 -->
        <div class="control-item">
          <span class="section-label">图片</span>
          <span class="img-count">{{ images.length }}</span>
        </div>
        <!-- 每页张数 -->
        <div class="control-item">
          <span class="section-label">每页</span>
          <div class="stepper">
            <button type="button" class="icon-btn" :disabled="pageSize <= 1" @click="stepPageSize(-1)" title="减少每页张数"><el-icon><Minus /></el-icon></button>
            <span class="stepper-value">{{ pageSize }}</span>
            <button type="button" class="icon-btn" :disabled="pageSize >= maxPageSize" @click="stepPageSize(1)" title="增加每页张数"><el-icon><Plus /></el-icon></button>
          </div>
        </div>
        <!-- 每行张数 -->
        <div class="control-item">
          <span class="section-label">每行</span>
          <div class="stepper">
            <button type="button" class="icon-btn" :disabled="cols <= 1" @click="stepCols(-1)" title="减少每行张数"><el-icon><Minus /></el-icon></button>
            <span class="stepper-value">{{ cols }}</span>
            <button type="button" class="icon-btn" :disabled="cols >= pageSize" @click="stepCols(1)" title="增加每行张数"><el-icon><Plus /></el-icon></button>
          </div>
        </div>
        <!-- 缩放 -->
        <div class="control-item">
          <span class="section-label">缩放</span>
          <div class="zoom-section">
            <button type="button" class="icon-btn" @click="gridZoom(-0.1)" title="缩小"><el-icon><Minus /></el-icon></button>
            <span class="zoom-label">{{ Math.round(gridScale * 100) }}%</span>
            <button type="button" class="icon-btn" @click="gridZoom(0.1)" title="放大"><el-icon><Plus /></el-icon></button>
          </div>
        </div>
        <!-- 重置按钮 -->
        <button type="button" class="reset-btn" @click="resetToInitial" title="重置">重置</button>
      </div>
    </div>

    <!-- 加载失败（无 loading，避免遮挡） -->
    <div v-if="loadError" class="empty-state">
      {{ loadError }}
    </div>

    <!-- 图片列表：立即渲染（loading 时盖在上面的遮罩挡住），每行张数可输入，支持分页 -->
    <div v-else class="grid-wrapper" :class="{ 'pagination-open': totalPages > 1 }">
      <div class="grid" :style="gridStyle">
        <img
          v-for="(img, idx) in pageImages"
          :key="idx"
          class="thumb"
          :src="img"
          :alt="`图片 ${pageStart + idx + 1}`"
          loading="eager"
          @contextmenu.prevent="openImageMenu($event, pageStart + idx)"
        />
        <div v-if="!images.length" class="empty-state">暂无图片</div>
      </div>
      <div
        v-if="imageMenu.visible"
        class="image-context-menu"
        :style="{ left: `${imageMenu.x}px`, top: `${imageMenu.y}px` }"
        @click.stop
      >
        <button v-if="sourceUrl" class="context-action context-action-source" type="button" @click="openSourcePage">
          <el-icon><Promotion /></el-icon>
          <span>跳转来源页</span>
        </button>
        <button v-if="userStore.token" class="context-action context-action-tag" type="button" @click="openTagEditor">
          <el-icon><Edit /></el-icon>
          <span>设置标签</span>
        </button>
        <button v-if="userStore.token" class="context-action context-action-delete" type="button" @click="deleteSelectedImage">
          <el-icon><Delete /></el-icon>
          <span>删除图片</span>
        </button>
      </div>

      <!-- 分页控件：本页图片加载完成且多页时显示 -->
      <div v-if="!pageLoading && currentPageLoaded && totalPages > 1" class="pagination">
        <button type="button" class="page-btn" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
        <span class="page-info">第 {{ page }} / {{ totalPages }} 页</span>
        <button type="button" class="page-btn" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
      </div>
    </div>

    <!-- 首屏加载动画：盖在网格之上的全屏遮罩（网格已在渲染，图片在遮罩后面加载） -->
    <div v-if="loading" class="loading-mask">
      <div class="loading-spinner"></div>
      <span class="loading-text">图片加载中…</span>
    </div>

    <!-- 翻页加载动画：本页图片切换时盖在网格上方，全部加载完成后消失 -->
    <div v-if="pageLoading" class="page-loading-mask">
      <div class="loading-spinner"></div>
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
            @contextmenu.prevent="openImageMenu($event, viewerIndex)"
          />
        </div>

        <!-- 顶部工具栏 -->
        <div class="viewer-topbar">
          <button type="button" class="icon-btn" @click="viewerZoom(-0.15)" title="缩小"><el-icon><Minus /></el-icon></button>
          <span class="zoom-label">{{ Math.round(viewerScale * 100) }}%</span>
          <button type="button" class="icon-btn" @click="viewerZoom(0.15)" title="放大"><el-icon><Plus /></el-icon></button>
          <button type="button" class="reset-btn" @click="viewerScale = 1" title="重置缩放">重置</button>
        </div>

        <!-- 关闭按钮 -->
        <button class="viewer-close" type="button" @click="closeViewer" aria-label="关闭"><el-icon><Close /></el-icon></button>

        <!-- 左右导航 -->
        <button v-if="images.length > 1" class="viewer-nav prev" type="button" @click="prevImage" aria-label="上一张"><el-icon><ArrowLeft /></el-icon></button>
        <button v-if="images.length > 1" class="viewer-nav next" type="button" @click="nextImage" aria-label="下一张"><el-icon><ArrowRight /></el-icon></button>

        <!-- 底部位置指示 -->
        <div class="viewer-position">{{ viewerIndex + 1 }} / {{ images.length }}</div>
      </div>
    </Teleport>

    <TagEditorDialog
      v-model="tagVisible"
      :row="tagCurrentRow"
      :tag-options="tagOptions"
      @saved="handleTagsSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick, type CSSProperties } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, ArrowRight, Close, Minus, Plus, Setting, Promotion, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { confirm } from '@/utils/confirm'
import { dictChildren, searchDeleteImage, searchDetail } from '@/api'
import { useUserStore } from '@/stores/user'
import TagEditorDialog from '@/components/TagEditorDialog.vue'

const route = useRoute()
const userStore = useUserStore()
const contentId = computed(() => typeof route.query.id === 'string' ? route.query.id : '')

const title = ref('图片预览')
const sourceUrl = ref('')
const images = ref<string[]>([])
const imageObjects = ref<string[]>([])
const contentTags = ref<string[]>([])
const tagVisible = ref(false)
const tagCurrentRow = ref<{ id: string; tags: string[] } | null>(null)
const tagOptions = ref<any[]>([])
const imageMenu = ref({ visible: false, x: 0, y: 0, index: -1 })
const loading = ref(false)
const loadError = ref('')
const gridScale = ref(1)
// 每行显示的图片张数（+− 步进调整）
const cols = ref(4)
const viewerOpen = ref(false)
const viewerIndex = ref(0)
const viewerScale = ref(1)
// 分页：每页显示的图片张数与当前页码
const pageSize = ref(4)
const page = ref(1)
const stageRef = ref<HTMLElement | null>(null)
const imgRef = ref<HTMLImageElement | null>(null)
const showToolbar = ref(false)

// 工具栏拖拽
const triggerPos = ref({ x: 0, y: 0 })
const isDragging = ref(false)
const dragOffset = ref({ x: 0, y: 0 })

// 工具栏显示时重新计算位置
watch(showToolbar, (val) => {
  if (val) {
    nextTick(() => {
      // 触发重新计算
      triggerPos.value = { ...triggerPos.value }
    })
  }
})

const triggerStyle = computed<CSSProperties>(() => ({
  position: 'fixed',
  left: `${triggerPos.value.x}px`,
  top: `${triggerPos.value.y}px`,
  transform: 'none',
  right: 'auto',
  zIndex: 25
}))

const toolbarRef = ref<HTMLElement | null>(null)

const toolbarStyle = computed<CSSProperties>(() => {
  const { x, y } = triggerPos.value
  const vw = window.innerWidth
  const vh = window.innerHeight
  // 判断按钮在左边还是右边
  const isLeft = x < vw / 2
  // 竖向工具栏尺寸（小屏自适应，高度取实际渲染值）
  const baseWidth = toolbarRef.value?.offsetWidth || (window.matchMedia('(max-width: 640px)').matches ? 160 : 180)
  // 确保工具栏不超出屏幕宽度（留 10px 边距）
  const toolbarWidth = Math.min(baseWidth, Math.max(100, vw - 20))
  const toolbarHeight = Math.min(toolbarRef.value?.offsetHeight || 280, vh - 20)
  // 按钮尺寸 40px，面板与按钮间距 12px
  const triggerSize = 40
  const gap = 12
  // 垂直方向：面板与按钮中心垂直对齐，确保不超出上下边界
  let top = y + triggerSize / 2 - toolbarHeight / 2
  if (top < 10) top = 10
  if (top + toolbarHeight > vh - 10) top = Math.max(10, vh - 10 - toolbarHeight)
  // 水平方向：按钮在左半屏时面板向右展开，否则向左展开，并夹取在窗口内
  let left = isLeft ? x + triggerSize + gap : x - gap - toolbarWidth
  if (left < 10) left = 10
  if (left + toolbarWidth > vw - 10) left = Math.max(10, vw - 10 - toolbarWidth)
  return {
    position: 'fixed',
    left: `${left}px`,
    top: `${top}px`,
    right: 'auto',
    width: `${toolbarWidth}px`,
    transform: 'none',
    zIndex: 25
  } as CSSProperties
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
  const vw = window.innerWidth
  const vh = window.innerHeight
  // 限制在窗口内
  const x = Math.max(0, Math.min(vw - 40, e.clientX - dragOffset.value.x))
  const y = Math.max(0, Math.min(vh - 40, e.clientY - dragOffset.value.y))
  triggerPos.value = { x, y }
}

const endDrag = () => {
  isDragging.value = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', endDrag)
  // 吸附到最近的边
  const { x, y } = triggerPos.value
  const vw = window.innerWidth
  const snapX = x < vw / 2 ? 0 : vw - 40
  triggerPos.value = { x: snapX, y }
}

// 移动端断点：≤ 860px（与 CSS @media (max-width: 640px) 的视觉断点保持一致，
// 即竖屏手机/小屏 pad 都按"移动端"处理，每页 1 张）
const isMobile = () => window.innerWidth <= 860
// 平板/小屏断点：861–1800px（横屏 pad / 小屏笔记本，每页 2 张）
const isTablet = () => {
  const w = window.innerWidth
  return w > 860 && w <= 1800
}



// 初始化位置：右侧居中
onMounted(() => {
  triggerPos.value = {
    x: window.innerWidth - 40,
    y: window.innerHeight / 2 - 20
  }
  // 点击其他地方隐藏工具栏
  document.addEventListener('click', onDocumentClick)
  // ≤860 每页 1 张；861–1800 每页 2 张；>1800 按图片总数
  lastBreakpoint = 0
  applyBreakpointDefaults()
  window.addEventListener('resize', onResize)
  loadTagOptions()
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  window.removeEventListener('resize', onResize)
})

// 窗口尺寸 = 可视窗口尺寸（window.innerWidth/innerHeight，即 viewport 实际渲染区域，
// 已扣除滚动条、浏览器 UI 等，与 CSS 媒体查询 @media 的判断基准一致）。
// 分页计算基于可视窗口宽度断点：
// ≤860px 每页 1 张；861–1800px 每页 2 张；>1800px 每页 3~4 张。
const getBreakpoint = () => {
  const w = window.innerWidth
  if (w <= 860) return 1
  if (w <= 1800) return 2
  return 3
}
let lastBreakpoint = 0 // 0=未记录, 1=≤860, 2=861–1800, 3=>1800
let paramsDirty = false // 手动调整过参数后，resize 时强制重算
const applyBreakpointDefaults = (force = false) => {
  const bp = getBreakpoint()
  if (!force && bp === lastBreakpoint) return
  lastBreakpoint = bp
  // 无图时不计算（避免 images.length=0 导致 pageSize=0）
  if (!images.value.length) return
  // 约束：每页数量不超过图片总数（避免 1 张图却算出 1 页、pageSize 比实际大）
  const clampSize = (s: number) => Math.min(s, images.value.length || s)
  if (bp === 1) {
    pageSize.value = clampSize(1)
    cols.value = clampSize(1)
  } else if (bp === 2) {
    pageSize.value = clampSize(2)
    cols.value = clampSize(2)
  } else {
    const size = images.value.length > 4 ? 3 : 4
    pageSize.value = clampSize(size)
    cols.value = clampSize(size)
  }
  // 当前页码不能超过总页数（图片总数变化 / 断点切换后兜底）
  page.value = Math.min(page.value, Math.max(1, Math.ceil(images.value.length / pageSize.value)))
}
const onResize = () => {
  // 手动调整过参数后，窗口尺寸变化不再触发重算（只有重置才恢复自适应）
  if (paramsDirty) return
  applyBreakpointDefaults()
}
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

// ES 中存储的是 MinIO 相对路径（objectName），通过后端接口获取原图。
// 用 /image 接口（原图）：不带 width 参数，返回原始尺寸图片。
const imageUrl = (objectName: string) => {
  if (!objectName) return ''
  // 兼容旧数据：若已是完整 URL 则直接返回
  if (/^https?:\/\//i.test(objectName)) return objectName
  return `/api/file/image?objectName=${encodeURIComponent(objectName)}`
}

// 图片预览页整体流程：
// 1. 刚进去 → 请求页面数据（loading 遮罩显示加载动画）
// 2. 请求完成 → 获取可视窗口尺寸（window.innerWidth）计算分页（applyBreakpointDefaults）
// 3. 本页图片全部加载完成 → 加载动画消失（loading=false）
// 4. 执行初始化（currentPageLoaded=true → 分页条显示，网格可交互）
const loadImages = async () => {
  const id = contentId.value
  if (!id) {
    loadError.value = '缺少内容标识，无法加载图片'
    return
  }

  loading.value = true
  loadError.value = ''
  currentPageLoaded.value = false
  try {
    // 1. 请求页面数据
    const res: any = await searchDetail(id)
    const doc = res.data
    contentTags.value = doc?.tags || []
    sourceUrl.value = typeof doc?.url === 'string' ? doc.url.trim() : ''
    if (!title.value && doc?.title) title.value = doc.title
    const rawImages: string[] = doc?.images || []
    imageObjects.value = rawImages
    images.value = rawImages.map(imageUrl).filter(Boolean)

    if (!images.value.length) {
      loadError.value = '该条内容暂无图片'
      loading.value = false
      return
    }

    // 2. 等网格渲染到 DOM 后，复用旋转逻辑（onResize 同款）：
    //    用当前可视窗口尺寸强制重算分页（force=true 绕过 lastBreakpoint 短路）。
    //    不在请求完成时算：onMounted 时 window.innerWidth 可能是旧值（移动端动态视口 /
    //    旋转未稳定），网格渲染后再算才能保证拿到正确的当前宽度。
    await nextTick()
    applyBreakpointDefaults(true)

    // 3. 等本页可见图片全部加载完成（loading 遮罩盖在上面）
    await waitForPageLoaded()

    // 4. 加载动画消失 + 执行初始化（分页条显示，网格可交互）
    loading.value = false
    currentPageLoaded.value = true


  } catch {
    loadError.value = '图片加载失败，请稍后重试'
    loading.value = false
  }
}

const loadTagOptions = async () => {
  if (!userStore.token) return
  try {
    const res: any = await dictChildren('tag')
    tagOptions.value = res.data || []
  } catch {
    ElMessage.error('标签选项加载失败')
  }
}

const openTagEditor = () => {
  imageMenu.value.visible = false
  if (!contentId.value) return
  tagCurrentRow.value = { id: contentId.value, tags: [...contentTags.value] }
  tagVisible.value = true
}

const handleTagsSaved = (tags: string[]) => {
  contentTags.value = tags
  if (tagCurrentRow.value) tagCurrentRow.value.tags = tags
}

const openImageMenu = (event: MouseEvent, index: number) => {
  if (!userStore.token && !sourceUrl.value) return
  const menuW = 152
  const menuH = 110
  let x = event.clientX
  let y = event.clientY
  if (x + menuW > window.innerWidth - 4) x = Math.max(4, window.innerWidth - menuW - 4)
  if (y + menuH > window.innerHeight - 4) y = Math.max(4, window.innerHeight - menuH - 4)
  imageMenu.value = { visible: true, x, y, index }
}

const openSourcePage = () => {
  imageMenu.value.visible = false
  if (!sourceUrl.value) {
    ElMessage.info('该条内容暂无来源页')
    return
  }

  let url: URL
  try {
    url = new URL(sourceUrl.value)
  } catch {
    ElMessage.error('来源页地址无效')
    return
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    ElMessage.error('来源页地址无效')
    return
  }

  const win = window.open(url.href, '_blank', 'noopener,noreferrer')
  if (!win) {
    ElMessage.warning('浏览器阻止了新窗口，请允许弹出窗口后重试')
  }
}

const hideImageMenu = () => {
  imageMenu.value.visible = false
}

const deleteSelectedImage = async () => {
  const index = imageMenu.value.index
  imageMenu.value.visible = false
  const id = route.query.id as string
  const objectName = imageObjects.value[index]
  if (!id || !objectName) return
  try {
    if (!await confirm('确定删除选定的图片吗？删除后无法恢复。', { title: '删除图片', confirmText: '删除', danger: true })) return
  } catch {
    return
  }
  try {
    await searchDeleteImage(id, objectName)
    images.value.splice(index, 1)
    imageObjects.value.splice(index, 1)
    viewerIndex.value = Math.min(viewerIndex.value, Math.max(0, images.value.length - 1))
    if (!images.value.length) loadError.value = '该条内容暂无图片'
    ElMessage.success('图片已删除')
  } catch {
    ElMessage.error('图片删除失败')
  }
}

// 从路由参数获取内容 id 并加载图片
onMounted(() => {
  const t = route.query.title as string
  if (t) title.value = t
  loadImages()
  // 绑定键盘事件
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.title = '图片预览'
})

// 每页张数上限：不超过图片总数（无图片时退化为 200）
const maxPageSize = computed(() => (images.value.length ? Math.min(200, images.value.length) : 200))

// ===== 每页张数（+− 步进，1~图片总数）=====
const stepPageSize = (delta: number) => {
  const next = Math.min(maxPageSize.value, Math.max(1, pageSize.value + delta))
  if (next === pageSize.value) return
  pageSize.value = next
  // 每行张数不能超过每页张数
  if (cols.value > pageSize.value) cols.value = pageSize.value
  page.value = 1
  paramsDirty = true
}

// ===== 每行张数（+− 步进，1~每页张数）=====
const stepCols = (delta: number) => {
  const next = Math.min(pageSize.value, Math.max(1, cols.value + delta))
  if (next === cols.value) return
  cols.value = next
  paramsDirty = true
}

// ===== 分页 =====
const totalPages = computed(() => Math.max(1, Math.ceil(images.value.length / pageSize.value)))
const pageStart = computed(() => (page.value - 1) * pageSize.value)
const pageImages = computed(() => images.value.slice(pageStart.value, pageStart.value + pageSize.value))

// 本页图片加载状态：请求完成后初始 false，等本页图片全部 complete 后置 true
// （load/error 都置 complete，所以 404 的图也算完成）
const currentPageLoaded = ref(false)
const pageLoading = ref(false)

// 判断图片是否已进入可视区域（含上下少量缓冲区，避免边界抖动）
const isVisibleInViewport = (img: HTMLImageElement): boolean => {
  const rect = img.getBoundingClientRect()
  const viewportHeight = window.innerHeight || document.documentElement.clientHeight
  const buffer = 80
  return rect.bottom >= -buffer && rect.top <= viewportHeight + buffer
}

// 等当前页内可见图片全部加载完成，返回 Promise。
// 以 img.complete 为准，含 8 秒兜底，避免任何异常下永不 resolve。
const waitForPageLoaded = () => new Promise<void>((resolve) => {
  let elapsed = 0
  const timer = setInterval(() => {
    elapsed += 80
    const els = Array.from(document.querySelectorAll<HTMLImageElement>('.grid .thumb'))
    const visibleEls = els.filter(isVisibleInViewport)
    const ok = visibleEls.length > 0 && visibleEls.every((img) => img.complete)
    if (ok) {
      clearInterval(timer)
      resolve()
    } else if (elapsed >= 8000) {
      // 兜底：8 秒内没全部 complete，放行，避免一直不结束
      clearInterval(timer)
      resolve()
    }
  }, 80)
})

const changePage = async (p: number) => {
  const next = Math.min(totalPages.value, Math.max(1, p))
  if (next === page.value || pageLoading.value) return
  page.value = next
  window.scrollTo({ top: 0 })
  pageLoading.value = true
  currentPageLoaded.value = false
  try {
    await nextTick()
    await waitForPageLoaded()
  } finally {
    pageLoading.value = false
    currentPageLoaded.value = true
  }
}

// 重置：走页面初始化逻辑（重新加载图片，所有页面参数恢复初始默认值）
const resetToInitial = () => {
  gridScale.value = 1
  page.value = 1
  paramsDirty = false
  // ≤860 每页 1 张；861–1800 每页 2 张；>1800 默认每页 4 张
  // 约束：每页数量不超过图片总数，每行数量不超过每页数量
  let size = 4
  if (isMobile()) size = 1
  else if (isTablet()) size = 2
  pageSize.value = Math.min(size, images.value.length || size)
  cols.value = Math.min(size, pageSize.value)
  loadImages()
  window.scrollTo({ top: 0 })
}

// 列宽 = (100% - 所有列间 gap) / 每行张数；不足一行的图片按实际数量铺满
const gridStyle = computed<CSSProperties>(() => ({
  zoom: gridScale.value,
  gridTemplateColumns: `repeat(auto-fit, minmax(min(calc(100% / ${cols.value} - ${cols.value > 1 ? (cols.value - 1) * 14 / cols.value : 0}px), 100%), 1fr))`
}))

// ===== 网格缩放 =====
const gridZoom = (delta: number) => {
  gridScale.value = Math.min(3, Math.max(0.4, Number((gridScale.value + delta).toFixed(2))))
  paramsDirty = true
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
    // 网格模式：左右方向键切换上一页/下一页，0 重置缩放（缩放使用 Ctrl+滚轮）
    if (event.key === 'ArrowLeft') {
      changePage(page.value - 1)
      return
    }
    if (event.key === 'ArrowRight') {
      changePage(page.value + 1)
      return
    }
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
  height: 100%;
  min-height: 0;
  background: #f3f7f8;
  color: #172b4d;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', sans-serif;
  overflow: auto;
}

.image-context-menu {
  position: fixed;
  z-index: 100;
  min-width: 132px;
  padding: 4px;
  background: #fff;
  border: 1px solid #dfe7ee;
  border-radius: 8px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.10);
  animation: context-menu-in 0.12s ease-out;
}

.context-action {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 5px;
  text-align: left;
  background: transparent;
  border: 0;
  font: inherit;
  font-size: 13px;
  color: #486581;
  cursor: pointer;
  transition: background 0.12s ease;
}

.context-action .el-icon {
  font-size: 14px;
  color: #8993a4;
  flex-shrink: 0;
}

.context-action:hover {
  background: #f8fbfb;
}

.context-action-delete {
  color: #d65b50;
}

.context-action-delete .el-icon {
  color: #ed8b80;
}

.context-action-delete:hover {
  background: #fce9e6;
}

.context-action:focus-visible,
.reset-btn:focus-visible {
  outline: 2px solid rgba(114, 224, 200, 0.35);
  outline-offset: -2px;
}

@keyframes context-menu-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@supports (min-height: 100dvh) {
  .image-preview-page { height: 100%; min-height: 0; }
}

/* ===== 工具栏 ===== */
.toolbar-header {
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid #dfe7ee;
  border-radius: 12px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 180px;
  min-width: 180px;
  max-width: 180px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  backdrop-filter: blur(8px);
  /* 点击图标后渐进展开：从按钮侧滑入 + 淡入 */
  transform-origin: left center;
  transform: translateX(-12px) scale(0.96);
  opacity: 0;
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.25s ease;
}
.toolbar-header.open {
  transform: translateX(0) scale(1);
  opacity: 1;
}
.toolbar-controls {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
  min-width: 0;
}
.control-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 10px;
  border: 1px solid #dfe7ee;
  border-radius: 8px;
  background: #f8fbfb;
  min-width: 0;
}
.control-item .section-label {
  font-size: 11px;
  color: #8993a4;
}
.control-item .img-count {
  font-size: 13px;
  font-weight: 600;
  color: #243b53;
  line-height: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.control-item .stepper {
  display: flex;
  align-items: center;
  gap: 4px;
}
.control-item .zoom-section {
  display: flex;
  align-items: center;
  gap: 4px;
}
.toolbar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.inline-section {
  flex-direction: row;
  align-items: center;
  gap: 5px;
}
.section-label {
  font-size: 11px;
  color: #8993a4;
  font-weight: 500;
  white-space: nowrap;
}
.img-count {
  font-size: 14px;
  font-weight: 600;
  color: #243b53;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}
.toolbar-divider {
  width: 1px;
  height: 24px;
  background: #dfe7ee;
}
/* +− 步进器（每页/每行张数） */
.stepper {
  display: flex;
  align-items: center;
  gap: 5px;
}
.stepper-value {
  min-width: 24px;
  text-align: center;
  font-size: 13px;
  font-weight: 600;
  color: #243b53;
  font-variant-numeric: tabular-nums;
}
.icon-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
  transform: none;
}
.icon-btn:disabled:hover {
  background: #f8fbfb;
  color: #5e6c84;
}
.zoom-section {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 5px;
  flex-wrap: nowrap;
  white-space: nowrap;
}
.icon-btn {
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 50%;
  background: #f8fbfb;
  color: #5e6c84;
  font-size: 14px;
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: all 0.2s;
}
.icon-btn:hover {
  background: #0f9f9a;
  color: #fff;
  transform: scale(1.08);
}
.icon-btn:active {
  transform: scale(0.94);
}
.zoom-label {
  font-size: 12px;
  color: #8993a4;
  min-width: 38px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.reset-btn {
  padding: 7px 14px;
  border: 1px solid #a9e0d7;
  border-radius: 9px;
  background: linear-gradient(180deg, #fff 0%, #e2f6f3 100%);
  color: #087b78;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 2px 5px rgba(37, 99, 235, 0.08);
  transition: background 0.18s ease, border-color 0.18s ease, color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
  white-space: nowrap;
  flex-shrink: 0;
  align-self: center;
}
.reset-btn:hover {
  background: linear-gradient(180deg, #e2f6f3 0%, #d9f5ef 100%);
  border-color: #72e0c8;
  color: #087b78;
  box-shadow: 0 4px 10px rgba(37, 99, 235, 0.16);
  transform: translateY(-1px);
}
.reset-btn:active {
  box-shadow: 0 1px 3px rgba(37, 99, 235, 0.12);
  transform: translateY(0);
}
.toolbar-trigger {
  width: 40px;
  height: 40px;
  border: 1px solid rgba(226, 232, 240, 0.5);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.6);
  color: #8993a4;
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
  color: #0f9f9a;
}
.toolbar-trigger:hover {
  background: #fff;
  color: #0f9f9a;
  border-color: #8993a4;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}
.toolbar-trigger.open {
  background: #fff;
  color: #0f9f9a;
  border-color: #0f9f9a;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.25);
}
.img-count {
  font-size: 13px;
  color: #8993a4;
  white-space: nowrap;
}

/* ===== 工具栏按钮 ===== */
.toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
}
.tool-btn {
  border: 1px solid #dfe7ee;
  border-radius: 6px;
  padding: 6px 12px;
  background: #f8fbfb;
  color: #172b4d;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.15s;
  white-space: nowrap;
}
.tool-btn:hover {
  background: #dfe7ee;
  border-color: #dfe7ee;
}
.tool-btn:active {
  transform: scale(0.96);
}
.zoom-label {
  min-width: 48px;
  text-align: center;
  font-size: 13px;
  color: #8993a4;
  font-variant-numeric: tabular-nums;
}

/* ===== 图片网格 ===== */
.grid-wrapper {
  position: relative;
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
  /* 可视窗口高 - 上下 padding（40px）- 分页条悬浮区（约 60px），
     避免图片撑满视口后盖住 fixed bottom 的分页条 */
  height: calc(100vh - 100px);
  object-fit: contain;
  border-radius: 10px;
  cursor: zoom-in;
  transition: transform 0.15s, box-shadow 0.15s;
  background: #f8fbfb;
}
.thumb:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
}
.empty-state {
  width: 100%;
  text-align: center;
  padding: 60px 0;
  color: #8993a4;
  font-size: 15px;
}

/* ===== 分页控件（悬浮于页面底部） ===== */
.pagination {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 30;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  backdrop-filter: blur(8px);
}
/* 多页时：网格底部留白，避免首屏图片盖住悬浮分页条 */
.grid-wrapper.pagination-open {
  padding-bottom: 80px;
}
.page-btn {
  padding: 5px 14px;
  border: 1px solid #dfe7ee;
  border-radius: 6px;
  background: #fff;
  color: #5e6c84;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.page-btn:hover:not(:disabled) {
  background: #f8fbfb;
  border-color: #8993a4;
}
.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.page-info {
  font-size: 13px;
  color: #8993a4;
  font-variant-numeric: tabular-nums;
}

/* ===== 翻页加载遮罩（本页图片切换时盖在网格上方，半透明 + spinner） ===== */
.page-loading-mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(243, 247, 248, 0.72);
  backdrop-filter: blur(2px);
}

/* ===== 首屏加载遮罩（盖在网格之上，网格已在渲染，图片在遮罩后加载） ===== */
.loading-mask {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  background: #f3f7f8;
}
.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #dfe7ee;
  border-top-color: #0f9f9a;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
.loading-text {
  color: #8993a4;
  font-size: 14px;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
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
  color: #172b4d;
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  transition: all 0.15s;
}
.viewer-close:hover {
  background: #dfe7ee;
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
  color: #172b4d;
  font-size: 26px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  transition: all 0.15s;
}
.viewer-nav:hover {
  background: #dfe7ee;
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
  color: #172b4d;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
}

/* 响应式 */
@media (max-width: 640px) {
  .grid-wrapper { padding: 16px; }
  .grid { gap: 10px; }
  /* 移动端工具栏：竖向排列，固定宽度 */
  .toolbar-header {
    width: 160px;
    min-width: 160px;
    max-width: 160px;
    padding: 8px;
  }
  .toolbar-controls {
    gap: 5px;
  }
  .control-item {
    padding: 6px 8px;
  }
  .control-item .section-label {
    font-size: 10px;
  }
  .control-item .img-count {
    font-size: 13px;
  }
  .icon-btn {
    width: 24px;
    height: 24px;
    font-size: 12px;
  }
  .stepper-value {
    min-width: 20px;
    font-size: 12px;
  }
  .zoom-label {
    min-width: 32px;
    font-size: 11px;
  }
  .reset-btn {
    padding: 6px 12px;
    font-size: 12px;
    width: 100%;
  }
  /* 移动端分页：贴底通栏，按钮加大便于点按 */
  .pagination {
    left: 16px;
    right: 16px;
    bottom: 16px;
    transform: none;
    justify-content: space-between;
    gap: 8px;
    padding: 10px 14px;
    border-radius: 14px;
  }
  .page-btn {
    flex: 1;
    padding: 10px 0;
    font-size: 14px;
    border-radius: 8px;
  }
  .page-info {
    flex-shrink: 0;
    font-size: 13px;
  }
  .thumb { height: calc(100dvh - 80px); }
  .viewer-stage img { max-width: calc(100vw - 60px); max-height: calc(100dvh - 100px); }
  .viewer-nav.prev { left: 10px; }
  .viewer-nav.next { right: 10px; }
  .viewer-nav { width: 38px; height: 38px; font-size: 22px; }
}


</style>
