<template>
  <el-card>
    <el-form :inline="true" @submit.prevent>
      <el-form-item>
        <el-select v-model="spiderFilter" placeholder="全部爬虫" clearable style="width: 180px">
          <el-option v-for="s in spiders" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 160px">
          <el-option label="运行中" value="RUNNING" />
          <el-option label="排队中" value="PENDING" />
          <el-option label="取消中" value="CANCELING" />
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="已取消" value="CANCELED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData" :icon="Search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
      <el-form-item>
        <el-button type="danger" plain @click="handleBatchDelete" :disabled="selectedRows.length === 0">
          批量删除<span v-if="selectedRows.length">（{{ selectedRows.length }}）</span>
        </el-button>
      </el-form-item>
      <el-form-item class="concurrency-toolbar-item">
        <el-button :loading="concurrencyLoading" @click="openConcurrencyDialog">并发策略</el-button>
      </el-form-item>
      <el-form-item class="refresh-toolbar-item">
        <div class="refresh-toolbar">
          <el-switch v-model="autoRefresh" active-text="自动刷新" />
          <el-select
            v-model="refreshIntervalSeconds"
            class="refresh-interval-select"
            size="small"
            aria-label="自动刷新间隔"
            @change="saveRefreshInterval"
          >
            <el-option v-for="seconds in refreshIntervalOptions" :key="seconds" :label="`${seconds} 秒`" :value="seconds" />
          </el-select>
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
        </div>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" stripe @selection-change="handleSelectionChange" resizable border>
      <el-table-column type="selection" width="50"  resizable />
      <el-table-column prop="taskId" label="任务ID" min-width="200" show-overflow-tooltip  resizable />
      <el-table-column prop="spiderName" label="爬虫名称" min-width="160"  resizable />
      <el-table-column label="状态" min-width="100" resizable>
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" min-width="180"  resizable />
      <el-table-column prop="endTime" label="结束时间" min-width="180"  resizable />
      <el-table-column label="总耗时" min-width="110" align="center" resizable>
        <template #default="{ row }">
          {{ formatDuration(row.totalCostMs || 0) }}
        </template>
      </el-table-column>
      <el-table-column label="HTML" align="center" resizable>
        <el-table-column prop="htmlSuccessCount" label="成功" min-width="70" align="center"  resizable />
        <el-table-column prop="htmlFailCount" label="失败" min-width="70" align="center"  resizable />
        <el-table-column prop="htmlExistingCount" label="已存在" min-width="80" align="center"  resizable />
      </el-table-column>
      <el-table-column label="图片" align="center" resizable>
        <el-table-column prop="imageSuccessCount" label="成功" min-width="70" align="center"  resizable />
        <el-table-column prop="imageFailCount" label="失败" min-width="70" align="center"  resizable />
        <el-table-column prop="imageExistingCount" label="已存在" min-width="80" align="center"  resizable />
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right" resizable>
        <template #default="{ row }">
          <el-button v-if="row.status === 'RUNNING' || row.status === 'PENDING'" size="small" type="warning" @click="handleCancel(row)">取消</el-button>
          <el-button size="small" type="primary" text @click="showLogs(row)">日志</el-button>
          <el-button v-if="row.status !== 'RUNNING' && row.status !== 'CANCELING'" size="small" type="danger" text @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 15, 20, 50]"
      layout="total, sizes, prev, pager, next, jumper"
      @change="loadData"
    />

    <el-dialog
      v-model="concurrencyDialogVisible"
      title="任务并发策略"
      width="460px"
      :close-on-click-modal="!concurrencySaving"
      :close-on-press-escape="!concurrencySaving"
    >
      <div v-loading="concurrencyLoading" class="concurrency-settings">
        <div class="concurrency-setting-row">
          <span>全局最大并发任务数</span>
          <el-input-number
            v-model="maxConcurrency"
            :min="1"
            :max="20"
            :step="1"
            controls-position="right"
            :disabled="!concurrencyLoaded || concurrencyLoading || concurrencySaving"
            aria-label="最大并发任务数"
          />
        </div>
        <p class="concurrency-hint">
          同时运行的任务数上限为 1–20。降低上限不会中断正在运行的任务，后续任务会等待空位。
        </p>
      </div>
      <template #footer>
        <el-button :disabled="concurrencySaving" @click="closeConcurrencyDialog">取消</el-button>
        <el-button
          type="primary"
          :loading="concurrencySaving"
          :disabled="!concurrencyLoaded || concurrencyLoading || maxConcurrency === savedMaxConcurrency"
          @click="saveConcurrency"
        >
          保存策略
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="logVisible"
      :width="logFullscreen ? '100%' : '900px'"
      :top="logFullscreen ? '0' : '5vh'"
      :class="{ 'log-fullscreen-dialog': logFullscreen }"
      destroy-on-close
      @closed="logFullscreen = false"
    >
      <template #header>
        <div class="log-dialog-header">
          <span>任务日志</span>
          <el-button size="small" text type="primary" @click="logFullscreen = !logFullscreen">
            <el-icon><component :is="logFullscreen ? 'Minus' : 'FullScreen'" /></el-icon>
            {{ logFullscreen ? '退出放大' : '放大' }}
          </el-button>
        </div>
      </template>
      <div class="log-filter">
        <el-input v-model="logKeyword" placeholder="搜索 URL / 信息" clearable size="small" style="width: 220px" @clear="reloadLogs" @keyup.enter="reloadLogs" />
        <el-select v-model="logStatus" placeholder="状态" clearable size="small" style="width: 100px" @change="reloadLogs">
          <el-option label="成功" :value="1" />
          <el-option label="失败" :value="0" />
          <el-option label="已存在" :value="2" />
        </el-select>
        <el-select v-model="logType" placeholder="类型" clearable size="small" style="width: 130px" @change="reloadLogs">
          <el-option label="HTML" value="html" />
          <el-option label="图片" value="image" />
          <el-option label="JavaScript" value="js" />
          <el-option label="CSS" value="css" />
        </el-select>
        <el-select v-model="logLevel" placeholder="级别" clearable size="small" style="width: 100px" @change="reloadLogs">
          <el-option label="INFO" value="INFO" />
          <el-option label="ERROR" value="ERROR" />
        </el-select>
        <el-button size="small" :icon="Search" @click="reloadLogs">查询</el-button>
        <el-button size="small" @click="resetLogFilters">重置</el-button>
      </div>
      <el-empty v-if="!logLoading && logs.length === 0" description="暂无日志" :image-size="60" />
      <el-table v-else :data="logs" v-loading="logLoading" stripe size="small" :max-height="logFullscreen ? 'calc(100vh - 190px)' : '60vh'" resizable border>
        <el-table-column label="URL" show-overflow-tooltip resizable>
          <template #default="{ row }">
            <a class="log-url" :href="row.url" target="_blank" rel="noopener noreferrer">{{ row.url }}</a>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="80" align="center" resizable>
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success" size="small">成功</el-tag>
            <el-tag v-else-if="row.status === 0" type="danger" size="small">失败</el-tag>
            <el-tag v-else type="warning" size="small">已存在</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" min-width="80" align="center" resizable>
          <template #default="{ row }">
            <el-tag :type="logTypeTag(row.type)" size="small">{{ logTypeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="level" label="级别" min-width="70" align="center"  resizable />
        <el-table-column prop="message" label="信息" show-overflow-tooltip  resizable />
        <el-table-column prop="costMs" label="耗时(ms)" min-width="90" align="center"  resizable />
        <el-table-column prop="createTime" label="时间" min-width="180"  resizable />
      </el-table>
      <el-pagination
        v-show="logs.length > 0"
        v-model:current-page="logPage"
        v-model:page-size="logSize"
        :total="logTotal"
        :page-sizes="[10, 15, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @change="loadLogs"
      />
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { taskPage, taskLogs, taskCancel, taskDelete, spiderPage, taskConcurrency, updateTaskConcurrency } from '@/api'
import { Refresh, Search } from '@element-plus/icons-vue'

const list = ref<any[]>([])
const loading = ref(false)
const prevStatusMap = new Map<string, string>()
const page = ref(1)
const size = ref(15)
const total = ref(0)
const statusFilter = ref('')
const spiderFilter = ref<number | null>(null)
const spiders = ref<any[]>([])
const autoRefresh = ref(true)
const refreshIntervalOptions = [5, 10, 15, 30, 60]
const loadRefreshInterval = () => {
  const saved = Number(localStorage.getItem('task-auto-refresh-interval-seconds'))
  return refreshIntervalOptions.includes(saved) ? saved : 5
}
const refreshIntervalSeconds = ref(loadRefreshInterval())
const maxConcurrency = ref(1)
const savedMaxConcurrency = ref(1)
const concurrencyLoading = ref(false)
const concurrencySaving = ref(false)
const concurrencyLoaded = ref(false)
const concurrencyDialogVisible = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

const logVisible = ref(false)
const logFullscreen = ref(false)
const logLoading = ref(false)
const logs = ref<any[]>([])
const logPage = ref(1)
const logSize = ref(15)
const selectedRows = ref<any[]>([])

const handleSelectionChange = (rows: any[]) => {
  selectedRows.value = rows
}

const handleBatchDelete = async () => {
  if (selectedRows.value.length === 0) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedRows.value.length} 个任务吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  loading.value = true
  try {
    for (const row of selectedRows.value) {
      await taskDelete(row.id)
    }
    ElMessage.success(`成功删除 ${selectedRows.value.length} 个任务`)
    selectedRows.value = []
    loadData()
  } catch {
    ElMessage.error('批量删除失败')
  } finally {
    loading.value = false
  }
}
const logTotal = ref(0)
const currentTask = ref<any>(null)
const logKeyword = ref('')
const logStatus = ref<number | null>(null)
const logType = ref('')
const logLevel = ref('')

const reloadLogs = () => {
  logPage.value = 1
  loadLogs()
}

const resetLogFilters = () => {
  logKeyword.value = ''
  logStatus.value = null
  logType.value = ''
  logLevel.value = ''
  reloadLogs()
}

const statusTag = (status: string) => {
  const map: Record<string, string> = {
    RUNNING: 'primary', SUCCESS: 'success', FAILED: 'danger',
    PENDING: 'warning', CANCELING: 'warning', CANCELED: 'info'
  }
  return map[status] || 'info'
}

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    RUNNING: '运行中', SUCCESS: '成功', FAILED: '失败',
    PENDING: '排队中', CANCELING: '正在取消', CANCELED: '已取消'
  }
  return map[status] || status
}

const logTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    html: 'HTML', image: '图片', js: 'JavaScript', css: 'CSS'
  }
  return map[type] || type || '未知'
}

const logTypeTag = (type: string) => {
  const map: Record<string, string> = {
    html: 'info', image: 'warning', js: 'primary', css: 'success'
  }
  return map[type] || 'info'
}

const formatDuration = (ms: number) => {
  if (!ms && ms !== 0) return '-'
  const totalSeconds = Math.max(0, Math.floor(ms / 1000))
  const h = Math.floor(totalSeconds / 3600)
  const m = Math.floor((totalSeconds % 3600) / 60)
  const s = totalSeconds % 60
  if (h > 0) return `${h}h ${m}m ${s}s`
  if (m > 0) return `${m}m ${s}s`
  return `${s}s`
}

const loadData = async () => {
  loading.value = true
  try {
    const params: any = { current: page.value, size: size.value, status: statusFilter.value }
    if (spiderFilter.value) params.spiderId = spiderFilter.value
    const res: any = await taskPage(params)
    const records = res.data?.records || []
    for (const t of records) {
      const prev = prevStatusMap.get(String(t.id))
      if (['RUNNING', 'CANCELING'].includes(prev || '')
          && ['SUCCESS', 'FAILED', 'CANCELED'].includes(t.status)) {
        const msg = t.status === 'SUCCESS'
          ? `任务「${t.spiderName}」已完成：成功 ${t.successCount} 条，失败 ${t.failCount} 条`
          : t.status === 'FAILED'
            ? `任务「${t.spiderName}」执行失败`
            : `任务「${t.spiderName}」已取消`
        ElMessage({ type: t.status === 'SUCCESS' ? 'success' : 'warning', message: msg, duration: 5000 })
      }
      prevStatusMap.set(String(t.id), t.status)
    }
    list.value = records
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  spiderFilter.value = null
  statusFilter.value = ''
  page.value = 1
  loadData()
}

const loadSpiders = async () => {
  const res: any = await spiderPage({ current: 1, size: 100 })
  spiders.value = res.data?.records || []
}

const loadConcurrency = async () => {
  concurrencyLoading.value = true
  concurrencyLoaded.value = false
  try {
    const res: any = await taskConcurrency()
    const value = Number(res.data)
    if (!Number.isInteger(value) || value < 1 || value > 20) {
      throw new Error('服务器返回的任务并发数无效')
    }
    maxConcurrency.value = value
    savedMaxConcurrency.value = value
    concurrencyLoaded.value = true
  } catch {
    ElMessage.error('读取任务并发策略失败')
  } finally {
    concurrencyLoading.value = false
  }
}

const openConcurrencyDialog = async () => {
  concurrencyDialogVisible.value = true
  await loadConcurrency()
}

const closeConcurrencyDialog = () => {
  maxConcurrency.value = savedMaxConcurrency.value
  concurrencyDialogVisible.value = false
}

const saveConcurrency = async () => {
  if (!Number.isInteger(maxConcurrency.value) || maxConcurrency.value < 1 || maxConcurrency.value > 20) {
    ElMessage.warning('最大并发数需设置为 1 到 20 的整数')
    return
  }
  concurrencySaving.value = true
  try {
    await updateTaskConcurrency(maxConcurrency.value)
    savedMaxConcurrency.value = maxConcurrency.value
    ElMessage.success('任务并发策略已更新')
    concurrencyDialogVisible.value = false
  } catch {
    ElMessage.error('更新任务并发策略失败')
  } finally {
    concurrencySaving.value = false
  }
}

const showLogs = async (row: any) => {
  currentTask.value = row
  logVisible.value = true
  logPage.value = 1
  logKeyword.value = ''
  logStatus.value = null
  logType.value = ''
  logLevel.value = ''
  await loadLogs()
}

const loadLogs = async () => {
  if (!currentTask.value) return
  logLoading.value = true
  try {
    const params: any = { current: logPage.value, size: logSize.value }
    if (logStatus.value != null) params.status = logStatus.value
    if (logType.value) params.type = logType.value
    if (logLevel.value) params.level = logLevel.value
    if (logKeyword.value) params.keyword = logKeyword.value
    const res: any = await taskLogs(currentTask.value.id, params)
    logs.value = res.data?.records || []
    logTotal.value = res.data?.total || 0
  } finally {
    logLoading.value = false
  }
}

const handleCancel = async (row: any) => {
  try {
    await taskCancel(row.id)
    ElMessage.success('已提交取消请求')
  } catch {
    ElMessage.error('取消失败')
  }
  loadData()
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定删除该任务及其日志吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await taskDelete(row.id)
    ElMessage.success('已删除')
  } catch {
    ElMessage.error('删除失败')
  }
  loadData()
}

