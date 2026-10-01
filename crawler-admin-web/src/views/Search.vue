<template>
  <el-card>
    <div class="search-toolbar">
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
              <el-button class="reset-search-button" plain @click="resetSearch">
                <el-icon><RefreshLeft /></el-icon>
                重置
              </el-button>
            </div>
          </SearchHistoryDropdown>
        </div>
        <div class="search-row filter-row">
          <el-select v-model="filterGroup" placeholder="分组" clearable style="width: 160px" @change="onGroupChange">
            <el-option v-for="g in groupOptions" :key="g" :label="g" :value="g" />
          </el-select>
          <el-select v-model="filterSpider" placeholder="站点" clearable filterable style="width: 200px" @change="onSpiderChange">
            <el-option-group v-for="sec in spiderGroupedOptions" :key="sec.key" :label="sec.label">
              <el-option v-for="s in sec.items" :key="s.id" :label="s.name" :value="Number(s.id)" />
            </el-option-group>
          </el-select>
          <el-select v-model="filterTag" placeholder="标签" clearable style="width: 160px" @change="doSearch">
            <el-option v-for="t in tagOptions" :key="t.id" :label="t.label" :value="t.label" />
          </el-select>
          <el-checkbox v-model="favoriteOnly" @change="doSearch">只看我的收藏</el-checkbox>
          <el-checkbox v-model="hasImages" @change="doSearch">只看有图片</el-checkbox>
        </div>
      </div>
      <el-button
        class="public-search-link"
        type="primary"
        plain
        :icon="Promotion"
        @click="openPublicSearch"
      >
        公共搜索
      </el-button>
    </div>

    <SearchResultsFrame
      v-model:current-page="page"
      v-model:page-size="size"
      :loading="loading"
      :total="total"
      :page-sizes="[5, 10, 15, 20, 50, 100, 200, 500]"
      cursor-mode
      :has-more="hasMore"
      @prev="loadPrevPage"
      @next="loadNextPage"
      @change="onPageSizeChange"
    >
      <template #heading>
        <div v-if="total > 0" class="result-count">
          找到约 {{ total }} 条结果
          <span v-if="searchDurationMs > 0" class="search-duration">{{ searchDurationMs >= 1000 ? `${(searchDurationMs / 1000).toFixed(1)}s` : `${searchDurationMs}ms` }}</span>
        </div>
      </template>

      <SearchResultList
        :rows="list"
        authenticated
        :image-url="imageUrl"
        :thumb-url="thumbUrl"
        :format-time="formatTime"
        @preview="showPreviewContent"
        @detail="showDetail"
        @images="openAllImages"
        @tag="openTagEditor"
        @spider="row => goToSpider(row.spiderId, row.spiderName)"
        @favorite="toggleFavorite"
        @command="handleCommand"
      />
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
              <el-icon><component :is="isFullscreen ? Minus : FullScreen" /></el-icon>
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

    <ContentPreviewDialog
      v-model:visible="previewContentVisible"
      :title="previewTitle"
      :html="previewHtml"
      :source="previewSource"
      :loading="previewLoading"
      :source-url="previewBaseUrl"
      v-model:localize="localizePreviewResources"
      v-model:show-source="showPreviewSource"
      @localize-change="togglePreviewResourceLocalization"
    />

    <SearchDetailDialog
      v-model="detailVisible"
      :loading="detailLoading"
      :detail="detailData"
      :image-url="imageUrl"
      :format-time="formatTime"
    />

    <TagEditorDialog v-model="tagVisible" :row="tagCurrentRow" :tag-options="tagOptions" @saved="handleTagsSaved" />
  </el-card>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { confirm } from '@/utils/confirm'
import SearchResultList from '@/components/SearchResultList.vue'
import SearchResultsFrame from '@/components/SearchResultsFrame.vue'
import SearchHistoryDropdown from '@/components/SearchHistoryDropdown.vue'
import SearchDetailDialog from '@/components/SearchDetailDialog.vue'
import TagEditorDialog from '@/components/TagEditorDialog.vue'
import ContentPreviewDialog from '@/components/ContentPreviewDialog.vue'
import { searchContent, searchDetail, searchDelete, dictChildren, spiderPage, spiderRerun, favoriteAdd, favoriteDelete } from '@/api'
import { Search, FullScreen, Minus, ZoomIn, ZoomOut, Promotion, RefreshLeft } from '@element-plus/icons-vue'
import { resolvePreviewHtml } from '@/utils/previewHtml'
import { formatDateTime as formatTime } from '@/utils/dateTime'

