<template>
  <section class="stock-market-panel">
    <div v-if="mode === 'history'">
      <p class="panel-description">查询股票日线历史行情。API 查询结果会自动缓存；数据库查询仅读取已缓存的数据。</p>
      <el-form class="market-form" inline @submit.prevent="loadHistory">
        <el-form-item label="股票代码">
          <el-input :model-value="code" readonly />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="historyStartDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="historyEndDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="数据来源">
          <el-select v-model="historySource" style="width: 160px">
            <el-option label="API 查询并缓存" value="api" />
            <el-option label="数据库缓存" value="database" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="historySource === 'api'" label="历史行情 API">
          <el-select v-model="historyProvider" style="width: 180px">
            <el-option v-for="option in availableProviders" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="historyLoading" @click="loadHistory">查询历史行情</el-button>
        </el-form-item>
      </el-form>

      <div v-if="historyResult" class="history-result">
        <div class="history-caption">
          <strong>{{ historyResult.name }}（{{ historyResult.code }}）</strong>
          <span>{{ historyResult.points.length }} 个交易日</span>
        </div>
        <el-tabs v-model="chartType" class="chart-type-tabs">
          <el-tab-pane label="折线图" name="line" />
          <el-tab-pane label="K 线图" name="candlestick" />
        </el-tabs>
        <div v-if="historyResult.points.length" class="history-chart">
          <div class="chart-value chart-top">¥{{ money(chartMaxPrice) }}</div>
          <svg viewBox="0 0 1000 220" preserveAspectRatio="none" role="img" :aria-label="`${historyResult.name}历史收盘价走势`">
            <line x1="0" y1="30" x2="1000" y2="30" class="chart-grid" />
            <line x1="0" y1="110" x2="1000" y2="110" class="chart-grid" />
            <line x1="0" y1="190" x2="1000" y2="190" class="chart-grid" />
            <polyline v-if="chartType === 'line'" :points="historyChartPoints" class="chart-line" />
            <g v-else v-for="candle in historyCandles" :key="candle.date">
              <line
                :x1="candle.x"
                :y1="candle.highY"
                :x2="candle.x"
                :y2="candle.lowY"
                :class="candle.isUp ? 'candle-wick candle-up' : 'candle-wick candle-down'"
              />
              <rect
                :x="candle.x - candle.width / 2"
                :y="candle.bodyY"
                :width="candle.width"
                :height="candle.bodyHeight"
                :class="candle.isUp ? 'candle-body candle-up' : 'candle-body candle-down'"
              />
            </g>
            <template v-if="historyHoverIndex !== null">
              <line
                :x1="historyChartCoordinates[historyHoverIndex]?.x"
                y1="20"
                :x2="historyChartCoordinates[historyHoverIndex]?.x"
                y2="200"
                class="chart-crosshair"
              />
              <circle
                :cx="historyHoverCoordinate?.x"
                :cy="historyHoverCoordinate?.y"
                r="5"
                class="chart-hover-dot"
              />
            </template>
            <rect
              x="0"
              y="20"
              width="1000"
              height="180"
              class="chart-interaction-area"
              @pointermove="updateChartHover"
              @pointerleave="historyHoverIndex = null"
            />
          </svg>
          <div v-if="historyHoverPoint" class="chart-hover-label">
            <strong>{{ historyHoverPoint.date }}</strong>
            <span>开盘 ¥{{ money(historyHoverPoint.open) }}</span>
            <span>收盘 ¥{{ money(historyHoverPoint.close) }}</span>
            <span>最高 ¥{{ money(historyHoverPoint.high) }}</span>
            <span>最低 ¥{{ money(historyHoverPoint.low) }}</span>
            <span :class="tone(historyHoverPoint.changePercent)">涨跌 {{ percent(historyHoverPoint.changePercent) }}</span>
          </div>
          <div class="chart-value chart-bottom">¥{{ money(chartMinPrice) }}</div>
          <div class="chart-dates">
            <span>{{ historyResult.points[0]?.date }}</span>
            <span>{{ historyResult.points[historyResult.points.length - 1]?.date }}</span>
          </div>
        </div>
        <el-empty v-else description="所选日期范围没有交易行情" :image-size="64" />
        <div class="table-scroll-wrapper">
          <el-table :data="recentHistoryPoints" class="investment-data-table history-detail-table" stripe border resizable max-height="360">
            <el-table-column prop="date" label="日期" min-width="120" />
            <el-table-column label="开盘" min-width="95" align="right">
              <template #default="{ row }">¥{{ money(row.open) }}</template>
            </el-table-column>
            <el-table-column label="收盘" min-width="95" align="right">
              <template #default="{ row }">¥{{ money(row.close) }}</template>
            </el-table-column>
            <el-table-column label="最高" min-width="95" align="right">
              <template #default="{ row }">¥{{ money(row.high) }}</template>
            </el-table-column>
            <el-table-column label="最低" min-width="95" align="right">
              <template #default="{ row }">¥{{ money(row.low) }}</template>
            </el-table-column>
            <el-table-column label="涨跌幅" min-width="95" align="right">
              <template #default="{ row }"><span :class="tone(row.changePercent)">{{ percent(row.changePercent) }}</span></template>
            </el-table-column>
          </el-table>
        </div>
        <p class="fee-note">图表和回测使用数据源提供的前复权日线收盘价；历史表现不代表未来收益，不构成投资建议。</p>
      </div>
      <el-empty v-else-if="!historyLoading" description="选择日期范围后查询历史行情" :image-size="64" />
    </div>

    <div v-else>
      <p class="panel-description">
        按所选星期定额买入，遇休市顺延至当周下一个交易日，不足 100 股的资金结转；并与相同累计投入的一次性买入作比较。
      </p>
      <el-form class="market-form" inline @submit.prevent="runBacktest">
        <el-form-item label="股票代码">
          <el-input :model-value="code" readonly />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="backtestStartDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="backtestEndDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="每周定投日">
          <el-select v-model="weekday" style="width: 110px">
            <el-option v-for="day in weekdays" :key="day.value" :label="day.label" :value="day.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="每期金额">
          <el-input-number v-model="amount" :min="1" :max="10000000" :precision="2" :step="100" controls-position="right" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="backtestLoading" @click="runBacktest">运行回测</el-button>
        </el-form-item>
      </el-form>

      <template v-if="backtestResult">
        <div class="backtest-summary">
          <article><span>累计投入</span><strong>¥{{ money(backtestResult.totalContribution) }}</strong><small>{{ backtestResult.executedPeriods }} 次执行，{{ backtestResult.skippedPeriods }} 期未成交</small></article>
          <article><span>期末资产（含结余）</span><strong>¥{{ money(backtestResult.totalAssets) }}</strong><small>截至 {{ backtestResult.finalQuoteDate }}</small></article>
          <article :class="tone(backtestResult.profit)"><span>模拟盈亏</span><strong>{{ signedMoney(backtestResult.profit) }}</strong><small>{{ percent(backtestResult.profitPercent) }}</small></article>
          <article><span>累计持股</span><strong>{{ Number(backtestResult.totalShares).toLocaleString() }} 股</strong><small>持仓市值 ¥{{ money(backtestResult.marketValue) }}</small></article>
          <article><span>持仓平均成本</span><strong>¥{{ money(backtestResult.averageCost) }}</strong><small>期末收盘 ¥{{ money(backtestResult.finalPrice) }}</small></article>
          <article><span>结转现金 / 佣金</span><strong>¥{{ money(backtestResult.remainingCash) }}</strong><small>累计佣金 ¥{{ money(backtestResult.totalCommission) }}</small></article>
        </div>
        <h4>定投与一次性买入对比</h4>
        <div class="table-scroll-wrapper">
          <el-table :data="comparisonRows" class="investment-data-table" stripe border resizable>
            <el-table-column prop="strategy" label="策略" min-width="130" />
            <el-table-column prop="investmentDate" label="开始买入日" min-width="120" />
            <el-table-column label="累计投入" min-width="120" align="right">
              <template #default="{ row }">¥{{ money(row.totalContribution) }}</template>
            </el-table-column>
            <el-table-column label="期末资产" min-width="120" align="right">
              <template #default="{ row }">¥{{ money(row.finalValue) }}</template>
            </el-table-column>
            <el-table-column label="盈亏" min-width="120" align="right">
              <template #default="{ row }"><span :class="tone(row.profit)">{{ signedMoney(row.profit) }}</span></template>
            </el-table-column>
            <el-table-column label="收益率" min-width="100" align="right">
              <template #default="{ row }"><span :class="tone(row.profitPercent)">{{ percent(row.profitPercent) }}</span></template>
            </el-table-column>
            <el-table-column label="佣金" min-width="100" align="right">
              <template #default="{ row }">¥{{ money(row.totalFees) }}</template>
            </el-table-column>
          </el-table>
        </div>
        <h4>定投明细</h4>
        <div class="table-scroll-wrapper">
          <el-table :data="recentSchedule" class="investment-data-table dca-detail-table" stripe border resizable max-height="360">
            <el-table-column prop="scheduledDate" label="计划日期" min-width="115" />
            <el-table-column prop="tradeDate" label="成交日期" min-width="115" />
            <el-table-column label="成交价" min-width="95" align="right">
              <template #default="{ row }">¥{{ money(row.price) }}</template>
            </el-table-column>
            <el-table-column label="本期投入" min-width="105" align="right">
              <template #default="{ row }">¥{{ money(row.contribution) }}</template>
            </el-table-column>
            <el-table-column label="买入股数" min-width="105" align="right">
              <template #default="{ row }">{{ Number(row.shares).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="累计持股" min-width="105" align="right">
              <template #default="{ row }">{{ Number(row.cumulativeShares).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="累计盈亏" min-width="110" align="right">
              <template #default="{ row }"><span :class="tone(row.profit)">{{ signedMoney(row.profit) }}</span></template>
            </el-table-column>
          </el-table>
        </div>
        <p class="fee-note">回测采用前复权日线收盘价、每笔佣金 0.03%（最低 ¥5）和 100 股整手买入；仅测算买入并按期末行情估值，不含卖出及印花税。历史表现不代表未来收益，不构成投资建议。</p>
      </template>
      <el-empty v-else-if="!backtestLoading" description="选择日期、定投日和金额后运行回测" :image-size="64" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { historySyncConcurrencyGet, simulateStockBacktest, stockHistory } from '@/api'

