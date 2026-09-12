<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>任务管理</span>
        <div>
          <el-switch v-model="autoRefresh" active-text="自动刷新" style="margin-right: 12px" />
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
        </div>
      </div>
    </template>

    <div class="filter-bar">
      <span class="filter-label">爬虫</span>
      <el-select v-model="spiderFilter" placeholder="全部" clearable style="width: 160px" @change="loadData">
        <el-option v-for="s in spiders" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <span class="filter-label">状态</span>
      <el-select v-model="statusFilter" placeholder="全部" clearable style="width: 120px" @change="loadData">
        <el-option label="运行中" value="RUNNING" />
        <el-option label="成功" value="SUCCESS" />
        <el-option label="失败" value="FAILED" />
        <el-option label="已取消" value="CANCELED" />
      </el-select>
      <el-button type="primary" @click="loadData" :icon="Search" style="margin-left: 8px">查询</el-button>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="taskId" label="任务ID" width="200" show-overflow-tooltip />
      <el-table-column prop="spiderName" label="爬虫名称" width="160" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" width="180" />
      <el-table-column prop="endTime" label="结束时间" width="180" />
      <el-table-column prop="successCount" label="成功" width="70" align="center" />
      <el-table-column prop="failCount" label="失败" width="70" align="center" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button v-if="row.status === 'RUNNING'" size="small" type="warning" @click="handleCancel(row)">取消</el-button>
          <el-button size="small" type="primary" text @click="showLogs(row)">日志</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @change="loadData"
    />

    <el-dialog v-model="logVisible" title="任务日志" width="900px" top="5vh" destroy-on-close>
      <el-table :data="logs" v-loading="logLoading" stripe size="small" max-height="60vh">
        <el-table-column prop="url" label="URL" show-overflow-tooltip />
        <el-table-column label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="level" label="级别" width="70" align="center" />
        <el-table-column prop="message" label="信息" show-overflow-tooltip />
        <el-table-column prop="costMs" label="耗时(ms)" width="90" align="center" />
        <el-table-column prop="createTime" label="时间" width="180" />
      </el-table>
      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        v-model:current-page="logPage"
        :page-size="logSize"
        :total="logTotal"
        layout="total, prev, pager, next"
        @change="loadLogs"
      />
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { taskPage, taskLogs, taskCancel, spiderPage } from '@/api'
import { Refresh, Search } from '@element-plus/icons-vue'

const list = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const statusFilter = ref('')
const spiderFilter = ref<number | null>(null)
const spiders = ref<any[]>([])
const autoRefresh = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

const logVisible = ref(false)
const logLoading = ref(false)
const logs = ref<any[]>([])
const logPage = ref(1)
const logSize = ref(50)
const logTotal = ref(0)
const currentTask = ref<any>(null)

const statusTag = (status: string) => {
  const map: Record<string, string> = {
    RUNNING: 'primary', SUCCESS: 'success', FAILED: 'danger',
    PENDING: 'warning', CANCELED: 'info'
  }
  return map[status] || 'info'
}

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    RUNNING: '运行中', SUCCESS: '成功', FAILED: '失败',
    PENDING: '等待中', CANCELED: '已取消'
  }
  return map[status] || status
}

const loadData = async () => {
  loading.value = true
  try {
    const params: any = { current: page.value, size: size.value, status: statusFilter.value }
    if (spiderFilter.value) params.spiderId = spiderFilter.value
    const res: any = await taskPage(params)
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const loadSpiders = async () => {
  const res: any = await spiderPage({ current: 1, size: 100 })
  spiders.value = res.data?.records || []
}

const showLogs = async (row: any) => {
  currentTask.value = row
  logVisible.value = true
  logPage.value = 1
  await loadLogs()
}

const loadLogs = async () => {
  if (!currentTask.value) return
  logLoading.value = true
  try {
    const res: any = await taskLogs(currentTask.value.id, { current: logPage.value, size: logSize.value })
    logs.value = res.data?.records || []
    logTotal.value = res.data?.total || 0
  } finally {
    logLoading.value = false
  }
}

const handleCancel = async (row: any) => {
  await taskCancel(row.id)
  ElMessage.success('已取消')
  loadData()
}

const hasRunning = () => list.value.some((t: any) => t.status === 'RUNNING')

const startTimer = () => {
  stopTimer()
  if (autoRefresh.value && hasRunning()) {
    timer = setInterval(loadData, 5000)
  }
}

const stopTimer = () => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

watch([autoRefresh, () => list.value], () => {
  startTimer()
})

onMounted(() => {
  loadData()
  loadSpiders()
  startTimer()
})

onUnmounted(stopTimer)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.filter-label {
  font-size: 14px;
  color: #606266;
  white-space: nowrap;
}
</style>
