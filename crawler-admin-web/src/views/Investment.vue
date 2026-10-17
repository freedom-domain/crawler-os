<template>
  <section class="investment-page">
    <template v-if="pageType === 'overview'">
    <header v-if="!tradeOnly" class="page-heading">
      <div>
        <p>查看虚拟账户资产、持仓与最近成交。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadAccount">刷新资产</el-button>
    </header>

    <div v-if="!tradeOnly" class="asset-grid" v-loading="loading">
      <article class="asset-card">
        <span>总资产</span>
        <strong>¥{{ money(account?.totalAssets) }}</strong>
        <small>初始虚拟资金 ¥{{ money(account?.initialCash) }}</small>
      </article>
      <article class="asset-card">
        <span>可用资金</span>
        <strong>¥{{ money(account?.cashBalance) }}</strong>
        <small>下单费用从此处扣除</small>
      </article>
      <article class="asset-card">
        <span>持仓市值</span>
        <strong>¥{{ money(account?.marketValue) }}</strong>
        <small>{{ account?.positions?.length || 0 }} 个持仓标的</small>
      </article>
      <article class="asset-card" :class="profitTone(account?.totalProfit)">
        <span>累计收益</span>
        <strong>{{ signedMoney(account?.totalProfit) }}</strong>
        <small>{{ signedPercent(account?.profitPercent) }}</small>
      </article>
    </div>
    <div v-else class="asset-grid" v-loading="loading">
      <article class="asset-card">
        <span>当前股票持有数量</span>
        <strong>{{ currentStockPosition?.quantity || 0 }} 股</strong>
        <small>{{ currentStockCode || '尚未选择股票' }}</small>
      </article>
      <article class="asset-card">
        <span>当前股票持仓成本</span>
        <strong>¥{{ money(currentStockPosition?.costValue) }}</strong>
        <small>成本价 ¥{{ money(currentStockPosition?.averageCost) }}</small>
      </article>
      <article class="asset-card">
        <span>当前股票持仓市值</span>
        <strong>¥{{ money(currentStockPosition?.marketValue) }}</strong>
        <small>现价 ¥{{ money(currentStockPosition?.currentPrice) }}</small>
      </article>
      <article class="asset-card" :class="profitTone(currentStockPosition?.unrealizedProfit)">
        <span>当前股票浮动盈亏</span>
        <strong>{{ signedMoney(currentStockPosition?.unrealizedProfit) }}</strong>
        <small>{{ signedPercent(currentStockPosition?.profitPercent) }}</small>
      </article>
    </div>

    <el-tabs v-if="tradeOnly" v-model="tradeActiveTab" class="trade-only-tabs">
      <el-tab-pane label="模拟交易" name="trade" />
      <el-tab-pane label="持仓" name="positions" />
      <el-tab-pane label="最近成交" name="trades" />
    </el-tabs>
    <el-tabs v-else v-model="overviewActiveTab" class="trade-only-tabs">
      <el-tab-pane label="我的持仓" name="positions" />
      <el-tab-pane label="最近成交" name="trades" />
    </el-tabs>
    </template>

    <template v-if="pageType === 'overview' && tradeOnly && tradeActiveTab === 'trade'">
    <el-card class="quote-card" shadow="never">
      <template #header>
        <div class="section-heading">
          <div>
            <strong>行情与交易</strong>
            <span>输入 6 位证券代码查询 A 股或场内基金</span>
          </div>
        </div>
      </template>
      <el-form class="quote-form" inline @submit.prevent="loadQuote">
        <el-form-item label="证券代码">
          <el-input
            v-model="code"
            maxlength="6"
            placeholder="例如 600000"
            inputmode="numeric"
            :readonly="tradeOnly"
            @keyup.enter="loadQuote"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="quoteLoading" @click="loadQuote">查询行情</el-button>
        </el-form-item>
      </el-form>

      <div v-if="quote" class="quote-result">
        <div class="quote-summary">
          <div>
            <strong>{{ quote.name }}</strong>
            <el-tag size="small" effect="plain">{{ quote.instrumentType }}</el-tag>
            <span class="quote-code">{{ quote.code }} · {{ quote.market }}</span>
          </div>
          <div class="quote-price" :class="profitTone(quote.changePercent)">
            <strong>¥{{ money(quote.currentPrice) }}</strong>
            <span>涨跌 {{ signedPercent(quote.changePercent) }}</span>
          </div>
        </div>
        <div class="quote-meta">
          <span>昨收 ¥{{ money(quote.previousClose) }}</span>
          <span>行情时间 {{ quote.quoteTime || '暂无' }}</span>
        </div>
        <div class="quote-actions">
          <el-button type="danger" :disabled="!quote" @click="openTrade('BUY')">买入</el-button>
          <el-button type="success" plain :disabled="quoteAvailableQuantity < 100" @click="openTrade('SELL')">卖出</el-button>
        </div>
      </div>
      <el-empty v-else-if="!quoteLoading" description="查询行情后可进行模拟交易" :image-size="64" />
      <p class="fee-note">模拟费用：佣金 0.03%（每笔最低 ¥5）；卖出股票另收 0.05% 印花税；交易按 100 股/份整数倍，实行 T+1。</p>
    </el-card>
    </template>

    <template v-if="pageType === 'onshore-fund'">
    <el-tabs v-model="onshoreActiveTab" class="fund-tabs">
    <el-tab-pane label="基金历史行情" name="history">
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="section-heading">
          <div>
            <strong>基金历史行情</strong>
            <span>查询场内基金日线、周线或月线，行情复权由数据源提供</span>
          </div>
        </div>
      </template>
      <el-form class="history-form" inline @submit.prevent="loadFundHistory">
        <el-form-item label="基金代码">
          <FundCodeSelect v-model="fundCode" fund-type="ONSHORE" />
        </el-form-item>
        <el-form-item label="周期">
          <el-select v-model="historyFrequency" style="width: 100px">
            <el-option label="日线" value="day" />
            <el-option label="周线" value="week" />
            <el-option label="月线" value="month" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据来源">
          <el-select v-model="historySource" style="width: 180px">
            <el-option label="API 查询并缓存" value="api" />
            <el-option label="数据库" value="database" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="historySource === 'api'" label="历史行情 API">
          <el-select v-model="historyProvider" style="width: 180px">
            <el-option v-for="option in availableHistoryProviders" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="historySource === 'api' && availableHistoryProviders.length === 0">
          <span class="fee-note">当前启用的 API 不支持场内基金，请到历史行情 API 设置中启用东方财富。</span>
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="historyStartDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="historyEndDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="historyLoading"
            :disabled="historySource === 'api' && availableHistoryProviders.length === 0"
            @click="loadFundHistory"
          >查询历史行情</el-button>
        </el-form-item>
      </el-form>
      <div v-if="historyResult" class="history-result">
        <div class="history-caption">
          <strong>{{ historyResult.name }}（{{ historyResult.code }}）</strong>
          <span>{{ historyResult.points.length }} 个{{ frequencyLabel(historyFrequency) }}数据点</span>
        </div>
        <div v-if="historyResult.points.length" class="history-chart">
          <div class="chart-value chart-top">¥{{ money(historyMaxClose) }}</div>
          <svg viewBox="0 0 1000 220" preserveAspectRatio="none" role="img" :aria-label="`${historyResult.name}历史收盘价走势`">
            <line x1="0" y1="30" x2="1000" y2="30" class="chart-grid" />
            <line x1="0" y1="110" x2="1000" y2="110" class="chart-grid" />
            <line x1="0" y1="190" x2="1000" y2="190" class="chart-grid" />
            <polyline :points="historyChartPoints" class="chart-line" />
            <template v-if="historyHoverIndex !== null">
              <line
                :x1="historyChartCoordinates[historyHoverIndex]?.x"
                y1="20"
                :x2="historyChartCoordinates[historyHoverIndex]?.x"
                y2="200"
                class="chart-crosshair"
              />
              <circle
                :cx="historyChartCoordinates[historyHoverIndex]?.x"
                :cy="historyChartCoordinates[historyHoverIndex]?.y"
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
              @pointermove="updateChartHover($event, historyPoints.length, 'onshore')"
              @pointerleave="historyHoverIndex = null"
            />
          </svg>
          <div v-if="historyHoverPoint" class="chart-hover-label">
            <strong>{{ historyHoverPoint.date }}</strong>
            <span>收盘 ¥{{ money(historyHoverPoint.close) }}</span>
            <span :class="profitTone(historyHoverPoint.changePercent)">涨跌 {{ signedPercent(historyHoverPoint.changePercent) }}</span>
          </div>
          <div class="chart-value chart-bottom">¥{{ money(historyMinClose) }}</div>
          <div class="chart-dates">
            <span>{{ historyResult.points[0]?.date }}</span>
            <span>{{ historyResult.points[historyResult.points.length - 1]?.date }}</span>
          </div>
        </div>
        <div v-if="historyResult.analysis" class="analysis-grid">
          <article class="asset-card" :class="profitTone(historyResult.analysis.totalReturnPercent)">
            <span>区间收益率</span>
            <strong>{{ analysisPercent(historyResult.analysis.totalReturnPercent, true) }}</strong>
            <small>{{ historyResult.analysis.periodDays }} 个自然日</small>
          </article>
          <article class="asset-card" :class="profitTone(historyResult.analysis.annualizedReturnPercent)">
            <span>年化收益率</span>
            <strong>{{ analysisPercent(historyResult.analysis.annualizedReturnPercent, true) }}</strong>
            <small>按实际区间折算</small>
          </article>
          <article class="asset-card is-loss">
            <span>最大回撤</span>
            <strong>{{ drawdownPercent(historyResult.analysis.maxDrawdownPercent) }}</strong>
            <small>历史峰值至后续低点</small>
          </article>
          <article class="asset-card">
            <span>年化波动率</span>
            <strong>{{ analysisPercent(historyResult.analysis.annualizedVolatilityPercent) }}</strong>
            <small>按{{ frequencyLabel(historyResult.frequency) }}收益率年化</small>
          </article>
          <article class="asset-card">
            <span>夏普比率</span>
            <strong>{{ analysisRatio(historyResult.analysis.sharpeRatio, historyResult.analysis.observationCount, 'sharpe') }}</strong>
            <small>无风险利率按 0% 估算</small>
          </article>
          <article class="asset-card">
            <span>索提诺比率</span>
            <strong>{{ analysisRatio(historyResult.analysis.sortinoRatio, historyResult.analysis.observationCount, 'sortino') }}</strong>
            <small>仅计入下行波动</small>
          </article>
        </div>
        <div v-if="historyResult.analysis" class="analysis-tables">
          <div>
            <h4>月度收益</h4>
            <el-table :data="recentMonthlyReturns" class="market-history-table" stripe max-height="320">
              <el-table-column prop="period" label="月份" min-width="110" />
              <el-table-column label="收益率" min-width="100" align="right">
                <template #default="{ row }"><span :class="profitTone(row.returnPercent)">{{ signedPercent(row.returnPercent) }}</span></template>
              </el-table-column>
            </el-table>
          </div>
          <div>
            <h4>滚动一年收益</h4>
            <el-table :data="recentRollingReturns" class="market-history-table" stripe max-height="320">
              <el-table-column prop="period" label="截至日期" min-width="120" />
              <el-table-column label="近一年收益率" min-width="120" align="right">
                <template #default="{ row }"><span :class="profitTone(row.returnPercent)">{{ signedPercent(row.returnPercent) }}</span></template>
              </el-table-column>
            </el-table>
            <p v-if="!historyResult.analysis.rollingOneYearReturns?.length" class="fee-note">所选数据尚不足一年，暂无完整滚动一年数据。</p>
          </div>
        </div>
        <el-table :data="recentHistoryPoints" class="market-history-table" stripe max-height="360">
          <el-table-column prop="date" label="日期" min-width="120" />
          <el-table-column label="开盘" min-width="100" align="right">
            <template #default="{ row }">¥{{ money(row.open) }}</template>
          </el-table-column>
          <el-table-column label="收盘" min-width="100" align="right">
            <template #default="{ row }">¥{{ money(row.close) }}</template>
          </el-table-column>
          <el-table-column label="最高" min-width="100" align="right">
            <template #default="{ row }">¥{{ money(row.high) }}</template>
          </el-table-column>
          <el-table-column label="最低" min-width="100" align="right">
            <template #default="{ row }">¥{{ money(row.low) }}</template>
          </el-table-column>
          <el-table-column label="涨跌幅" min-width="100" align="right">
            <template #default="{ row }"><span :class="profitTone(row.changePercent)">{{ signedPercent(row.changePercent) }}</span></template>
          </el-table-column>
        </el-table>
        <p class="fee-note">收益率按复权收盘价计算；夏普/索提诺假定无风险利率为 0%，波动率按所选周期年化。月度收益首月因缺少区间前数据不计；历史表现不代表未来收益，不构成投资建议。</p>
      </div>
      <el-empty v-else-if="!historyLoading" description="选择基金代码和日期范围后查询历史行情" :image-size="64" />
    </el-card>
    </el-tab-pane>
    <el-tab-pane label="定投回测" name="dca">
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="section-heading">
          <div>
            <strong>定投回测</strong>
            <span>按指定星期定投，遇休市顺延至当周下一个交易日；不足一手的资金结转</span>
          </div>
        </div>
      </template>
      <el-form class="history-form" inline @submit.prevent="runDcaSimulation">
        <el-form-item label="基金代码">
          <FundCodeSelect v-model="fundCode" fund-type="ONSHORE" />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="dcaStartDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="dcaEndDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="每周定投日">
          <el-select v-model="dcaWeekday" style="width: 110px">
            <el-option v-for="day in weekdays" :key="day.value" :label="day.label" :value="day.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="每期金额">
          <el-input-number v-model="dcaAmount" :min="1" :max="10000000" :precision="2" :step="100" controls-position="right" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="dcaLoading" @click="runDcaSimulation">运行回测</el-button>
        </el-form-item>
      </el-form>
      <template v-if="dcaResult">
        <div class="dca-summary">
          <article><span>累计投入</span><strong>¥{{ money(dcaResult.totalContribution) }}</strong></article>
          <article><span>期末资产（含结余）</span><strong>¥{{ money(dcaResult.totalAssets) }}</strong></article>
          <article :class="profitTone(dcaResult.profit)"><span>模拟盈亏</span><strong>{{ signedMoney(dcaResult.profit) }}</strong><small>{{ signedPercent(dcaResult.profitPercent) }}</small></article>
          <article><span>累计份额</span><strong>{{ Number(dcaResult.totalShares).toLocaleString() }}</strong></article>
          <article><span>结转现金</span><strong>¥{{ money(dcaResult.remainingCash) }}</strong></article>
        </div>
        <div class="dca-comparison">
          <h4>定投与一次性买入（相同累计投入）</h4>
          <el-table :data="dcaComparisonRows" stripe>
            <el-table-column prop="strategy" label="策略" min-width="130" />
            <el-table-column prop="investmentDate" label="开始买入日" min-width="120" />
            <el-table-column label="累计投入" min-width="120" align="right">
              <template #default="{ row }">¥{{ money(row.totalContribution) }}</template>
            </el-table-column>
            <el-table-column label="期末资产" min-width="120" align="right">
              <template #default="{ row }">¥{{ money(row.finalValue) }}</template>
            </el-table-column>
            <el-table-column label="盈亏" min-width="120" align="right">
              <template #default="{ row }"><span :class="profitTone(row.profit)">{{ signedMoney(row.profit) }}（{{ signedPercent(row.profitPercent) }}）</span></template>
            </el-table-column>
            <el-table-column label="费用" min-width="100" align="right">
              <template #default="{ row }">¥{{ money(row.totalFees) }}</template>
            </el-table-column>
          </el-table>
          <p class="fee-note">一次性买入假设在区间首个交易日投入全部定投累计金额；场内按整手和佣金规则计算，未用现金计入期末资产。</p>
        </div>
        <p class="fee-note">
          {{ dcaResult.name }}（{{ dcaResult.code }}）· 成功定投 {{ dcaResult.executedPeriods }} 期
          <span v-if="dcaResult.skippedPeriods">· 休市跳过 {{ dcaResult.skippedPeriods }} 期</span>
          · 总佣金 ¥{{ money(dcaResult.totalCommission) }}
          · 期末估值日期 {{ dcaResult.finalQuoteDate }}（收盘价 ¥{{ money(dcaResult.finalPrice) }}）。
          按 100 份为一手买入，佣金 0.03%（每笔最低 ¥5），不含其他交易费用。
        </p>
        <el-table :data="dcaResult.schedule" stripe max-height="460">
          <el-table-column prop="scheduledDate" label="计划定投日" min-width="120" />
          <el-table-column prop="tradeDate" label="实际交易日" min-width="120" />
          <el-table-column label="收盘价" min-width="100" align="right">
            <template #default="{ row }">¥{{ money(row.price) }}</template>
          </el-table-column>
          <el-table-column label="本期投入" min-width="110" align="right">
            <template #default="{ row }">¥{{ money(row.contribution) }}</template>
          </el-table-column>
          <el-table-column prop="shares" label="买入份额" min-width="100" align="right" />
          <el-table-column label="成交金额" min-width="110" align="right">
            <template #default="{ row }">¥{{ money(row.investedAmount) }}</template>
          </el-table-column>
          <el-table-column label="佣金" min-width="90" align="right">
            <template #default="{ row }">¥{{ money(row.commission) }}</template>
          </el-table-column>
          <el-table-column prop="cumulativeShares" label="累计份额" min-width="110" align="right" />
          <el-table-column label="累计盈亏" min-width="115" align="right">
            <template #default="{ row }"><span :class="profitTone(row.profit)">{{ signedMoney(row.profit) }}</span></template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-else-if="!dcaLoading" description="设置基金、定投金额和时间段后运行回测" :image-size="64" />
    </el-card>
    </el-tab-pane>
    </el-tabs>
    </template>

    <template v-if="pageType === 'offshore-fund'">
    <el-tabs v-model="offshoreActiveTab" class="fund-tabs">
    <el-tab-pane label="基金历史行情" name="history">
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="section-heading">
          <div>
            <strong>场外基金净值查询</strong>
            <span>按基金代码查询历史单位净值和累计净值</span>
          </div>
        </div>
      </template>
      <el-form class="history-form" inline @submit.prevent="loadOffExchangeFundHistory">
        <el-form-item label="场外基金代码">
          <FundCodeSelect v-model="offExchangeFundCode" fund-type="OFFSHORE" />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="offExchangeHistoryStartDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="offExchangeHistoryEndDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="数据来源">
          <el-select v-model="offExchangeHistorySource" style="width: 180px">
            <el-option label="API 查询并缓存" value="api" />
            <el-option label="数据库" value="database" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="offExchangeHistoryLoading" @click="loadOffExchangeFundHistory">查询历史净值</el-button>
        </el-form-item>
      </el-form>
      <div v-if="offExchangeHistoryResult" class="history-result">
        <div class="history-caption">
          <strong>{{ offExchangeHistoryResult.name }}（{{ offExchangeHistoryResult.code }}）</strong>
          <span>{{ offExchangeHistoryResult.points.length }} 个净值数据点</span>
        </div>
        <div v-if="offExchangeHistoryResult.points.length" class="history-chart">
          <div class="chart-value chart-top">¥{{ money(offExchangeNavMax) }}</div>
          <svg viewBox="0 0 1000 220" preserveAspectRatio="none" role="img" :aria-label="`${offExchangeHistoryResult.name}历史单位净值走势`">
            <line x1="0" y1="30" x2="1000" y2="30" class="chart-grid" />
            <line x1="0" y1="110" x2="1000" y2="110" class="chart-grid" />
            <line x1="0" y1="190" x2="1000" y2="190" class="chart-grid" />
            <polyline :points="offExchangeNavChartPoints" class="chart-line" />
            <template v-if="offExchangeHoverIndex !== null">
              <line
                :x1="offExchangeNavChartCoordinates[offExchangeHoverIndex]?.x"
                y1="20"
                :x2="offExchangeNavChartCoordinates[offExchangeHoverIndex]?.x"
                y2="200"
                class="chart-crosshair"
              />
              <circle
                :cx="offExchangeNavChartCoordinates[offExchangeHoverIndex]?.x"
                :cy="offExchangeNavChartCoordinates[offExchangeHoverIndex]?.y"
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
              @pointermove="updateChartHover($event, offExchangeNavPoints.length, 'offshore')"
              @pointerleave="offExchangeHoverIndex = null"
            />
          </svg>
          <div v-if="offExchangeHoverPoint" class="chart-hover-label">
            <strong>{{ offExchangeHoverPoint.date }}</strong>
            <span>单位净值 ¥{{ money(offExchangeHoverPoint.unitNav) }}</span>
            <span :class="profitTone(offExchangeHoverPoint.changePercent)">涨跌 {{ signedPercent(offExchangeHoverPoint.changePercent) }}</span>
          </div>
          <div class="chart-value chart-bottom">¥{{ money(offExchangeNavMin) }}</div>
          <div class="chart-dates">
            <span>{{ offExchangeHistoryResult.points[0]?.date }}</span>
            <span>{{ offExchangeHistoryResult.points[offExchangeHistoryResult.points.length - 1]?.date }}</span>
          </div>
        </div>
        <div v-if="offExchangeHistoryResult.analysis" class="analysis-grid">
          <article class="asset-card" :class="profitTone(offExchangeHistoryResult.analysis.totalReturnPercent)">
            <span>区间收益率</span>
            <strong>{{ analysisPercent(offExchangeHistoryResult.analysis.totalReturnPercent, true) }}</strong>
            <small>{{ offExchangeHistoryResult.analysis.periodDays }} 个自然日</small>
          </article>
          <article class="asset-card" :class="profitTone(offExchangeHistoryResult.analysis.annualizedReturnPercent)">
            <span>年化收益率</span>
            <strong>{{ analysisPercent(offExchangeHistoryResult.analysis.annualizedReturnPercent, true) }}</strong>
            <small>按实际区间折算</small>
          </article>
          <article class="asset-card is-loss">
            <span>最大回撤</span>
            <strong>{{ drawdownPercent(offExchangeHistoryResult.analysis.maxDrawdownPercent) }}</strong>
            <small>历史峰值至后续低点</small>
          </article>
          <article class="asset-card">
            <span>年化波动率</span>
            <strong>{{ analysisPercent(offExchangeHistoryResult.analysis.annualizedVolatilityPercent) }}</strong>
            <small>按净值日收益率年化</small>
          </article>
          <article class="asset-card">
            <span>夏普比率</span>
            <strong>{{ analysisRatio(offExchangeHistoryResult.analysis.sharpeRatio, offExchangeHistoryResult.analysis.observationCount, 'sharpe') }}</strong>
            <small>无风险利率按 0% 估算</small>
          </article>
          <article class="asset-card">
            <span>索提诺比率</span>
            <strong>{{ analysisRatio(offExchangeHistoryResult.analysis.sortinoRatio, offExchangeHistoryResult.analysis.observationCount, 'sortino') }}</strong>
            <small>仅计入下行波动</small>
          </article>
        </div>
        <div v-if="offExchangeHistoryResult.analysis" class="analysis-tables">
          <div>
            <h4>月度收益</h4>
            <el-table :data="recentOffExchangeMonthlyReturns" class="market-history-table" stripe max-height="320">
              <el-table-column prop="period" label="月份" min-width="110" />
              <el-table-column label="收益率" min-width="100" align="right">
                <template #default="{ row }"><span :class="profitTone(row.returnPercent)">{{ signedPercent(row.returnPercent) }}</span></template>
              </el-table-column>
            </el-table>
          </div>
          <div>
            <h4>滚动一年收益</h4>
            <el-table :data="recentOffExchangeRollingReturns" class="market-history-table" stripe max-height="320">
              <el-table-column prop="period" label="截至日期" min-width="120" />
              <el-table-column label="近一年收益率" min-width="120" align="right">
                <template #default="{ row }"><span :class="profitTone(row.returnPercent)">{{ signedPercent(row.returnPercent) }}</span></template>
              </el-table-column>
            </el-table>
            <p v-if="!offExchangeHistoryResult.analysis.rollingOneYearReturns?.length" class="fee-note">所选数据尚不足一年，暂无完整滚动一年数据。</p>
          </div>
        </div>
        <el-table :data="recentOffExchangeNavPoints" class="market-history-table" stripe max-height="360">
          <el-table-column prop="date" label="净值日期" min-width="120" />
          <el-table-column label="单位净值" min-width="110" align="right">
            <template #default="{ row }">¥{{ money(row.unitNav) }}</template>
          </el-table-column>
          <el-table-column label="累计净值" min-width="110" align="right">
            <template #default="{ row }">{{ row.cumulativeNav == null ? '-' : `¥${money(row.cumulativeNav)}` }}</template>
          </el-table-column>
          <el-table-column label="日涨跌幅" min-width="110" align="right">
            <template #default="{ row }"><span :class="profitTone(row.changePercent)">{{ signedPercent(row.changePercent) }}</span></template>
          </el-table-column>
        </el-table>
        <p class="fee-note">收益率优先使用累计净值计算，缺少累计净值时使用单位净值；夏普/索提诺假定无风险利率为 0%，年化波动率按净值日收益率估算。月度收益首月因缺少区间前数据不计；历史表现不代表未来收益，不构成投资建议。</p>
      </div>
      <el-empty v-else-if="!offExchangeHistoryLoading" description="输入场外基金代码和日期范围后查询净值" :image-size="64" />
    </el-card>
    </el-tab-pane>
    <el-tab-pane label="定投回测" name="dca">
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="section-heading">
          <div>
            <strong>场外基金定投回测</strong>
            <span>按单位净值计算份额，可设置申购费率和期末赎回费率</span>
          </div>
        </div>
      </template>
      <el-form class="history-form" inline @submit.prevent="runOffExchangeFundDca">
        <el-form-item label="场外基金代码">
          <FundCodeSelect v-model="offExchangeFundCode" fund-type="OFFSHORE" />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="offExchangeDcaStartDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="offExchangeDcaEndDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="每周定投日">
          <el-select v-model="offExchangeDcaWeekday" style="width: 110px">
            <el-option v-for="day in weekdays" :key="day.value" :label="day.label" :value="day.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="每期金额">
          <el-input-number v-model="offExchangeDcaAmount" :min="1" :max="10000000" :precision="2" :step="100" controls-position="right" />
        </el-form-item>
        <el-form-item label="申购费率 (%)">
          <el-input-number v-model="offExchangeSubscriptionFeeRate" :min="0" :max="100" :precision="4" :step="0.01" controls-position="right" />
        </el-form-item>
        <el-form-item label="赎回费率 (%)">
          <el-input-number v-model="offExchangeRedemptionFeeRate" :min="0" :max="100" :precision="4" :step="0.01" controls-position="right" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="offExchangeDcaLoading" @click="runOffExchangeFundDca">运行回测</el-button>
        </el-form-item>
      </el-form>
      <template v-if="offExchangeDcaResult">
        <div class="dca-summary">
          <article><span>累计投入</span><strong>¥{{ money(offExchangeDcaResult.totalContribution) }}</strong></article>
          <article><span>期末赎回净值</span><strong>¥{{ money(offExchangeDcaResult.netLiquidationValue) }}</strong></article>
          <article :class="profitTone(offExchangeDcaResult.profit)"><span>模拟盈亏</span><strong>{{ signedMoney(offExchangeDcaResult.profit) }}</strong><small>{{ signedPercent(offExchangeDcaResult.profitPercent) }}</small></article>
          <article><span>累计份额</span><strong>{{ money(offExchangeDcaResult.totalShares) }}</strong></article>
          <article><span>申购费 / 赎回费</span><strong>¥{{ money(offExchangeDcaResult.totalSubscriptionFee) }} / ¥{{ money(offExchangeDcaResult.redemptionFee) }}</strong></article>
        </div>
        <div class="dca-comparison">
          <h4>定投与一次性买入（相同累计投入）</h4>
          <el-table :data="offExchangeDcaComparisonRows" stripe>
            <el-table-column prop="strategy" label="策略" min-width="130" />
            <el-table-column prop="investmentDate" label="开始买入日" min-width="120" />
            <el-table-column label="累计投入" min-width="120" align="right">
              <template #default="{ row }">¥{{ money(row.totalContribution) }}</template>
            </el-table-column>
            <el-table-column label="期末赎回净值" min-width="130" align="right">
              <template #default="{ row }">¥{{ money(row.finalValue) }}</template>
            </el-table-column>
            <el-table-column label="盈亏" min-width="120" align="right">
              <template #default="{ row }"><span :class="profitTone(row.profit)">{{ signedMoney(row.profit) }}（{{ signedPercent(row.profitPercent) }}）</span></template>
            </el-table-column>
            <el-table-column label="申购 / 赎回费" min-width="120" align="right">
              <template #default="{ row }">¥{{ money(row.subscriptionFee) }} / ¥{{ money(row.redemptionFee) }}</template>
            </el-table-column>
          </el-table>
          <p class="fee-note">一次性买入假设在区间首个净值日投入全部定投累计金额；两种策略使用相同申购费率、期末赎回费率和估值日期。</p>
        </div>
        <p class="fee-note">
          {{ offExchangeDcaResult.name }}（{{ offExchangeDcaResult.code }}）· 成功定投 {{ offExchangeDcaResult.executedPeriods }} 期
          <span v-if="offExchangeDcaResult.skippedPeriods">· 无净值日期跳过 {{ offExchangeDcaResult.skippedPeriods }} 期</span>
          · 期末估值日期 {{ offExchangeDcaResult.finalNavDate }}（单位净值 ¥{{ money(offExchangeDcaResult.finalNav) }}）。
          每期申购费按输入费率从投入金额中扣除；期末赎回费按赎回费率估算。实际申购确认、折扣及持有期限费率以基金公告为准。
        </p>
        <el-table :data="offExchangeDcaResult.schedule" stripe max-height="460">
          <el-table-column prop="scheduledDate" label="计划定投日" min-width="120" />
          <el-table-column prop="tradeDate" label="净值日期" min-width="120" />
          <el-table-column label="单位净值" min-width="100" align="right">
            <template #default="{ row }">¥{{ money(row.nav) }}</template>
          </el-table-column>
          <el-table-column label="本期投入" min-width="110" align="right">
            <template #default="{ row }">¥{{ money(row.contribution) }}</template>
          </el-table-column>
          <el-table-column label="申购费" min-width="100" align="right">
            <template #default="{ row }">¥{{ money(row.subscriptionFee) }}</template>
          </el-table-column>
          <el-table-column label="买入份额" min-width="100" align="right">
            <template #default="{ row }">{{ money(row.shares) }}</template>
          </el-table-column>
          <el-table-column label="累计份额" min-width="110" align="right">
            <template #default="{ row }">{{ money(row.cumulativeShares) }}</template>
          </el-table-column>
          <el-table-column label="累计盈亏" min-width="115" align="right">
            <template #default="{ row }"><span :class="profitTone(row.profit)">{{ signedMoney(row.profit) }}</span></template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-else-if="!offExchangeDcaLoading" description="设置场外基金、定投金额、费率和时间段后运行回测" :image-size="64" />
    </el-card>
    </el-tab-pane>
    </el-tabs>
    </template>

    <template v-if="pageType === 'overview' && (tradeOnly ? tradeActiveTab === 'positions' : overviewActiveTab === 'positions')">
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="section-heading">
          <div>
            <strong>{{ tradeOnly ? `${currentStockName}（${currentStockCode}）持仓` : '我的持仓' }}</strong>
            <span>{{ tradeOnly ? '当前股票持仓，盈亏按行情估值且未扣除卖出费用' : '持仓盈亏按行情估值，未扣除卖出费用' }}</span>
          </div>
        </div>
      </template>
      <el-table :data="displayedPositions" v-loading="loading" stripe>
        <el-table-column prop="code" label="代码" width="100" />
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="instrumentType" label="类型" width="90" />
        <el-table-column prop="quantity" label="持有数量" width="105" align="right" />
        <el-table-column prop="availableQuantity" label="可卖数量" width="105" align="right" />
        <el-table-column label="成本价" width="105" align="right">
          <template #default="{ row }">¥{{ money(row.averageCost) }}</template>
        </el-table-column>
        <el-table-column label="现价" width="105" align="right">
          <template #default="{ row }">¥{{ money(row.currentPrice) }}</template>
        </el-table-column>
        <el-table-column label="市值" width="125" align="right">
          <template #default="{ row }">¥{{ money(row.marketValue) }}</template>
        </el-table-column>
        <el-table-column label="浮动盈亏" width="135" align="right">
          <template #default="{ row }">
            <span :class="profitTone(row.unrealizedProfit)">{{ signedMoney(row.unrealizedProfit) }}</span>
          </template>
        </el-table-column>
        <template #empty><el-empty :description="tradeOnly ? '当前股票暂无持仓' : '暂无持仓，查询证券代码后即可模拟买入'" /></template>
      </el-table>
    </el-card>
    </template>

    <template v-if="pageType === 'overview' && (tradeOnly ? tradeActiveTab === 'trades' : overviewActiveTab === 'trades')">
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="section-heading">
          <div>
            <strong>{{ tradeOnly ? `${currentStockName}（${currentStockCode}）最近成交` : '最近成交' }}</strong>
            <span>{{ tradeOnly ? '当前股票最近 50 笔模拟成交' : '最多显示最近 50 笔模拟交易' }}</span>
          </div>
        </div>
      </template>
      <el-table :data="displayedRecentTrades" v-loading="loading || tradesLoading" stripe>
        <el-table-column label="成交时间" min-width="170">
          <template #default="{ row }">{{ dateTime(row.tradeTime) }}</template>
        </el-table-column>
        <el-table-column prop="code" label="代码" width="100" />
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="方向" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.side === 'BUY' ? 'danger' : 'success'" size="small">{{ row.side === 'BUY' ? '买入' : '卖出' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column label="成交价" width="105" align="right">
          <template #default="{ row }">¥{{ money(row.price) }}</template>
        </el-table-column>
        <el-table-column label="成交金额" width="120" align="right">
          <template #default="{ row }">¥{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="交易费用" width="120" align="right">
          <template #default="{ row }">¥{{ money(Number(row.commission) + Number(row.stampTax)) }}</template>
        </el-table-column>
        <template #empty><el-empty :description="tradeOnly ? '当前股票暂无成交记录' : '暂无成交记录'" /></template>
      </el-table>
    </el-card>
    </template>

    <template v-if="pageType === 'overview'">
    <el-dialog v-model="tradeDialogVisible" :title="tradeSide === 'BUY' ? '模拟买入' : '模拟卖出'" width="440px">
      <div v-if="quote" class="trade-dialog-summary">
        <strong>{{ quote.name }}（{{ quote.code }}）</strong>
        <span>最新参考价 ¥{{ money(quote.currentPrice) }} · {{ quote.market }}</span>
        <span v-if="tradeSide === 'SELL'">可卖数量：{{ currentAvailableQuantity }} 股/份</span>
      </div>
      <el-form label-width="100px">
        <el-form-item label="交易数量">
          <el-input-number
            v-model="tradeQuantity"
            :min="100"
            :max="tradeSide === 'SELL' ? currentAvailableQuantity : undefined"
            :step="100"
            :precision="0"
            controls-position="right"
          />
          <span class="quantity-unit">股/份</span>
        </el-form-item>
        <el-form-item v-if="quote" label="预计成交额">
          <span>¥{{ money(estimatedAmount) }}</span>
        </el-form-item>
        <el-form-item v-if="quote" label="预计佣金">
          <span>¥{{ money(estimatedCommission) }}</span>
        </el-form-item>
        <el-form-item v-if="quote && tradeSide === 'SELL' && quote.instrumentType === '股票'" label="预计印花税">
          <span>¥{{ money(estimatedStampTax) }}</span>
        </el-form-item>
        <el-form-item v-if="quote" label="预计资金变动">
          <strong>{{ tradeSide === 'BUY' ? '-' : '+' }}¥{{ money(estimatedNetAmount) }}</strong>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tradeDialogVisible = false">取消</el-button>
        <el-button :type="tradeSide === 'BUY' ? 'danger' : 'success'" :loading="trading" @click="submitTrade">
          确认{{ tradeSide === 'BUY' ? '买入' : '卖出' }}
        </el-button>
      </template>
    </el-dialog>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import FundCodeSelect from '@/components/FundCodeSelect.vue'
