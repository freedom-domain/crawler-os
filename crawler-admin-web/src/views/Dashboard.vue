<template>
  <div class="dashboard-page">
    <section class="dashboard-hero">
      <div class="hero-copy">
        <p class="eyebrow">CRAWLEROS // COMMAND CENTER<span class="eyebrow-caret"></span></p>
        <h1>{{ greeting }}，{{ displayName }}</h1>
        <p class="intro-copy">任务进度 · 内容资产 · 全站模块，一屏掌控。</p>
        <div class="hero-meta">
          <span class="hero-status" :class="{ 'hero-status-bad': !syncOk }" :title="syncOk ? '' : '点击重试'" @click="!syncOk && refreshDashboard()"><span class="status-pulse"></span>{{ syncOk ? '系统运行正常' : '数据连接异常' }} <b>·</b> {{ syncOk ? '实时同步中' : '点击重试' }}</span>
          <span v-if="activeCount > 0" class="hero-active"><el-icon :size="13"><Loading /></el-icon>{{ activeCount }} 个任务进行中</span>
          <span class="hero-date">{{ todayLabel }}</span>
        </div>
      </div>
      <div class="hero-radar" aria-hidden="true">
        <div class="radar-ring radar-ring-1"></div>
        <div class="radar-ring radar-ring-2"></div>
        <div class="radar-ring radar-ring-3"></div>
        <div class="radar-cross"></div>
        <div class="radar-sweep"></div>
        <span class="radar-blip blip-1"></span>
        <span class="radar-blip blip-2"></span>
        <span class="radar-blip blip-3"></span>
        <div class="radar-orbit"><span class="radar-satellite"></span></div>
        <div class="radar-core">
          <span class="radar-clock">{{ clock }}</span>
          <span class="radar-clock-label">SYS TIME</span>
        </div>
      </div>
    </section>

    <section class="metric-grid">
      <article
        v-for="(stat, index) in statCards"
        :key="stat.label"
        v-loading="statLoading(index)"
        element-loading-text="加载中..."
        class="metric-card"
        :class="[`metric-${stat.tone}`, { 'metric-clickable': stat.route }]"
        :style="{ '--enter-delay': `${140 + index * 70}ms` }"
        :role="stat.route ? 'button' : undefined"
        :tabindex="stat.route ? 0 : undefined"
        :aria-label="stat.route ? `${stat.label}：${stat.value}，回车查看` : undefined"
        @click="stat.route && router.push({ name: stat.route })"
        @keydown.enter.prevent="stat.route && router.push({ name: stat.route })"
        @keydown.space.prevent="stat.route && router.push({ name: stat.route })"
      >
        <div class="metric-top">
          <span class="metric-icon"><el-icon :size="18"><component :is="stat.icon" /></el-icon></span>
          <span class="metric-index">0{{ index + 1 }}</span>
        </div>
        <div class="stat-label">{{ stat.label }}</div>
        <div class="stat-value">{{ stat.value }}</div>
        <div class="metric-foot">
          <div class="metric-rule"><span :style="{ width: stat.progress }"></span></div>
          <span v-if="stat.route" class="metric-link">查看 →</span>
        </div>
      </article>
    </section>

    <section class="fund-watchlist-panel panel">
      <div class="panel-heading">
        <div><p class="panel-kicker">FUND WATCHLIST</p><h2>关注基金</h2></div>
        <div class="panel-actions">
          <span class="fund-source-label">最新缓存净值 · 数据库历史</span>
          <el-button circle size="small" class="refresh-btn" :loading="fundListLoading" aria-label="刷新关注基金" @click="loadFavoriteFunds">
            <el-icon v-if="!fundListLoading"><Refresh /></el-icon>
          </el-button>
          <el-button link type="primary" size="small" class="panel-link-btn" @click="router.push({ name: 'FundManagement' })">基金管理 →</el-button>
        </div>
      </div>

      <div v-if="fundListError" class="fund-watchlist-message fund-watchlist-error" role="alert">
        <span>{{ fundListError }}</span>
        <el-button link type="primary" @click="loadFavoriteFunds">重试</el-button>
      </div>
      <div v-else-if="!favoriteFunds.length" class="fund-watchlist-message">
        <span>{{ fundListLoading ? '加载关注基金...' : '尚未关注基金。' }}</span>
        <el-button v-if="!fundListLoading" link type="primary" @click="router.push({ name: 'FundManagement' })">前往基金管理</el-button>
      </div>
      <div v-else class="fund-watchlist-grid" v-loading="fundListLoading" element-loading-text="加载关注基金...">
        <article v-for="fund in favoriteFunds" :key="`${fund.fundType}-${fund.code}`" class="fund-watch-card">
          <div class="fund-watch-heading">
            <div class="fund-watch-name" :title="fund.name">{{ fund.name || fund.code }}</div>
            <el-tag size="small" effect="plain">{{ fund.fundType === 'ONSHORE' ? '场内' : '场外' }} · {{ frequencyLabel(fund.frequency) }}</el-tag>
          </div>
          <div class="fund-watch-quote">
            <div class="fund-watch-quote-meta">
              <span class="fund-watch-code">{{ fund.code }} · {{ fund.fundType === 'ONSHORE' ? '最新收盘价' : '最新单位净值' }}</span>
              <span class="fund-watch-date">净值日期 {{ fund.latestDate || '暂无' }}</span>
            </div>
            <strong v-if="fund.latestValue != null">¥{{ formatFundValue(fund.latestValue) }}</strong>
            <strong v-else class="fund-watch-no-value">暂无净值</strong>
            <span
              v-if="fund.latestChangePercent != null"
              class="fund-watch-change"
              :class="fund.latestChangePercent > 0 ? 'is-positive' : fund.latestChangePercent < 0 ? 'is-negative' : ''"
            >{{ formatFundChange(fund.latestChangePercent) }}</span>
          </div>
          <div v-if="fund.historyLoading" class="fund-watch-chart fund-watch-chart-loading">读取数据库历史行情...</div>
          <div v-else-if="fund.historyError" class="fund-watch-chart fund-watch-chart-error" role="alert">{{ fund.historyError }}</div>
          <svg
            v-else-if="fund.points.length"
            class="fund-watch-chart"
            viewBox="0 0 1000 110"
            preserveAspectRatio="none"
            role="img"
            :aria-label="`${fund.name}从${fund.points[0].date}至${fund.points[fund.points.length - 1].date}的历史净值走势`"
          >
            <line x1="0" y1="12" x2="1000" y2="12" class="fund-chart-grid" />
            <line x1="0" y1="55" x2="1000" y2="55" class="fund-chart-grid" />
            <line x1="0" y1="98" x2="1000" y2="98" class="fund-chart-grid" />
            <polyline :points="fundTrendPoints(fund)" class="fund-chart-line" />
            <circle :cx="fundLastCoordinate(fund)?.x" :cy="fundLastCoordinate(fund)?.y" r="5" class="fund-chart-endpoint" />
          </svg>
          <div v-else class="fund-watch-chart fund-watch-chart-error">数据库暂无可绘制的历史行情</div>
          <div class="fund-watch-card-footer">
          <div v-if="fund.points.length" class="fund-watch-dates">
            <span>{{ fund.points[0].date }}</span>
            <span>{{ fund.points[fund.points.length - 1].date }}</span>
          </div>
          <div v-else-if="fund.latestDate" class="fund-watch-dates"><span>最新缓存日期 {{ fund.latestDate }}</span></div>
          <el-button
            link
            type="primary"
            size="small"
            :loading="fund.refreshing"
            :disabled="fund.refreshing || fund.historyLoading"
            @click="refreshFavoriteFund(fund)"
          >立即更新</el-button>
          </div>
        </article>
      </div>
    </section>

    <section class="dashboard-grid">
      <div v-loading="recentTaskLoading" element-loading-text="加载中..." class="panel task-panel">
        <div class="panel-heading">
          <div><p class="panel-kicker">ACTIVITY STREAM</p><h2>最近任务</h2></div>
          <div class="panel-actions">
            <span v-if="stats.taskCount > 0" class="panel-success-chip">今日成功 <b>{{ todaySuccess }}</b></span>
            <span v-if="lastUpdated" class="panel-updated">{{ lastUpdated }} 更新</span>
            <el-tooltip content="刷新" placement="top">
              <el-button circle size="small" class="refresh-btn" :loading="recentTaskLoading" @click="refreshDashboard">
                <el-icon v-if="!recentTaskLoading"><Refresh /></el-icon>
              </el-button>
            </el-tooltip>
            <el-button link type="primary" size="small" class="panel-link-btn" @click="goToTaskPage">查看全部 →</el-button>
          </div>
        </div>
        <div class="table-scroll-wrapper">
        <el-table :data="recentTasks" size="small" resizable border class="recent-tasks-table" @row-click="onTaskRowClick">
          <el-table-column prop="spiderName" label="爬虫名称" min-width="120" resizable show-overflow-tooltip />
          <el-table-column prop="status" label="状态" min-width="90" resizable>
            <template #default="{ row }">
              <span class="status-cell" :class="`status-${statusTag(row.status)}`">
                <span class="status-dot" :class="{ 'status-dot-live': row.status === 'RUNNING' }"></span>{{ statusLabel(row.status) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="startTime" label="开始时间" min-width="150" resizable class-name="col-start-time">
            <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
          </el-table-column>
          <el-table-column prop="endTime" label="结束时间" min-width="150" resizable class-name="col-end-time">
            <template #default="{ row }">{{ formatDateTime(row.endTime) }}</template>
          </el-table-column>
          <el-table-column label="总耗时" min-width="90" align="center" resizable>
            <template #default="{ row }">{{ formatDuration(row.totalCostMs) }}</template>
          </el-table-column>
          <el-table-column label="HTML" align="center" resizable class-name="col-html">
            <el-table-column prop="htmlSuccessCount" label="成功" min-width="55" align="center" resizable>
              <template #default="{ row }"><span class="count-ok">{{ row.htmlSuccessCount ?? 0 }}</span></template>
            </el-table-column>
            <el-table-column prop="htmlFailCount" label="失败" min-width="55" align="center" resizable>
              <template #default="{ row }"><span :class="row.htmlFailCount > 0 ? 'count-bad' : 'count-zero'">{{ row.htmlFailCount ?? 0 }}</span></template>
            </el-table-column>
            <el-table-column prop="htmlExistingCount" label="已存在" min-width="65" align="center" resizable>
              <template #default="{ row }"><span class="count-muted">{{ row.htmlExistingCount ?? 0 }}</span></template>
            </el-table-column>
          </el-table-column>
          <el-table-column label="图片" align="center" resizable class-name="col-image">
            <el-table-column prop="imageSuccessCount" label="成功" min-width="55" align="center" resizable>
              <template #default="{ row }"><span class="count-ok">{{ row.imageSuccessCount ?? 0 }}</span></template>
            </el-table-column>
            <el-table-column prop="imageFailCount" label="失败" min-width="55" align="center" resizable>
              <template #default="{ row }"><span :class="row.imageFailCount > 0 ? 'count-bad' : 'count-zero'">{{ row.imageFailCount ?? 0 }}</span></template>
            </el-table-column>
            <el-table-column prop="imageExistingCount" label="已存在" min-width="65" align="center" resizable>
              <template #default="{ row }"><span class="count-muted">{{ row.imageExistingCount ?? 0 }}</span></template>
            </el-table-column>
          </el-table-column>
          <el-table-column label="操作" width="56" align="center" resizable class-name="col-action">
            <template #default="{ row }">
              <el-button link size="small" class="col-action-view" @click.stop="onTaskRowClick(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
        </div>
        <div v-if="!recentTasks.length && !recentTaskLoading" class="panel-empty">
          <el-icon :size="34" class="empty-icon"><DocumentRemove /></el-icon>
          <p>暂无最近任务</p>
          <el-button type="primary" size="small" plain class="empty-btn" @click="router.push({ name: 'Spider' })">去启动一个爬虫</el-button>
        </div>
        <div v-if="recentTasks.length" class="output-block">
          <div class="output-head">
            <span class="footer-tag">OUTPUT ANALYSIS</span>
            <span class="output-scope">最近 {{ recentTasks.length }} 个任务产出</span>
          </div>
          <template v-if="hasOutput">
            <div v-for="bar in outputBars" :key="bar.label" class="output-row">
              <span class="output-label">{{ bar.label }}</span>
              <div class="output-bar">
                <span
                  v-for="seg in bar.segments"
                  :key="seg.key"
                  class="output-seg"
                  :class="`seg-${seg.key}`"
                  :style="{ flexGrow: seg.value }"
                  :title="`${seg.name} ${seg.value.toLocaleString('en-US')}`"
                ></span>
              </div>
              <span class="output-total">{{ bar.total.toLocaleString('en-US') }}</span>
            </div>
          </template>
          <p v-else class="output-empty">暂无产出</p>
          <div class="output-legend">
            <span><i class="seg-ok"></i>成功</span>
            <span><i class="seg-bad"></i>失败</span>
            <span><i class="seg-muted"></i>已存在</span>
          </div>
        </div>

      </div>
    </section>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onActivated, onDeactivated, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  spiderPage, taskStats, taskPage, taskActiveCount, searchContent,
  fundManagementList, fundHistory, offExchangeFundHistory, fundHistoryRefresh
} from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import {
  Connection, List, CircleCheck, DataBoard,
  Refresh, Loading, DocumentRemove
} from '@element-plus/icons-vue'
import { formatDateTime } from '@/utils/dateTime'

const router = useRouter()
const userStore = useUserStore()
const goToTaskPage = () => router.push({ name: 'Task' })

const displayName = computed(() => userStore.nickname || userStore.username || '管理员')
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
const todayLabel = computed(() => {
  const now = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][now.getDay()]
  return `${now.getFullYear()} 年 ${now.getMonth() + 1} 月 ${now.getDate()} 日 · 星期${week}`
})

