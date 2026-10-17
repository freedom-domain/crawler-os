<template>
  <div class="history-sync-panel">
    <el-dialog
      v-model="concurrencyDialogVisible"
      title="历史行情 API 与并发设置"
      width="min(520px, 94vw)"
      :close-on-click-modal="!concurrencySaving"
      :close-on-press-escape="!concurrencySaving"
    >
      <div v-loading="concurrencyLoading">
        <div class="concurrency-setting">
          <span>历史行情任务并发数</span>
          <el-input-number
            v-model="concurrency"
            :min="1"
            :max="20"
            :step="1"
            controls-position="right"
            :disabled="concurrencyLoading || concurrencySaving"
            aria-label="历史行情任务并发数"
          />
        </div>
        <div class="concurrency-setting">
          <span>请求间隔（毫秒）</span>
          <el-input-number
            v-model="requestIntervalMillis"
            :min="0"
            :max="60000"
            :step="100"
            controls-position="right"
            :disabled="concurrencyLoading || concurrencySaving"
            aria-label="历史行情请求间隔毫秒"
          />
        </div>
        <p class="concurrency-hint">
          请求开始之间至少间隔此时长；0 表示不限制。并发数控制同时执行的任务和请求数量，间隔与并发数共同生效。默认 1000 毫秒。
        </p>
        <div class="concurrency-setting api-provider-setting">
          <span>启用的历史行情 API</span>
          <el-select
            v-model="providers"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="至少选择一个 API"
            :disabled="concurrencyLoading || concurrencySaving"
            aria-label="启用的历史行情 API"
          >
            <el-option v-for="option in providerOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </div>
      </div>
      <template #footer>
        <el-button :disabled="concurrencySaving" @click="concurrencyDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="concurrencySaving"
          :disabled="concurrencyLoading || (
            concurrency === savedConcurrency
            && sameProviders(providers, savedProviders)
            && requestIntervalMillis === savedRequestIntervalMillis
          )"
          @click="saveConcurrency"
        >保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="dialogVisible"
      :title="instrumentType === 'FUND' ? '基金历史行情同步记录' : '股票历史行情同步记录'"
      width="min(1000px, 96vw)"
      destroy-on-close
      @closed="stopPolling"
    >
      <template v-if="task">
        <div class="task-summary">
          <div class="task-status">
            <el-tag :type="taskStatusType(task.status)">{{ taskStatusLabel(task.status) }}</el-tag>
            <span>任务 #{{ task.id }} · 创建于 {{ dateTime(task.createdAt) }}</span>
          </div>
          <el-progress :percentage="progress" :status="task.status === 'FAILED' ? 'exception' : undefined" />
          <div class="task-counts">
            <span>总计 {{ task.totalCount }} 项</span>
            <span>已完成 {{ task.finishedCount }} 项</span>
            <span class="success-count">成功 {{ task.successCount }} 项</span>
            <span class="failed-count">失败 {{ task.failedCount }} 项</span>
            <span>已取消 {{ task.cancelledCount }} 项</span>
          </div>
          <p v-if="task.message" class="task-message">{{ task.message }}</p>
        </div>

        <el-table :data="task.items" stripe border max-height="52vh" v-loading="loadingTask">
          <el-table-column label="类型" width="85">
            <template #default="{ row }">{{ row.fundType === 'ONSHORE' ? '场内基金' : row.fundType === 'OFFSHORE' ? '场外基金' : '股票' }}</template>
          </el-table-column>
          <el-table-column prop="code" label="代码" width="100" />
          <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
          <el-table-column label="同步状态" width="100">
            <template #default="{ row }">
              <el-tag :type="taskStatusType(row.status)" size="small">{{ taskStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="数据点数" width="100" align="right">
            <template #default="{ row }">{{ row.pointCount ?? '—' }}</template>
          </el-table-column>
          <el-table-column prop="message" label="同步结果 / 错误" min-width="230" show-overflow-tooltip>
            <template #default="{ row }">{{ row.message || '等待处理' }}</template>
          </el-table-column>
          <el-table-column label="开始时间" min-width="155">
            <template #default="{ row }">{{ dateTime(row.startedAt) }}</template>
          </el-table-column>
          <el-table-column label="完成时间" min-width="155">
            <template #default="{ row }">{{ dateTime(row.finishedAt) }}</template>
          </el-table-column>
          <template #empty><el-empty description="该目录当前没有可同步的标的" /></template>
        </el-table>
        <el-pagination
          v-if="task.itemTotal > 0"
          class="task-pagination"
          v-model:current-page="itemPage"
          v-model:page-size="itemPageSize"
          :total="task.itemTotal"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadCurrentTask"
          @size-change="onPageSizeChange"
        />
      </template>
      <el-empty v-else description="尚无同步任务" />
      <template #footer>
        <el-button
          v-if="task && isActive(task.status)"
          type="danger"
          plain
          :loading="cancelling"
          :disabled="task.status === 'CANCELLING'"
          @click="cancelTask"
        >{{ task.status === 'CANCELLING' ? '正在取消' : '取消任务' }}</el-button>
        <el-button @click="dialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirm } from '@/utils/confirm'
import {
  historySyncConcurrencyGet,
  historySyncConcurrencyUpdate,
  historySyncTaskCancel,
  historySyncTaskGet,
  historySyncTaskLatest,
  historySyncTaskStart
} from '@/api'

type InstrumentType = 'FUND' | 'STOCK'
interface SyncTask {
  id: number
  instrumentType: InstrumentType
  status: string
  totalCount: number
  finishedCount: number
  successCount: number
  failedCount: number
  cancelledCount: number
  message?: string | null
  createdAt: string
  itemTotal: number
  items: Array<{
    id: number
    fundType: string | null
    code: string
    name: string
    status: string
    message: string | null
    pointCount: number | null
    startedAt: string | null
    finishedAt: string | null
  }>
}

const props = defineProps<{ instrumentType: InstrumentType }>()
const concurrencyDialogVisible = ref(false)
const dialogVisible = ref(false)
const starting = ref(false)
const loadingLatest = ref(false)
const loadingTask = ref(false)
const concurrency = ref(1)
const savedConcurrency = ref(1)
const requestIntervalMillis = ref(1000)
const savedRequestIntervalMillis = ref(1000)
const providers = ref<string[]>(['TENCENT'])
const savedProviders = ref<string[]>(['TENCENT'])
const providerOptions = [
  { label: '腾讯财经', value: 'TENCENT' },
  { label: '东方财富', value: 'EASTMONEY' }
]
const concurrencyLoading = ref(false)
const concurrencySaving = ref(false)
const task = ref<SyncTask | null>(null)
const itemPage = ref(1)
const itemPageSize = ref(10)
let pollingTimer: ReturnType<typeof setInterval> | undefined
let polling = false

const progress = computed(() => task.value?.totalCount
  ? Math.round(task.value.finishedCount / task.value.totalCount * 100)
  : 100)

const responseData = (response: any): SyncTask | null => response.data || null
const concurrencyResponseData = (response: any): {
  concurrency: number
  providers: string[]
  requestIntervalMillis: number
} => response.data
const sameProviders = (left: string[], right: string[]) =>
  left.length === right.length && left.every(provider => right.includes(provider))
const dateTime = (value: string | null | undefined) => value ? value.replace('T', ' ').slice(0, 19) : '—'
const taskStatusLabel = (status: string) => ({
  PENDING: '等待中',
  RUNNING: '同步中',
  CANCELLING: '正在取消',
  SUCCESS: '成功',
  COMPLETED: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消'
}[status] || status)
const taskStatusType = (status: string) => ({
  PENDING: 'info',
  RUNNING: 'warning',
  CANCELLING: 'warning',
  SUCCESS: 'success',
  COMPLETED: 'success',
  FAILED: 'danger',
  CANCELLED: 'info'
}[status] || 'info')
const isActive = (status?: string) => status === 'PENDING' || status === 'RUNNING' || status === 'CANCELLING'

const loadConcurrency = async () => {
  concurrencyLoading.value = true
  try {
    const response: any = await historySyncConcurrencyGet()
    const settings = concurrencyResponseData(response)
    concurrency.value = settings.concurrency
    savedConcurrency.value = concurrency.value
    requestIntervalMillis.value = settings.requestIntervalMillis
    savedRequestIntervalMillis.value = requestIntervalMillis.value
    providers.value = [...settings.providers]
    savedProviders.value = [...settings.providers]
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '读取历史行情并发配置失败')
  } finally {
    concurrencyLoading.value = false
  }
}

const openConcurrencySettings = () => {
  concurrencyDialogVisible.value = true
  void loadConcurrency()
}

const saveConcurrency = async () => {
  if (!Number.isInteger(concurrency.value) || concurrency.value < 1 || concurrency.value > 20) {
    ElMessage.warning('历史行情并发数量需为 1 到 20 的整数')
    return
  }
  if (providers.value.length === 0) {
    ElMessage.warning('至少启用一个历史行情 API')
    return
  }
  if (!Number.isInteger(requestIntervalMillis.value)
      || requestIntervalMillis.value < 0 || requestIntervalMillis.value > 60000) {
    ElMessage.warning('历史行情请求间隔需为 0 到 60000 毫秒的整数')
    return
  }
  concurrencySaving.value = true
  try {
    const response: any = await historySyncConcurrencyUpdate(
      concurrency.value, providers.value, requestIntervalMillis.value
    )
    const settings = concurrencyResponseData(response)
    concurrency.value = settings.concurrency
    savedConcurrency.value = concurrency.value
    requestIntervalMillis.value = settings.requestIntervalMillis
    savedRequestIntervalMillis.value = requestIntervalMillis.value
    providers.value = [...settings.providers]
    savedProviders.value = [...settings.providers]
    ElMessage.success('历史行情 API 与并发配置已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存历史行情并发配置失败')
  } finally {
    concurrencySaving.value = false
  }
}

const stopPolling = () => {
  if (pollingTimer) clearInterval(pollingTimer)
  pollingTimer = undefined
  polling = false
}

const loadTask = async (id: number, showError = true) => {
  if (loadingTask.value) return
  loadingTask.value = true
  try {
    const response: any = await historySyncTaskGet(id, itemPage.value, itemPageSize.value)
    task.value = responseData(response)
    if (!isActive(task.value?.status)) stopPolling()
  } catch (error) {
    if (showError) ElMessage.error(error instanceof Error ? error.message : '读取同步任务失败')
  } finally {
    loadingTask.value = false
  }
}

const beginPolling = (id: number) => {
  stopPolling()
  pollingTimer = setInterval(async () => {
    if (polling || !dialogVisible.value) return
    polling = true
    await loadTask(id, false)
    polling = false
  }, 3000)
}

const startTask = async () => {
  const name = props.instrumentType === 'FUND' ? '基金' : '股票'
  if (!await confirm(`将从数据源同步目录中全部${name}可提供的完整历史行情，任务可能持续较长时间。确认开始？`, {
    title: '同步全部历史行情',
    confirmText: '开始同步'
  })) return

  starting.value = true
  itemPage.value = 1
  try {
    const response: any = await historySyncTaskStart(props.instrumentType)
    task.value = responseData(response)
    dialogVisible.value = true
    ElMessage.success('全量历史行情同步任务已创建')
    if (task.value && isActive(task.value.status)) beginPolling(task.value.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建历史行情同步任务失败')
  } finally {
    starting.value = false
  }
}

const openLatest = async () => {
  loadingLatest.value = true
  itemPage.value = 1
  try {
    const response: any = await historySyncTaskLatest(props.instrumentType, itemPage.value, itemPageSize.value)
    task.value = responseData(response)
    dialogVisible.value = true
    if (task.value && isActive(task.value.status)) beginPolling(task.value.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '读取同步记录失败')
  } finally {
    loadingLatest.value = false
  }
}

const loadCurrentTask = () => {
  if (task.value) void loadTask(task.value.id)
}
const onPageSizeChange = () => {
  itemPage.value = 1
  loadCurrentTask()
}

const cancelling = ref(false)
const cancelTask = async () => {
  if (!task.value || !isActive(task.value.status) || task.value.status === 'CANCELLING') return
  if (!await confirm('取消后将在当前标的同步完成后停止，尚未开始的标的将标记为已取消；已写入的数据不会回滚。确认取消？', {
    title: '取消历史行情同步',
    confirmText: '确认取消',
    cancelText: '继续同步',
    danger: true
  })) return

  cancelling.value = true
  try {
    const response: any = await historySyncTaskCancel(task.value.id, itemPage.value, itemPageSize.value)
    task.value = responseData(response)
    ElMessage.success(task.value?.status === 'CANCELLED' ? '同步任务已取消' : '已请求取消，将在当前标的完成后停止')
    if (task.value && isActive(task.value.status)) beginPolling(task.value.id)
    else stopPolling()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '取消历史行情同步任务失败')
  } finally {
    cancelling.value = false
  }
}

onBeforeUnmount(stopPolling)

defineExpose({
  startTask,
  openLatest,
  openConcurrencySettings
})
</script>

<style scoped>
.concurrency-setting {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.concurrency-setting > span {
  color: var(--el-text-color-regular);
  font-weight: 600;
}
.concurrency-hint {
  margin: 14px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.task-summary {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}
.task-status,
.task-counts {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
  color: #68778a;
  font-size: 13px;
}
.success-count {
  color: #16845b;
}
.failed-count {
  color: #c45656;
}
.task-message {
  margin: 0;
  color: #c45656;
  font-size: 13px;
}
.task-pagination {
  justify-content: flex-end;
  margin-top: 14px;
}
@media (max-width: 650px) {
  .history-sync-panel {
    flex-wrap: wrap;
  }
}
</style>