type Mode = 'history' | 'backtest'
type DataSource = 'api' | 'database'

interface StockHistoryPoint {
  date: string
  open: number
  close: number
  high: number
  low: number
  volume: number
  changePercent: number
}

interface ChartCoordinate {
  x: number
  y: number
}

interface StockBacktestEntry {
  scheduledDate: string
  tradeDate: string
  price: number
  contribution: number
  shares: number
  cumulativeShares: number
  profit: number
}

interface StockBacktestComparison {
  investmentDate: string
  totalContribution: number
  finalValue: number
  profit: number
  profitPercent: number
  totalFees: number
}

interface StockBacktestResult {
  code: string
  name: string
  startDate: string
  endDate: string
  weekday: number
  amountPerPeriod: number
  executedPeriods: number
  skippedPeriods: number
  totalContribution: number
  investedAmount: number
  totalCommission: number
  remainingCash: number
  totalShares: number
  averageCost: number
  finalPrice: number
  marketValue: number
  totalAssets: number
  profit: number
  profitPercent: number
  finalQuoteDate: string
  schedule: StockBacktestEntry[]
  lumpSumComparison: StockBacktestComparison | null
}

const props = defineProps<{ code: string; mode: Mode }>()
const today = new Date()
const dateString = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const oneYearAgo = new Date(today)
oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1)
const initialStartDate = dateString(oneYearAgo)
const initialEndDate = dateString(today)
const historyStartDate = ref(initialStartDate)
const historyEndDate = ref(initialEndDate)
const historySource = ref<DataSource>('api')
const historyProvider = ref('TENCENT')
const configuredProviders = ref<string[]>(['TENCENT'])
const providerOptions = [
  { label: '腾讯财经', value: 'TENCENT' },
  { label: '东方财富', value: 'EASTMONEY' }
]
const availableProviders = computed(() => providerOptions
  .filter(option => configuredProviders.value.includes(option.value)))
