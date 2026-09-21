<template>
  <el-card>
    <template #header><span>数据搜索</span></template>

    <div class="search-bar">
      <div class="search-row">
        <SearchHistoryDropdown
          :open="historyOpen"
          :keyword="keyword"
          authenticated
          @select="selectHistory"
          @close="historyOpen = false"
        >
          <div class="search-box" :class="{ 'history-search-open': historyOpen && !keyword }">
            <el-icon class="search-icon"><Search /></el-icon>
            <input
              v-model="keyword"
              class="search-input"
              placeholder="搜索爬取的内容…"
              @focus="historyOpen = !keyword"
              @input="historyOpen = !keyword"
              @keyup.enter="doSearch"
            />
            <button v-if="keyword" class="clear-btn" @click="keyword = ''; doSearch()">&times;</button>
            <button class="search-btn" type="button" @click="doSearch">搜索</button>
          </div>
        </SearchHistoryDropdown>
      </div>
      <div class="search-row filter-row">
        <el-select v-model="filterGroup" placeholder="分组" clearable style="width: 160px" @change="onGroupChange">
          <el-option v-for="g in groupOptions" :key="g" :label="g" :value="g" />
        </el-select>
        <el-select v-model="filterSpider" placeholder="站点" clearable filterable style="width: 200px" @change="onSpiderChange">
          <el-option-group v-for="sec in spiderGroupedOptions" :key="sec.key" :label="sec.label">
            <el-option v-for="s in sec.items" :key="s.id" :label="s.name" :value="s.id" />
          </el-option-group>
        </el-select>
        <el-select v-model="filterTag" placeholder="标签" clearable style="width: 160px" @change="loadData">
          <el-option v-for="t in tagOptions" :key="t.id" :label="t.label" :value="t.label" />
        </el-select>
        <el-checkbox v-model="favoriteOnly" @change="doSearch">只看我的收藏</el-checkbox>
        <el-checkbox v-model="hasImages" @change="doSearch">只看有图片</el-checkbox>
      </div>
    </div>

    <SearchResultsFrame
      v-model:current-page="page"
      v-model:page-size="size"
      :loading="loading"
      :total="total"
      :page-sizes="[10, 20, 50, 100, 200, 500]"
      @change="loadData"
    >
      <template #heading>
        <div v-if="total > 0" class="result-count">
          找到约 {{ total }} 条结果
        </div>
      </template>

      <SearchResultItem
        v-for="row in list"
        :key="row.id"
        :row="row"
        @preview="showPreviewContent"
        @detail="showDetail"
      >
        <template #images>
          <div v-if="row.images && row.images.length" class="result-images">
            <img
              v-for="(img, idx) in row.images.slice(0, 6)"
              :key="idx"
              :src="imageUrl(img)"
              class="result-thumb"
              @click="openAllImages(row)"
            />
          </div>
        </template>

        <template #tags>
          <div class="result-tags" v-if="row.tags && row.tags.length">
            <el-tag v-for="t in row.tags" :key="t" size="small" class="tag-item" @click="openTagEditor(row)">{{ t }}</el-tag>
          </div>
        </template>

        <template #meta>
          <div class="result-meta">
            <span v-if="row.spiderName" class="meta-tag spider-tag" @click="goToSpider(row.spiderId, row.spiderName)">{{ row.spiderName }}</span>
            <span v-if="row.spiderGroup" class="meta-tag group-tag">{{ row.spiderGroup }}</span>
            <span class="meta-time">{{ formatTime(row.crawlTime) }}</span>
            <span v-if="row.updateTime" class="meta-time update-time">更新: {{ formatTime(row.updateTime) }}</span>
            <el-button
              size="small"
              text
              :type="row.favorited ? 'warning' : 'primary'"
              @click.stop="toggleFavorite(row, !row.favorited)"
            >
              <el-icon :size="16" :color="row.favorited ? '#f56c6c' : ''"><StarFilled v-if="row.favorited" /><Star v-else /></el-icon>
              <span style="margin-left: 2px">{{ row.favorited ? '已收藏' : '收藏' }}</span>
            </el-button>
            <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, row)">
              <el-button size="small" text type="primary">
                操作<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="tag">标签</el-dropdown-item>
                  <el-dropdown-item command="rerun" :disabled="!row.spiderId || !row.url">重新爬取</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </SearchResultItem>
    </SearchResultsFrame>

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
            :initial-index="previewInitialIndex"
            fit="contain"
            class="preview-img"
            :style="{ width: isFullscreen ? `${Math.round(100 * imgZoom)}%` : (imgWidths[idx] || '240px') }"
            preview-teleported
            hide-on-click-modal
            @load="onPreviewImgLoad($event, idx)"
          />
        </div>
        <div v-else class="preview-empty">暂无图片</div>
      </div>
    </el-dialog>

    <el-dialog v-model="previewContentVisible" width="80%" top="5vh" destroy-on-close>
      <template #header>
        <div class="preview-content-header">
          <span>{{ previewTitle }} - 内容</span>
          <el-switch v-model="showPreviewSource" inactive-text="内容预览" active-text="HTML 源码" />
        </div>
      </template>
      <div v-loading="previewLoading">
        <div v-if="!showPreviewSource" class="preview-container preview-html" v-html="previewHtml || '无正文内容'"></div>
        <pre v-else class="preview-container detail-text code-text">{{ previewSource || '无原始内容' }}</pre>
      </div>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="搜索结果详情" width="90%" top="5vh" destroy-on-close>
      <template #header>
        <div class="detail-dialog-title">{{ detailData?.title || '搜索结果详情' }}</div>
      </template>
      <div v-loading="detailLoading" class="detail-dialog-body">
        <div v-if="detailData" class="detail-content">
          <div class="detail-header">
            <div class="detail-url-row">
              <a v-if="detailData.url" class="detail-url" :href="detailData.url" target="_blank" rel="noopener noreferrer">{{ detailData.url }}</a>
              <span v-else class="detail-url">暂无来源地址</span>
              <span class="detail-time">抓取：{{ formatTime(detailData.crawlTime) || '未知' }} | 更新：{{ formatTime(detailData.updateTime) || '未更新' }}</span>
            </div>
            <div class="detail-badges">
              <span v-if="detailData.spiderName" class="meta-tag">{{ detailData.spiderName }}</span>
              <span v-if="detailData.spiderGroup" class="meta-tag group-tag">{{ detailData.spiderGroup }}</span>
              <span class="detail-meta-item">来源：{{ detailData.sourceType || '未知' }}</span>
              <span class="detail-meta-item">标签：{{ detailData.tags && detailData.tags.length ? detailData.tags.join(' / ') : '无' }}</span>
            </div>
          </div>

          <el-tabs v-model="detailTab" class="detail-tabs">
            <el-tab-pane label="正文内容" name="content">
              <div v-if="detailData.images && detailData.images.length" class="detail-images">
                <el-image
                  v-for="(img, idx) in detailData.images"
                  :key="idx"
                  :src="imageUrl(img)"
                  :preview-src-list="detailData.images.map(imageUrl)"
                  :initial-index="idx"
                  fit="cover"
                  class="detail-image"
                  preview-teleported
                  hide-on-click-modal
                />
              </div>
              <div class="preview-container detail-text">{{ detailData.content || '无正文内容' }}</div>
            </el-tab-pane>
          </el-tabs>
        </div>
        <div v-else class="empty">暂无详情</div>
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
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import SearchResultItem from '@/components/SearchResultItem.vue'
import SearchResultsFrame from '@/components/SearchResultsFrame.vue'
import SearchHistoryDropdown from '@/components/SearchHistoryDropdown.vue'
import { searchContent, searchDetail, searchDelete, searchUpdateTags, dictChildren, spiderPage, spiderRerun, favoriteAdd, favoriteDelete } from '@/api'
import { Search, ArrowDown, FullScreen, Minus, ZoomIn, ZoomOut, Star, StarFilled } from '@element-plus/icons-vue'