const router = useRouter()
const route = useRoute()

const openPublicSearch = () => {
  const query: Record<string, string> = {
    page: String(page.value),
    size: String(size.value)
  }
  if (keyword.value) query.keyword = keyword.value
  if (filterSpider.value) query.spiderId = String(filterSpider.value)
  if (filterGroup.value) query.group = filterGroup.value
  if (filterTag.value) query.tag = filterTag.value
  if (favoriteOnly.value) query.favoriteOnly = 'true'
  if (hasImages.value) query.hasImages = 'true'

  const search = new URLSearchParams(query).toString()
  window.open(`/public/search?${search}`, 'crawler-public-search')
}

const list = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(5)
const total = ref(0)
const searchDurationMs = ref(0)
// PIT + search_after 游标式分页状态
const pitId = ref('')
const searchAfter = ref('')
const hasMore = ref(false)
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
const previewTitle = ref('')
const previewHtml = ref('')
const previewSource = ref('')
const previewBaseUrl = ref('')
const localizePreviewResources = ref(true)
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
  tagVisible.value = true
}

const handleTagsSaved = (tags: string[]) => {
  if (tagCurrentRow.value) {
    tagCurrentRow.value.tags = tags
    tagCurrentRow.value.favorited = true
  }
}

const stripHtml = (html: string) => {
  if (!html) return ''
  const div = document.createElement('div')
  div.innerHTML = html
  const text = div.textContent || div.innerText || ''
  return text.replace(/\s+/g, ' ').trim()
}

