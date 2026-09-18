<template>
  <el-card>
    <template #header><span>数据搜索</span></template>

    <div class="search-bar">
      <div class="search-row">
        <div class="search-box">
          <el-icon class="search-icon"><Search /></el-icon>
          <input
            v-model="keyword"
            class="search-input"
            placeholder="搜索爬取的内容…"
            @keyup.enter="loadData"
          />
          <button v-if="keyword" class="clear-btn" @click="keyword = ''; loadData()">&times;</button>
        </div>
        <el-button type="primary" class="search-btn" @click="loadData">搜索</el-button>
      </div>
      <div class="search-row filter-row">
        <el-select v-model="filterGroup" placeholder="爬虫分组" clearable style="width: 160px" @change="loadData">
          <el-option v-for="g in groupOptions" :key="g" :label="g" :value="g" />
        </el-select>
        <el-select v-model="filterTag" placeholder="标签" clearable style="width: 160px" @change="loadData">
          <el-option v-for="t in tagOptions" :key="t.id" :label="t.label" :value="t.label" />
        </el-select>
      </div>
    </div>

    <div v-if="total > 0" class="result-count">
      找到约 {{ total }} 条结果
    </div>

    <div v-loading="loading" class="result-list">
      <div v-for="row in list" :key="row.id" class="result-item">
        <a class="result-url" :href="row.url" target="_blank" rel="noopener noreferrer">{{ row.url }}</a>
        <h3 class="result-title" v-html="row.titleHl || row.title"></h3>
        <p class="result-content" v-html="row.contentHl || (row.content?.substring(0, 200) + '...')"></p>
        <div v-if="row.images && row.images.length" class="result-images">
          <el-image
            v-for="(img, idx) in row.images.slice(0, 6)"
            :key="idx"
            :src="imageUrl(img)"
            :preview-src-list="row.images.map(imageUrl)"
            :initial-index="idx"
            fit="cover"
            class="result-thumb"
            preview-teleported
            hide-on-click-modal
          />
        </div>
        <div class="result-tags" v-if="row.tags && row.tags.length">
          <el-tag v-for="t in row.tags" :key="t" size="small" class="tag-item" @click="openTagEditor(row)">{{ t }}</el-tag>
        </div>
        <div class="result-meta">
          <span v-if="row.spiderName" class="meta-tag">{{ row.spiderName }}</span>
          <span v-if="row.spiderGroup" class="meta-tag group-tag">{{ row.spiderGroup }}</span>
          <span class="meta-time">{{ formatTime(row.crawlTime) }}</span>
          <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, row)">
            <el-button size="small" text type="primary">
              操作<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="tag">标签</el-dropdown-item>
                <el-dropdown-item command="previewImages">预览图片</el-dropdown-item>
                <el-dropdown-item command="previewContent">预览内容</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
      <div v-if="!loading && list.length === 0" class="empty">
        未找到相关结果
      </div>
    </div>

    <el-pagination
      v-if="total > 0"
      style="margin-top: 24px; justify-content: center"
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @change="loadData"
    />

    <el-dialog
      v-model="previewImagesVisible"
      :width="isFullscreen ? '100%' : '80%'"
      :top="isFullscreen ? '0' : '5vh'"
      :class="{ 'fullscreen-dialog': isFullscreen }"
      destroy-on-close
    >
      <template #header>
        <div class="dialog-header">
          <span>{{ previewTitle }} - 图片</span>
          <div class="dialog-header-actions">
            <div class="zoom-controls">
              <el-button size="small" text @click="zoomOut">
                <el-icon><ZoomOut /></el-icon>
              </el-button>
              <span class="zoom-level">{{ Math.round(imgZoom * 100) }}%</span>
              <el-button size="small" text @click="zoomIn">
                <el-icon><ZoomIn /></el-icon>
              </el-button>
            </div>
            <el-button size="small" text type="primary" @click="toggleFullscreen">
              <el-icon><component :is="isFullscreen ? 'Minus' : 'FullScreen'" /></el-icon>
              {{ isFullscreen ? '退出全屏' : '全屏' }}
            </el-button>
          </div>
        </div>
      </template>
      <div v-loading="previewLoading" :class="{ 'fullscreen-content': isFullscreen }">
        <div v-if="previewImages.length" class="preview-images">
          <el-image
            v-for="(img, idx) in previewImages"
            :key="idx"
            :src="imageUrl(img)"
            :preview-src-list="previewImages.map(imageUrl)"
            :initial-index="idx"
            fit="contain"
            class="preview-img"
            :style="{ width: isFullscreen ? `${Math.round(30 * imgZoom)}%` : (imgWidths[idx] || '240px') }"
            preview-teleported
            hide-on-click-modal
            @load="onPreviewImgLoad($event, idx)"
          />
        </div>
        <div v-else class="preview-empty">暂无图片</div>
      </div>
    </el-dialog>

    <el-dialog v-model="previewContentVisible" :title="previewTitle + ' - 内容'" width="80%" top="5vh" destroy-on-close>
      <div v-loading="previewLoading">
        <div class="preview-container" v-html="previewHtml"></div>
      </div>
    </el-dialog>

    <el-dialog v-model="tagVisible" title="编辑标签" width="480px" destroy-on-close>
      <el-select
        v-model="tagSelection"
        multiple
        filterable
        allow-create
        default-first-option
        placeholder="选择或输入标签"
        style="width: 100%"
      >
        <el-option
          v-for="child in tagOptions"
          :key="child.id"
          :label="child.label"
          :value="child.label"
        />
      </el-select>
      <template #footer>
        <el-button @click="tagVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTags">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { searchContent, searchDetail, searchDelete, searchUpdateTags, dictChildren } from '@/api'
