<template>
  <div class="file-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>文件管理</span>
          <div>
            <el-select v-model="filterCategory" placeholder="全部分类" clearable style="width: 140px; margin-right: 12px" @change="refresh">
              <el-option label="文件" value="file" />
              <el-option label="图片" value="image" />
              <el-option label="其他" value="other" />
            </el-select>
            <el-select v-model="filterSpider" placeholder="全部爬虫" clearable filterable style="width: 160px; margin-right: 12px" @change="refresh">
              <el-option v-for="spider in spiders" :key="spider.id" :label="spider.name" :value="spider.id" />
            </el-select>
            <el-button type="danger" plain size="small" :disabled="selectedRows.length === 0" @click="removeSelected">
              批量删除<span v-if="selectedRows.length">（{{ selectedRows.length }}）</span>
            </el-button>
            <el-button type="primary" size="small" @click="refresh">
              <el-icon><Refresh /></el-icon>刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe @selection-change="selectedRows = $event">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="objectName" label="对象名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="bucket" label="存储桶" width="120" />
        <el-table-column prop="category" label="分类" width="100" />
        <el-table-column prop="crawlerName" label="所属爬虫" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.crawlerName || '-' }}</template>
        </el-table-column>
        <el-table-column label="来源" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link v-if="row.source" :href="row.source" target="_blank" rel="noopener noreferrer" type="primary">
              {{ row.source }}
            </el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" width="110">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="contentType" label="类型" width="160" show-overflow-tooltip />
        <el-table-column prop="createTime" label="上传时间" width="170" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canPreview(row)" type="primary" link size="small" @click="preview(row)">预览</el-button>
            <el-button type="primary" link size="small" @click="download(row)">下载</el-button>
            <el-button type="danger" link size="small" @click="remove(row)">删除</el-button>
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

    <el-dialog v-model="previewVisible" title="图片预览" width="760px" @closed="clearPreview">
      <div v-loading="previewLoading" class="preview-body">
        <img v-if="previewUrl" :src="previewUrl" :alt="previewRow?.fileName || '图片预览'" />
        <el-empty v-else description="图片加载失败" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { filePage, spiderPage } from '@/api'
import request from '@/api/request'

const loading = ref(false)
const tableData = ref<any[]>([])
const selectedRows = ref<any[]>([])
const filterCategory = ref('')
const filterSpider = ref<number | null>(null)
const spiders = ref<any[]>([])
const page = reactive({ current: 1, size: 10, total: 0 })
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewUrl = ref('')
const previewRow = ref<any>(null)

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
}

const deleteRows = async (rows: any[]) => {
  await Promise.all(rows.map(row => request.delete('/file', {
    params: { bucket: row.bucket, objectName: row.objectName }
  })))
}

const remove = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定删除该文件？', '提示', { type: 'warning' })
    await request.delete('/file', {
      params: { bucket: row.bucket, objectName: row.objectName }
    })
    ElMessage.success('删除成功')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') console.error(e)
  }
}

const removeSelected = async () => {
  const rows = [...selectedRows.value]
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${rows.length} 个文件？`, '提示', { type: 'warning' })
    await deleteRows(rows)
    selectedRows.value = []
    ElMessage.success('批量删除成功')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') console.error(e)
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
.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
.preview-body { min-height: 240px; display: flex; align-items: center; justify-content: center; }
.preview-body img { display: block; max-width: 100%; max-height: 60vh; object-fit: contain; }
</style>
