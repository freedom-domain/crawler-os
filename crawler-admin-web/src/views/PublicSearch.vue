<template>
  <div ref="publicSearchRef" class="public-search">
    <header class="ps-header">
      <div class="ps-header-inner">
        <div class="brand-mark" aria-label="CrawlerOS">
          <span class="brand-symbol"><el-icon><Search /></el-icon></span>
          <span class="brand-name">Crawler<span>OS</span></span>
        </div>

        <div class="header-search-wrap">
          <SearchHistoryDropdown
            class="history-search-wrapper"
            :open="historyOpen"
            :keyword="keyword"
            :authenticated="isLoggedIn"
            @select="selectHistory"
            @close="historyOpen = false"
          >
            <div class="search-box header-search-box" :class="{ 'history-search-open': historyOpen && !keyword }">
              <el-icon class="search-icon"><Search /></el-icon>
              <input
                v-model="keyword"
                class="search-input"
                placeholder="输入关键词"
                @focus="historyOpen = !keyword"
                @input="historyOpen = !keyword"
                @keyup.enter="doSearch"
              />
              <button v-if="keyword" class="clear-btn" @click="keyword = ''; doSearch()">&times;</button>
              <button class="more-conditions-btn" type="button" @click="showFilters = !showFilters" :aria-expanded="showFilters">
                <svg class="toggle-chevron" viewBox="0 0 24 24" aria-hidden="true" :class="{ 'is-open': showFilters }">
                  <path d="M6 9.5 12 15.5 18 9.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              </button>
              <button class="search-btn" type="button" @click="doSearch">搜索</button>
              <button class="reset-search-btn" type="button" @click="resetSearch">
                <el-icon><RefreshLeft /></el-icon>
                <span>重置</span>
              </button>
            </div>
          </SearchHistoryDropdown>
        </div>

        <div class="header-user">
          <template v-if="isLoggedIn">
            <span class="user-account">
              <el-icon><UserFilled /></el-icon>
              <span class="user-nickname">{{ userStore.nickname || userStore.username }}</span>
            </span>
            <el-button class="header-admin-button" type="primary" plain size="small" :icon="Setting" @click="openAdminSearch">后台管理</el-button>
            <el-button class="header-logout-button" plain size="small" :icon="SwitchButton" @click="handleLogout">登出</el-button>
          </template>
          <el-button v-else class="header-login-button" type="primary" size="small" :icon="UserFilled" @click="loginVisible = true">登录</el-button>
        </div>
      </div>

      <div v-if="showFilters" class="header-filter-panel">
        <div class="header-filter-row">
          <el-select v-model="filterGroup" placeholder="分组" clearable style="width: 160px" @change="onGroupChange">
            <el-option v-for="g in groupOptions" :key="g" :label="g" :value="g" />
          </el-select>
          <el-select v-model="filterSpider" placeholder="站点" clearable filterable style="width: 200px" @change="onSpiderChange">
            <el-option-group v-for="sec in spiderGroupedOptions" :key="sec.key" :label="sec.label">
              <el-option v-for="s in sec.items" :key="s.id" :label="s.name" :value="s.id" />
            </el-option-group>
          </el-select>
          <el-select v-if="isLoggedIn" v-model="filterTag" placeholder="标签" clearable style="width: 160px" @change="doSearch">
            <el-option v-for="t in tagOptions" :key="t.id" :label="t.label" :value="t.label" />
          </el-select>
          <el-checkbox v-if="isLoggedIn" v-model="favoriteOnly" @change="doSearch">只看我的收藏</el-checkbox>
          <el-checkbox v-model="hasImages" @change="doSearch">只看有图片</el-checkbox>
        </div>
      </div>
    </header>

    <main class="ps-main">
      <SearchResultsFrame
        v-model:current-page="page"
        v-model:page-size="size"
        :loading="loading"
        :total="total"
        :page-sizes="[10, 15, 20, 50, 100, 200, 500]"
        cursor-mode
        :has-more="hasMore"
        @prev="loadPrevPage"
        @next="loadNextPage"
        @change="onPageSizeChange"
      >
        <template #heading>
          <div class="results-heading">
            <div>
              <span class="section-kicker">SEARCH RESULTS</span>
              <span v-if="total > 0" class="result-count">找到约 {{ total }} 条结果</span>
            </div>
            <span class="result-page">第 {{ page }} 页</span>
          </div>
        </template>

        <SearchResultList
          :rows="list"
          :authenticated="isLoggedIn"
          :image-url="imageUrl"
          :format-time="formatTime"
          @preview="showPreviewContent"
          @detail="showDetail"
          @images="openAllImages"
          @tag="openTagEditor"
          @favorite="toggleFavorite"
          @command="handleCommand"
        />
      </SearchResultsFrame>
    </main>

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
            :style="{ height: `${Math.round(88 * imgZoom)}vh` }"
            preview-teleported
            hide-on-click-modal
            @load="onPreviewImgLoad($event, idx)"
          />
        </div>
        <div v-else class="preview-empty">暂无图片</div>
      </div>
    </el-dialog>

    <el-dialog v-model="previewContentVisible" width="80%" top="5vh" class="content-preview-dialog" destroy-on-close>
      <template #header>
        <div class="preview-content-header">
          <span>{{ previewTitle }} - 内容</span>
          <el-switch v-model="showPreviewSource" inactive-text="内容预览" active-text="HTML 源码" />
        </div>
      </template>
      <div v-loading="previewLoading">
        <iframe
          v-if="!showPreviewSource"
          class="preview-container preview-html"
          :srcdoc="previewHtml || '<p>无正文内容</p>'"
          sandbox="allow-scripts allow-forms allow-popups allow-popups-to-escape-sandbox"
          title="内容预览"
        ></iframe>
        <pre v-else class="preview-container detail-text code-text">{{ previewSource || '无原始内容' }}</pre>
      </div>
    </el-dialog>

    <SearchDetailDialog
      v-model="detailVisible"
      :loading="detailLoading"
      :detail="detailData"
      :image-url="imageUrl"
      :format-time="formatTime"
    />

    <TagEditorDialog v-model="tagVisible" :row="tagCurrentRow" :tag-options="tagOptions" @saved="handleTagsSaved" />

    <el-dialog v-model="loginVisible" title="登录" width="400px" class="login-dialog" :close-on-click-modal="false" destroy-on-close>
      <el-form :model="loginForm" :rules="loginRules" ref="loginFormRef" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="loginForm.username" prefix-icon="User" placeholder="用户名" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="loginForm.password" prefix-icon="Lock" type="password" placeholder="密码" show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width:100%" :loading="loginLoading" @click="handleLogin">登录</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox, FormInstance } from 'element-plus'