import {
  fundHistory,
  historySyncConcurrencyGet,
  investmentAccount,
  investmentQuote,
  investmentTrade,
  investmentTrades,
  offExchangeFundHistory,
  simulateFundDca,
  simulateOffExchangeFundDca
} from '@/api'

const props = defineProps<{
  pageType: 'overview' | 'onshore-fund' | 'offshore-fund'
  initialCode?: string
  tradeOnly?: boolean
}>()

interface Quote {
  code: string
  name: string
  market: string
  instrumentType: string
  currentPrice: number
  previousClose: number
  changePercent: number
  quoteTime: string
}

interface ChartCoordinate {
  x: number
  y: number
}

const account = ref<any>(null)
const stockRecentTrades = ref<any[]>([])
const tradesLoading = ref(false)
const code = ref('')
const quote = ref<Quote | null>(null)
const quoteLoading = ref(false)
const loading = ref(false)
const trading = ref(false)
const tradeDialogVisible = ref(false)
const tradeSide = ref<'BUY' | 'SELL'>('BUY')
const tradeQuantity = ref(100)
const currentAvailableQuantity = ref(0)
const today = new Date()
const currentStockCode = computed(() => quote.value?.code || code.value.trim() || props.initialCode || '')
const currentStockName = computed(() =>
  quote.value?.name
  || account.value?.positions?.find((item: any) => item.code === currentStockCode.value)?.name
  || currentStockCode.value
  || '当前股票'
)
const currentStockPosition = computed(() =>
  account.value?.positions?.find((item: any) => item.code === currentStockCode.value) || null
)
const toDateString = (date: Date) => {
  const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
  return localDate.toISOString().slice(0, 10)
}
const oneYearAgo = new Date(today)
oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1)
const initialStartDate = toDateString(oneYearAgo)
const initialEndDate = toDateString(today)
const onshoreActiveTab = ref('history')
const offshoreActiveTab = ref('history')
const tradeActiveTab = ref<'trade' | 'positions' | 'trades'>('trade')
const overviewActiveTab = ref<'positions' | 'trades'>('positions')
const fundCode = ref('')
const historyStartDate = ref(initialStartDate)
const historyEndDate = ref(initialEndDate)
const historyFrequency = ref('day')
const historySource = ref<'api' | 'database'>('api')
const historyProvider = ref('TENCENT')
const configuredHistoryProviders = ref<string[]>(['TENCENT'])
const historyProviderOptions = [
  { label: '腾讯财经', value: 'TENCENT' },
  { label: '东方财富', value: 'EASTMONEY' }
]
const availableHistoryProviders = computed(() => historyProviderOptions
  .filter(option => configuredHistoryProviders.value.includes(option.value)))