// ES 中存储的是 MinIO 相对路径（objectName），通过后端接口获取图片数据
const imageUrl = (objectName: string, width?: number) => {
  if (!objectName) return ''
  // 兼容旧数据：若已是完整 URL 则直接返回
  if (/^https?:\/\//i.test(objectName)) return objectName
  const sizeParam = width ? `&width=${width}` : ''
  return `/api/file/image?objectName=${encodeURIComponent(objectName)}${sizeParam}`
}

// 搜索结果列表缩略图：走新缩略图接口（后端缓存于 minio thumbnail 目录，比 /image 每次缩放更快更稳）
const thumbUrl = (objectName: string, width?: number) => {
  if (!objectName) return ''
  // 完整外部 URL 无法用本地缩略图，回退原图直链
  if (/^https?:\/\//i.test(objectName)) return objectName
  const sizeParam = width ? `&width=${width}` : ''
  return `/api/file/thumbnail?objectName=${encodeURIComponent(objectName)}${sizeParam}`
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

const syncSearchQuery = () => {
  router.replace({
    query: {
      ...(keyword.value ? { keyword: keyword.value } : {}),
      ...(filterSpider.value ? { spiderId: String(filterSpider.value) } : {}),
      ...(filterGroup.value ? { group: filterGroup.value } : {}),
      ...(filterTag.value ? { tag: filterTag.value } : {}),
      ...(favoriteOnly.value ? { favoriteOnly: 'true' } : {}),
      ...(hasImages.value ? { hasImages: 'true' } : {}),
      page: String(page.value),
      size: String(size.value)
    }
  })
}

// 点击搜索/回车：重置游标再查询
const doSearch = () => {
  page.value = 1
  pitId.value = ''
  searchAfter.value = ''
  hasMore.value = false
  historyOpen.value = false
  syncSearchQuery()
  loadData()
}

const resetSearch = () => {
  keyword.value = ''
  filterGroup.value = ''
  filterSpider.value = ''
  filterTag.value = ''
  favoriteOnly.value = false
  hasImages.value = false
  doSearch()
}

const selectHistory = (value: string) => {
  keyword.value = value
  doSearch()
}

const buildSearchParams = (pit = '', after = '') => {
  const params: any = { size: size.value, keyword: keyword.value }
  if (filterSpider.value) params.spiderId = filterSpider.value
  if (filterGroup.value) params.spiderGroup = filterGroup.value
  if (filterTag.value) params.tag = filterTag.value
  if (favoriteOnly.value) params.favoriteOnly = true
  if (hasImages.value) params.hasImages = true
  if (pit) params.pitId = pit
  if (after) params.searchAfter = after
  return params
}

const loadData = async () => {
  loading.value = true
  const start = performance.now()
  try {
    const res: any = await searchContent(buildSearchParams(pitId.value, searchAfter.value))
    const data = res.data
    const content: any[] = data?.content || []
    list.value = content
    total.value = data?.total ?? data?.totalElements ?? content.length
    pitId.value = data?.pitId || ''
    // 取最后一条的 sortValues 作为下一页游标
    const last = content[content.length - 1]
    searchAfter.value = last?.sortValues ? JSON.stringify(last.sortValues) : ''
    hasMore.value = page.value * size.value < total.value
    searchDurationMs.value = Math.round(performance.now() - start)
  } finally {
    loading.value = false
  }
}

const loadPageFromStart = async (targetPage: number) => {
  loading.value = true
  let pit = ''
  let after = ''
  let loadedPage = 0
  let pageContent: any[] = []

  try {
    for (let currentPage = 1; currentPage <= targetPage; currentPage++) {
      const res: any = await searchContent(buildSearchParams(pit, after))
      if (!res) break

      const data = res.data
      const content: any[] = data?.content || []
      total.value = data?.total ?? data?.totalElements ?? content.length
      pit = data?.pitId || pit
      if (currentPage > 1 && content.length === 0) break
      pageContent = content
      const last = pageContent[pageContent.length - 1]
      after = last?.sortValues ? JSON.stringify(last.sortValues) : ''
      loadedPage = currentPage
      if (currentPage < targetPage && pageContent.length < size.value) break
    }
  } finally {
    list.value = pageContent
    page.value = Math.max(1, loadedPage)
    pitId.value = pit
    searchAfter.value = after
    hasMore.value = page.value * size.value < total.value
    loading.value = false
    syncSearchQuery()
  }
}

// 下一页：基于游标追加加载
const loadNextPage = () => {
  if (loading.value || !hasMore.value) return
  page.value += 1
  syncSearchQuery()
  document.querySelector('.el-main')?.scrollTo({ top: 0, behavior: 'smooth' })
  loadData()
}

// 上一页：游标式分页无法直接回退，重新从第 1 页加载并截取到目标页
const loadPrevPage = () => {
  if (loading.value || page.value <= 1) return
  page.value -= 1
  const targetCount = page.value * size.value
  loading.value = true
  const collected: any[] = []
  let pit = ''
  let after = ''
  let guard = 0
  const finish = () => {
    const pageStart = (page.value - 1) * size.value
    list.value = collected.slice(pageStart, targetCount)
    pitId.value = pit
    searchAfter.value = after
    hasMore.value = page.value * size.value < total.value
    loading.value = false
    syncSearchQuery()
  }
  const step = async (): Promise<void> => {
    if (collected.length >= targetCount || guard >= 50) {
      finish()
      return
    }
    const res: any = await searchContent(buildSearchParams(pit, after))
    const data = res.data
    const content: any[] = data?.content || []
    collected.push(...content)
    pit = data?.pitId || ''
    const last = content[content.length - 1]
    after = last?.sortValues ? JSON.stringify(last.sortValues) : ''
    guard++
    if (content.length < size.value) {
      finish()
      return
    }
    step()
  }
  step()
}

// 修改每页条数：重置游标重新查询
const onPageSizeChange = () => {
  doSearch()
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

const showPreviewContent = async (row: any) => {
  previewContentVisible.value = true
  previewLoading.value = true
  previewTitle.value = row.title || '内容预览'
  previewHtml.value = ''
  previewSource.value = ''
  previewBaseUrl.value = ''
  localizePreviewResources.value = true
  showPreviewSource.value = false
  try {
    const res: any = await searchDetail(row.id)
    const rawHtml = res.data?.rawHtml || res.data?.content || '无原始内容'
    previewSource.value = rawHtml
    previewBaseUrl.value = res.data?.url || row.url || ''
    previewHtml.value = resolvePreviewHtml(rawHtml, previewBaseUrl.value, localizePreviewResources.value)
  } catch {
    previewHtml.value = '加载失败'
    previewSource.value = '加载失败'
  } finally {
    previewLoading.value = false
  }
}

const togglePreviewResourceLocalization = (enabled: string | number | boolean) => {
  if (!previewSource.value || previewSource.value === '加载失败') return
  previewHtml.value = resolvePreviewHtml(previewSource.value, previewBaseUrl.value, Boolean(enabled))
}

const showDetail = async (row: any) => {
  detailVisible.value = true
  detailLoading.value = true
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
    if (!await confirm(`确定重新爬取该记录？\n${row.url}`, { title: '重新爬取' })) return
    const res: any = await spiderRerun(row.spiderId, row.url)
    ElMessage.success(res.data?.status === 'PENDING' ? '重新爬取任务已加入等待队列' : '重新爬取任务已派发')
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.message || '重新爬取失败')
  }
}

const handleDelete = async (row: any) => {
  try {
    if (!await confirm('确定删除该条数据？', { title: '删除确认', danger: true })) return
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
    syncSearchQuery()
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
  doSearch()
}

const onSpiderChange = () => {
  doSearch()
}

watch(() => route.query.spiderId, (value) => {
  const spiderId = Number(value)
  const nextSpider = Number.isInteger(spiderId) && spiderId > 0 ? spiderId : ''
  if (filterSpider.value !== nextSpider) {
    filterSpider.value = nextSpider
    doSearch()
  }
})

onMounted(() => {
  keyword.value = String(route.query.keyword || '')
  filterGroup.value = String(route.query.group || '')
  filterTag.value = String(route.query.tag || '')
  favoriteOnly.value = route.query.favoriteOnly === 'true'
  hasImages.value = route.query.hasImages === 'true'
  const requestedPage = Number(route.query.page)
  page.value = Number.isInteger(requestedPage) && requestedPage > 0
    ? Math.min(requestedPage, 50)
    : 1
  const requestedSize = Number(route.query.size)
  if ([5, 10, 15, 20, 50, 100, 200, 500].includes(requestedSize)) {
    size.value = requestedSize
  }
  const spiderId = Number(route.query.spiderId)
  filterSpider.value = Number.isInteger(spiderId) && spiderId > 0 ? spiderId : ''
  loadGroupOptions()
  loadTagOptions()
  loadSpiderOptions()
  if (page.value > 1) {
    void loadPageFromStart(page.value)
  } else {
    loadData()
  }
})
</script>

<style scoped>
.search-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
}

.public-search-link {
  flex: 0 0 auto;
  margin-top: 8px;
  border-radius: 9px;
  box-shadow: 0 3px 9px rgba(15, 159, 154, 0.1);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.public-search-link:hover {
  transform: translateY(-1px);
  box-shadow: 0 5px 12px rgba(15, 159, 154, 0.18);
}

.search-bar {
  margin-bottom: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-row {
  width: 100%;
  justify-content: center;
  gap: 12px;
}

.reset-search-button {
  flex-shrink: 0;
  width: 88px;
  height: 36px;
  min-height: 36px;
  margin-left: 8px;
  padding: 0;
  border-color: #d9e7e8;
  border-radius: 999px;
  background: linear-gradient(135deg, #fff 0%, #f6fbfa 100%);
  color: #52706f;
  font-weight: 600;
  box-shadow: 0 2px 7px rgba(32, 86, 84, 0.08);
  transition: border-color 0.18s ease, color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
  justify-content: center;
}

.reset-search-button:hover {
  border-color: #9bd4cd;
  background: #eef9f7;
  color: #0f817c;
  box-shadow: 0 4px 10px rgba(15, 129, 124, 0.12);
  transform: translateY(-1px);
}

@media (max-width: 767px) {
  .search-toolbar { flex-direction: column; gap: 12px; }
  .search-bar { width: 100%; }
  .public-search-link { align-self: flex-start; margin-top: 0; }
  .search-row { flex-wrap: wrap; }
  .search-row .el-select { width: 100% !important; }
  :deep(.content-preview-dialog) { top: 4px !important; margin: 0 auto !important; }
}

/* 窄屏：搜索/重置按钮偏大，缩小；搜索框压缩内边距/图标间距，防止有输入时按钮溢出 */
@media (max-width: 560px) {
  .search-box {
    padding: 0 4px 0 10px;
  }
  .search-icon {
    font-size: 18px;
    margin-right: 8px;
  }
  .search-input {
    font-size: 14px;
  }
  .clear-btn {
    font-size: 20px;
    padding: 0 3px;
  }
  /* 两个按钮统一固定尺寸 72×32px，确保视觉大小一致 */
  .search-btn,
  .reset-search-button {
    width: 72px;
    height: 32px !important;
    min-height: 32px !important;
    padding: 0 !important;
    font-size: 13px;
    line-height: 1;
    box-sizing: border-box;
  }
  .search-btn {
    flex-shrink: 0;
  }
  .reset-search-button {
    flex-shrink: 0;
    margin-left: 4px;
    --el-button-size: 32px;
    --el-button-padding-horizontal: 0;
    --el-button-padding-vertical: 0;
  }
}

.search-box {
  flex: 1;
  display: flex;
  align-items: center;
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  overflow: hidden;
  border: 1px solid #dfe7ee;
  border-radius: 999px;
  padding: 0 6px 0 16px;
  height: 48px;
  background: #fff;
  transition: box-shadow 0.2s, border-color 0.2s;
  box-shadow: 0 1px 2px rgba(23, 43, 77, 0.06);
}

.search-box:focus-within {
  box-shadow: 0 1px 6px rgba(23, 43, 77, 0.14);
  border-color: var(--teal);
}

.history-search-open {
  border-radius: 24px 24px 0 0;
  box-shadow: 0 1px 6px rgba(23, 43, 77, 0.18);
  border-color: #c8d6dd;
}

.search-icon {
  color: var(--ink-500);
  font-size: 20px;
  margin-right: 12px;
  flex-shrink: 0;
}

.search-input {
  flex: 1 1 auto;
  min-width: 0;
  border: none;
  outline: none;
  font-size: 15px;
  height: 100%;
  color: var(--ink-950);
}

.search-input::placeholder {
  color: var(--ink-500);
}

.clear-btn {
  border: none;
  background: none;
  color: var(--ink-500);
  font-size: 22px;
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
}

.clear-btn:hover {
  color: var(--ink-950);
}

.search-btn {
  flex-shrink: 0;
  width: 88px;
  border: none;
  border-radius: 999px;
  background: var(--teal);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  padding: 0;
  height: 36px;
  min-height: 36px;
  box-sizing: border-box;
  box-shadow: 0 3px 9px rgba(15, 159, 154, 0.16);
  cursor: pointer;
  transition: background 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.search-btn:hover {
  background: var(--teal-dark);
  box-shadow: 0 5px 12px rgba(15, 159, 154, 0.22);
  transform: translateY(-1px);
}

.result-count {
  color: var(--ink-700);
  font-size: 13px;
  margin-bottom: 16px;
}

.search-duration {
  margin-left: 8px;
  padding: 1px 8px;
  border-radius: 10px;
  background: #f1f5f5;
  color: var(--ink-500);
  font-size: 11px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.result-list {
  min-height: 100px;
}

.result-item {
  padding: 16px 0;
  border-bottom: 1px solid #edf0f4;
}

.result-item:last-child {
  border-bottom: none;
}

.result-url {
  display: block;
  width: fit-content;
  max-width: 100%;
  color: var(--teal-dark);
  font-size: 13px;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-decoration: none;
}

.result-url:hover {
  color: var(--teal);
  text-decoration: underline;
}

.result-title {
  display: block;
  width: fit-content;
  max-width: 100%;
  color: var(--ink-950);
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 6px 0;
  line-height: 1.4;
  cursor: pointer;
  transition: color 0.2s ease, text-shadow 0.2s ease, transform 0.2s ease;
}

.result-title:hover {
  color: var(--teal-dark);
  text-shadow: 0 1px 0 rgba(15, 159, 154, 0.08);
}

.result-title:active {
  color: var(--teal);
  transform: translateY(1px);
}

.result-title :deep(em) {
  font-style: normal;
  color: var(--teal);
  font-weight: 800;
}

.result-content-line {
  display: inline;
  line-height: 1.6;
  margin: 0 0 8px;
}

.result-content {
  color: var(--ink-700);
  font-size: 14px;
  line-height: 1.6;
  margin: 0;
  display: inline;
}

.result-detail-link {
  display: inline;
  color: var(--teal);
  font-size: 13px;
  cursor: pointer;
  margin-left: 4px;
  white-space: nowrap;
  vertical-align: baseline;
}

.result-detail-link:hover {
  color: var(--teal-dark);
  text-decoration: underline;
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
  border-radius: 6px;
  border: 1px solid #e3e8ee;
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
  color: var(--ink-700);
}

.meta-tag {
  background: #f1f5f5;
  padding: 2px 8px;
  border-radius: 4px;
  color: var(--ink-700);
}

.spider-tag {
  cursor: pointer;
  transition: all 0.2s ease;
}

.spider-tag:hover {
  background: #e2f6f3;
  color: var(--teal-dark);
}

.group-tag {
  background: #d9f5ef;
  color: var(--teal-dark);
}

.update-time {
  color: var(--ink-500);
  font-size: 12px;
}

.empty {
  text-align: center;
  color: var(--ink-500);
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
  border-radius: 6px;
  border: 1px solid #e3e8ee;
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

/* 预览弹窗样式已抽到 ContentPreviewDialog 组件 */
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
  color: #8993a4;
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
  border: 1px solid #e3e8ee;
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
