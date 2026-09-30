<template>
  <el-card>
    <el-form :inline="true" @submit.prevent>
      <el-form-item>
        <el-input v-model="title" clearable placeholder="标题" style="width: 200px" @keyup.enter="refresh" @clear="refresh" />
      </el-form-item>
      <el-form-item>
        <el-input v-model="url" clearable placeholder="来源 URL" style="width: 200px" @keyup.enter="refresh" @clear="refresh" />
      </el-form-item>
      <el-form-item>
        <el-select v-model="spiderGroup" clearable placeholder="分组" style="width: 140px" @change="onGroupChange">
          <el-option v-for="g in groupOptions" :key="g" :label="g" :value="g" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-select v-model="spiderName" clearable filterable placeholder="爬虫" style="width: 160px" @change="refresh">
          <el-option v-for="spider in filteredSpiderOptions" :key="spider.id" :label="spider.name" :value="spider.name" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-select v-model="tag" clearable filterable placeholder="标签" style="width: 160px" @change="refresh" @clear="clearTagFilter">
          <el-option v-for="option in tagOptions" :key="option.id" :label="option.label" :value="option.label" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="refresh">刷新</el-button>
        <el-button :disabled="loading" @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="table-scroll-wrapper">
    <el-table :data="list" v-loading="loading" stripe resizable border>
      <el-table-column prop="title" label="标题" min-width="420" show-overflow-tooltip resizable>
        <template #default="{ row }">{{ row.title || '无标题' }}</template>
      </el-table-column>
      <el-table-column label="来源 URL" min-width="300" show-overflow-tooltip resizable>
        <template #default="{ row }">
          <a v-if="row.url" class="source-url" :href="row.url" target="_blank" rel="noopener noreferrer">{{ row.url }}</a>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="spiderGroup" label="分组" min-width="100" resizable>
        <template #default="{ row }">
          <el-tag v-if="row.spiderGroup" size="small">{{ row.spiderGroup }}</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="spiderName" label="爬虫" min-width="160" show-overflow-tooltip resizable>
        <template #default="{ row }">{{ row.spiderName || '-' }}</template>
      </el-table-column>
      <el-table-column label="标签" min-width="180" resizable>
        <template #default="{ row }">
          <el-tag v-for="tag in row.tags || []" :key="tag" size="small" class="tag" @click="openTagEditor(row)">{{ tag }}</el-tag>
          <span v-if="!row.tags || !row.tags.length" class="tag-empty" @click="openTagEditor(row)">添加标签</span>
        </template>
      </el-table-column>
      <el-table-column prop="crawlTime" label="抓取时间" min-width="180" resizable>
        <template #default="{ row }">{{ formatDateTime(row.crawlTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right" align="center" resizable>
        <template #default="{ row }">
          <div class="row-action-cell">
            <el-button size="small" type="danger" plain @click="remove(row)">取消收藏</el-button>
            <TableRowActions :items="getRowActions(row)" @command="command => handleRowAction(command, row)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    </div>

    <TagEditorDialog v-model="tagVisible" :row="tagCurrentRow" :tag-options="tagOptions" @saved="handleTagsSaved" />

    <ContentPreviewDialog
      v-model:visible="previewContentVisible"
      :title="previewContentTitle"
      :html="previewContentHtml"
      :source="previewContentSource"
      :loading="previewContentLoading"
      :source-url="previewContentBaseUrl"
      v-model:localize="previewContentLocalize"
      v-model:show-source="previewContentShowSource"
      @localize-change="toggleContentLocalize"
    />

    <el-empty v-if="!loading && !list.length" description="暂无收藏" />
    <el-pagination
      v-if="total > 0"
      class="pagination"
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :hide-on-single-page="false"
      :page-sizes="[10, 15, 20, 50]"
      layout="total, sizes, prev, pager, next, jumper"
      @change="loadData"
    />
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { confirm } from '@/utils/confirm'
import { Refresh, Edit, View, Picture } from '@element-plus/icons-vue'
import TableRowActions from '@/components/TableRowActions.vue'
import ContentPreviewDialog from '@/components/ContentPreviewDialog.vue'
import { dictChildren, favoriteDelete, favoritePage, searchDetail, spiderPage } from '@/api'
import TagEditorDialog from '@/components/TagEditorDialog.vue'
import { resolvePreviewHtml } from '@/utils/previewHtml'
import { formatDateTime } from '@/utils/dateTime'

const list = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(15)
const total = ref(0)
const title = ref('')
const url = ref('')
const spiderName = ref('')
const spiderGroup = ref('')
const tag = ref('')
const spiderOptions = ref<any[]>([])
const groupOptions = ref<string[]>([])
const tagVisible = ref(false)
const tagCurrentRow = ref<any>(null)
const tagOptions = ref<any[]>([])