const hasActive = () => list.value.some((t: any) =>
  t.status === 'RUNNING' || t.status === 'PENDING' || t.status === 'CANCELING'
)

const startTimer = () => {
  stopTimer()
  if (autoRefresh.value && hasActive()) {
    timer = setInterval(loadData, refreshIntervalSeconds.value * 1000)
  }
}

const saveRefreshInterval = (seconds: number) => {
  localStorage.setItem('task-auto-refresh-interval-seconds', String(seconds))
  startTimer()
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
.log-dialog-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%; }
.log-dialog-header .el-button { margin-right: 8px; }
:deep(.log-fullscreen-dialog) { margin: 0 auto !important; height: 100vh; }
:deep(.log-fullscreen-dialog .el-dialog__body) { height: calc(100vh - 72px); overflow: auto; }
.concurrency-settings { min-height: 88px; }
.concurrency-setting-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.concurrency-setting-row > span { color: #475569; font-weight: 600; }
.concurrency-hint { margin-top: 14px; color: #64748b; font-size: 13px; line-height: 1.6; }
.refresh-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}
.refresh-interval-select { width: 90px; }
:deep(.concurrency-toolbar-item) { margin-left: auto !important; }

.log-filter {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.log-url {
  color: #409eff;
  text-decoration: none;
  word-break: break-all;
}

.log-url:hover {
  text-decoration: underline;
}

@media (max-width: 767px) {
  :deep(.concurrency-toolbar-item) { margin-left: 0 !important; }
  .refresh-toolbar { justify-content: flex-end; }
}
</style>
