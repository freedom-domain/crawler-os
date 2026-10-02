<template>
  <el-card>
    <el-form class="task-toolbar" :inline="true" @submit.prevent>
      <el-form-item class="task-filter-item">
        <el-select v-model="spiderFilter" placeholder="全部爬虫" clearable style="width: 180px" @change="onFilterChange">
          <el-option v-for="s in spiders" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item class="task-filter-item">
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 160px" @change="onFilterChange">
          <el-option label="运行中" value="RUNNING" />
          <el-option label="排队中" value="PENDING" />
          <el-option label="取消中" value="CANCELING" />
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="已取消" value="CANCELED" />
        </el-select>
      </el-form-item>
      <el-form-item class="task-query-actions">
        <el-button type="primary" @click="loadData" :icon="Search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
      <el-form-item class="task-batch-action">
        <el-button type="danger" plain @click="handleBatchDelete" :disabled="selectedRows.length === 0">
          批量删除<span v-if="selectedRows.length">（{{ selectedRows.length }}）</span>
        </el-button>
      </el-form-item>
      <el-form-item class="concurrency-toolbar-item task-concurrency-action">
        <el-button :loading="concurrencyLoading" @click="openConcurrencyDialog">并发策略</el-button>
      </el-form-item>
      <el-form-item class="refresh-toolbar-item task-refresh-action">
        <div class="refresh-toolbar">
          <span class="auto-refresh-indicator" :class="{ active: autoRefreshing }" title="自动刷新状态"></span>
          <el-tooltip
            :content="hasActive() ? '正在自动刷新' : '无运行中任务，自动刷新已暂停'"
            placement="bottom"
            :show-after="400"
          >
            <el-switch
              v-model="autoRefresh"
              :disabled="!hasActive() && autoRefresh"
              active-text="自动刷新"
              inactive-text="自动刷新"
            />
          </el-tooltip>
          <el-select
            v-model="refreshIntervalSeconds"
            class="refresh-interval-select"
            :disabled="!hasActive()"
            aria-label="自动刷新间隔"
            @change="saveRefreshInterval"
          >
            <el-option v-for="seconds in refreshIntervalOptions" :key="seconds" :label="`${seconds} 秒`" :value="seconds" />
          </el-select>
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
        </div>
      </el-form-item>
    </el-form>

    <div class="table-scroll-wrapper">
    <el-table :data="list" v-loading="loading" stripe @selection-change="handleSelectionChange" resizable border class="task-table">
      <el-table-column type="selection" width="50"  resizable />
      <el-table-column prop="taskId" label="任务ID" width="200" show-overflow-tooltip resizable />
      <el-table-column label="爬虫名称" min-width="160" resizable>
        <template #default="{ row }">
          <el-dropdown trigger="click" @command="(cmd: string) => handleSpiderJump(cmd, row)">
            <el-button link type="primary" class="spider-name-dropdown">
              {{ row.spiderName }}
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="data">查看数据</el-dropdown-item>
                <el-dropdown-item command="spider">跳转爬虫</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120" resizable>
        <template #default="{ row }">
          <el-tag
            :type="statusTag(row.status)"
            effect="light"
            round
            class="task-status-tag"
            :class="`task-status-${String(row.status || '').toLowerCase()}`"
          >
            <span class="task-status-content">
              <span class="task-status-dot" :data-paused="row.status === 'RUNNING' && row.pausedAt ? '1' : undefined"></span>
              {{ statusLabel(row.status, row) }}
            </span>
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" width="190" resizable>
        <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
      </el-table-column>
      <el-table-column prop="endTime" label="结束时间" width="190" resizable>
        <template #default="{ row }">{{ formatDateTime(row.endTime) }}</template>
      </el-table-column>
      <el-table-column label="总耗时" min-width="110" align="center" resizable>
        <template #default="{ row }">
          {{ formatDuration(taskDurationMs(row)) }}
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
      <el-table-column label="操作" min-width="120" fixed="right" align="center" resizable>
        <template #default="{ row }">
          <TableRowActions :items="getRowActions(row)" @command="command => handleRowAction(command, row)" />
        </template>
      </el-table-column>
    </el-table>
    </div>

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
          <span>同时运行任务数</span>
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
          全局同时运行的爬虫任务上限。同一爬虫同时只允许一个排队或运行中的任务。降低上限不会中断正在运行的任务。
        </p>
        <div class="concurrency-setting-row">
          <span>每任务 URL 并发数</span>
          <el-input-number
            v-model="urlConcurrency"
            :min="1"
            :max="50"
            :step="1"
            controls-position="right"
            :disabled="!concurrencyLoaded || concurrencyLoading || concurrencySaving"
            aria-label="每任务URL并发数"
          />
        </div>
        <p class="concurrency-hint">
          单个任务内同时抓取的 URL 数量。值越大抓取越快，但对目标站点压力越大。
        </p>
      </div>
      <template #footer>
        <el-button :disabled="concurrencySaving" @click="closeConcurrencyDialog">取消</el-button>
        <el-button
          type="primary"
          :loading="concurrencySaving"
          :disabled="!concurrencyLoaded || concurrencyLoading || (maxConcurrency === savedMaxConcurrency && urlConcurrency === savedUrlConcurrency)"
          @click="saveConcurrency"
        >
          保存策略
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="logVisible"
      :width="logFullscreen ? '100%' : 'min(1200px, calc(100vw - 32px))'"
      :top="logFullscreen ? '0' : '5vh'"
      :class="{ 'log-fullscreen-dialog': logFullscreen }"
      destroy-on-close
      @closed="logFullscreen = false"
    >
      <template #header>
        <div class="log-dialog-header">
          <span class="log-dialog-title">任务日志{{ currentTask ? `（任务 #${currentTask.taskId ?? currentTask.id}${currentTask.spiderName ? ' · ' + currentTask.spiderName : ''}）` : '' }}</span>
          <el-button size="small" text type="primary" @click="logFullscreen = !logFullscreen">
            <el-icon><component :is="logFullscreen ? Minus : FullScreen" /></el-icon>
            {{ logFullscreen ? '退出放大' : '放大' }}
          </el-button>
        </div>
      </template>
      <div class="log-filter">
        <el-input v-model="logKeyword" placeholder="搜索 URL / 信息" clearable size="small" class="log-filter-input" @clear="reloadLogs" @keyup.enter="reloadLogs" />
        <el-select v-model="logStatus" placeholder="状态" clearable size="small" class="log-filter-select" @change="reloadLogs">
          <el-option label="成功" :value="1" />
          <el-option label="失败" :value="0" />
          <el-option label="已存在" :value="2" />
        </el-select>
        <el-select v-model="logType" placeholder="类型" clearable size="small" class="log-filter-select" @change="reloadLogs">
          <el-option label="HTML" value="html" />
          <el-option label="图片" value="image" />
          <el-option label="JavaScript" value="js" />
          <el-option label="CSS" value="css" />
        </el-select>
        <el-select v-model="logLevel" placeholder="级别" clearable size="small" class="log-filter-select" @change="reloadLogs">
          <el-option label="INFO" value="INFO" />
          <el-option label="ERROR" value="ERROR" />
        </el-select>
        <el-button size="small" :icon="Search" @click="reloadLogs">查询</el-button>
        <el-button size="small" @click="resetLogFilters">重置</el-button>
      </div>
      <el-empty v-if="!logLoading && logs.length === 0" description="暂无日志" :image-size="60" />
      <div v-else class="table-scroll-wrapper">
      <el-table :data="logs" v-loading="logLoading" stripe size="small" :max-height="logFullscreen ? 'calc(100vh - 190px)' : 'min(65vh, calc(100vh - 220px))'" resizable border>
        <el-table-column label="URL" min-width="280" show-overflow-tooltip resizable>
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
        <el-table-column prop="message" label="信息" min-width="220" show-overflow-tooltip  resizable />
        <el-table-column prop="costMs" label="耗时(ms)" min-width="90" align="center"  resizable />
        <el-table-column prop="createTime" label="时间" min-width="180" resizable>
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
      </div>
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
import { ref, computed, onMounted, onUnmounted, onActivated, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { confirm } from '@/utils/confirm'
import { useRoute, useRouter } from 'vue-router'
import { taskPage, taskLogs, taskCancel, taskPause, taskResume, taskDelete, spiderPage, taskConcurrency, updateTaskConcurrency, taskActiveCount } from '@/api'
import { Refresh, Search, VideoPause, VideoPlay, Document, Delete, Minus, FullScreen, ArrowDown } from '@element-plus/icons-vue'
import { formatDateTime } from '@/utils/dateTime'
import TableRowActions, { type TableRowAction } from '@/components/TableRowActions.vue'

const router = useRouter()
const route = useRoute()
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
const currentTimeMs = ref(Date.now())
const maxConcurrency = ref(1)
const savedMaxConcurrency = ref(1)
const urlConcurrency = ref(8)
const savedUrlConcurrency = ref(8)
const concurrencyLoading = ref(false)
const concurrencySaving = ref(false)
const concurrencyLoaded = ref(false)
const concurrencyDialogVisible = ref(false)
let timer: ReturnType<typeof setInterval> | null = null
let durationTimer: ReturnType<typeof setInterval> | null = null
let statusCheckTimer: ReturnType<typeof setInterval> | null = null
const remoteActive = ref(false)

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
    if (!await confirm(`确定删除选中的 ${selectedRows.value.length} 个任务吗？`, { title: '批量删除', danger: true })) return
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

const getRowActions = (row: any): TableRowAction[] => [
  ...(['RUNNING', 'PENDING'].includes(row.status)
    ? [{ command: 'cancel', label: '取消', icon: VideoPause, danger: true }]
    : []),
  ...(['RUNNING'].includes(row.status) && !row.pausedAt
    ? [{ command: 'pause', label: '暂停', icon: VideoPause }]
    : []),
  ...(['RUNNING'].includes(row.status) && row.pausedAt
    ? [{ command: 'resume', label: '恢复', icon: VideoPlay }]
    : []),
  { command: 'logs', label: '日志', icon: Document },
  ...(!['RUNNING', 'CANCELING'].includes(row.status)
    ? [{ command: 'delete', label: '删除', icon: Delete, danger: true }]
    : [])
]

const handleRowAction = (command: string, row: any) => {
  if (command === 'cancel') handleCancel(row)
  if (command === 'pause') handlePause(row)
  if (command === 'resume') handleResume(row)
  if (command === 'logs') showLogs(row)
  if (command === 'delete') handleDelete(row)
}

const goToSpiderData = (spiderId?: number) => {
  if (!spiderId) return
  router.push({ name: 'Search', query: { spiderId: String(spiderId) } })
}

const goToSpider = (spiderId?: number, spiderName?: string) => {
  if (!spiderId) return
  const query: Record<string, string> = { spiderId: String(spiderId) }
  if (spiderName) query.keyword = spiderName
  router.push({ name: 'Spider', query })
}

const handleSpiderJump = (cmd: string, row: any) => {
  if (cmd === 'data') goToSpiderData(row.spiderId)
  if (cmd === 'spider') goToSpider(row.spiderId, row.spiderName)
}

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
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    RUNNING: 'primary', SUCCESS: 'success', FAILED: 'danger',
    PENDING: 'warning', CANCELING: 'warning', CANCELED: 'info'
  }
  return map[status] || 'info'
}