import { Search, ArrowDown, FullScreen, Minus, ZoomIn, ZoomOut } from '@element-plus/icons-vue'

const list = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const filterGroup = ref('')
const groupOptions = ref<string[]>([])
const filterTag = ref('')
const previewImagesVisible = ref(false)
const previewContentVisible = ref(false)
const previewLoading = ref(false)
const previewTitle = ref('')
const previewHtml = ref('')
const previewImages = ref<string[]>([])
const imgWidths = ref<Record<number, string>>({})
const isFullscreen = ref(false)
const imgZoom = ref(1)

const zoomIn = () => {
  imgZoom.value = Math.min(imgZoom.value + 0.25, 4)
}

const zoomOut = () => {
  imgZoom.value = Math.max(imgZoom.value - 0.25, 0.25)
}

const toggleFullscreen = async () => {
  if (!isFullscreen.value) {
    // 进入浏览器全屏
    try {
      await document.documentElement.requestFullscreen()
    } catch {
      // 浏览器拒绝全屏时仍切换 UI 状态
    }
    isFullscreen.value = true
  } else {
    // 退出浏览器全屏
    if (document.fullscreenElement) {
      try {
        await document.exitFullscreen()
      } catch {
        // ignore
      }
    }
    isFullscreen.value = false
  }
}

const MAX_IMG_WIDTH = 480
const MIN_IMG_WIDTH = 160

const onPreviewImgLoad = (e: Event, idx: number) => {
  const img = e.target as HTMLImageElement
  if (!img.naturalWidth) return
  const baseW = Math.min(Math.max(img.naturalWidth, MIN_IMG_WIDTH), MAX_IMG_WIDTH)
  imgWidths.value[idx] = Math.round(baseW * imgZoom.value) + 'px'
}

// 缩放变化时重新计算所有图片宽度
watch(imgZoom, () => {
  const keys = Object.keys(imgWidths.value)
  if (keys.length === 0) return
  // 重新触发 load 事件来更新宽度
  const imgs = document.querySelectorAll('.preview-img img')
  imgs.forEach((img, idx) => {
    const el = img as HTMLImageElement
    if (!el.naturalWidth) return
    const baseW = Math.min(Math.max(el.naturalWidth, MIN_IMG_WIDTH), MAX_IMG_WIDTH)
    imgWidths.value[idx] = Math.round(baseW * imgZoom.value) + 'px'
  })
})

const tagVisible = ref(false)
const tagSelection = ref<string[]>([])
const tagCurrentRow = ref<any>(null)
const tagOptions = ref<any[]>([])

const loadTagOptions = async () => {
  try {
    // 通过后端接口，根据父级 value 获取 tag 的子项作为标签选项
    const res: any = await dictChildren('tag')
    tagOptions.value = res.data || []
  } catch {
    tagOptions.value = []
  }
}

const openTagEditor = (row: any) => {
  tagCurrentRow.value = row
  tagSelection.value = [...(row.tags || [])]
  tagVisible.value = true
  if (tagOptions.value.length === 0) {
    loadTagOptions()
  }
}

const saveTags = async () => {
  if (!tagCurrentRow.value) return
  try {
    await searchUpdateTags(tagCurrentRow.value.id, tagSelection.value)
    tagCurrentRow.value.tags = [...tagSelection.value]
    ElMessage.success('标签已更新')
    tagVisible.value = false
  } catch {
    ElMessage.error('标签更新失败')
  }
}