const historyResult = ref<{ code: string; name: string; points: StockHistoryPoint[] } | null>(null)
const historyLoading = ref(false)
const historyHoverIndex = ref<number | null>(null)
const chartType = ref<'line' | 'candlestick'>('line')
const backtestStartDate = ref(initialStartDate)
const backtestEndDate = ref(initialEndDate)
const weekday = ref(1)
const amount = ref(500)
const backtestResult = ref<StockBacktestResult | null>(null)
const backtestLoading = ref(false)
const weekdays = [
  { value: 1, label: '星期一' },
  { value: 2, label: '星期二' },
  { value: 3, label: '星期三' },
  { value: 4, label: '星期四' },
  { value: 5, label: '星期五' }
]

const historyPoints = computed(() => historyResult.value?.points || [])
const recentHistoryPoints = computed(() => [...historyPoints.value].slice(-20).reverse())
const historyHoverPoint = computed(() => historyHoverIndex.value === null
  ? null
  : historyPoints.value[historyHoverIndex.value] || null)
const historyMinClose = computed(() => historyPoints.value.length
  ? Math.min(...historyPoints.value.map(point => Number(point.close)))
  : 0)
const historyMaxClose = computed(() => historyPoints.value.length
  ? Math.max(...historyPoints.value.map(point => Number(point.close)))
  : 0)