import { useUserStore } from '@/stores/user'
import SearchResultList from '@/components/SearchResultList.vue'
import SearchResultsFrame from '@/components/SearchResultsFrame.vue'
import SearchHistoryDropdown from '@/components/SearchHistoryDropdown.vue'
import SearchDetailDialog from '@/components/SearchDetailDialog.vue'
import TagEditorDialog from '@/components/TagEditorDialog.vue'
import { searchContent, searchDetail, dictChildren, spiderPage, favoriteAdd, favoriteDelete, searchDelete, spiderRerun } from '@/api'
import { Search, FullScreen, Minus, ZoomIn, ZoomOut, UserFilled, Setting, SwitchButton, RefreshLeft } from '@element-plus/icons-vue'
import router from '@/router'
import { useRoute } from 'vue-router'

const list = ref<any[]>([])
const publicSearchRef = ref<HTMLElement | null>(null)
const route = useRoute()

const openAdminSearch = () => {
  const query: Record<string, string> = {
    page: String(page.value),
    size: String(size.value)
  }
  if (keyword.value) query.keyword = keyword.value
  if (filterSpider.value) query.spiderId = String(filterSpider.value)
  if (filterGroup.value) query.group = filterGroup.value
  if (filterTag.value) query.tag = filterTag.value
  if (favoriteOnly.value && isLoggedIn.value) query.favoriteOnly = 'true'
  if (hasImages.value) query.hasImages = 'true'

  const search = new URLSearchParams(query).toString()
  window.open(`/search?${search}`, 'crawler-admin-search')
}
const userStore = useUserStore()
const isLoggedIn = computed(() => Boolean(userStore.token))