const router = useRouter()

const list = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const keyword = ref('')
const filterSpider = ref<number | ''>('')
const spiderOptions = ref<any[]>([])
const filterGroup = ref('')
const groupOptions = ref<string[]>([])
const filterTag = ref('')
const favoriteOnly = ref(false)
const hasImages = ref(false)
const historyOpen = ref(false)
const previewImagesVisible = ref(false)
const previewContentVisible = ref(false)
const detailVisible = ref(false)
const previewLoading = ref(false)
const detailLoading = ref(false)
const detailTab = ref('content')
const previewTitle = ref('')
const previewHtml = ref('')
const previewSource = ref('')
const showPreviewSource = ref(false)
const previewImages = ref<string[]>([])
const previewInitialIndex = ref(0)
const detailData = ref<any>(null)
const imgWidths = ref<Record<number, string>>({})
const isFullscreen = ref(false)
const MIN_ZOOM = 0.25
const DEFAULT_ZOOM = 1
const normalizeZoom = (value: number) => Math.max(MIN_ZOOM, Number(value.toFixed(2)))
const getDefaultPreviewZoom = () => Math.min(1.8, Math.max(0.8, (window.innerWidth || 1200) / 1000))
const imgZoom = ref(getDefaultPreviewZoom())

const zoomIn = () => {
  imgZoom.value = normalizeZoom(imgZoom.value + 0.25)
}