const historyMinLow = computed(() => historyPoints.value.length
  ? Math.min(...historyPoints.value.map(point => Number(point.low)))
  : 0)
const historyMaxHigh = computed(() => historyPoints.value.length
  ? Math.max(...historyPoints.value.map(point => Number(point.high)))
  : 0)
const chartMinPrice = computed(() => chartType.value === 'candlestick' ? historyMinLow.value : historyMinClose.value)
const chartMaxPrice = computed(() => chartType.value === 'candlestick' ? historyMaxHigh.value : historyMaxClose.value)
const historyCandles = computed(() => {
  const points = historyPoints.value
  if (!points.length) return []
  const min = historyMinLow.value
  const max = historyMaxHigh.value
  const padding = max === min ? Math.max(max * 0.02, 0.01) : 0
  const range = max - min + padding * 2 || 1
  const yFor = (value: number) => 200 - (value - min + padding) / range * 180
  const width = Math.max(1.5, Math.min(10, 800 / points.length))
  return points.map((point, index) => {
    const x = points.length === 1 ? 500 : index / (points.length - 1) * 1000
    const openY = yFor(Number(point.open))
    const closeY = yFor(Number(point.close))
    const bodyY = Math.min(openY, closeY)
    return {
      date: point.date,
      x: Number(x.toFixed(1)),
      highY: yFor(Number(point.high)),
      lowY: yFor(Number(point.low)),
      bodyY,
      bodyHeight: Math.max(1, Math.abs(closeY - openY)),
      width,
      closeY,
      isUp: Number(point.close) >= Number(point.open)
    }
  })
})
const historyHoverCoordinate = computed<ChartCoordinate | null>(() => {
  if (historyHoverIndex.value === null) return null
  if (chartType.value === 'candlestick') {
    const candle = historyCandles.value[historyHoverIndex.value]
    return candle ? { x: candle.x, y: candle.closeY } : null
  }
  return historyChartCoordinates.value[historyHoverIndex.value] || null
})
const historyChartCoordinates = computed(() => {
  const points = historyPoints.value
  if (!points.length) return []
  const min = historyMinClose.value
  const max = historyMaxClose.value
  const padding = max === min ? Math.max(max * 0.02, 0.01) : 0
  const range = max - min + padding * 2 || 1
  return points.map((point, index) => {
    const x = points.length === 1 ? 500 : index / (points.length - 1) * 1000
    const y = 200 - (Number(point.close) - min + padding) / range * 180
    return { x: Number(x.toFixed(1)), y: Number(y.toFixed(1)) }
  })
})
const historyChartPoints = computed(() => historyChartCoordinates.value
  .map(({ x, y }) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' '))