// 任务时钟：雷达中心实时显示系统时间
const clock = ref('--:--:--')
let clockTimer: ReturnType<typeof setInterval> | null = null
const tickClock = () => {
  const now = new Date()
  clock.value = [now.getHours(), now.getMinutes(), now.getSeconds()]
    .map(part => String(part).padStart(2, '0')).join(':')
}
const startClock = () => {
  stopClock()
  tickClock()
  clockTimer = setInterval(tickClock, 1000)
}
const stopClock = () => {
  if (clockTimer) { clearInterval(clockTimer); clockTimer = null }
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

const stats = ref({ spiderCount: 0, taskCount: 0, successRate: 0, dataCount: 0 })
const spiderErr = ref(false)
const taskErr = ref(false)
const recentErr = ref(false)
const dataErr = ref(false)
// 四个数据源全部失败才判定连接异常，避免单接口抖动误报
const syncOk = computed(() => !(spiderErr.value && taskErr.value && recentErr.value && dataErr.value))
const todaySuccess = ref(0)
const activeCount = ref(0)
const lastUpdated = ref('')
const recentTasks = ref<any[]>([])
const spiderLoading = ref(false)
const taskLoading = ref(false)
const recentTaskLoading = ref(false)
const dataLoading = ref(false)

interface FundTrendPoint {
  date: string
  value: number
  changePercent: number | null
}

interface DashboardFund {
  fundType: 'ONSHORE' | 'OFFSHORE'
  code: string
  frequency: string
  name: string
  cachedPoints: number
  latestDate: string | null
  latestValue: number | null
  latestChangePercent: number | null
  points: FundTrendPoint[]
  historyLoading: boolean
  historyError: string
  refreshing: boolean
}

const favoriteFunds = ref<DashboardFund[]>([])
const fundListLoading = ref(false)
const fundListError = ref('')
const frequencyRank: Record<string, number> = { day: 0, week: 1, month: 2 }

const selectPreferredFrequencies = (items: any[]) => {
  const grouped = new Map<string, any[]>()
  items.forEach(item => {
    if (item.fundType !== 'ONSHORE' && item.fundType !== 'OFFSHORE') return
    const key = `${item.fundType}-${item.code}`
    grouped.set(key, [...(grouped.get(key) || []), item])
  })

  return [...grouped.values()].flatMap(fundItems => {
    const watched = fundItems.filter(item => item.watched === true || item.watched === 1)
    if (!watched.length) return []
    const watchedWithHistory = watched.filter(item => Number(item.cachedPoints) > 0)
    const cached = fundItems.filter(item => Number(item.cachedPoints) > 0)
    const candidates = watchedWithHistory.length ? watchedWithHistory : cached.length ? cached : watched
    const item = [...candidates].sort((left, right) =>
      (frequencyRank[String(left.frequency).toLowerCase()] ?? 3)
      - (frequencyRank[String(right.frequency).toLowerCase()] ?? 3)
    )[0]
    return [{
      fundType: item.fundType,
      code: String(item.code),
      frequency: String(item.frequency || 'day').toLowerCase(),
      name: String(item.name || item.code),
      cachedPoints: Number(item.cachedPoints) || 0,
      latestDate: item.latestDate || null,
      latestValue: null,
      latestChangePercent: null,
      points: [],
      historyLoading: false,
      historyError: '',
      refreshing: false
    } satisfies DashboardFund]
  })
}

const oneYearBefore = (date: string) => {
  const [year, month, day] = date.split('-').map(Number)
  const start = new Date(Date.UTC(year, month - 1, day))
  start.setUTCDate(start.getUTCDate() - 365)
  return start.toISOString().slice(0, 10)
}

const loadFundHistory = async (fund: DashboardFund) => {
  if (!fund.cachedPoints || !fund.latestDate) {
    fund.historyError = '数据库暂无已缓存的历史行情'
    return
  }
  fund.historyLoading = true
  fund.historyError = ''
  try {
    const params = {
      code: fund.code,
      startDate: oneYearBefore(fund.latestDate),
      endDate: fund.latestDate,
      source: 'database' as const
    }
    const response: any = fund.fundType === 'ONSHORE'
      ? await fundHistory({ ...params, frequency: fund.frequency })
      : await offExchangeFundHistory(params)
    const rawPoints = response?.data?.points
    if (!Array.isArray(rawPoints)) throw new Error('历史行情数据格式无效')
    const points = rawPoints
      .map((point: any): FundTrendPoint => {
        const rawValue = fund.fundType === 'ONSHORE' ? point.close : point.unitNav
        return {
          date: String(point.date || ''),
          value: rawValue == null ? Number.NaN : Number(rawValue),
          changePercent: point.changePercent == null ? null : Number(point.changePercent)
        }
      })
      .filter((point: FundTrendPoint) => point.date && Number.isFinite(point.value))
    if (!points.length) throw new Error('数据库暂无可绘制的历史行情')
    fund.points = points
    const latestPoint = points[points.length - 1]
    fund.latestDate = latestPoint.date
    fund.latestValue = latestPoint.value
    fund.latestChangePercent = latestPoint.changePercent
  } catch (error) {
    console.error(error)
    fund.historyError = error instanceof Error ? error.message : '读取数据库历史行情失败'
  } finally {
    fund.historyLoading = false
  }
}

const loadFavoriteFunds = async () => {
  fundListLoading.value = true
  fundListError.value = ''
  try {
    const response: any = await fundManagementList()
    if (!Array.isArray(response?.data)) throw new Error('关注基金列表数据格式无效')
    const funds = selectPreferredFrequencies(response.data)
    favoriteFunds.value = funds
    await Promise.all(funds.map(loadFundHistory))
  } catch (error) {
    console.error(error)
    fundListError.value = error instanceof Error ? error.message : '读取关注基金失败'
  } finally {
    fundListLoading.value = false
  }
}

const refreshFavoriteFund = async (fund: DashboardFund) => {
  if (fund.refreshing) return
  fund.refreshing = true
  fund.historyError = ''
  try {
    await fundHistoryRefresh({
      fundType: fund.fundType,
      code: fund.code,
      frequency: fund.frequency
    })
    const response: any = await fundManagementList()
    if (!Array.isArray(response?.data)) throw new Error('关注基金列表数据格式无效')
    const updated = response.data.find((item: any) =>
      item.fundType === fund.fundType
      && String(item.code) === fund.code
      && String(item.frequency).toLowerCase() === fund.frequency
      && (item.watched === true || item.watched === 1)
    )
    if (!updated) throw new Error('基金已不在关注清单中，请刷新首页')
    fund.cachedPoints = Number(updated.cachedPoints) || 0
    fund.latestDate = updated.latestDate || null
    await loadFundHistory(fund)
    if (fund.historyError) throw new Error(fund.historyError)
    ElMessage.success(`${fund.name} 行情已更新`)
  } catch (error) {
    console.error(error)
    fund.historyError = error instanceof Error ? error.message : '更新基金行情失败'
    ElMessage.error(fund.historyError)
  } finally {
    fund.refreshing = false
  }
}

const frequencyLabel = (frequency: string) => ({
  day: '日线',
  week: '周线',
  month: '月线'
}[frequency] || frequency)

const formatFundValue = (value: number) => value.toLocaleString('zh-CN', {
  minimumFractionDigits: 4,
  maximumFractionDigits: 4
})

const formatFundChange = (value: number) => `${value > 0 ? '+' : ''}${value.toFixed(2)}%`

const fundChartCoordinates = (fund: DashboardFund) => {
  const values = fund.points.map(point => point.value)
  if (!values.length) return []
  const min = Math.min(...values)
  const max = Math.max(...values)
  const padding = max === min ? Math.max(Math.abs(max) * 0.03, 0.01) : (max - min) * 0.12
  const rangeMin = min - padding
  const range = max - min + padding * 2
  return values.map((value, index) => ({
    x: values.length === 1 ? 500 : index / (values.length - 1) * 1000,
    y: 98 - (value - rangeMin) / range * 86
  }))
}

const fundTrendPoints = (fund: DashboardFund) => fundChartCoordinates(fund)
  .map(point => `${point.x.toFixed(1)},${point.y.toFixed(1)}`)
  .join(' ')

const fundLastCoordinate = (fund: DashboardFund) => {
  const coordinates = fundChartCoordinates(fund)
  return coordinates[coordinates.length - 1]
}

// ===== 指标数字滚动动画 =====
const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
const spiderDisp = ref(0)
const taskDisp = ref(0)
const rateDisp = ref(0)
const dataDisp = ref(0)
const tweenHandles: Record<string, number> = {}
const tween = (key: string, from: number, to: number, apply: (v: number) => void) => {
  if (tweenHandles[key]) cancelAnimationFrame(tweenHandles[key])
  if (prefersReducedMotion || from === to) { apply(to); return }
  const start = performance.now()
  const duration = 700
  const step = (t: number) => {
    const p = Math.min(1, (t - start) / duration)
    const eased = 1 - Math.pow(1 - p, 3)
    apply(Math.round(from + (to - from) * eased))
    if (p < 1) tweenHandles[key] = requestAnimationFrame(step)
  }
  tweenHandles[key] = requestAnimationFrame(step)
}
watch(() => stats.value.spiderCount, (to, from) => tween('spider', from ?? 0, to, v => { spiderDisp.value = v }))
watch(() => stats.value.taskCount, (to, from) => tween('task', from ?? 0, to, v => { taskDisp.value = v }))
watch(() => stats.value.successRate, (to, from) => tween('rate', from ?? 0, to, v => { rateDisp.value = v }))
watch(() => stats.value.dataCount, (to, from) => tween('data', from ?? 0, to, v => { dataDisp.value = v }))

const statCards = computed(() => [
  { label: '总爬虫数', tone: 'teal', icon: Connection, route: 'Spider', value: spiderDisp.value, progress: stats.value.spiderCount > 0 ? '100%' : '12%' },
  { label: '今日任务', tone: 'blue', icon: List, route: 'Task', value: taskDisp.value, progress: stats.value.taskCount > 0 ? '100%' : '12%' },
  { label: '成功率', tone: 'amber', icon: CircleCheck, route: '', value: `${rateDisp.value}%`, progress: `${Math.max(stats.value.successRate, 4)}%` },
  { label: '数据总量', tone: 'coral', icon: DataBoard, route: 'Search', value: dataDisp.value.toLocaleString('en-US'), progress: stats.value.dataCount > 0 ? '100%' : '12%' }
])


const statusTag = (status: string) => {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    RUNNING: 'primary', SUCCESS: 'success', FAILED: 'danger',
    PENDING: 'warning', CANCELED: 'info', CANCELING: 'warning', PAUSED: 'info'
  }
  return map[status] || 'info'
}

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    RUNNING: '运行中', SUCCESS: '成功', FAILED: '失败',
    PENDING: '排队中', CANCELED: '已取消', CANCELING: '取消中', PAUSED: '已暂停'
  }
  return map[status] || status
}

