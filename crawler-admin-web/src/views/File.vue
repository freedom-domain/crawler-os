<template>
  <div class="file-page">
    <el-card v-loading="deleting" element-loading-text="正在删除文件及 MinIO 对象...">
      <template #header><span>文件管理</span></template>

      <el-form :inline="true" @submit.prevent>
        <el-form-item>
          <el-input v-model="filterTitle" placeholder="搜索标题" clearable style="width: 200px" @keyup.enter="refresh" @clear="refresh" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="filterFileName" placeholder="搜索文件名" clearable style="width: 200px" @keyup.enter="refresh" @clear="refresh" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="filterCategory" placeholder="全部分类" clearable style="width: 140px" @change="refresh">
            <el-option label="文件" value="file" />
            <el-option label="图片" value="image" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="filterSpider" placeholder="全部爬虫" clearable filterable style="width: 160px" @change="refresh">
            <el-option v-for="spider in spiders" :key="spider.id" :label="spider.name" :value="spider.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="deleting" @click="refresh">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="danger" plain :disabled="deleting || selectedRows.length === 0" @click="removeSelected">
            批量删除<span v-if="selectedRows.length">（{{ selectedRows.length }}）</span>
          </el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="danger" plain :disabled="deleting || !hasFilters" @click="removeByCondition">删除筛选结果</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" stripe @selection-change="selectedRows = $event" resizable>
        <el-table-column type="selection" width="48"  resizable />
        <el-table-column prop="id" label="ID" min-width="70"  resizable />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip  resizable />
        <el-table-column prop="crawlerName" label="所属爬虫" min-width="140" show-overflow-tooltip resizable>
          <template #default="{ row }">{{ row.crawlerName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="category" label="分类" min-width="100"  resizable />
        <el-table-column label="来源" min-width="220" show-overflow-tooltip resizable>
          <template #default="{ row }">
            <el-link v-if="row.source" :href="row.source" target="_blank" rel="noopener noreferrer" type="primary">
              {{ row.source }}
            </el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" min-width="110" resizable>
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="contentType" label="类型" min-width="160" show-overflow-tooltip  resizable />
        <el-table-column prop="createTime" label="上传时间" min-width="170"  resizable />
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip  resizable />
        <el-table-column prop="objectName" label="对象名" min-width="200" show-overflow-tooltip  resizable />
        <el-table-column prop="bucket" label="存储桶" min-width="120"  resizable />
        <el-table-column label="操作" width="190" fixed="right" resizable>
          <template #default="{ row }">
            <el-button v-if="canPreview(row)" type="primary" link size="small" @click="preview(row)">预览</el-button>
            <el-button type="primary" link size="small" @click="download(row)">下载</el-button>
            <el-button type="danger" link size="small" :disabled="deleting" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :total="page.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog v-model="previewVisible" :title="previewRow?.title || '图片预览'" width="820px" @closed="clearPreview">
      <div v-loading="previewLoading" class="preview-body">
        <el-image
          v-if="previewUrl && !previewError"
          :src="previewUrl"
          :alt="previewRow?.fileName || '图片预览'"
          fit="contain"
          class="preview-image"
          :preview-src-list="[previewUrl]"
          preview-teleported
          hide-on-click-modal
          @error="previewError = true"
        />
        <el-empty v-else-if="previewError" description="图片加载失败，请重新尝试" />
        <el-empty v-else description="暂无预览内容" />
      </div>
      <div v-if="previewRow && !previewError" class="preview-meta">
        <span>{{ previewRow.fileName || previewRow.objectName }}</span>
        <el-button link type="primary" size="small" @click="download(previewRow)">下载原图</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { filePage, spiderPage } from '@/api'
import request from '@/api/request'

const loading = ref(false)
const tableData = ref<any[]>([])
const selectedRows = ref<any[]>([])
const filterCategory = ref('')
const filterSpider = ref<number | null>(null)
const filterFileName = ref('')
const filterTitle = ref('')
const spiders = ref<any[]>([])
const page = reactive({ current: 1, size: 10, total: 0 })
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewUrl = ref('')
const previewRow = ref<any>(null)
const previewError = ref(false)
const deleting = ref(false)
const hasFilters = computed(() => Boolean(filterCategory.value || filterSpider.value || filterFileName.value.trim() || filterTitle.value.trim()))