const loginVisible = ref(false)
const loginLoading = ref(false)
const loginFormRef = ref<FormInstance>()
const loginForm = reactive({ username: '', password: '' })
const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  await loginFormRef.value?.validate()
  loginLoading.value = true
  try {
    await userStore.login(loginForm.username, loginForm.password)
    ElMessage.success('登录成功')
    loginVisible.value = false
    loginForm.username = ''
    loginForm.password = ''
  } finally {
    loginLoading.value = false
  }
}

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要登出吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    userStore.logout()
    ElMessage.success('已登出')
  } catch {
    // 用户取消
  }
}
const loading = ref(false)
const page = ref(1)
const size = ref(15)
const total = ref(0)
// PIT + search_after 游标式分页状态
const pitId = ref('')
const searchAfter = ref('')
const hasMore = ref(false)
const keyword = ref('')
const showFilters = ref(false)
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

const toggleFullscreen = async () => {
  if (!isFullscreen.value) {
    try {
      await document.documentElement.requestFullscreen()
    } catch {
      // 浏览器拒绝全屏时仍切换 UI 状态
    }
    isFullscreen.value = true
  } else {
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

const getPreviewImageWidth = (naturalWidth?: number) => {
  // 以图片原始宽度为基准，未加载完成时回退到 240px
  return naturalWidth && Number.isFinite(naturalWidth) ? naturalWidth : 240
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

watch([imgZoom, isFullscreen], () => {
  syncPreviewImageWidths()
}, { flush: 'post' })

const tagOptions = ref<any[]>([])

const loadTagOptions = async () => {
  try {
    const res: any = await dictChildren('tag')
    if (res) {
      tagOptions.value = res.data || []
    }
  } catch {
    tagOptions.value = []
  }
}

const formatTime = (t: string) => {
  if (!t) return ''
  const d = new Date(t)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const imageUrl = (objectName: string) => {
  if (!objectName) return ''
  if (/^https?:\/\//i.test(objectName)) return objectName
  return `/api/file/image?objectName=${encodeURIComponent(objectName)}`
}

// 不再传递图片列表，仅传递内容 id（含爬虫信息与 url），由预览页自行从后端获取图片
const openAllImages = (row: any) => {
  if (!row.id) {
    ElMessage.info('该条内容暂无图片')
    return
  }

  const query: Record<string, string> = {
    id: String(row.id),
    title: String(row.title || '图片预览')
  }
  const win = window.open(`/image-preview?${new URLSearchParams(query).toString()}`, '_blank')
  if (!win) {
    ElMessage.warning('浏览器阻止了新窗口，请允许弹出窗口后重试')
  }
}

const scrollToTop = () => {
  publicSearchRef.value?.scrollTo({ top: 0, behavior: 'smooth' })
}

const syncSearchQuery = () => {
  router.replace({
    query: {
      ...(keyword.value ? { keyword: keyword.value } : {}),
      ...(filterSpider.value ? { spiderId: String(filterSpider.value) } : {}),
      ...(filterGroup.value ? { group: filterGroup.value } : {}),
      ...(filterTag.value ? { tag: filterTag.value } : {}),
      ...(favoriteOnly.value && isLoggedIn.value ? { favoriteOnly: 'true' } : {}),
      ...(hasImages.value ? { hasImages: 'true' } : {}),
      page: String(page.value),
      size: String(size.value)
    }
  })
}

const doSearch = () => {
  page.value = 1
  pitId.value = ''
  searchAfter.value = ''
  hasMore.value = false
  historyOpen.value = false
  if (!isLoggedIn.value && keyword.value.trim()) {
    const raw = JSON.parse(localStorage.getItem('crawler-search-history') || '[]')
    const values: { keyword: string; time: number }[] = Array.isArray(raw)
      ? raw
          .map((value: unknown): { keyword: string; time: number } | null => {
            if (typeof value === 'string' && value.trim()) {
              return { keyword: value.trim(), time: Date.now() }
            }
            if (value && typeof value === 'object' && typeof (value as any).keyword === 'string' && (value as any).keyword.trim()) {
              return { keyword: (value as any).keyword.trim(), time: typeof (value as any).time === 'number' ? (value as any).time : Date.now() }
            }
            return null
          })
          .filter((item): item is { keyword: string; time: number } => item !== null)
      : []
    const newEntry = { keyword: keyword.value.trim(), time: Date.now() }
    const merged = [newEntry, ...values.filter(item => item.keyword !== newEntry.keyword)].slice(0, 20)
    localStorage.setItem('crawler-search-history', JSON.stringify(merged))
  }
  syncSearchQuery()
  scrollToTop()
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
  if (favoriteOnly.value && isLoggedIn.value) params.favoriteOnly = true
  if (hasImages.value) params.hasImages = true
  if (pit) params.pitId = pit
  if (after) params.searchAfter = after
  return params
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await searchContent(buildSearchParams(pitId.value, searchAfter.value))
    if (res) {
      const data = res.data
      const content: any[] = data?.content || []
      list.value = content
      total.value = data?.total ?? data?.totalElements ?? content.length
      pitId.value = data?.pitId || ''
      const last = content[content.length - 1]
      searchAfter.value = last?.sortValues ? JSON.stringify(last.sortValues) : ''
      hasMore.value = page.value * size.value < total.value
    }
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
      pageContent = data?.content || []
      total.value = data?.total ?? data?.totalElements ?? pageContent.length
      pit = data?.pitId || pit
      if (currentPage > 1 && pageContent.length === 0) break
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
  scrollToTop()
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
    scrollToTop()
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

const resolveHtmlLinks = (html: string, baseUrl: string): string => {
  if (!html || !baseUrl) return html
  const div = document.createElement('div')
  div.innerHTML = html
  const toAbsolute = (url: string): string => {
    if (!url) return url
    const trimmed = url.trim()
    if (/^(https?:)?\/\//i.test(trimmed)) return trimmed
    if (/^(javascript:|mailto:|tel:|data:|blob:|#)/i.test(trimmed)) return trimmed
    if (/^\/api\/file\/resource(?:[/?#]|$)/i.test(trimmed)) return trimmed
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

const tagVisible = ref(false)
const tagCurrentRow = ref<any>(null)

const openTagEditor = (row: any) => {
  tagCurrentRow.value = row
  tagVisible.value = true
}

const handleTagsSaved = (tags: string[]) => {
  if (tagCurrentRow.value) tagCurrentRow.value.tags = tags
}

const handleRerun = async (row: any) => {
  if (!row.spiderId || !row.url) return
  try {
    await ElMessageBox.confirm(`确定重新爬取该记录？\n${row.url}`, '重新爬取', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    const res: any = await spiderRerun(row.spiderId, row.url)
    ElMessage.success(res.data?.status === 'PENDING' ? '重新爬取任务已加入等待队列' : '重新爬取任务已派发')
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

const handleCommand = (command: string, row: any) => {
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

const loadGroupOptions = async () => {
  try {
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

const onGroupChange = () => {
  doSearch()
}

const onSpiderChange = () => {
  doSearch()
}

onMounted(() => {
  document.title = 'CrawlerOS 数据搜索'
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
  if ([10, 15, 20, 50, 100, 200, 500].includes(requestedSize)) {
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
.public-search {
  height: 100%;
  min-height: 0;
  background: #fff;
  display: flex;
  flex-direction: column;
  color: #202124;
  overflow: auto;
}

.ps-header {
  position: sticky;
  top: 0;
  z-index: 20;
  min-height: 88px;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid #f1f3f4;
}

.ps-header-inner {
  width: 100%;
  max-width: 1200px;
  height: 100%;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.brand-mark {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-symbol {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: #4285f4;
  background: #f1f3f4;
  font-size: 17px;
}

.brand-name {
  color: #4285f4;
  font-size: 20px;
  letter-spacing: .02em;
  font-weight: 800;
}

.brand-name span { color: #ea4335; }

.header-search-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  justify-content: flex-start;
  padding: 10px 0 10px 12px;
  margin-left: -8px;
}

.header-user {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding: 7px 8px;
  border: 1px solid #e8edf3;
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.92);
}

.user-account {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  padding: 0 5px 0 2px;
  color: #5b6b7e;
}

.user-account > .el-icon {
  flex-shrink: 0;
  color: #4285f4;
  font-size: 17px;
}

.user-nickname {
  font-size: 14px;
  color: #34465a;
  font-weight: 600;
  max-width: 130px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-user :deep(.el-button) {
  height: 30px;
  margin: 0;
  padding: 0 10px;
  border-radius: 8px;
  font-weight: 600;
}

.header-admin-button {
  --el-button-hover-text-color: #fff;
  --el-button-hover-bg-color: #4285f4;
}

.header-logout-button {
  color: #66758a;
  border-color: transparent;
  background: transparent;
}

.header-logout-button:hover {
  color: #d14b4b;
  border-color: #f4dada;
  background: #fff7f7;
}

.header-login-button {
  border-radius: 9px;
  padding: 0 15px;
}

.header-search-box {
  width: 100%;
  max-width: 700px;
  margin-right: 0;
  margin-left: 0;
}

.history-search-wrapper {
  width: 100%;
  max-width: 700px;
}

.more-conditions-btn {
  border: none;
  background: transparent;
  color: #5f6368;
  cursor: pointer;
  padding: 0;
  margin-left: 6px;
  margin-right: 12px;
  height: 20px;
  width: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: color 0.2s ease;
}

.more-conditions-btn:hover {
  color: #1a73e8;
}

.toggle-chevron {
  width: 16px;
  height: 16px;
  display: block;
  transition: transform 0.2s ease;
}

.toggle-chevron.is-open {
  transform: rotate(180deg);
}

.more-conditions-btn:hover {
  color: #1a73e8;
}

.header-filter-panel {
  border-top: 1px solid #f1f3f4;
  background: rgba(255, 255, 255, 0.96);
  padding: 12px 24px 16px;
}

.header-filter-row {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: center;
  transform: translateX(-50px);
}

.header-filter-row .el-select {
  width: auto;
}

.ps-main {
  flex: 1;
  width: 100%;
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px 24px 56px;
}

.search-hero {
  display: block;
  padding: 8px 0 20px;
}

.eyebrow,
.section-kicker {
  color: #4285f4;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: .14em;
}

.search-bar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 760px;
  margin: 0 auto;
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
  padding: 0 6px 0 18px;
  height: 56px;
  min-height: 56px;
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
  height: 44px;
  min-height: 44px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.search-btn:hover {
  background: #1769d1;
}

.reset-search-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  flex-shrink: 0;
  width: 72px;
  height: 44px;
  margin-left: 8px;
  padding: 0;
  border: 1px solid #d9e7e8;
  border-radius: 999px;
  background: linear-gradient(135deg, #fff 0%, #f4faf9 100%);
  color: #52706f;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 2px 7px rgba(32, 86, 84, 0.08);
  transition: border-color 0.18s ease, color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.reset-search-btn:hover {
  border-color: #9bd4cd;
  background: #eef9f7;
  color: #0f817c;
  box-shadow: 0 4px 10px rgba(15, 129, 124, 0.12);
  transform: translateY(-1px);
}

.reset-search-btn:focus-visible {
  outline: 2px solid rgba(15, 129, 124, 0.35);
  outline-offset: 2px;
}

.results-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 42px;
  margin: 3px 0 8px;
  padding: 0 2px;
}

.result-count {
  margin-left: 14px;
  color: #5f6368;
  font-size: 13px;
}

.result-page {
  color: #80868b;
  font-size: 12px;
}

.result-list {
  min-height: 100px;
  background: #fff;
  border-top: 1px solid #f1f3f4;
  border-bottom: 1px solid #f1f3f4;
  padding: 0 28px;
}

.result-item {
  padding: 20px 0;
  border-bottom: 1px solid #f1f3f4;
}

.result-item:last-child {
  border-bottom: none;
}

.result-url {
  display: block;
  color: #188038;
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
  display: block;
  color: #1a0dab;
  font-size: 19px;
  font-weight: 400;
  margin: 0 0 6px 0;
  line-height: 1.4;
  text-decoration: none;
}

.result-title:hover {
  text-decoration: underline;
}

.result-title :deep(em) {
  font-style: normal;
  color: #1a0dab;
  font-weight: 700;
}

.result-content-line {
  display: inline;
  margin: 0 0 8px;
  max-width: 100%;
  line-height: 1.6;
}

.result-content {
  color: #4d5156;
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
  white-space: nowrap;
  line-height: 1.6;
  vertical-align: baseline;
  margin-left: 4px;
}

.result-detail-link:hover {
  text-decoration: underline;
}

.result-detail-link:hover {
  text-decoration: underline;
}

.result-content :deep(em) {
  font-style: normal;
  color: #202124;
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
  border: 1px solid #dadce0;
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
  cursor: default;
}
.tag-item-editable { cursor: pointer; }

.result-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #70757a;
}

.meta-tag {
  background: #f1f3f4;
  padding: 2px 8px;
  border-radius: 3px;
  color: #5f6368;
}

.spider-tag {
  cursor: default;
}

.group-tag {
  background: #e8f0fe;
  color: #1967d2;
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
  width: auto;
  max-width: none;
  border-radius: 4px;
  border: 1px solid #eee;
  background: #fafafa;
  cursor: pointer;
  transition: height 0.2s ease;
}

:deep(.preview-img .el-image__inner) {
  display: block;
  width: auto;
  height: 100%;
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

.detail-title-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 0;
}

.detail-header-meta {
  display: flex;
  flex: 1 1 58%;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 4px 8px;
  min-width: 0;
  color: #627d98;
  font-size: 12px;
  letter-spacing: 0.01em;
}

.detail-url-row {
  display: flex;
  flex: 1 1 320px;
  align-items: baseline;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.detail-dialog-title {
  flex: 1 1 42%;
  min-width: 0;
  color: #102a43;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.detail-url {
  color: #087f7d;
  font-size: 13px;
  word-break: break-all;
}

.detail-time {
  color: #909399;
  font-size: 12px;
  white-space: nowrap;
}

.detail-badges {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.detail-meta-item {
  color: #486581;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.detail-images {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
  margin-bottom: 12px;
}

.detail-image {
  width: clamp(160px, 22vw, 240px);
  height: clamp(110px, 16vw, 160px);
  border-radius: 8px;
  border: 1px solid #eee;
  overflow: hidden;
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
  border: 0;
  border-radius: 0;
  padding: 0;
  background: transparent;
  max-height: 42vh;
  overflow-y: auto;
}

.preview-html {
  display: block;
  width: 100%;
  height: 70vh;
  padding: 0;
  line-height: 1.8;
  color: #303133;
  word-break: break-word;
  border: 0;
  border-radius: 0;
  padding: 0;
  background: transparent;
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

@media (max-width: 860px) {
  .ps-header {
    min-height: auto;
    height: auto;
  }
  .ps-header-inner {
    flex-direction: column;
    align-items: stretch;
    height: auto;
    flex: none;
    padding: 14px 16px 10px;
    gap: 12px;
  }
  .brand-mark {
    justify-content: center;
  }
  .header-search-wrap {
    width: 100%;
    padding: 0;
    margin-left: 0;
    flex-wrap: wrap;
  }
  .header-search-box {
    max-width: none;
    flex: 1 1 100%;
  }
  .search-btn {
    height: 40px;
    min-height: 40px;
    padding: 0 18px;
  }
  .header-user {
    width: 100%;
    justify-content: flex-end;
    padding: 7px 8px;
  }
  .user-nickname {
    max-width: min(34vw, 180px);
  }
  .header-filter-panel {
    height: auto;
    flex: none;
    padding: 12px 16px 14px;
  }
  .header-filter-row {
    display: grid;
    grid-template-columns: 1fr;
    gap: 10px;
    transform: none;
  }
  .header-filter-row .el-select {
    width: 100% !important;
  }
  .ps-main {
    padding: 20px 16px 40px;
  }
  .result-list {
    padding: 0 12px;
  }
}

@media (max-width: 720px) {
  .public-search { height: 100%; min-height: 0; }
  .ps-header { height: auto; min-height: 0; }
  .ps-header-inner { height: auto; padding: 12px 18px 10px; }
  .header-caption { font-size: 11px; }
  .ps-main { padding: 26px 16px 40px; }
  .search-hero { display: block; padding: 12px 0 24px; }
  .hero-copy { padding-bottom: 24px; }
  .hero-copy h1 { font-size: 32px; }
  .search-row { align-items: stretch; }
  .search-box {
    height: 48px;
    min-height: 48px;
    padding: 0 5px 0 14px;
  }
  .search-btn {
    height: 38px;
    min-height: 38px;
    padding: 0 16px;
  }
  .search-icon {
    font-size: 18px;
    margin-right: 10px;
  }
  .search-input {
    font-size: 16px;
  }
  .clear-btn {
    font-size: 20px;
    padding: 0 6px;
  }
  .more-conditions-btn {
    width: 24px;
    height: 24px;
    margin-right: 8px;
  }
  .header-filter-panel {
    padding: 10px 18px 12px;
  }
  .header-filter-row {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
    transform: none;
  }
  .header-filter-row .el-select,
  .header-filter-row :deep(.el-select),
  .header-filter-row :deep(.el-checkbox) {
    width: 100% !important;
    margin-right: 0;
  }
  .header-filter-row :deep(.el-select__wrapper) {
    width: 100%;
  }
  .filter-row { flex-wrap: wrap; }
  .filter-row :deep(.el-select) { width: calc(50% - 6px) !important; }
  .results-heading { align-items: flex-end; }
  .result-count { display: block; margin: 7px 0 0; }
  .result-title { font-size: 17px; }
  .result-meta { flex-wrap: wrap; gap: 8px; }
  .detail-title-row { flex-direction: column; }
  .detail-dialog-title { flex-basis: auto; width: 100%; }
  .detail-header-meta { justify-content: flex-start; }
  .detail-images { gap: 8px; }
  .detail-image { width: calc(50% - 4px); height: 120px; }
  :deep(.content-preview-dialog) { top: 4px !important; margin: 0 auto !important; }
}

@media (max-width: 460px) {
  .search-row { gap: 8px; }
  .search-box { min-width: 0; }
  .search-btn { padding: 0 14px; }
  .reset-search-btn { width: 56px; height: 38px; margin-left: 6px; font-size: 12px; }
  .header-user {
    gap: 8px;
    justify-content: center;
    flex-wrap: wrap;
  }
  .user-nickname {
    max-width: 34vw;
    font-size: 13px;
  }
  .filter-row :deep(.el-select) { width: 100% !important; }
  .result-page { display: none; }
  .result-url,
  .result-title,
  .result-content,
  .detail-url,
  .detail-dialog-title {
    word-break: break-word;
    overflow-wrap: anywhere;
  }
  .result-item {
    padding: 16px 0;
  }
  .result-thumb {
    width: 64px;
    height: 64px;
  }
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

:deep(.login-dialog) {
  width: calc(100vw - 32px) !important;
  max-width: 400px;
}

@media (max-width: 460px) {
  :deep(.login-dialog) {
    width: calc(100vw - 24px) !important;
  }
}
</style>