// 最近产出聚合：对当前表格行的 HTML/图片 三态计数求和（真实数据，不外推）
const outputBars = computed(() => {
  const sum = (key: string) => recentTasks.value.reduce((acc, t) => acc + (Number(t[key]) || 0), 0)
  const build = (label: string, prefix: 'html' | 'image') => {
    const ok = sum(`${prefix}SuccessCount`)
    const bad = sum(`${prefix}FailCount`)
    const exist = sum(`${prefix}ExistingCount`)
    return {
      label,
      total: ok + bad + exist,
      segments: [
        { key: 'ok', name: '成功', value: ok },
        { key: 'bad', name: '失败', value: bad },
        { key: 'muted', name: '已存在', value: exist }
      ].filter(s => s.value > 0)
    }
  }
  return [build('HTML', 'html'), build('图片', 'image')]
})
// 所有任务均无产出（排队/运行中）时，产出区显示空态提示
const hasOutput = computed(() =>
  recentTasks.value.some(t =>
    ['htmlSuccessCount', 'htmlFailCount', 'htmlExistingCount', 'imageSuccessCount', 'imageFailCount', 'imageExistingCount']
      .some(key => Number(t[key]) > 0)
  )
)

const onTaskRowClick = (row: any) => {
  router.push({ name: 'Task', query: row.spiderId ? { spiderId: row.spiderId } : {} })
}