const historyResult = ref<any>(null)
const historyLoading = ref(false)
const historyHoverIndex = ref<number | null>(null)
const dcaStartDate = ref(initialStartDate)
const dcaEndDate = ref(initialEndDate)
const dcaWeekday = ref(1)
const dcaAmount = ref(500)
const dcaResult = ref<any>(null)
const dcaLoading = ref(false)
const offExchangeFundCode = ref('')
const offExchangeHistoryStartDate = ref(initialStartDate)
const offExchangeHistoryEndDate = ref(initialEndDate)
const offExchangeHistorySource = ref<'api' | 'database'>('api')
const offExchangeHistoryResult = ref<any>(null)
const offExchangeHistoryLoading = ref(false)
const offExchangeHoverIndex = ref<number | null>(null)
const offExchangeDcaStartDate = ref(initialStartDate)
const offExchangeDcaEndDate = ref(initialEndDate)
const offExchangeDcaWeekday = ref(1)
const offExchangeDcaAmount = ref(500)
const offExchangeSubscriptionFeeRate = ref(0.15)
const offExchangeRedemptionFeeRate = ref(0.5)
const offExchangeDcaResult = ref<any>(null)
const offExchangeDcaLoading = ref(false)
const weekdays = [
  { value: 1, label: '星期一' },
  { value: 2, label: '星期二' },
  { value: 3, label: '星期三' },
  { value: 4, label: '星期四' },
  { value: 5, label: '星期五' }
]

