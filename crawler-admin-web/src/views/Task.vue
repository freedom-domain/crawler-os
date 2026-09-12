<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>任务管理</span>
        <el-button :icon="Refresh" @click="loadData">刷新</el-button>
      </div>
    </template>

    <el-form :inline="true">
      <el-form-item>
        <el-select v-model="statusFilter" placeholder="状态" clearable @change="loadData">
          <el-option label="运行中" value="RUNNING" />
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="已取消" value="CANCELED" />
        </el-select>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="spiderName" label="爬虫名称" width="160" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" width="180" />
      <el-table-column prop="endTime" label="结束时间" width="180" />
      <el-table-column prop="successCount" label="成功数" width="80" />
      <el-table-column prop="failCount" label="失败数" width="80" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button v-if="row.status === 'RUNNING'" size="small" type="warning" @click="handleCancel(row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top:16px; justify-content:flex-end"
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      layout="total, prev, pager, next"
      @change="loadData"
    />
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { taskPage, taskCancel } from '@/api'
import { Refresh } from '@element-plus/icons-vue'

const list = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const statusFilter = ref('')

const statusTag = (status: string) => {
  const map: Record<string, string> = {
    RUNNING: 'primary', SUCCESS: 'success', FAILED: 'danger',
    PENDING: 'warning', CANCELED: 'info'
  }
  return map[status] || 'info'
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await taskPage({ current: page.value, size: size.value, status: statusFilter.value })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const handleCancel = async (row: any) => {
  await taskCancel(row.id)
  ElMessage.success('已取消')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