const statLoading = (index: number) => {
  if (index === 0) return spiderLoading.value
  if (index === 1 || index === 2) return taskLoading.value
  return dataLoading.value
}

const loadSpiderStats = async () => {
  spiderLoading.value = true
  try {
    const spiderRes: any = await spiderPage({ current: 1, size: 1 })
    stats.value.spiderCount = spiderRes?.data?.total ?? spiderRes?.data?.page?.totalElements ?? 0
    spiderErr.value = false
  } catch (e) {
    console.error(e)
    spiderErr.value = true
  } finally {
    spiderLoading.value = false
  }
}

const loadTaskStats = async () => {
  taskLoading.value = true
  try {
    const [response, activeRes]: any[] = await Promise.all([taskStats(), taskActiveCount().catch(() => null)])
    const total = response?.data?.total ?? 0
    const success = response?.data?.success ?? 0
    stats.value.taskCount = total
    todaySuccess.value = success
    stats.value.successRate = total > 0 ? Math.round(success / total * 100) : 0
    activeCount.value = Number(activeRes?.data ?? 0)
    taskErr.value = false
  } catch (e) {
    console.error(e)
    taskErr.value = true
  } finally {
    taskLoading.value = false
  }
}

const loadDataStats = async () => {
  dataLoading.value = true
  try {
    const searchRes: any = await searchContent({ current: 1, size: 1 })
    stats.value.dataCount = searchRes?.data?.total ?? searchRes?.data?.totalElements ?? searchRes?.data?.page?.totalElements ?? 0
    dataErr.value = false
  } catch (e) {
    console.error(e)
    dataErr.value = true
  } finally {
    dataLoading.value = false
  }
}

const loadRecentTasks = async () => {
  recentTaskLoading.value = true
  try {
    const response: any = await taskPage({ current: 1, size: 5 })
    recentTasks.value = response?.data?.records || []
    recentErr.value = false
  } catch (e) {
    console.error(e)
    recentTasks.value = []
    recentErr.value = true
  } finally {
    recentTaskLoading.value = false
  }
}

const stampSync = () => {
  const now = new Date()
  lastUpdated.value = [now.getHours(), now.getMinutes(), now.getSeconds()]
    .map(part => String(part).padStart(2, '0')).join(':')
}

// 10s 内的重复激活（keep-alive 快速切页）不重复拉取；手动刷新强制拉取
let lastLoadedAt = 0
const loadDashboard = (force = false) => {
  if (!force && Date.now() - lastLoadedAt < 10_000) return
  lastLoadedAt = Date.now()
  void Promise.all([loadSpiderStats(), loadTaskStats(), loadRecentTasks(), loadDataStats(), loadFavoriteFunds()]).then(stampSync)
}

const refreshDashboard = () => loadDashboard(true)

// 页面可见时每 30s 静默刷新任务动态；离开页面（keep-alive 失活）时停止
let refreshTimer: ReturnType<typeof setInterval> | null = null
const startAutoRefresh = () => {
  stopAutoRefresh()
  refreshTimer = setInterval(() => {
    if (document.visibilityState === 'visible') {
      void Promise.all([loadTaskStats(), loadRecentTasks()]).then(stampSync)
    }
  }, 30_000)
}
const stopAutoRefresh = () => {
  if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = null }
}

onActivated(() => {
  loadDashboard()
  startAutoRefresh()
  startClock()
})
onDeactivated(() => {
  stopAutoRefresh()
  stopClock()
})
onUnmounted(() => {
  stopAutoRefresh()
  stopClock()
  Object.values(tweenHandles).forEach(handle => cancelAnimationFrame(handle))
})
</script>