watch(
  () => [props.pageType, props.initialCode] as const,
  ([pageType, initialCode]) => {
    if (!initialCode) return
    if (pageType === 'overview') {
      code.value = initialCode
    } else if (pageType === 'onshore-fund') {
      fundCode.value = initialCode
    } else if (pageType === 'offshore-fund') {
      offExchangeFundCode.value = initialCode
    }
  },
  { immediate: true }
)

const quoteAvailableQuantity = computed(() =>
  account.value?.positions?.find((item: any) => item.code === quote.value?.code)?.availableQuantity || 0
)
const displayedPositions = computed(() => {
  const positions = account.value?.positions || []
  return props.tradeOnly
    ? positions.filter((item: any) => item.code === currentStockCode.value)
    : positions
})
const displayedRecentTrades = computed(() => props.tradeOnly ? stockRecentTrades.value : account.value?.recentTrades || [])
const historyPoints = computed(() => historyResult.value?.points || [])
const recentHistoryPoints = computed(() => [...historyPoints.value].slice(-20).reverse())
const historyHoverPoint = computed(() => historyHoverIndex.value === null
  ? null
  : historyPoints.value[historyHoverIndex.value] || null)
const recentMonthlyReturns = computed(() => [...(historyResult.value?.analysis?.monthlyReturns || [])].slice(-12).reverse())
const recentRollingReturns = computed(() => [...(historyResult.value?.analysis?.rollingOneYearReturns || [])].slice(-12).reverse())
const offExchangeNavPoints = computed(() => offExchangeHistoryResult.value?.points || [])
const recentOffExchangeNavPoints = computed(() => [...offExchangeNavPoints.value].slice(-20).reverse())
const offExchangeHoverPoint = computed(() => offExchangeHoverIndex.value === null
  ? null
  : offExchangeNavPoints.value[offExchangeHoverIndex.value] || null)
