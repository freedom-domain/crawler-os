<template>
  <div v-if="entries.length" class="dca-performance-chart">
    <div class="dca-chart-legend" aria-label="图表曲线显示">
      <button
        type="button"
        :class="{ 'is-hidden': !visibility.value }"
        :aria-pressed="visibility.value"
        @click="toggleSeries('value')"
      ><i class="legend-price" />{{ valueLabel }}</button>
      <button
        type="button"
        :class="{ 'is-hidden': !visibility.dailyProfit }"
        :aria-pressed="visibility.dailyProfit"
        @click="toggleSeries('dailyProfit')"
      ><i class="legend-daily" />当天盈亏</button>
      <button
        type="button"
        :class="{ 'is-hidden': !visibility.cumulativeProfit }"
        :aria-pressed="visibility.cumulativeProfit"
        @click="toggleSeries('cumulativeProfit')"
      ><i class="legend-cumulative" />累计盈亏</button>
    </div>
    <div class="dca-chart-body">
      <div v-if="visibility.value" class="dca-chart-axis">
        <span>{{ formatAxis(priceRange.max) }}</span>
        <span>{{ formatAxis(priceRange.min) }}</span>
      </div>
      <svg
        class="dca-chart-svg"
        viewBox="0 0 1000 220"
        preserveAspectRatio="none"
        role="img"
        :aria-label="`${valueLabel}、当天盈亏和累计盈亏走势`"
        @pointermove="updateHover"
        @pointerleave="hoverIndex = null"
      >
        <line x1="0" y1="20" x2="1000" y2="20" class="dca-chart-grid" />
        <line x1="0" y1="110" x2="1000" y2="110" class="dca-chart-grid" />
        <line x1="0" y1="200" x2="1000" y2="200" class="dca-chart-grid" />
        <polyline v-if="visibility.value" :points="valuePoints" class="dca-chart-line dca-chart-value" />
        <polyline v-if="visibility.dailyProfit" :points="dailyProfitPoints" class="dca-chart-line dca-chart-daily" />
        <polyline
          v-if="visibility.cumulativeProfit"
          :points="cumulativeProfitPoints"
          class="dca-chart-line dca-chart-cumulative"
        />
        <template v-if="hoveredEntry">
          <line :x1="hoverX" y1="20" :x2="hoverX" y2="200" class="dca-chart-crosshair" />
          <circle v-if="visibility.value" :cx="hoverX" :cy="hoverValueY" r="5" class="dca-chart-dot dca-chart-value-dot" />
          <circle v-if="visibility.dailyProfit" :cx="hoverX" :cy="hoverDailyY" r="4" class="dca-chart-dot dca-chart-daily-dot" />
          <circle
            v-if="visibility.cumulativeProfit"
            :cx="hoverX"
            :cy="hoverCumulativeY"
            r="4"
            class="dca-chart-dot dca-chart-cumulative-dot"
          />
          <rect x="0" y="20" width="1000" height="180" class="dca-chart-interaction-area" />
        </template>
        <rect
          v-else
          x="0"
          y="20"
          width="1000"
          height="180"
          class="dca-chart-interaction-area"
        />
      </svg>
      <div v-if="visibility.dailyProfit || visibility.cumulativeProfit" class="dca-chart-axis dca-chart-right-axis">
        <span>{{ formatAxis(profitRange.max) }}</span>
        <span>{{ formatAxis(profitRange.min) }}</span>
      </div>
    </div>
    <div class="dca-chart-dates">
      <span>{{ entries[0].date }}</span>
      <span>{{ entries[entries.length - 1].date }}</span>
    </div>
    <div v-if="hoveredEntry" class="dca-chart-tooltip" aria-live="polite">
      <strong>{{ hoveredEntry.date }}</strong>
      <span v-if="visibility.value" class="tooltip-value">{{ valueLabel }} ¥{{ formatAxis(hoveredEntry.value) }}</span>
      <span v-if="visibility.dailyProfit" class="tooltip-daily">当天盈亏 {{ signedMoney(hoveredEntry.dailyProfit) }}</span>
      <span v-if="visibility.cumulativeProfit" class="tooltip-cumulative">累计盈亏 {{ signedMoney(hoveredEntry.cumulativeProfit) }}</span>
    </div>
    <div class="dca-chart-axis-labels">
      <span>{{ visibility.value ? valueLabel : '' }}</span>
      <span>{{ visibility.dailyProfit || visibility.cumulativeProfit ? '盈亏（元）' : '' }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'

interface DcaChartEntry {
  date: string
  value: number
  dailyProfit: number
  cumulativeProfit: number
}

const props = defineProps<{
  entries: DcaChartEntry[]
  valueLabel: string
}>()
const visibility = reactive({
  value: true,
  dailyProfit: true,
  cumulativeProfit: true
})
const hoverIndex = ref<number | null>(null)
const hoveredEntry = computed(() => hoverIndex.value === null ? null : props.entries[hoverIndex.value] || null)
const toggleSeries = (series: keyof typeof visibility) => {
  visibility[series] = !visibility[series]
}

const rangeFor = (values: number[]) => {
  const min = Math.min(...values)
  const max = Math.max(...values)
  const padding = max === min ? Math.max(Math.abs(max) * 0.02, 0.01) : (max - min) * 0.05
  return { min: min - padding, max: max + padding }
}

const priceRange = computed(() => rangeFor(props.entries.map(entry => entry.value)))
const profitRange = computed(() => {
  const values = props.entries.flatMap(entry => [
    ...(visibility.dailyProfit ? [entry.dailyProfit] : []),
    ...(visibility.cumulativeProfit ? [entry.cumulativeProfit] : [])
  ])
  return rangeFor([0, ...values])
})

const pointsFor = (values: number[], range: { min: number; max: number }) => values
  .map((value, index) => {
    const x = values.length < 2 ? 500 : index / (values.length - 1) * 1000
    const y = 200 - (value - range.min) / (range.max - range.min) * 180
    return `${x.toFixed(1)},${y.toFixed(1)}`
  })
  .join(' ')

const valuePoints = computed(() => pointsFor(props.entries.map(entry => entry.value), priceRange.value))
const dailyProfitPoints = computed(() => pointsFor(
  props.entries.map(entry => entry.dailyProfit),
  profitRange.value
))
const cumulativeProfitPoints = computed(() => pointsFor(
  props.entries.map(entry => entry.cumulativeProfit),
  profitRange.value
))
const yFor = (value: number, range: { min: number; max: number }) =>
  200 - (value - range.min) / (range.max - range.min) * 180
const hoverX = computed(() => {
  if (hoverIndex.value === null || props.entries.length < 2) return 500
  return hoverIndex.value / (props.entries.length - 1) * 1000
})
const hoverValueY = computed(() => hoveredEntry.value
  ? yFor(hoveredEntry.value.value, priceRange.value)
  : 0)
const hoverDailyY = computed(() => hoveredEntry.value
  ? yFor(hoveredEntry.value.dailyProfit, profitRange.value)
  : 0)
const hoverCumulativeY = computed(() => hoveredEntry.value
  ? yFor(hoveredEntry.value.cumulativeProfit, profitRange.value)
  : 0)
const updateHover = (event: PointerEvent) => {
  const target = event.currentTarget
  if (!(target instanceof SVGElement) || props.entries.length === 0) return
  const { left, width } = target.getBoundingClientRect()
  if (width <= 0) return
  const fraction = Math.max(0, Math.min(1, (event.clientX - left) / width))
  hoverIndex.value = props.entries.length === 1
    ? 0
    : Math.round(fraction * (props.entries.length - 1))
}
const formatAxis = (value: number) => value.toLocaleString('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})
const signedMoney = (value: number) => `${value > 0 ? '+' : ''}¥${formatAxis(value)}`
</script>

<style scoped>
.dca-performance-chart {
  margin: 20px 0;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.dca-chart-legend,
.dca-chart-axis-labels,
.dca-chart-dates {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dca-chart-legend {
  justify-content: center;
  gap: 24px;
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.dca-chart-legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.dca-chart-legend button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 6px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--el-text-color-secondary);
  font: inherit;
  cursor: pointer;
}

.dca-chart-legend button:hover,
.dca-chart-legend button:focus-visible {
  background: var(--el-fill-color-light);
  outline: none;
}

.dca-chart-legend button.is-hidden {
  opacity: 0.45;
  text-decoration: line-through;
}

.dca-chart-legend i {
  width: 14px;
  height: 3px;
  border-radius: 2px;
}

.legend-price {
  background: #409eff;
}

.legend-daily {
  background: #e6a23c;
}

.legend-cumulative {
  background: #67c23a;
}

.dca-chart-body {
  display: flex;
  align-items: stretch;
  gap: 8px;
  height: 180px;
}

.dca-chart-axis {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  justify-content: space-between;
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 1;
}

.dca-chart-svg {
  width: 100%;
  height: 100%;
  overflow: visible;
}

.dca-chart-grid {
  stroke: var(--el-border-color-lighter);
  stroke-dasharray: 4 5;
  stroke-width: 1;
}

.dca-chart-line {
  fill: none;
  stroke-width: 2.5;
  vector-effect: non-scaling-stroke;
}

.dca-chart-interaction-area {
  fill: transparent;
  pointer-events: all;
  cursor: crosshair;
}

.dca-chart-crosshair {
  stroke: var(--el-text-color-secondary);
  stroke-dasharray: 4 4;
  stroke-width: 1;
  pointer-events: none;
}

.dca-chart-dot {
  stroke: var(--el-bg-color);
  stroke-width: 2;
  pointer-events: none;
}

.dca-chart-value-dot {
  fill: #409eff;
}

.dca-chart-daily-dot {
  fill: #e6a23c;
}

.dca-chart-cumulative-dot {
  fill: #67c23a;
}

.dca-chart-value {
  stroke: #409eff;
}

.dca-chart-daily {
  stroke: #e6a23c;
}

.dca-chart-cumulative {
  stroke: #67c23a;
}

.dca-chart-right-axis {
  text-align: right;
}

.dca-chart-dates {
  margin: 8px 48px 0;
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.dca-chart-tooltip {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px 18px;
  margin-top: 10px;
  color: var(--el-text-color-primary);
  font-size: 12px;
}

.tooltip-value {
  color: #409eff;
}

.tooltip-daily {
  color: #b88230;
}

.tooltip-cumulative {
  color: #529b2e;
}

.dca-chart-axis-labels {
  margin: 6px 48px 0;
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

@media (max-width: 600px) {
  .dca-performance-chart {
    padding: 12px 8px;
  }

  .dca-chart-legend {
    gap: 10px;
    font-size: 11px;
  }

  .dca-chart-body {
    gap: 4px;
    height: 150px;
  }

  .dca-chart-axis {
    font-size: 9px;
  }

  .dca-chart-dates,
  .dca-chart-axis-labels {
    margin-right: 38px;
    margin-left: 38px;
  }
}
</style>