<style scoped>
.dashboard-page {
  /* ===== 白天（默认）===== */
  --dc-bg: #eaf1f3;
  --dc-panel: linear-gradient(180deg, #ffffff 0%, #fbfdfd 100%);
  --dc-line: #d9e5e9;
  --dc-line-soft: #e4edf0;
  --dc-line-strong: #c3d8dd;
  --dc-text: #172b4d;
  --dc-text-dim: #486581;
  --dc-text-mute: #8999a8;
  --dc-text-bright: #102644;
  --dc-neon: #0f9f9a;
  --dc-neon-hover: #087b78;
  --dc-neon-text: #087b78;
  --dc-neon-soft: rgba(15, 159, 154, .09);
  --dc-neon-faint: rgba(15, 159, 154, .04);
  --dc-neon-glow: rgba(15, 159, 154, .16);
  --dc-neon-glow-strong: rgba(15, 159, 154, .35);
  --dc-teal: #0f9f9a;
  --dc-teal-soft: rgba(15, 159, 154, .12);
  --dc-blue: #2f7fb8;
  --dc-amber: #b97f1f;
  --dc-coral: #d9534f;
  --dc-violet: #6f5fc7;
  --dc-page-bg:
    radial-gradient(ellipse 70% 46% at 88% -8%, rgba(15, 159, 154, .1), transparent),
    radial-gradient(ellipse 56% 40% at -8% 108%, rgba(47, 127, 184, .06), transparent),
    linear-gradient(180deg, #f3f8f9 0%, var(--dc-bg) 100%);
  --dc-grid: rgba(15, 159, 154, .045);
  --dc-hero-bg: linear-gradient(120deg, #ffffff 0%, #f2fafa 55%, #e4f4f1 100%);
  --dc-hero-glow:
    radial-gradient(circle at 84% 130%, rgba(15, 159, 154, .1), transparent 46%),
    radial-gradient(circle at 6% -50%, rgba(47, 127, 184, .08), transparent 42%);
  --dc-sweep: linear-gradient(100deg, transparent, rgba(15, 159, 154, .04) 45%, rgba(15, 159, 154, .09) 50%, rgba(15, 159, 154, .04) 55%, transparent);
  --dc-sweep-strong: conic-gradient(from 0deg, rgba(15, 159, 154, .2), rgba(15, 159, 154, .04) 62deg, transparent 90deg);
  --dc-card-bg: linear-gradient(180deg, #ffffff 0%, #f8fcfc 100%);
  --dc-radar-core: radial-gradient(circle at 50% 32%, #f2fbf9, #e2f1ef 72%);
  --dc-shadow: 0 18px 40px rgba(23, 43, 77, .1);
  --dc-shadow-soft: 0 10px 26px rgba(23, 43, 77, .07);
  --st-primary: #0f9f9a;
  --st-success: #1e9e63;
  --st-danger: #d9534f;
  --st-warning: #b97f1f;
  --st-info: #5e6c84;
  --st-zero: #a9bac4;
  --font-mono: 'JetBrains Mono', 'Cascadia Code', 'SF Mono', Consolas, 'Courier New', monospace;
  position: relative;
  margin: -32px;
  min-height: calc(100% + 64px);
  padding: 26px 30px 34px;
  background: var(--dc-page-bg);
}
/* ===== 黑夜：沿用暗色指挥台原视觉 ===== */
html[data-theme="dark"] .dashboard-page {
  --dc-bg: #070d17;
  --dc-panel: linear-gradient(180deg, rgba(16, 30, 48, .85), rgba(11, 22, 38, .9));
  --dc-line: rgba(114, 224, 200, .14);
  --dc-line-soft: rgba(114, 224, 200, .08);
  --dc-line-strong: rgba(114, 224, 200, .28);
  --dc-text: #e8f4f2;
  --dc-text-dim: #9db4c0;
  --dc-text-mute: #5f7688;
  --dc-text-bright: #ffffff;
  --dc-neon: #72e0c8;
  --dc-neon-hover: #9beede;
  --dc-neon-text: #d6f3e9;
  --dc-neon-soft: rgba(114, 224, 200, .08);
  --dc-neon-faint: rgba(114, 224, 200, .03);
  --dc-neon-glow: rgba(114, 224, 200, .18);
  --dc-neon-glow-strong: rgba(114, 224, 200, .8);
  --dc-teal: #0f9f9a;
  --dc-teal-soft: rgba(114, 224, 200, .12);
  --dc-blue: #5eb6e0;
  --dc-amber: #e0b05e;
  --dc-coral: #f08a72;
  --dc-violet: #a99bf0;
  --dc-page-bg:
    radial-gradient(ellipse 70% 46% at 88% -8%, rgba(15, 159, 154, .16), transparent),
    radial-gradient(ellipse 56% 40% at -8% 108%, rgba(94, 182, 224, .09), transparent),
    linear-gradient(180deg, #081120 0%, var(--dc-bg) 100%);
  --dc-grid: rgba(114, 224, 200, .05);
  --dc-hero-bg: linear-gradient(120deg, #0a1826 0%, #0d2434 55%, #0f3038 100%);
  --dc-hero-glow:
    radial-gradient(circle at 84% 130%, rgba(114, 224, 200, .14), transparent 46%),
    radial-gradient(circle at 6% -50%, rgba(94, 182, 224, .1), transparent 42%);
  --dc-sweep: linear-gradient(100deg, transparent, rgba(114, 224, 200, .06) 45%, rgba(114, 224, 200, .12) 50%, rgba(114, 224, 200, .06) 55%, transparent);
  --dc-sweep-strong: conic-gradient(from 0deg, rgba(114, 224, 200, .3), rgba(114, 224, 200, .06) 62deg, transparent 90deg);
  --dc-card-bg: linear-gradient(180deg, rgba(17, 32, 52, .88), rgba(10, 20, 35, .92));
  --dc-radar-core: radial-gradient(circle at 50% 32%, #0e2233, #08131f 72%);
  --dc-shadow: 0 18px 40px rgba(2, 8, 16, .5);
  --dc-shadow-soft: 0 10px 26px rgba(2, 8, 16, .4);
  --st-primary: #72e0c8;
  --st-success: #5fd39a;
  --st-danger: #f08a72;
  --st-warning: #e0b05e;
  --st-info: #8fa3b8;
  --st-zero: #44606e;
}
/* ===== 纯黑（AMOLED）：只改底色为纯黑，其他颜色跟随白天（--dc-* 白天值直接沿用）===== */
html[data-theme="black"] .dashboard-page {
  --dc-bg: #000000;
  --dc-line: rgba(255, 255, 255, .14);
  --dc-line-soft: rgba(255, 255, 255, .08);
  --dc-line-strong: rgba(255, 255, 255, .28);
  --dc-panel: linear-gradient(180deg, rgba(10, 10, 10, .85), rgba(5, 5, 5, .9));
  --dc-text: #fafafa;
  --dc-text-dim: #a3a3a3;
  --dc-text-mute: #737373;
  --dc-text-bright: #ffffff;
  --dc-page-bg:
    radial-gradient(ellipse 70% 46% at 88% -8%, rgba(15, 159, 154, .16), transparent),
    radial-gradient(ellipse 56% 40% at -8% 108%, rgba(47, 127, 184, .09), transparent),
    linear-gradient(180deg, #050505 0%, var(--dc-bg) 100%);
  --dc-hero-bg: linear-gradient(120deg, #050505 0%, #0f0f0f 55%, #141414 100%);
  --dc-hero-glow:
    radial-gradient(circle at 84% 130%, rgba(15, 159, 154, .14), transparent 46%),
    radial-gradient(circle at 6% -50%, rgba(47, 127, 184, .1), transparent 42%);
  --dc-card-bg: linear-gradient(180deg, rgba(12, 12, 12, .88), rgba(6, 6, 6, .92));
  --dc-radar-core: radial-gradient(circle at 50% 32%, #0f0f0f, #050505 72%);
  --dc-shadow: 0 18px 40px rgba(0, 0, 0, .65);
  --dc-shadow-soft: 0 10px 26px rgba(0, 0, 0, .5);
}
.dashboard-page::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--dc-grid) 1px, transparent 1px),
    linear-gradient(90deg, var(--dc-grid) 1px, transparent 1px);
  background-size: 44px 44px;
  -webkit-mask-image: radial-gradient(ellipse 95% 72% at 50% 0%, #000 28%, transparent 76%);
  mask-image: radial-gradient(ellipse 95% 72% at 50% 0%, #000 28%, transparent 76%);
  pointer-events: none;
}
.dashboard-page > section { position: relative; z-index: 1; }

/* ===== 指挥台头部 ===== */
.dashboard-hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-height: 212px;
  margin-bottom: 20px;
  padding: 30px clamp(24px, 4vw, 48px);
  overflow: hidden;
  border: 1px solid var(--dc-line-strong);
  border-radius: 20px;
  color: var(--dc-text);
  background: var(--dc-hero-bg);
  box-shadow: var(--dc-shadow);
}
.dashboard-hero::before {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--dc-hero-glow);
  pointer-events: none;
}
.dashboard-hero::after {
  content: '';
  position: absolute;
  top: 0;
  left: -32%;
  width: 26%;
  height: 100%;
  background: var(--dc-sweep);
  transform: skewX(-18deg);
  animation: hero-sweep 7.5s ease-in-out infinite;
  pointer-events: none;
}
@keyframes hero-sweep { 0% { left: -32%; } 58% { left: 112%; } 100% { left: 112%; } }
.hero-copy { position: relative; z-index: 1; }
.eyebrow {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0 0 12px;
  color: var(--dc-neon);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2.4px;
  text-shadow: 0 0 14px var(--dc-neon-glow-strong);
}
.eyebrow-caret { width: 6px; height: 12px; background: var(--dc-neon); animation: caret-blink 1.2s steps(1) infinite; }
@keyframes caret-blink { 0%, 55% { opacity: 1; } 56%, 100% { opacity: 0; } }
.dashboard-hero h1 { margin: 0; color: var(--dc-text-bright); font-size: clamp(27px, 3vw, 34px); font-weight: 850; letter-spacing: -.7px; text-shadow: 0 2px 22px var(--dc-neon-glow); }
.intro-copy { margin: 11px 0 18px; color: var(--dc-text-dim); font-size: 14px; letter-spacing: .3px; }
.hero-meta { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.hero-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 13px;
  border: 1px solid var(--dc-line-strong);
  border-radius: 999px;
  color: var(--dc-neon-text);
  background: var(--dc-neon-soft);
  font-size: 12px;
  box-shadow: 0 0 16px var(--dc-neon-glow);
}
.hero-status b { color: var(--dc-neon); }
.hero-active {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 13px;
  border: 1px solid color-mix(in srgb, var(--dc-coral) 40%, transparent);
  border-radius: 999px;
  color: color-mix(in srgb, var(--dc-coral) 78%, var(--dc-text));
  background: color-mix(in srgb, var(--dc-coral) 12%, transparent);
  font-size: 12px;
}
.hero-active .el-icon { animation: spin 1.6s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.hero-date { color: var(--dc-text-mute); font-family: var(--font-mono); font-size: 11px; letter-spacing: .4px; }
.status-pulse { display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: var(--dc-neon); box-shadow: 0 0 8px var(--dc-neon-glow-strong); animation: pulse 2.2s ease-in-out infinite; }
@keyframes pulse { 0%, 100% { box-shadow: 0 0 0 4px var(--dc-neon-glow), 0 0 8px var(--dc-neon-glow-strong); } 50% { box-shadow: 0 0 0 8px var(--dc-neon-faint), 0 0 8px var(--dc-neon-glow-strong); } }

/* 雷达 + 系统时钟 */
.hero-radar { position: relative; z-index: 1; flex: none; width: 172px; height: 172px; margin-right: 5%; }
.radar-ring { position: absolute; border: 1px solid var(--dc-line-strong); border-radius: 50%; }
.radar-ring-1 { inset: 0; }
.radar-ring-2 { inset: 22px; border-color: var(--dc-line); }
.radar-ring-3 { inset: 44px; border-color: var(--dc-line-soft); }
.radar-cross::before, .radar-cross::after { content: ''; position: absolute; background: var(--dc-line-soft); }
.radar-cross::before { left: 0; right: 0; top: 50%; height: 1px; }
.radar-cross::after { top: 0; bottom: 0; left: 50%; width: 1px; }
.radar-sweep {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: var(--dc-sweep-strong);
  animation: radar-spin 4.2s linear infinite;
}
@keyframes radar-spin { to { transform: rotate(360deg); } }
.radar-blip { position: absolute; width: 5px; height: 5px; border-radius: 50%; background: var(--dc-neon); box-shadow: 0 0 8px var(--dc-neon-glow-strong); animation: blip 4.2s ease-in-out infinite; }
.blip-1 { top: 26%; left: 62%; }
.blip-2 { top: 58%; left: 24%; animation-delay: 1.4s; }
.blip-3 { top: 72%; left: 68%; animation-delay: 2.6s; background: var(--dc-coral); box-shadow: 0 0 8px color-mix(in srgb, var(--dc-coral) 90%, transparent); }
@keyframes blip { 0%, 18% { opacity: 1; } 46%, 100% { opacity: .12; } }
.radar-orbit { position: absolute; inset: -11px; animation: radar-spin 14s linear infinite; }
.radar-satellite { position: absolute; top: -3px; left: 50%; width: 7px; height: 7px; margin-left: -3.5px; border-radius: 50%; background: var(--dc-blue); box-shadow: 0 0 10px color-mix(in srgb, var(--dc-blue) 95%, transparent); }
.radar-core {
  position: absolute;
  inset: 52px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  border: 1px solid var(--dc-line-strong);
  border-radius: 50%;
  background: var(--dc-radar-core);
  box-shadow: 0 0 24px var(--dc-neon-glow), inset 0 0 16px var(--dc-neon-faint);
}
.radar-clock { color: var(--dc-neon); font-family: var(--font-mono); font-size: 15px; font-weight: 700; letter-spacing: .5px; text-shadow: 0 0 12px var(--dc-neon-glow-strong); font-variant-numeric: tabular-nums; }
.radar-clock-label { color: var(--dc-text-mute); font-family: var(--font-mono); font-size: 7px; letter-spacing: 2px; }

/* ===== 指标卡 ===== */
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin-bottom: 20px; }
.metric-card {
  position: relative;
  min-height: 154px;
  padding: 19px 21px;
  overflow: hidden;
  border: 1px solid var(--dc-line);
  border-radius: 16px;
  background: var(--dc-card-bg);
  box-shadow: var(--dc-shadow-soft);
}
.metric-card::before {
  content: '';
  position: absolute;
  inset: 7px;
  background:
    linear-gradient(var(--metric-color) 0 0) left top / 13px 1px,
    linear-gradient(var(--metric-color) 0 0) left top / 1px 13px,
    linear-gradient(var(--metric-color) 0 0) right top / 13px 1px,
    linear-gradient(var(--metric-color) 0 0) right top / 1px 13px,
    linear-gradient(var(--metric-color) 0 0) left bottom / 13px 1px,
    linear-gradient(var(--metric-color) 0 0) left bottom / 1px 13px,
    linear-gradient(var(--metric-color) 0 0) right bottom / 13px 1px,
    linear-gradient(var(--metric-color) 0 0) right bottom / 1px 13px;
  background-repeat: no-repeat;
  opacity: .5;
  pointer-events: none;
}
.metric-card::after {
  content: '';
  position: absolute;
  width: 120px;
  height: 120px;
  right: -44px;
  bottom: -52px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--metric-glow), transparent 70%);
  pointer-events: none;
}
.metric-top { display: flex; justify-content: space-between; align-items: center; }
.metric-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--metric-border);
  border-radius: 10px;
  color: var(--metric-color);
  background: var(--metric-soft);
  box-shadow: 0 0 14px var(--metric-glow);
}
.metric-index { color: var(--dc-text-mute); font-family: var(--font-mono); font-size: 10px; font-weight: 700; letter-spacing: 1.6px; }
.stat-label { margin-top: 14px; color: var(--dc-text-dim); font-size: 12px; font-weight: 600; letter-spacing: .4px; }
.stat-value {
  margin-top: 5px;
  color: var(--dc-text);
  font-family: var(--font-mono);
  font-size: 30px;
  font-weight: 800;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  text-shadow: 0 0 18px var(--metric-glow);
}
.metric-foot { position: relative; z-index: 1; display: flex; align-items: center; justify-content: space-between; margin-top: 15px; }
.metric-rule { width: 76px; height: 3px; overflow: hidden; border-radius: 3px; background: var(--dc-line-soft); }
.metric-rule span { display: block; width: 65%; height: 100%; background: var(--metric-color); box-shadow: 0 0 8px var(--metric-glow); transition: width .6s ease; }
.metric-link { color: var(--metric-color); font-size: 11px; font-weight: 700; opacity: .78; transition: opacity .18s ease, transform .18s ease; }
.metric-card {
  --metric-soft: color-mix(in srgb, var(--metric-color) 10%, transparent);
  --metric-border: color-mix(in srgb, var(--metric-color) 34%, transparent);
  --metric-glow: color-mix(in srgb, var(--metric-color) 20%, transparent);
}
.metric-teal { --metric-color: var(--dc-neon); }
.metric-blue { --metric-color: var(--dc-blue); }
.metric-amber { --metric-color: var(--dc-amber); }
.metric-coral { --metric-color: var(--dc-coral); }
.metric-clickable { cursor: pointer; transition: box-shadow .18s ease, transform .18s ease, border-color .18s ease; }
.metric-clickable:hover { border-color: var(--metric-border); box-shadow: var(--dc-shadow), 0 0 22px var(--metric-glow); transform: translateY(-2px); }
.metric-clickable:hover .metric-link, .metric-clickable:focus-visible .metric-link { opacity: 1; }

/* ===== 面板通用 ===== */
.dashboard-grid { display: grid; grid-template-columns: 1fr; gap: 20px; margin-bottom: 20px; }
.panel {
  position: relative;
  min-width: 0;
  padding: 22px 24px;
  border: 1px solid var(--dc-line);
  border-radius: 16px;
  background: var(--dc-panel);
  box-shadow: var(--dc-shadow-soft);
}
.panel::before {
  content: '';
  position: absolute;
  top: 0;
  left: 12%;
  right: 12%;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--dc-line-strong), transparent);
  pointer-events: none;
}
.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.panel-heading h2 { margin: 0; color: var(--dc-text); font-size: 18px; font-weight: 800; letter-spacing: .2px; }
.panel-kicker { margin: 0 0 6px; color: var(--dc-text-mute); font-family: var(--font-mono); font-size: 9px; font-weight: 700; letter-spacing: 2px; }
.panel-actions { display: flex; align-items: center; gap: 10px; }
.panel-updated { color: var(--dc-text-mute); font-family: var(--font-mono); font-size: 11px; white-space: nowrap; }
.panel-link-btn { color: var(--dc-neon) !important; font-weight: 700; }
.panel-link-btn:hover { color: var(--dc-neon-hover) !important; }
.refresh-btn { border-color: var(--dc-line-strong); color: var(--dc-neon); background: var(--dc-neon-soft); }
.refresh-btn:hover { border-color: var(--dc-neon); color: var(--dc-neon-hover); background: var(--dc-neon-glow); }
.panel-success-chip { padding: 3px 10px; border: 1px solid var(--dc-line-strong); border-radius: 999px; color: var(--dc-neon-text); background: var(--dc-neon-soft); font-size: 11px; white-space: nowrap; }
.panel-success-chip b { color: var(--dc-neon); font-weight: 700; margin-left: 5px; font-family: var(--font-mono); }

/* ===== 关注基金净值与数据库历史走势 ===== */
.fund-watchlist-panel { margin-bottom: 20px; }
.fund-source-label { color: var(--dc-text-mute); font-size: 11px; white-space: nowrap; }
.fund-watchlist-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 12px; }
.fund-watch-card {
  min-width: 0;
  padding: 15px 16px 12px;
  border: 1px solid var(--dc-line-soft);
  border-radius: 12px;
  background: var(--dc-card-bg);
}
.fund-watch-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.fund-watch-name { overflow: hidden; color: var(--dc-text); font-size: 13px; font-weight: 750; text-overflow: ellipsis; white-space: nowrap; }
.fund-watch-heading :deep(.el-tag) { flex: none; border-color: var(--dc-line-strong); color: var(--dc-text-dim); background: var(--dc-neon-faint); }
.fund-watch-quote { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 3px 8px; margin-top: 15px; }
.fund-watch-quote-meta { grid-column: 1; grid-row: 1 / span 2; display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.fund-watch-code { overflow: hidden; color: var(--dc-text-mute); font-family: var(--font-mono); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.fund-watch-date { color: var(--dc-text-mute); font-size: 10px; }
.fund-watch-quote strong { grid-column: 2; grid-row: 1; color: var(--dc-text-bright); font-family: var(--font-mono); font-size: 21px; font-variant-numeric: tabular-nums; white-space: nowrap; }
.fund-watch-quote .fund-watch-no-value { color: var(--dc-text-mute); font-family: inherit; font-size: 14px; }
.fund-watch-change { grid-column: 2; grid-row: 2; color: var(--dc-text-mute); font-family: var(--font-mono); font-size: 10px; text-align: right; }
.fund-watch-change.is-positive { color: var(--st-success); }
.fund-watch-change.is-negative { color: var(--st-danger); }
.fund-watch-chart { display: block; width: 100%; height: 92px; margin-top: 9px; }
.fund-watch-chart-loading, .fund-watch-chart-error {
  display: grid;
  place-items: center;
  padding: 10px;
  color: var(--dc-text-mute);
  font-size: 11px;
  text-align: center;
}
.fund-watch-chart-error { color: var(--dc-amber); }
.fund-chart-grid { stroke: var(--dc-line-soft); stroke-dasharray: 4 7; stroke-width: 1; }
.fund-chart-line { fill: none; stroke: var(--dc-neon); stroke-linecap: round; stroke-linejoin: round; stroke-width: 3; vector-effect: non-scaling-stroke; }
.fund-chart-endpoint { fill: var(--dc-neon); stroke: var(--dc-panel); stroke-width: 2; vector-effect: non-scaling-stroke; }
.fund-watch-dates { display: flex; justify-content: space-between; gap: 8px; color: var(--dc-text-mute); font-family: var(--font-mono); font-size: 9px; }
.fund-watch-card-footer { display: flex; align-items: center; justify-content: space-between; gap: 8px; min-height: 28px; }
.fund-watchlist-message { display: flex; align-items: center; justify-content: center; gap: 8px; min-height: 100px; color: var(--dc-text-mute); font-size: 12px; text-align: center; }
.fund-watchlist-error { color: var(--dc-coral); }

/* ===== 最近任务表格（暗色覆盖）===== */
.task-panel { min-width: 0; display: flex; flex-direction: column; }
.task-panel .table-scroll-wrapper { flex: 1 1 auto; }
.footer-tag { color: var(--dc-text-mute); font-weight: 700; letter-spacing: 1.6px; }
.table-scroll-wrapper::-webkit-scrollbar-thumb { background: rgba(var(--neon-rgb), .35); background-clip: padding-box; }
.table-scroll-wrapper::-webkit-scrollbar-thumb:hover { background: rgba(var(--neon-rgb), .55); background-clip: padding-box; }
.task-panel :deep(.el-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: var(--dc-neon-soft);
  --el-table-row-hover-bg-color: var(--dc-neon-soft);
  --el-table-border-color: var(--dc-line);
  --el-table-text-color: var(--dc-text);
  --el-table-header-text-color: var(--dc-text-mute);
  border-radius: 8px;
  background: transparent;
}
.task-panel :deep(.el-table th.el-table__cell) { background: var(--dc-neon-soft); font-weight: 700; }
.task-panel :deep(.el-table td.el-table__cell), .task-panel :deep(.el-table th.el-table__cell) { border-bottom: 1px solid var(--dc-line-soft); }
.task-panel :deep(.el-table--border .el-table__cell) { border-right: 1px solid var(--dc-line-soft); }
.task-panel :deep(.el-table .cell) { font-variant-numeric: tabular-nums; }
.task-panel :deep(.el-table__empty-block) { background: transparent; }
.task-panel :deep(.el-table__empty-text) { color: var(--dc-text-mute); }
.dashboard-page :deep(.el-loading-mask) { background: var(--loading-mask); backdrop-filter: blur(2px); }
.dashboard-page :deep(.el-loading-spinner .circular .path) { stroke: var(--dc-neon); }
.dashboard-page :deep(.el-loading-text) { color: var(--dc-neon); }
.status-cell { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; font-weight: 600; }
.status-dot { width: 7px; height: 7px; border-radius: 50%; background: currentColor; box-shadow: 0 0 7px currentColor; flex: none; }
.status-dot-live { animation: pulse-dot 1.6s ease-in-out infinite; }
@keyframes pulse-dot { 0%, 100% { box-shadow: 0 0 0 2px var(--dc-neon-glow), 0 0 7px currentColor; } 50% { box-shadow: 0 0 0 5px var(--dc-neon-faint), 0 0 7px currentColor; } }
.status-primary { color: var(--st-primary); }
.status-success { color: var(--st-success); }
.status-danger { color: var(--st-danger); }
.status-warning { color: var(--st-warning); }
.status-info { color: var(--st-info); }
.count-ok { color: var(--st-success); font-weight: 700; }
.count-bad { color: var(--st-danger); font-weight: 700; }
.count-zero { color: var(--st-zero); }
.count-muted { color: var(--dc-text-mute); }
.panel-empty { display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 34px 0; color: var(--dc-text-mute); text-align: center; font-size: 13px; }
.panel-empty p { margin: 0; }
.empty-icon { color: var(--dc-text-mute); }
.empty-btn { background: var(--dc-teal-soft); border-color: var(--dc-line-strong); color: var(--dc-neon); }
.empty-btn:hover { background: var(--dc-neon-glow); border-color: var(--dc-neon); color: var(--dc-neon-hover); }
.col-action-view { font-weight: 700; color: var(--dc-neon); }
.col-action-view:hover { color: var(--dc-neon-hover); }
.metric-clickable:focus-visible { outline: 2px solid var(--metric-color); outline-offset: 2px; }



/* ===== 最近产出聚合 ===== */
.output-block {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
  min-height: 104px;
  margin-top: 16px;
  padding: 14px 16px;
  border: 1px solid var(--dc-line-soft);
  border-radius: 10px;
  background: var(--dc-neon-faint);
}
.output-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.output-scope { color: var(--dc-text-mute); font-family: var(--font-mono); font-size: 10px; letter-spacing: .4px; white-space: nowrap; }
.output-row { display: flex; align-items: center; gap: 10px; }
.output-label { flex: none; width: 34px; color: var(--dc-text-dim); font-size: 11px; font-weight: 700; }
.output-bar { flex: 1 1 auto; display: flex; height: 10px; overflow: hidden; border-radius: 6px; background: var(--dc-neon-soft); }
.output-seg { height: 100%; transition: flex-grow .6s ease; }
.output-seg + .output-seg { box-shadow: inset 1px 0 0 rgba(7, 13, 23, .65); }
.seg-ok { background: var(--st-success); }
.seg-bad { background: var(--st-danger); }
.seg-muted { background: var(--st-zero); }
.output-total { flex: none; width: 64px; text-align: right; color: var(--dc-text); font-family: var(--font-mono); font-size: 12px; font-weight: 700; font-variant-numeric: tabular-nums; }
.output-legend { display: flex; gap: 14px; color: var(--dc-text-mute); font-size: 10px; }
.output-legend i { display: inline-block; width: 7px; height: 7px; margin-right: 5px; border-radius: 2px; }
.output-empty { margin: 2px 0 0; color: var(--dc-text-mute); font-size: 12px; }
.task-panel :deep(.el-table__body tr) { cursor: pointer; }

/* ===== 连接异常徽标 ===== */
.hero-status-bad { border-color: color-mix(in srgb, var(--dc-coral) 45%, transparent); color: color-mix(in srgb, var(--dc-coral) 78%, var(--dc-text)); background: color-mix(in srgb, var(--dc-coral) 12%, transparent); cursor: pointer; }
.hero-status-bad b { color: var(--dc-coral); }
.hero-status-bad .status-pulse { background: var(--dc-coral); box-shadow: 0 0 8px color-mix(in srgb, var(--dc-coral) 80%, transparent); animation: none; }

/* ===== 入场编排 ===== */
@keyframes rise-in { from { opacity: 0; transform: translateY(14px); } to { opacity: 1; transform: translateY(0); } }
.dashboard-hero { animation: rise-in .55s ease backwards; }
.metric-card { animation: rise-in .5s ease backwards; animation-delay: var(--enter-delay, 0ms); }
.task-panel { animation: rise-in .55s ease backwards; animation-delay: .28s; }

/* ===== 响应式 ===== */
@media (max-width: 1100px) {
  .dashboard-page { margin: -20px; min-height: calc(100% + 40px); padding: 20px; }
}
@media (max-width: 1000px) {
  .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .hero-radar { margin-right: 2%; }
}
@media (max-width: 767px) {
  .dashboard-page { margin: -14px; min-height: calc(100% + 28px); padding: 16px 14px 24px; }
}
@media (max-width: 600px) {
  .dashboard-hero { min-height: 200px; padding: 26px 24px; }
  .hero-meta { gap: 8px; }
  .hero-status, .hero-active { padding: 5px 10px; font-size: 11px; }
  .dashboard-hero h1 { font-size: 25px; }
  .hero-radar { display: none; }
  .metric-grid { grid-template-columns: 1fr 1fr; gap: 10px; }
  .metric-card { min-height: 132px; padding: 14px; }
  .stat-value { font-size: clamp(20px, 6vw, 24px); }
  .panel { padding: 17px 14px; }
  .panel-heading { align-items: flex-start; flex-direction: column; gap: 10px; margin-bottom: 14px; }
  .panel-actions { width: 100%; justify-content: flex-start; flex-wrap: wrap; }
  .fund-source-label { white-space: normal; }
  .panel-updated { display: none; }
  .output-block { min-height: 88px; padding: 12px; }
  .output-total { width: 52px; font-size: 11px; }
  /* 小屏：el-table 原生横向滚动，不裁剪不隐藏 */
  .task-panel { padding-right: 18px; }
}
@media (prefers-reduced-motion: reduce) {
  .dashboard-hero::after, .radar-sweep, .radar-orbit, .radar-blip, .eyebrow-caret, .status-pulse, .status-dot-live, .hero-active .el-icon, .command-tile,
.dashboard-hero, .metric-card, .task-panel { animation: none; }
  .output-seg, .metric-rule span, .metric-clickable, .metric-link, .command-tile, .command-icon { transition: none; }
}
</style>