const updateChartHover = (event: PointerEvent) => {
  const pointCount = historyPoints.value.length
  if (pointCount < 1) return
  const target = event.currentTarget
  if (!(target instanceof SVGElement)) return
  const { left, width } = target.getBoundingClientRect()
  if (width <= 0) return
  const normalizedX = Math.max(0, Math.min(1, (event.clientX - left) / width))
  historyHoverIndex.value = pointCount === 1 ? 0 : Math.round(normalizedX * (pointCount - 1))
}
const comparisonRows = computed(() => {
  const result = backtestResult.value
  if (!result) return []
  const rows = [{
    strategy: '每周定投',
    investmentDate: result.schedule?.[0]?.tradeDate || result.startDate,
    totalContribution: result.totalContribution,
    finalValue: result.totalAssets,
    profit: result.profit,
    profitPercent: result.profitPercent,
    totalFees: result.totalCommission
  }]
  if (result.lumpSumComparison) {
    rows.push({
      strategy: '一次性买入',
      investmentDate: result.lumpSumComparison.investmentDate,
      totalContribution: result.lumpSumComparison.totalContribution,
      finalValue: result.lumpSumComparison.finalValue,
      profit: result.lumpSumComparison.profit,
      profitPercent: result.lumpSumComparison.profitPercent,
      totalFees: result.lumpSumComparison.totalFees
    })
  }
  return rows
})
const recentSchedule = computed(() => [...(backtestResult.value?.schedule || [])].reverse())

onMounted(async () => {
  try {
    const response: any = await historySyncConcurrencyGet()
    configuredProviders.value = response.data.providers
    if (!configuredProviders.value.includes(historyProvider.value)) {
      historyProvider.value = configuredProviders.value[0]
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '读取历史行情 API 配置失败')
  }
})

const money = (value: number | null | undefined) =>
  Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const percent = (value: number | null | undefined) => {
  if (value == null) return '暂无'
  const number = Number(value)
  return `${number > 0 ? '+' : ''}${number.toFixed(2)}%`
}
const signedMoney = (value: number | null | undefined) => {
  if (value == null) return '暂无'
  const number = Number(value)
  return `${number > 0 ? '+' : number < 0 ? '-' : ''}¥${money(Math.abs(number))}`
}
const tone = (value: number | null | undefined) =>
  Number(value || 0) >= 0 ? 'is-profit' : 'is-loss'

watch(() => props.code, () => {
  historyResult.value = null
  historyHoverIndex.value = null
  backtestResult.value = null
  historyStartDate.value = initialStartDate
  historyEndDate.value = initialEndDate
  backtestStartDate.value = initialStartDate
  backtestEndDate.value = initialEndDate
})

const loadHistory = async () => {
  historyLoading.value = true
  historyResult.value = null
  historyHoverIndex.value = null
  try {
    const response: any = await stockHistory({
      code: props.code,
      startDate: historyStartDate.value,
      endDate: historyEndDate.value,
      source: historySource.value,
      provider: historyProvider.value
    })
    historyResult.value = response.data
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查询股票历史行情失败')
  } finally {
    historyLoading.value = false
  }
}