const recentOffExchangeMonthlyReturns = computed(() =>
  [...(offExchangeHistoryResult.value?.analysis?.monthlyReturns || [])].slice(-12).reverse())
const recentOffExchangeRollingReturns = computed(() =>
  [...(offExchangeHistoryResult.value?.analysis?.rollingOneYearReturns || [])].slice(-12).reverse())
const dcaComparisonRows = computed(() => {
  const result = dcaResult.value
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
const offExchangeDcaComparisonRows = computed(() => {
  const result = offExchangeDcaResult.value
  if (!result) return []
  const rows = [{
    strategy: '每周定投',
    investmentDate: result.schedule?.[0]?.tradeDate || result.startDate,
    totalContribution: result.totalContribution,
    finalValue: result.netLiquidationValue,
    profit: result.profit,
    profitPercent: result.profitPercent,
    subscriptionFee: result.totalSubscriptionFee,
    redemptionFee: result.redemptionFee
  }]
  if (result.lumpSumComparison) {
    rows.push({
      strategy: '一次性买入',
      investmentDate: result.lumpSumComparison.investmentDate,
      totalContribution: result.lumpSumComparison.totalContribution,
      finalValue: result.lumpSumComparison.finalValue,
      profit: result.lumpSumComparison.profit,
      profitPercent: result.lumpSumComparison.profitPercent,
      subscriptionFee: result.lumpSumComparison.subscriptionFee,
      redemptionFee: result.lumpSumComparison.redemptionFee
    })
  }
  return rows
})
const offExchangeNavMin = computed(() => offExchangeNavPoints.value.length
  ? Math.min(...offExchangeNavPoints.value.map((point: any) => Number(point.unitNav)))
  : 0)
const offExchangeNavMax = computed(() => offExchangeNavPoints.value.length
  ? Math.max(...offExchangeNavPoints.value.map((point: any) => Number(point.unitNav)))
  : 0)
const offExchangeNavChartCoordinates = computed<ChartCoordinate[]>(() => {
  const points = offExchangeNavPoints.value
  if (!points.length) return []
  const values = points.map((point: any) => Number(point.unitNav))
  const min = Math.min(...values)
  const max = Math.max(...values)
  const padding = max === min ? Math.max(max * 0.02, 0.01) : 0
  const range = max - min + padding * 2 || 1
  return values.map((value: number, index: number) => {
    const x = points.length === 1 ? 500 : index / (points.length - 1) * 1000
    const y = 200 - (value - min + padding) / range * 180
    return { x: Number(x.toFixed(1)), y: Number(y.toFixed(1)) }
  })
})
const offExchangeNavChartPoints = computed(() => offExchangeNavChartCoordinates.value
  .map(({ x, y }) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' '))
const historyMinClose = computed(() => historyPoints.value.length
  ? Math.min(...historyPoints.value.map((point: any) => Number(point.close)))
  : 0)
const historyMaxClose = computed(() => historyPoints.value.length
  ? Math.max(...historyPoints.value.map((point: any) => Number(point.close)))
  : 0)
const historyChartCoordinates = computed<ChartCoordinate[]>(() => {
  const points = historyPoints.value
  if (!points.length) return []
  const values = points.map((point: any) => Number(point.close))
  const min = Math.min(...values)
  const max = Math.max(...values)
  const padding = max === min ? Math.max(max * 0.02, 0.01) : 0
  const range = max - min + padding * 2 || 1
  return values.map((value: number, index: number) => {
    const x = points.length === 1 ? 500 : index / (points.length - 1) * 1000
    const y = 200 - (value - min + padding) / range * 180
    return { x: Number(x.toFixed(1)), y: Number(y.toFixed(1)) }
  })
})
const historyChartPoints = computed(() => historyChartCoordinates.value
  .map(({ x, y }) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' '))

const updateChartHover = (event: PointerEvent, pointCount: number, chart: 'onshore' | 'offshore') => {
  if (pointCount < 1) return
  const target = event.currentTarget
  if (!(target instanceof SVGElement)) return
  const { left, width } = target.getBoundingClientRect()
  if (width <= 0) return
  const normalizedX = Math.max(0, Math.min(1, (event.clientX - left) / width))
  const index = pointCount === 1 ? 0 : Math.round(normalizedX * (pointCount - 1))
  if (chart === 'onshore') historyHoverIndex.value = index
  else offExchangeHoverIndex.value = index
}
const estimatedAmount = computed(() => Number(quote.value?.currentPrice || 0) * tradeQuantity.value)
const estimatedCommission = computed(() => Math.max(5, Math.ceil(estimatedAmount.value * 0.0003 * 100) / 100))
const estimatedStampTax = computed(() =>
  tradeSide.value === 'SELL' && quote.value?.instrumentType === '股票'
    ? Math.ceil(estimatedAmount.value * 0.0005 * 100) / 100
    : 0
)
const estimatedNetAmount = computed(() =>
  tradeSide.value === 'BUY'
    ? estimatedAmount.value + estimatedCommission.value
    : estimatedAmount.value - estimatedCommission.value - estimatedStampTax.value
)

const money = (value: unknown) => {
  const number = Number(value)
  return Number.isFinite(number) ? number.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '0.00'
}
const signedMoney = (value: unknown) => {
  const number = Number(value || 0)
  return `${number > 0 ? '+' : ''}¥${money(number)}`
}
const signedPercent = (value: unknown) => {
  const number = Number(value || 0)
  return `${number > 0 ? '+' : ''}${number.toFixed(2)}%`
}
const analysisPercent = (value: unknown, signed = false) => {
  if (value == null || !Number.isFinite(Number(value))) return '-'
  const number = Number(value)
  return `${signed && number > 0 ? '+' : ''}${number.toFixed(2)}%`
}
const analysisRatio = (value: unknown, observationCount: number, ratioType: 'sharpe' | 'sortino') => {
  if (value != null && Number.isFinite(Number(value))) return Number(value).toFixed(2)
  if (observationCount < 3) return '数据不足'
  return ratioType === 'sortino' ? '无下行波动' : '无波动'
}
const drawdownPercent = (value: unknown) => {
  if (value == null || !Number.isFinite(Number(value))) return '-'
  const number = Math.abs(Number(value))
  return `${number > 0 ? '-' : ''}${number.toFixed(2)}%`
}
const profitTone = (value: unknown) => Number(value || 0) > 0 ? 'is-profit' : Number(value || 0) < 0 ? 'is-loss' : ''
const dateTime = (value: string) => value ? value.replace('T', ' ').slice(0, 19) : '-'
const frequencyLabel = (frequency: string) => ({ day: '日线', week: '周线', month: '月线' }[frequency] || '行情')

const loadCurrentStockTrades = async (stockCode = currentStockCode.value) => {
  if (!props.tradeOnly || !stockCode) {
    stockRecentTrades.value = []
    return
  }
  tradesLoading.value = true
  try {
    const response: any = await investmentTrades(stockCode)
    if (stockCode === currentStockCode.value) {
      stockRecentTrades.value = response.data || []
    }
  } catch {
    stockRecentTrades.value = []
  } finally {
    tradesLoading.value = false
  }
}

const loadAccount = async () => {
  loading.value = true
  try {
    const response: any = await investmentAccount()
    account.value = response.data
    await loadCurrentStockTrades()
  } catch {
    account.value = null
  } finally {
    loading.value = false
  }
}

const loadQuote = async () => {
  quoteLoading.value = true
  quote.value = null
  try {
    const response: any = await investmentQuote(code.value.trim())
    if (response.data.instrumentType !== '股票') {
      ElMessage.warning('股票模拟交易页面仅支持 A 股，请在场内基金页面查询 ETF')
      return
    }
    quote.value = response.data
    code.value = response.data.code
    await loadCurrentStockTrades(response.data.code)
  } catch {
    quote.value = null
    stockRecentTrades.value = []
  } finally {
    quoteLoading.value = false
  }
}

const openTrade = async (side: 'BUY' | 'SELL', symbol = code.value) => {
  if (symbol !== quote.value?.code) {
    code.value = symbol
    await loadQuote()
  }
  if (!quote.value) return
  tradeSide.value = side
  tradeQuantity.value = 100
  const holding = account.value?.positions?.find((item: any) => item.code === quote.value?.code)
  currentAvailableQuantity.value = holding?.availableQuantity || 0
  tradeDialogVisible.value = true
}

const submitTrade = async () => {
  if (!quote.value) return
  if (tradeQuantity.value < 100 || tradeQuantity.value % 100 !== 0) {
    ElMessage.warning('交易数量必须为 100 的整数倍')
    return
  }
  if (tradeSide.value === 'SELL' && tradeQuantity.value > currentAvailableQuantity.value) {
    ElMessage.warning('交易数量超过当前可卖数量')
    return
  }
  trading.value = true
  try {
    await investmentTrade({ code: quote.value.code, side: tradeSide.value, quantity: tradeQuantity.value })
    ElMessage.success(`模拟${tradeSide.value === 'BUY' ? '买入' : '卖出'}已成交`)
    tradeDialogVisible.value = false
    await loadAccount()
    await loadQuote()
  } catch {
    // The shared API interceptor displays request errors.
  } finally {
    trading.value = false
  }
}

const loadFundHistory = async () => {
  historyLoading.value = true
  historyResult.value = null
  historyHoverIndex.value = null
  try {
    const response: any = await fundHistory({
      code: fundCode.value.trim(),
      startDate: historyStartDate.value,
      endDate: historyEndDate.value,
      frequency: historyFrequency.value,
      source: historySource.value,
      provider: historyProvider.value
    })
    historyResult.value = response.data
  } catch {
    historyResult.value = null
  } finally {
    historyLoading.value = false
  }
}

const runDcaSimulation = async () => {
  dcaLoading.value = true
  dcaResult.value = null
  try {
    const response: any = await simulateFundDca({
      code: fundCode.value.trim(),
      startDate: dcaStartDate.value,
      endDate: dcaEndDate.value,
      amount: dcaAmount.value,
      weekday: dcaWeekday.value
    })
    dcaResult.value = response.data
  } catch {
    dcaResult.value = null
  } finally {
    dcaLoading.value = false
  }
}

const loadOffExchangeFundHistory = async () => {
  offExchangeHistoryLoading.value = true
  offExchangeHistoryResult.value = null
  offExchangeHoverIndex.value = null
  try {
    const response: any = await offExchangeFundHistory({
      code: offExchangeFundCode.value.trim(),
      startDate: offExchangeHistoryStartDate.value,
      endDate: offExchangeHistoryEndDate.value,
      source: offExchangeHistorySource.value
    })
    offExchangeHistoryResult.value = response.data
  } catch {
    offExchangeHistoryResult.value = null
  } finally {
    offExchangeHistoryLoading.value = false
  }
}

const runOffExchangeFundDca = async () => {
  offExchangeDcaLoading.value = true
  offExchangeDcaResult.value = null
  try {
    const response: any = await simulateOffExchangeFundDca({
      code: offExchangeFundCode.value.trim(),
      startDate: offExchangeDcaStartDate.value,
      endDate: offExchangeDcaEndDate.value,
      amount: offExchangeDcaAmount.value,
      weekday: offExchangeDcaWeekday.value,
      subscriptionFeeRate: offExchangeSubscriptionFeeRate.value,
      redemptionFeeRate: offExchangeRedemptionFeeRate.value
    })
    offExchangeDcaResult.value = response.data
  } catch {
    offExchangeDcaResult.value = null
  } finally {
    offExchangeDcaLoading.value = false
  }
}

onMounted(() => {
  void historySyncConcurrencyGet().then((response: any) => {
    configuredHistoryProviders.value = response.data.providers
    if (!configuredHistoryProviders.value.includes(historyProvider.value)) {
      historyProvider.value = configuredHistoryProviders.value[0]
    }
  }).catch((error) => {
    ElMessage.error(error instanceof Error ? error.message : '读取历史行情 API 配置失败')
  })
  if (props.pageType === 'overview') {
    loadAccount()
    if (props.initialCode) {
      void loadQuote()
    }
  }
})
</script>

<style scoped>
.investment-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.page-heading,
.section-heading,
.quote-summary,
.quote-meta,
.quote-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.page-heading h1 {
  margin: 0;
  color: #243345;
  font-size: 22px;
}
.page-heading p,
.section-heading span,
.asset-card small,
.quote-meta,
.fee-note,
.trade-dialog-summary span {
  color: #8792a2;
  font-size: 13px;
}
.page-heading p {
  margin: 6px 0 0;
}
.asset-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.asset-card {
  display: flex;
  min-height: 120px;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: 18px 20px;
  border: 1px solid #e8edf2;
  border-radius: 10px;
  background: #fff;
}
.asset-card > span {
  color: #788496;
  font-size: 13px;
}
.asset-card strong {
  color: #253448;
  font-size: 24px;
  font-variant-numeric: tabular-nums;
}
.asset-card small {
  font-size: 12px;
}
.quote-card,
.table-card {
  border-radius: 10px;
}
.section-heading {
  justify-content: flex-start;
}
.section-heading > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.section-heading strong {
  color: #29384a;
  font-size: 15px;
}
.quote-form {
  margin-bottom: 0;
}
.quote-form :deep(.el-input) {
  width: 180px;
}
.history-form {
  margin-bottom: 0;
}
.history-form :deep(.el-input) {
  width: 150px;
}
.history-form :deep(.el-input-number) {
  width: 150px;
}
.history-result {
  margin-top: 18px;
}
.analysis-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 16px 0 20px;
}
.analysis-grid .asset-card {
  min-height: 104px;
  padding: 14px 16px;
}
.analysis-grid .asset-card strong {
  font-size: 20px;
}
.analysis-tables {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin: 18px 0;
}
.analysis-tables h4,
.dca-comparison h4 {
  margin: 0 0 10px;
  color: #29384a;
  font-size: 14px;
}
.dca-comparison {
  margin: 18px 0;
}
.history-caption,
.chart-dates {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.history-caption {
  margin-bottom: 12px;
  color: #29384a;
}
.history-caption span {
  color: #8792a2;
  font-size: 13px;
}
.history-chart {
  position: relative;
  height: 230px;
  margin: 8px 0 22px;
  padding: 0 38px;
}
.history-chart svg {
  width: 100%;
  height: 200px;
  overflow: visible;
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
  right: 38px;
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
.chart-grid {
  stroke: #e8edf2;
  stroke-width: 1;
  vector-effect: non-scaling-stroke;
}
.chart-line {
  fill: none;
  stroke: #0f9f9a;
  stroke-width: 2.5;
  stroke-linecap: round;
  stroke-linejoin: round;
  vector-effect: non-scaling-stroke;
}
.chart-value {
  position: absolute;
  left: 0;
  color: #8792a2;
  font-size: 12px;
}
.chart-top {
  top: 10px;
}
.chart-bottom {
  top: 168px;
}
.chart-dates {
  margin: 0 0 0 38px;
  color: #8792a2;
  font-size: 12px;
}
.dca-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin: 12px 0;
}
.dca-summary article {
  display: flex;
  min-height: 92px;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f7f9fb;
}
.dca-summary article span,
.dca-summary article small {
  color: #8792a2;
  font-size: 12px;
}
.dca-summary article strong {
  color: #29384a;
  font-size: 18px;
  font-variant-numeric: tabular-nums;
}
.dca-summary article.is-profit strong {
  color: #e35d6a;
}
.dca-summary article.is-loss strong {
  color: #16a085;
}
.quote-result {
  padding: 16px;
  border-radius: 8px;
  background: #f7f9fb;
}
.quote-summary > div:first-child {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.quote-summary strong {
  color: #27384c;
  font-size: 17px;
}
.quote-code {
  color: #8792a2;
  font-size: 13px;
}
.quote-price {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
.quote-price strong {
  font-size: 22px;
}
.quote-price span {
  font-size: 13px;
}
.quote-meta {
  justify-content: flex-start;
  gap: 24px;
  margin-top: 12px;
}
.quote-actions {
  justify-content: flex-start;
  margin-top: 16px;
}
.fee-note {
  margin: 12px 0 0;
  line-height: 1.6;
}
.is-profit {
  color: #e35d6a !important;
}
.is-loss {
  color: #16a085 !important;
}
.trade-dialog-summary {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-bottom: 22px;
  padding: 14px;
  border-radius: 8px;
  background: #f7f9fb;
}
.trade-dialog-summary strong {
  color: #29384a;
}
.quantity-unit {
  margin-left: 10px;
  color: #8792a2;
}
@media (max-width: 900px) {
  .asset-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .analysis-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .analysis-tables {
    grid-template-columns: 1fr;
  }
  .dca-summary {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .page-heading {
    align-items: flex-start;
  }
  .page-heading h1 {
    font-size: 19px;
  }
  .asset-grid {
    grid-template-columns: 1fr 1fr;
    gap: 8px;
  }
  .asset-card {
    min-height: 104px;
    padding: 12px;
  }
  .asset-card strong {
    font-size: 19px;
  }
  .analysis-grid {
    gap: 8px;
  }
  .analysis-grid .asset-card {
    min-height: 96px;
    padding: 12px;
  }
  .analysis-grid .asset-card strong {
    font-size: 17px;
  }
  .dca-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .history-form :deep(.el-input),
  .history-form :deep(.el-input-number) {
    width: 130px;
  }
  .quote-summary {
    align-items: flex-start;
  }
  .quote-meta {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }
}
</style>