const formatSize = (bytes: number) => {
  if (!bytes && bytes !== 0) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(1) + ' GB'
}

const loadData = async () => {
  loading.value = true
  try {
    const params: any = { current: page.current, size: page.size }
    if (filterCategory.value) params.category = filterCategory.value
    if (filterSpider.value) params.spiderId = filterSpider.value
    if (filterFileName.value.trim()) params.fileName = filterFileName.value.trim()
    if (filterTitle.value.trim()) params.title = filterTitle.value.trim()
    const res: any = await filePage(params)
    tableData.value = res?.data?.records || []
    page.total = res?.data?.total || 0
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const refresh = () => {
  page.current = 1
  loadData()
}

const download = (row: any) => {
  const url = `/api/file/download?bucket=${encodeURIComponent(row.bucket)}&objectName=${encodeURIComponent(row.objectName)}`
  window.open(url, '_blank')
}

const canPreview = (row: any) => row.category === 'image' || row.contentType?.startsWith('image/')

const preview = async (row: any) => {
  previewRow.value = row
  previewVisible.value = true
  previewLoading.value = true
  previewError.value = false
  try {
    const response: any = await request.get('/file/image', {
      params: { bucket: row.bucket, objectName: row.objectName },
      responseType: 'blob'
    })
    const blob = response instanceof Blob ? response : response.data
    previewUrl.value = URL.createObjectURL(blob)
  } catch (e) {
    console.error(e)
  } finally {
    previewLoading.value = false
  }
}

const clearPreview = () => {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  previewRow.value = null
  previewError.value = false
}

const deleteRows = async (rows: any[]) => {
  await Promise.all(rows.map(row => request.delete('/file', {
    params: { bucket: row.bucket, objectName: row.objectName }
  })))
}

const remove = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定删除该文件？', '提示', { type: 'warning' })
    deleting.value = true
    await request.delete('/file', {
      params: { bucket: row.bucket, objectName: row.objectName }
    })
    ElMessage.success('删除成功')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') console.error(e)
  } finally {
    deleting.value = false
  }
}

const removeSelected = async () => {
  const rows = [...selectedRows.value]
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${rows.length} 个文件？`, '提示', { type: 'warning' })
    deleting.value = true
    await deleteRows(rows)
    selectedRows.value = []
    ElMessage.success('批量删除成功')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') console.error(e)
  } finally {
    deleting.value = false
  }
}

const removeByCondition = async () => {
  if (!hasFilters.value) return
  try {
    await ElMessageBox.confirm('确定删除当前筛选条件下的全部文件？此操作不可恢复。', '危险操作', { type: 'warning' })
    deleting.value = true
    const res: any = await request.delete('/file/condition', {
      params: {
        category: filterCategory.value || undefined,
        spiderId: filterSpider.value || undefined,
        fileName: filterFileName.value.trim() || undefined,
        title: filterTitle.value.trim() || undefined
      }
    })
    ElMessage.success(`已删除 ${res?.data || 0} 个文件`)
    selectedRows.value = []
    refresh()
  } catch (e: any) {
    if (e !== 'cancel') console.error(e)
  } finally {
    deleting.value = false
  }
}

onMounted(loadData)
onMounted(async () => {
  try {
    const res: any = await spiderPage({ current: 1, size: 100 })
    spiders.value = res?.data?.records || []
  } catch (e) {
    console.error(e)
  }
})
onUnmounted(clearPreview)
</script>

<style scoped>
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
.preview-body { min-height: 240px; display: flex; align-items: center; justify-content: center; }
.preview-image { display: block; width: 100%; height: min(62vh, 620px); }
.preview-meta { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 12px; padding-top: 12px; border-top: 1px solid #edf0f4; color: #5e6c84; font-size: 13px; }
</style>