const runBacktest = async () => {
  backtestLoading.value = true
  backtestResult.value = null
  try {
    const response: any = await simulateStockBacktest({
      code: props.code,
      startDate: backtestStartDate.value,
      endDate: backtestEndDate.value,
      amount: amount.value,
      weekday: weekday.value
    })
    backtestResult.value = response.data
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '股票回测失败')
  } finally {
    backtestLoading.value = false
  }
}
</script>

<style scoped>
.stock-market-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.panel-description,
.fee-note {
  margin: 0;
  color: #8792a2;
  font-size: 12px;
  line-height: 1.7;
}
.market-form {
  margin-bottom: 4px;
}
.history-caption,
.chart-dates {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #8792a2;
  font-size: 12px;
}
.history-caption {
  margin-bottom: 12px;
  color: #34495e;
}
.chart-type-tabs {
  height: 38px;
}
.chart-type-tabs :deep(.el-tabs__header) {
  margin-bottom: 4px;
}
.chart-type-tabs :deep(.el-tabs__content) {
  display: none;
}
.history-chart {
  position: relative;
  height: 250px;
  margin-bottom: 20px;
  padding: 8px 36px 22px 48px;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: linear-gradient(180deg, #fbfdfd, #fff);
}
.history-chart svg {
  width: 100%;
  height: 200px;
  overflow: visible;
}
.chart-grid {
  stroke: #e8edf2;
  stroke-dasharray: 4 5;
}
.chart-line {
  fill: none;
  stroke: #0f9f9a;
  stroke-width: 2.5;
  vector-effect: non-scaling-stroke;
}
.candle-wick {
  stroke-width: 1.2;
  vector-effect: non-scaling-stroke;
}
.candle-body {
  stroke-width: 1;
  vector-effect: non-scaling-stroke;
}
.candle-up {
  fill: #d9534f;
  stroke: #d9534f;
}
.candle-down {
  fill: #16a085;
  stroke: #16a085;
}
.chart-interaction-area {
  fill: transparent;
  cursor: crosshair;
  touch-action: pan-y;
}
.chart-crosshair {
  stroke: #64748b;
  stroke-dasharray: 4 4;
  stroke-width: 1.2;
  vector-effect: non-scaling-stroke;
  pointer-events: none;
}
.chart-hover-dot {
  fill: #fff;
  stroke: #0f9f9a;
  stroke-width: 2.5;
  vector-effect: non-scaling-stroke;
  pointer-events: none;
}
.chart-hover-label {
  position: absolute;
  top: 0;
  right: 36px;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px 12px;
  max-width: calc(100% - 100px);
  padding: 6px 9px;
  border: 1px solid #e8edf2;
  border-radius: 6px;
  background: rgb(255 255 255 / 94%);
  box-shadow: 0 2px 8px rgb(35 52 72 / 8%);
  color: #586579;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.chart-hover-label strong {
  color: #29384a;
}
.chart-value {
  position: absolute;
  left: 8px;
  color: #8792a2;
  font-size: 11px;
}
.chart-top {
  top: 6px;
}
.chart-bottom {
  bottom: 30px;
}
.chart-dates {
  position: absolute;
  right: 36px;
  bottom: 6px;
  left: 48px;
}
.backtest-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 4px 0 18px;
}
.backtest-summary article {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  border: 1px solid #e8edf2;
  border-radius: 8px;
}
.backtest-summary article span,
.backtest-summary article small {
  color: #8792a2;
  font-size: 12px;
}
.backtest-summary article strong {
  color: #26384b;
  font-size: 16px;
}
.backtest-summary article.is-profit strong,
.is-profit {
  color: #d84a4a;
}
.backtest-summary article.is-loss strong,
.is-loss {
  color: #12936f;
}
h4 {
  margin: 18px 0 10px;
  color: #26384b;
}
@media (max-width: 750px) {
  .backtest-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .market-form :deep(.el-date-editor),
  .market-form :deep(.el-input-number) {
    width: 100%;
  }
}
@media (max-width: 480px) {
  .backtest-summary {
    grid-template-columns: 1fr;
  }
}
</style>