const formatTime = (t: string) => {
  if (!t) return ''
  const d = new Date(t)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// ES 中存储的是 MinIO 相对路径（objectName），通过后端接口获取图片数据
const imageUrl = (objectName: string) => {
  if (!objectName) return ''
  // 兼容旧数据：若已是完整 URL 则直接返回
  if (/^https?:\/\//i.test(objectName)) return objectName
  return `/api/file/image?bucket=crawler-images&objectName=${encodeURIComponent(objectName)}`
}

const loadData = async () => {
  loading.value = true
  try {
    const params: any = { current: page.value, size: size.value, keyword: keyword.value }
    if (filterGroup.value) params.spiderGroup = filterGroup.value
    if (filterTag.value) params.tag = filterTag.value
    const res: any = await searchContent(params)
    list.value = res.data?.content || []
    total.value = res.data?.totalElements || 0
  } finally {
    loading.value = false
  }
}

const showPreviewImages = async (row: any) => {
  previewImagesVisible.value = true
  previewLoading.value = true
  previewTitle.value = row.title || '内容预览'
  previewImages.value = row.images || []
  imgWidths.value = {}
  try {
    const res: any = await searchDetail(row.id)
    if (res.data?.images?.length) {
      previewImages.value = res.data.images
    }
  } catch {
    // 加载失败时保留列表中的图片
  } finally {
    previewLoading.value = false
  }
}

const showPreviewContent = async (row: any) => {
  previewContentVisible.value = true
  previewLoading.value = true
  previewTitle.value = row.title || '内容预览'
  previewHtml.value = ''
  try {
    const res: any = await searchDetail(row.id)
    previewHtml.value = res.data?.rawHtml || '<p>无原始内容</p>'
  } catch {
    previewHtml.value = '<p>加载失败</p>'
  } finally {
    previewLoading.value = false
  }
}

const handleCommand = (command: string, row: any) => {
  if (command === 'tag') {
    openTagEditor(row)
  } else if (command === 'previewImages') {
    showPreviewImages(row)
  } else if (command === 'previewContent') {
    showPreviewContent(row)
  } else if (command === 'delete') {
    handleDelete(row)
  }
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定删除该条数据?', '警告', { type: 'warning' })
  } catch {
    return
  }
  try {
    await searchDelete(row.id)
    ElMessage.success('删除成功')
  } catch {
    ElMessage.error('删除失败')
  }
  if (list.value.length === 1 && page.value > 1) {
    page.value--
  }
  loadData()
}

const loadGroupOptions = async () => {
  try {
    // 通过后端接口，根据父级 value 获取其子项列表
    const res: any = await dictChildren('spider-group')
    const children = res.data || []
    groupOptions.value = children.map((c: any) => c.value || c.label).filter(Boolean)
  } catch {
    groupOptions.value = []
  }
}

onMounted(() => {
  loadGroupOptions()
  loadTagOptions()
  loadData()
})
</script>

<style scoped>
.search-bar {
  margin-bottom: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 700px;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-row {
  gap: 12px;
}

.search-box {
  flex: 1;
  display: flex;
  align-items: center;
  border: 1px solid #dfe1e5;
  border-radius: 24px;
  padding: 0 16px;
  height: 44px;
  background: #fff;
  transition: box-shadow 0.2s;
}

.search-box:focus-within {
  box-shadow: 0 1px 6px rgba(32, 33, 36, 0.28);
  border-color: transparent;
}

.search-icon {
  color: #9aa0a6;
  font-size: 20px;
  margin-right: 12px;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 16px;
  height: 100%;
  color: #202124;
}

.search-input::placeholder {
  color: #9aa0a6;
}

.clear-btn {
  border: none;
  background: none;
  color: #9aa0a6;
  font-size: 22px;
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
}

.clear-btn:hover {
  color: #202124;
}

.search-btn {
  border-radius: 24px;
  padding: 0 24px;
  height: 44px;
  font-size: 15px;
}

.result-count {
  color: #999;
  font-size: 13px;
  margin-bottom: 16px;
}

.result-list {
  min-height: 100px;
}

.result-item {
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
}

.result-item:last-child {
  border-bottom: none;
}

.result-url {
  display: block;
  color: #006621;
  font-size: 13px;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-decoration: none;
}

.result-url:hover {
  text-decoration: underline;
}

.result-title {
  color: #1a0dab;
  font-size: 18px;
  font-weight: 400;
  margin: 0 0 6px 0;
  line-height: 1.4;
}

.result-title :deep(em) {
  font-style: normal;
  color: #1a0dab;
  font-weight: 700;
}

.result-content {
  color: #545454;
  font-size: 14px;
  line-height: 1.6;
  margin: 0 0 8px 0;
}

.result-content :deep(em) {
  font-style: normal;
  font-weight: 700;
}

.result-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0 0 10px 0;
}

.result-thumb {
  width: 72px;
  height: 72px;
  border-radius: 4px;
  border: 1px solid #eee;
  cursor: pointer;
}

.result-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0 0 8px 0;
}

.tag-item {
  cursor: pointer;
}

.result-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #999;
}

.meta-tag {
  background: #f0f0f0;
  padding: 2px 8px;
  border-radius: 3px;
  color: #666;
}

.group-tag {
  background: #e8f4fd;
  color: #409eff;
}

.empty {
  text-align: center;
  color: #999;
  padding: 40px 0;
  font-size: 14px;
}

.preview-images {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 16px;
  align-items: flex-start;
}

.preview-img {
  height: auto;
  max-width: 100%;
  border-radius: 4px;
  border: 1px solid #eee;
  background: #fafafa;
  cursor: pointer;
  transition: width 0.2s ease;
}

.preview-container {
  max-height: 70vh;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 16px;
  background: #fff;
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.fullscreen-content {
  max-height: calc(100vh - 120px);
  overflow-y: auto;
}

:deep(.fullscreen-dialog) {
  margin: 0 !important;
  height: 100vh;
  border-radius: 0;
}

:deep(.fullscreen-dialog .el-dialog__body) {
  height: calc(100vh - 54px);
  overflow-y: auto;
}
</style>