// 图片预览：跳转到独立的 /image-preview 页面
const openImagePreview = (row: any) => {
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

// 内容预览
const previewContentVisible = ref(false)
const previewContentLoading = ref(false)
const previewContentTitle = ref('')
const previewContentHtml = ref('')
const previewContentSource = ref('')
const previewContentBaseUrl = ref('')
const previewContentLocalize = ref(true)
const previewContentShowSource = ref(false)

const imageUrl = (objectName: string) => {
  if (!objectName) return ''
  if (/^https?:\/\//i.test(objectName)) return objectName
  return `/api/file/image?objectName=${encodeURIComponent(objectName)}`
}

const filteredSpiderOptions = computed(() => {
  if (!spiderGroup.value) return spiderOptions.value
  return spiderOptions.value.filter((s: any) => s.group === spiderGroup.value)
})

const normalizedFilter = (value: unknown) => typeof value === 'string' ? value.trim() || undefined : undefined

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await favoritePage({
      current: page.value,
      size: size.value,
      title: normalizedFilter(title.value),
      url: normalizedFilter(url.value),
      spiderName: normalizedFilter(spiderName.value),
      spiderGroup: normalizedFilter(spiderGroup.value),
      tag: normalizedFilter(tag.value)
    })
    const data = res?.data
    const content = data?.content || data?.records || []
    list.value = content
    total.value = Number(data?.totalElements ?? data?.total ?? data?.page?.totalElements ?? content.length) || 0
  } finally {
    loading.value = false
  }
}

const refresh = () => {
  page.value = 1
  loadData()
}

const resetFilters = () => {
  title.value = ''
  url.value = ''
  spiderName.value = ''
  spiderGroup.value = ''
  tag.value = ''
  refresh()
}

const onGroupChange = () => {
  spiderName.value = ''
  refresh()
}

const clearTagFilter = () => {
  tag.value = ''
  refresh()
}

const loadTagOptions = async () => {
  try {
    const res: any = await dictChildren('tag')
    tagOptions.value = res.data || []
  } catch {
    tagOptions.value = []
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

const loadGroupOptions = async () => {
  try {
    const res: any = await dictChildren('spider-group')
    const children = res.data || []
    groupOptions.value = children.map((c: any) => c.value || c.label).filter(Boolean)
  } catch {
    groupOptions.value = []
  }
}

const openTagEditor = (row: any) => {
  tagCurrentRow.value = row
  tagVisible.value = true
}

const handleTagsSaved = (tags: string[]) => {
  if (tagCurrentRow.value) tagCurrentRow.value.tags = tags
}

const getRowActions = (row: any) => [
  { command: 'preview-images', label: '预览图片', icon: Picture, disabled: !row.images || !row.images.length },
  { command: 'preview-content', label: '预览内容', icon: View },
  { command: 'edit-tags', label: '编辑标签', icon: Edit }
]

const handleRowAction = (command: string, row: any) => {
  if (command === 'edit-tags') openTagEditor(row)
  if (command === 'preview-images') openImagePreview(row)
  if (command === 'preview-content') openContentPreview(row)
}

const openContentPreview = async (row: any) => {
  previewContentVisible.value = true
  previewContentLoading.value = true
  previewContentTitle.value = row.title || '内容预览'
  previewContentHtml.value = ''
  previewContentSource.value = ''
  previewContentBaseUrl.value = ''
  previewContentLocalize.value = true
  previewContentShowSource.value = false
  try {
    const res: any = await searchDetail(row.id)
    const rawHtml = res.data?.rawHtml || res.data?.content || '无原始内容'
    previewContentSource.value = rawHtml
    previewContentBaseUrl.value = res.data?.url || row.url || ''
    previewContentHtml.value = resolvePreviewHtml(rawHtml, previewContentBaseUrl.value, previewContentLocalize.value)
  } catch {
    previewContentHtml.value = '加载失败'
    previewContentSource.value = '加载失败'
  } finally {
    previewContentLoading.value = false
  }
}

const toggleContentLocalize = (enabled: string | number | boolean) => {
  if (!previewContentSource.value || previewContentSource.value === '加载失败') return
  previewContentHtml.value = resolvePreviewHtml(previewContentSource.value, previewContentBaseUrl.value, Boolean(enabled))
}

const remove = async (row: any) => {
  try {
    if (!await confirm('确定取消收藏该内容吗？')) return
    await favoriteDelete(row.id)
    ElMessage.success('已取消收藏')
    await loadData()
  } catch (error: any) {
    if (error !== 'cancel') console.error(error)
  }
}

onMounted(() => {
  loadTagOptions()
  loadSpiderOptions()
  loadGroupOptions()
  loadData()
})
</script>

<style scoped>
.pagination { margin-top: 18px; display: flex; justify-content: center; }
.source-url { color: var(--teal); text-decoration: none; }
.source-url:hover { color: var(--teal-dark); text-decoration: underline; }
.tag { margin-right: 6px; margin-bottom: 4px; cursor: pointer; }
.tag-empty { color: #0f9f9a; cursor: pointer; }
.row-action-cell { display: flex; align-items: center; gap: 8px; }
</style>
