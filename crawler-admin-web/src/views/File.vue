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
            <el-button type="primary" size="small" @click="refresh">
              <el-icon><Refresh /></el-icon>刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="objectName" label="对象名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="bucket" label="存储桶" width="120" />
        <el-table-column prop="category" label="分类" width="100" />
        <el-table-column prop="fileSize" label="大小" width="110">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="contentType" label="类型" width="160" show-overflow-tooltip />
        <el-table-column prop="createTime" label="上传时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { filePage } from '@/api'
import request from '@/api/request'

const loading = ref(false)
const tableData = ref<any[]>([])
const filterCategory = ref('')
const page = reactive({ current: 1, size: 10, total: 0 })

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

onMounted(loadData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