const statusLabel = (status: string, row?: any) => {
  if (status === 'RUNNING' && row?.pausedAt) return '已暂停'
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
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
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

const taskDurationMs = (task: any) => {
  if (task.endTime || !task.startTime) return task.totalCostMs || 0

  const startTime = typeof task.startTime === 'string'
    ? task.startTime.replace(' ', 'T')
    : task.startTime
  const startMs = new Date(startTime).getTime()
  return Number.isFinite(startMs) ? Math.max(0, currentTimeMs.value - startMs) : task.totalCostMs || 0
}

const loadData = async () => {
  if (loading.value) return
  loading.value = true
  try {
    const params: any = { current: page.value, size: size.value, status: statusFilter.value }
    if (spiderFilter.value) params.spiderId = spiderFilter.value
    const res: any = await taskPage(params)
    const records = res.data?.records || []
    for (const t of records) {
      const prev = prevStatusMap.get(String(t.id))
      if (['PENDING', 'RUNNING', 'CANCELING'].includes(prev || '')
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
    // 请求结束后按最新状态重建刷新定时器：
    // 覆盖"刷新进行中状态激活（remoteActive 变 true）但定时器被跳过"的情况，
    // 以及间隔/开关变化后的定时器重建
    startTimer()
  }
}

const onFilterChange = () => {
  page.value = 1
  loadData()
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
    const max = Number(res.data?.maxConcurrency)
    const url = Number(res.data?.urlConcurrency)
    if (!Number.isInteger(max) || max < 1 || max > 20) {
      throw new Error('服务器返回的任务并发数无效')
    }
    maxConcurrency.value = max
    savedMaxConcurrency.value = max
    urlConcurrency.value = Number.isInteger(url) && url >= 1 && url <= 50 ? url : 8
    savedUrlConcurrency.value = urlConcurrency.value
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
  urlConcurrency.value = savedUrlConcurrency.value
  concurrencyDialogVisible.value = false
}

const saveConcurrency = async () => {
  if (!Number.isInteger(maxConcurrency.value) || maxConcurrency.value < 1 || maxConcurrency.value > 20) {
    ElMessage.warning('任务并发数需设置为 1 到 20 的整数')
    return
  }
  if (!Number.isInteger(urlConcurrency.value) || urlConcurrency.value < 1 || urlConcurrency.value > 50) {
    ElMessage.warning('URL 并发数需设置为 1 到 50 的整数')
    return
  }
  concurrencySaving.value = true
  try {
    await updateTaskConcurrency(maxConcurrency.value, urlConcurrency.value)
    savedMaxConcurrency.value = maxConcurrency.value
    savedUrlConcurrency.value = urlConcurrency.value
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
    if (!await confirm('取消后任务会尽快停止，已经完成的数据不会回滚。', { title: '取消任务', confirmText: '确认取消', cancelText: '继续运行', danger: true })) return
  } catch {
    return
  }

  try {
    await taskCancel(row.id)
    ElMessage.success('已提交取消请求')
  } catch {
    ElMessage.error('取消失败')
  }
  loadData()
}

const handlePause = async (row: any) => {
  try {
    await taskPause(row.id)
    ElMessage.success('已暂停，正在爬取的页面完成后会停止')
  } catch {
    ElMessage.error('暂停失败')
  }
  loadData()
}

const handleResume = async (row: any) => {
  try {
    await taskResume(row.id)
    ElMessage.success('已恢复，继续爬取剩余内容')
  } catch {
    ElMessage.error('恢复失败')
  }
  loadData()
}

const handleDelete = async (row: any) => {
  try {
    if (!await confirm('确定删除该任务及其日志吗？', { title: '删除确认', danger: true })) return
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

const hasActive = () => remoteActive.value || list.value.some((t: any) =>
  t.status === 'RUNNING' || t.status === 'PENDING' || t.status === 'CANCELING' ||
  (t.status === 'RUNNING' && t.pausedAt)
)

const autoRefreshing = computed(() => autoRefresh.value && hasActive())

const checkRemoteStatus = async () => {
  try {
    const res: any = await taskActiveCount()
    remoteActive.value = (res.data ?? 0) > 0
  } catch {
    // 查询失败时保留上一次的 remoteActive 状态
  }
  startTimer()
}

const startStatusCheck = () => {
  stopStatusCheck()
  statusCheckTimer = setInterval(checkRemoteStatus, 5000)
}

const stopStatusCheck = () => {
  if (statusCheckTimer) {
    clearInterval(statusCheckTimer)
    statusCheckTimer = null
  }
}

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

// 从爬虫页跳转过来时，自动按 spiderId 筛选
onActivated(() => {
  const spiderId = Number(route.query.spiderId)
  if (Number.isInteger(spiderId) && spiderId > 0) {
    spiderFilter.value = spiderId
    page.value = 1
    loadData()
  }
})

onMounted(() => {
  durationTimer = setInterval(() => {
    currentTimeMs.value = Date.now()
  }, 1000)
  const spiderId = Number(route.query.spiderId)
  if (Number.isInteger(spiderId) && spiderId > 0) {
    spiderFilter.value = spiderId
  }
  loadData()
  loadSpiders()
  startTimer()
  startStatusCheck()
})

onUnmounted(() => {
  stopTimer()
  stopStatusCheck()
  if (durationTimer) clearInterval(durationTimer)
})
</script>

<style scoped>

.log-dialog-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%; }
.log-dialog-title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
:deep(.log-fullscreen-dialog) { margin: 0 auto !important; height: 100vh; }
:deep(.log-fullscreen-dialog .el-dialog__body) { height: calc(100vh - 72px); overflow: auto; }
.concurrency-settings { min-height: 88px; }
.concurrency-setting-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.concurrency-setting-row > span { color: #5e6c84; font-weight: 600; }
.concurrency-hint { margin-top: 14px; color: #8993a4; font-size: 13px; line-height: 1.6; }
.refresh-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}
.auto-refresh-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c0c4cc;
  transition: background .3s ease, box-shadow .3s ease;
  flex-shrink: 0;
}
.auto-refresh-indicator.active {
  background: var(--teal);
  box-shadow: 0 0 0 3px rgba(15, 159, 154, .2);
  animation: pulse-dot 1.5s ease-in-out infinite;
}
@keyframes pulse-dot {
  0%, 100% { box-shadow: 0 0 0 3px rgba(15, 159, 154, .2); }
  50% { box-shadow: 0 0 0 6px rgba(15, 159, 154, .08); }
}
.refresh-toolbar :deep(.el-switch),
.refresh-toolbar :deep(.el-switch__label) {
  white-space: nowrap;
}
.refresh-interval-select { width: 90px; }
.spider-name-dropdown { padding: 0 4px; }
:deep(.concurrency-toolbar-item) { margin-left: auto !important; }

.task-status-tag { border: 0; font-weight: 600; }
.task-status-content { display: inline-flex; align-items: center; gap: 7px; }
.task-status-dot { width: 7px; height: 7px; flex: 0 0 7px; border-radius: 50%; background: currentColor; }
.task-status-running .task-status-dot { box-shadow: 0 0 0 3px rgb(64 158 255 / 16%); }
.task-status-running .task-status-dot[data-paused] { background: #e6a23c; animation: paused-pulse 1.6s ease-in-out infinite; }
@keyframes paused-pulse {
  0%, 100% { box-shadow: 0 0 0 3px rgb(230 162 60 / 30%); }
  50% { box-shadow: 0 0 0 6px rgb(230 162 60 / 12%); }
}
.task-status-success .task-status-dot { box-shadow: 0 0 0 3px rgb(103 194 58 / 16%); }
.task-status-failed .task-status-dot { box-shadow: 0 0 0 3px rgb(245 108 108 / 16%); }

.log-filter {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}
.log-filter-input {
  flex: 1 1 200px;
  min-width: 140px;
  max-width: 280px;
}
.log-filter-select {
  flex: 0 1 120px;
  min-width: 90px;
}
@media (max-width: 767px) {
  .log-filter-input {
    flex: 1 1 100%;
    max-width: none;
  }
  .log-filter-select {
    flex: 1 1 45%;
    min-width: 0;
  }
}

.log-url {
  color: var(--teal);
  text-decoration: none;
  word-break: break-all;
}

.log-url:hover {
  color: var(--teal-dark);
  text-decoration: underline;
}

@media (max-width: 767px) {
  .task-toolbar { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
  .task-toolbar :deep(.el-form-item) { width: 100%; margin: 0; }
  .task-toolbar :deep(.el-form-item__content) { width: 100%; min-width: 0; }
  .task-filter-item :deep(.el-select) { width: 100% !important; }
  .task-query-actions,
  .task-refresh-action { grid-column: 1 / -1; }
  .task-query-actions :deep(.el-form-item__content) { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
  .task-query-actions :deep(.el-button + .el-button) { margin-left: 0; }
  .task-query-actions :deep(.el-button),
  .task-batch-action :deep(.el-button),
  .task-concurrency-action :deep(.el-button) { width: 100%; margin-left: 0; }
  .task-refresh-action :deep(.el-form-item__content) { justify-content: space-between; }
  .refresh-toolbar { width: 100%; justify-content: space-between; gap: 8px; }
}
</style>