const zoomOut = () => {
  imgZoom.value = normalizeZoom(imgZoom.value - 0.25)
}

const resetPreviewZoom = () => {
  imgZoom.value = getDefaultPreviewZoom()
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

const MIN_IMG_WIDTH = 160

const getPreviewImageWidth = (naturalWidth?: number) => {
  const fallback = 240
  const width = naturalWidth && Number.isFinite(naturalWidth) ? naturalWidth : fallback
  return Math.max(width, MIN_IMG_WIDTH)
}

const syncPreviewImageWidths = () => {
  const imgs = document.querySelectorAll('.preview-img img')
  if (!imgs.length) return

  imgs.forEach((img, idx) => {
    const el = img as HTMLImageElement
    const baseW = getPreviewImageWidth(el.naturalWidth || undefined)
    imgWidths.value[idx] = `${Math.round(baseW * imgZoom.value)}px`
  })
}

const onPreviewImgLoad = (e: Event, idx: number) => {
  const img = e.target as HTMLImageElement
  if (!img.naturalWidth) return
  const baseW = getPreviewImageWidth(img.naturalWidth)
  imgWidths.value[idx] = Math.round(baseW * imgZoom.value) + 'px'
}

// 缩放/全屏变化时重新计算所有图片宽度，保证图片在弹窗中保持可见且不溢出
watch([imgZoom, isFullscreen], () => {
  syncPreviewImageWidths()
}, { flush: 'post' })

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
    tagCurrentRow.value.favorited = true
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

const stripHtml = (html: string) => {
  if (!html) return ''
  const div = document.createElement('div')
  div.innerHTML = html
  const text = div.textContent || div.innerText || ''
  return text.replace(/\s+/g, ' ').trim()
}

const getImageBucket = () => {
  if (import.meta.env.VITE_MINIO_BUCKET) return import.meta.env.VITE_MINIO_BUCKET
  return import.meta.env.DEV ? 'crawler-images-local' : 'crawler-images'
}

// ES 中存储的是 MinIO 相对路径（objectName），通过后端接口获取图片数据
const imageUrl = (objectName: string) => {
  if (!objectName) return ''
  // 兼容旧数据：若已是完整 URL 则直接返回
  if (/^https?:\/\//i.test(objectName)) return objectName
  return `/api/file/image?bucket=${encodeURIComponent(getImageBucket())}&objectName=${encodeURIComponent(objectName)}`
}

const escapeHtml = (value: string) => value.replace(/[&<>"']/g, (char) => ({
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;'
}[char] || char))

// 新窗口打开当前内容的全部图片（跳转到真实的图片预览页面）
// 不再传递图片列表，仅传递内容 id（含爬虫信息与 url），由预览页自行从后端获取图片
const openAllImages = (row: any) => {
  if (!row.id) {
    ElMessage.info('该条内容暂无图片')
    return
  }

  // 跳转到真实的图片预览页面（独立路由，非 JS 生成的页面）
  const query: Record<string, string> = {
    id: String(row.id),
    title: String(row.title || '图片预览')
  }
  const win = window.open(`/image-preview?${new URLSearchParams(query).toString()}`, '_blank')
  if (!win) {
    ElMessage.warning('浏览器阻止了新窗口，请允许弹出窗口后重试')
  }
}

const goToSpider = async (spiderId?: number, spiderName?: string) => {
  if (!spiderId) return
  await router.push({
    name: 'Spider',
    query: {
      spiderId: String(spiderId),
      spiderName: spiderName || ''
    }
  })
}

// 点击搜索/回车：重置到第 1 页再查询
const doSearch = () => {
  page.value = 1
  historyOpen.value = false
  loadData()
}

const selectHistory = (value: string) => {
  keyword.value = value
  doSearch()
}

const loadData = async () => {
  loading.value = true
  try {
    const params: any = { current: page.value, size: size.value, keyword: keyword.value }
    if (filterSpider.value) params.spiderId = filterSpider.value
    if (filterGroup.value) params.spiderGroup = filterGroup.value
    if (filterTag.value) params.tag = filterTag.value
    if (favoriteOnly.value) params.favoriteOnly = true
    if (hasImages.value) params.hasImages = true
    const res: any = await searchContent(params)
    list.value = res.data?.content || []
    total.value = res.data?.totalElements || 0
  } finally {
    loading.value = false
  }
}

const showPreviewImages = async (row: any, initialIndex = 0) => {
  previewImagesVisible.value = true
  previewLoading.value = true
  imgZoom.value = getDefaultPreviewZoom()
  previewTitle.value = row.title || '内容预览'
  previewImages.value = row.images || []
  imgWidths.value = {}
  previewInitialIndex.value = initialIndex
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

/**
 * 将 rawHtml 中的相对链接替换为基于爬虫源 URL 的绝对链接。
 * 非 http(s) 开头的 href/src 会拼接源 URL；javascript:、mailto:、tel:、# 锚点等保持不变。
 */
const resolveHtmlLinks = (html: string, baseUrl: string): string => {
  if (!html || !baseUrl) return html
  const div = document.createElement('div')
  div.innerHTML = html
  const toAbsolute = (url: string): string => {
    if (!url) return url
    const trimmed = url.trim()
    if (/^(https?:)?\/\//i.test(trimmed)) return trimmed
    if (/^(javascript:|mailto:|tel:|data:|blob:|#)/i.test(trimmed)) return trimmed
    try {
      return new URL(trimmed, baseUrl).href
    } catch {
      return trimmed
    }
  }
  div.querySelectorAll('a[href]').forEach((a) => {
    a.setAttribute('href', toAbsolute(a.getAttribute('href') || ''))
  })
  div.querySelectorAll('img[src]').forEach((img) => {
    img.setAttribute('src', toAbsolute(img.getAttribute('src') || ''))
  })
  div.querySelectorAll('script[src]').forEach((s) => {
    s.setAttribute('src', toAbsolute(s.getAttribute('src') || ''))
  })
  div.querySelectorAll('link[href]').forEach((l) => {
    l.setAttribute('href', toAbsolute(l.getAttribute('href') || ''))
  })
  div.querySelectorAll('source[src]').forEach((s) => {
    s.setAttribute('src', toAbsolute(s.getAttribute('src') || ''))
  })
  div.querySelectorAll('video[src], audio[src]').forEach((m) => {
    m.setAttribute('src', toAbsolute(m.getAttribute('src') || ''))
  })
  return div.innerHTML
}

const showPreviewContent = async (row: any) => {
  previewContentVisible.value = true
  previewLoading.value = true
  previewTitle.value = row.title || '内容预览'
  previewHtml.value = ''
  previewSource.value = ''
  showPreviewSource.value = false
  try {
    const res: any = await searchDetail(row.id)
    const rawHtml = res.data?.rawHtml || res.data?.content || '无原始内容'
    previewSource.value = rawHtml
    previewHtml.value = resolveHtmlLinks(rawHtml, res.data?.url || row.url || '')
  } catch {
    previewHtml.value = '加载失败'
    previewSource.value = '加载失败'
  } finally {
    previewLoading.value = false
  }
}

const showDetail = async (row: any) => {
  detailVisible.value = true
  detailLoading.value = true
  detailTab.value = 'content'
  detailData.value = null
  try {
    const res: any = await searchDetail(row.id)
    detailData.value = res.data || row
  } catch {
    detailData.value = row
  } finally {
    detailLoading.value = false
  }
}

const handleCommand = async (command: string, row: any) => {
  if (command === 'view') {
    showDetail(row)
  } else if (command === 'tag') {
    openTagEditor(row)
  } else if (command === 'previewContent') {
    showPreviewContent(row)
  } else if (command === 'rerun') {
    handleRerun(row)
  } else if (command === 'delete') {
    handleDelete(row)
  }
}

const toggleFavorite = async (row: any, shouldFavorite: boolean) => {
  try {
    if (shouldFavorite) {
      await favoriteAdd(row.id)
    } else {
      await favoriteDelete(row.id)
    }
    row.favorited = shouldFavorite
    ElMessage.success(shouldFavorite ? '已收藏' : '已取消收藏')
  } catch {
    ElMessage.error(shouldFavorite ? '收藏失败' : '取消收藏失败')
  }
}

const handleRerun = async (row: any) => {
  if (!row.spiderId || !row.url) return
  try {
    await ElMessageBox.confirm(`确定重新爬取该记录？\n${row.url}`, '重新爬取', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    await spiderRerun(row.spiderId, row.url)
    ElMessage.success('重新爬取任务已创建')
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.message || '重新爬取失败')
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

const loadSpiderOptions = async () => {
  try {
    const res: any = await spiderPage({ current: 1, size: 1000 })
    spiderOptions.value = res.data?.records || []
  } catch {
    spiderOptions.value = []
  }
}

// 爬虫下拉按爬虫分组关联展示：已知分组在前，其余分组及未分组在后
// 当选择了爬虫分组时，仅展示该分组下的爬虫（联动）
const spiderGroupedOptions = computed(() => {
  const source = filterGroup.value
    ? spiderOptions.value.filter((s) => String(s.group || '') === filterGroup.value)
    : spiderOptions.value
  const map = new Map<string, any[]>()
  for (const s of source) {
    const key = s.group ? String(s.group) : '__none__'
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(s)
  }
  const sections: { key: string; label: string; items: any[] }[] = []
  for (const g of groupOptions.value) {
    if (map.has(g)) sections.push({ key: g, label: g, items: map.get(g)! })
  }
  for (const [key, items] of map.entries()) {
    if (!groupOptions.value.includes(key)) {
      sections.push({ key, label: key === '__none__' ? '未分组' : key, items })
    }
  }
  return sections
})

// 选择分组后刷新（分组在服务端解析为该分组下的爬虫 ID 进行查询）
const onGroupChange = () => {
  loadData()
}

// 联动：选择爬虫后，自动带出其所属分组
const onSpiderChange = () => {
  if (filterSpider.value) {
    const spider = spiderOptions.value.find((s) => s.id === filterSpider.value)
    if (spider) filterGroup.value = spider.group || ''
  }
  loadData()
}

onMounted(() => {
  loadGroupOptions()
  loadTagOptions()
  loadSpiderOptions()
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
  box-sizing: border-box;
  width: 100%;
  border: 1px solid #dfe1e5;
  border-radius: 999px;
  padding: 0 6px 0 16px;
  height: 48px;
  background: #fff;
  transition: box-shadow 0.2s, border-color 0.2s;
  box-shadow: 0 1px 2px rgba(60, 64, 67, 0.08);
}

.search-box:focus-within {
  box-shadow: 0 1px 6px rgba(32, 33, 36, 0.18);
  border-color: #dfe1e5;
}

.history-search-open {
  border-radius: 24px 24px 0 0;
  box-shadow: 0 1px 6px rgba(32, 33, 36, 0.28);
  border-color: #dadce0;
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
  font-size: 15px;
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
  flex-shrink: 0;
  border: none;
  border-radius: 999px;
  background: #1a73e8;
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  padding: 0 22px;
  height: 36px;
  min-height: 36px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.search-btn:hover {
  background: #1769d1;
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
  cursor: pointer;
  transition: color 0.2s ease, text-shadow 0.2s ease, transform 0.2s ease;
}

.result-title:hover {
  color: #0d47a1;
  text-shadow: 0 1px 0 rgba(13, 71, 161, 0.08);
}

.result-title:active {
  color: #0b3c8a;
  transform: translateY(1px);
}

.result-title :deep(em) {
  font-style: normal;
  color: #1a0dab;
  font-weight: 700;
}

.result-content-line {
  display: inline;
  line-height: 1.6;
  margin: 0 0 8px;
}

.result-content {
  color: #545454;
  font-size: 14px;
  line-height: 1.6;
  margin: 0;
  display: inline;
}

.result-detail-link {
  display: inline;
  color: #1a73e8;
  font-size: 13px;
  cursor: pointer;
  margin-left: 4px;
  white-space: nowrap;
  vertical-align: baseline;
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

.result-thumb-link {
  display: inline-block;
  line-height: 0;
  border-radius: 4px;
  overflow: hidden;
}

.result-thumb {
  width: 72px;
  height: 72px;
  border-radius: 4px;
  border: 1px solid #eee;
  cursor: pointer;
  display: block;
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

.spider-tag {
  cursor: pointer;
  transition: all 0.2s ease;
}

.spider-tag:hover {
  background: #e6f4ff;
  color: #1677ff;
}

.group-tag {
  background: #e8f4fd;
  color: #409eff;
}

.update-time {
  color: #999;
  font-size: 12px;
}

.empty {
  text-align: center;
  color: #999;
  padding: 40px 0;
  font-size: 14px;
}

.preview-images {
  display: flex;
  flex-wrap: nowrap;
  gap: 16px;
  margin-bottom: 16px;
  justify-content: flex-start;
  align-items: flex-start;
  overflow-x: auto;
  padding-bottom: 8px;
}

.preview-img {
  display: block;
  flex: 0 0 auto;
  height: auto;
  max-width: none;
  border-radius: 4px;
  border: 1px solid #eee;
  background: #fafafa;
  cursor: pointer;
  transition: width 0.2s ease, max-width 0.2s ease;
}
:deep(.preview-img .el-image__inner) {
  display: block;
  width: 100%;
  height: auto;
  margin: 0 auto;
  object-fit: contain;
}

.preview-container {
  max-height: 70vh;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 16px;
  background: #fff;
}

.preview-content-header {
  display: flex;
  align-items: center;
  gap: 16px;
  justify-content: space-between;
  color: #172b4d;
  font-size: 16px;
  font-weight: 600;
}

.detail-dialog-body {
  min-height: 220px;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 4px 0 16px;
  border-bottom: 1px solid #edf0f3;
}

.detail-dialog-title {
  color: #172b4d;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.detail-url {
  color: #087f7d;
  font-size: 13px;
  word-break: break-all;
}

.detail-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 0 24px;
  padding: 4px 0;
  border-bottom: 1px solid #edf0f3;
}

.detail-card {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-height: 42px;
  padding: 10px 0;
  border-bottom: 1px solid #f3f5f7;
  color: #486581;
  font-size: 13px;
}

.detail-label {
  flex: 0 0 56px;
  font-size: 12px;
  color: #909399;
}

.detail-images {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
  padding: 4px 0;
}

.detail-image {
  width: clamp(160px, 22vw, 240px);
  height: clamp(110px, 16vw, 160px);
  border-radius: 8px;
  border: 1px solid #eee;
  overflow: hidden;
}

.detail-tabs {
  width: 100%;
}

.detail-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-body h4 {
  margin: 0;
  font-size: 14px;
  color: #486581;
  font-weight: 700;
}

.detail-text {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #303133;
  max-height: 42vh;
  overflow-y: auto;
}

.code-text {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 13px;
}

.preview-html {
  line-height: 1.8;
  color: #303133;
  word-break: break-word;
}

.preview-html img {
  max-width: 100%;
  height: auto;
}

.preview-html iframe {
  max-width: 100%;
}

.source-tag { background: #e8f7f5; color: #087f7d; }

:deep(.meta-tag) { padding: 3px 8px; border-radius: 4px; font-size: 12px; font-weight: 500; }

@media (max-width: 720px) {
  .detail-header { flex-direction: column; }
  .detail-title { font-size: 21px; }
  .detail-grid { grid-template-columns: 1fr; }
  .detail-images { gap: 8px; }
  .detail-image { width: calc(50% - 4px); height: 120px; }
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
