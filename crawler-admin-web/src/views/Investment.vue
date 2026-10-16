<template>
  <section class="investment-page">
    <template v-if="pageType === 'overview'">
    <header class="page-heading">
      <div>
        <h1>投资总览与股票模拟交易</h1>
        <p>查看虚拟账户资产并模拟 A 股买卖；成交按行情接口报价，报价可能延迟，不构成投资建议。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadAccount">刷新资产</el-button>
    </header>

    <div class="asset-grid" v-loading="loading">
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
    </template>

    <template v-if="pageType === 'overview'">
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
          <el-input v-model="fundCode" maxlength="6" placeholder="510300 / 159915" inputmode="numeric" />
        </el-form-item>
        <el-form-item label="周期">
          <el-select v-model="historyFrequency" style="width: 100px">
            <el-option label="日线" value="day" />
            <el-option label="周线" value="week" />
            <el-option label="月线" value="month" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="historyStartDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="historyEndDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="historyLoading" @click="loadFundHistory">查询历史行情</el-button>
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
          </svg>
          <div class="chart-value chart-bottom">¥{{ money(historyMinClose) }}</div>
          <div class="chart-dates">
            <span>{{ historyResult.points[0]?.date }}</span>
            <span>{{ historyResult.points[historyResult.points.length - 1]?.date }}</span>
          </div>
        </div>
        <el-table :data="recentHistoryPoints" stripe max-height="440">
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
        <p class="fee-note">表格显示最近 20 个数据点；历史行情接口采用前复权口径。价格仅供模拟分析，不构成投资建议。</p>
      </div>
      <el-empty v-else-if="!historyLoading" description="选择基金代码和日期范围后查询历史行情" :image-size="64" />
    </el-card>

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
          <el-input v-model="fundCode" maxlength="6" placeholder="510300 / 159915" inputmode="numeric" />
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
    </template>

    <template v-if="pageType === 'offshore-fund'">
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
          <el-input v-model="offExchangeFundCode" maxlength="6" placeholder="例如 000001" inputmode="numeric" />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="offExchangeHistoryStartDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="offExchangeHistoryEndDate" type="date" value-format="YYYY-MM-DD" />
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
          </svg>
          <div class="chart-value chart-bottom">¥{{ money(offExchangeNavMin) }}</div>
          <div class="chart-dates">
            <span>{{ offExchangeHistoryResult.points[0]?.date }}</span>
            <span>{{ offExchangeHistoryResult.points[offExchangeHistoryResult.points.length - 1]?.date }}</span>
          </div>
        </div>
        <el-table :data="recentOffExchangeNavPoints" stripe max-height="440">
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
        <p class="fee-note">净值来自东方财富基金数据，仅显示已公布的历史单位净值；数据仅供模拟分析，不构成投资建议。</p>
      </div>
      <el-empty v-else-if="!offExchangeHistoryLoading" description="输入场外基金代码和日期范围后查询净值" :image-size="64" />
    </el-card>

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
          <el-input v-model="offExchangeFundCode" maxlength="6" placeholder="例如 000001" inputmode="numeric" />
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
    </template>

    <template v-if="pageType === 'overview'">
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="section-heading">
          <div>
            <strong>我的持仓</strong>
            <span>持仓盈亏按行情估值，未扣除卖出费用</span>
          </div>
        </div>
      </template>
      <el-table :data="account?.positions || []" v-loading="loading" stripe>
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
        <template #empty><el-empty description="暂无持仓，查询证券代码后即可模拟买入" /></template>
      </el-table>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="section-heading">
          <div>
            <strong>最近成交</strong>
            <span>最多显示最近 50 笔模拟交易</span>
          </div>
        </div>
      </template>
      <el-table :data="account?.recentTrades || []" v-loading="loading" stripe>
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
        <template #empty><el-empty description="暂无成交记录" /></template>
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
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  fundHistory,
  investmentAccount,
  investmentQuote,
  investmentTrade,
  offExchangeFundHistory,
  simulateFundDca,
  simulateOffExchangeFundDca
} from '@/api'

const props = defineProps<{
  pageType: 'overview' | 'onshore-fund' | 'offshore-fund'
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

const account = ref<any>(null)
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
const toDateString = (date: Date) => {
  const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
  return localDate.toISOString().slice(0, 10)
}
const oneYearAgo = new Date(today)
oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1)
const initialStartDate = toDateString(oneYearAgo)
const initialEndDate = toDateString(today)
const fundCode = ref('159915')
const historyStartDate = ref(initialStartDate)
const historyEndDate = ref(initialEndDate)
const historyFrequency = ref('day')
const historyResult = ref<any>(null)
const historyLoading = ref(false)
const dcaStartDate = ref(initialStartDate)
const dcaEndDate = ref(initialEndDate)
const dcaWeekday = ref(1)
const dcaAmount = ref(500)
const dcaResult = ref<any>(null)
const dcaLoading = ref(false)
const offExchangeFundCode = ref('000001')
const offExchangeHistoryStartDate = ref(initialStartDate)
const offExchangeHistoryEndDate = ref(initialEndDate)
const offExchangeHistoryResult = ref<any>(null)
const offExchangeHistoryLoading = ref(false)
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

const quoteAvailableQuantity = computed(() =>
  account.value?.positions?.find((item: any) => item.code === quote.value?.code)?.availableQuantity || 0
)
const historyPoints = computed(() => historyResult.value?.points || [])
const recentHistoryPoints = computed(() => [...historyPoints.value].slice(-20).reverse())
const offExchangeNavPoints = computed(() => offExchangeHistoryResult.value?.points || [])
const recentOffExchangeNavPoints = computed(() => [...offExchangeNavPoints.value].slice(-20).reverse())
const offExchangeNavMin = computed(() => offExchangeNavPoints.value.length
  ? Math.min(...offExchangeNavPoints.value.map((point: any) => Number(point.unitNav)))
  : 0)
const offExchangeNavMax = computed(() => offExchangeNavPoints.value.length
  ? Math.max(...offExchangeNavPoints.value.map((point: any) => Number(point.unitNav)))
  : 0)
const offExchangeNavChartPoints = computed(() => {
  const points = offExchangeNavPoints.value
  if (!points.length) return ''
  const values = points.map((point: any) => Number(point.unitNav))
  const min = Math.min(...values)
  const max = Math.max(...values)
  const padding = max === min ? Math.max(max * 0.02, 0.01) : 0
  const range = max - min + padding * 2 || 1
  return values.map((value: number, index: number) => {
    const x = points.length === 1 ? 500 : index / (points.length - 1) * 1000
    const y = 200 - (value - min + padding) / range * 180
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
})
const historyMinClose = computed(() => historyPoints.value.length
  ? Math.min(...historyPoints.value.map((point: any) => Number(point.close)))
  : 0)
const historyMaxClose = computed(() => historyPoints.value.length
  ? Math.max(...historyPoints.value.map((point: any) => Number(point.close)))
  : 0)
const historyChartPoints = computed(() => {
  const points = historyPoints.value
  if (!points.length) return ''
  const values = points.map((point: any) => Number(point.close))
  const min = Math.min(...values)
  const max = Math.max(...values)
  const padding = max === min ? Math.max(max * 0.02, 0.01) : 0
  const range = max - min + padding * 2 || 1
  return values.map((value: number, index: number) => {
    const x = points.length === 1 ? 500 : index / (points.length - 1) * 1000
    const y = 200 - (value - min + padding) / range * 180
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
})
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
const profitTone = (value: unknown) => Number(value || 0) > 0 ? 'is-profit' : Number(value || 0) < 0 ? 'is-loss' : ''
const dateTime = (value: string) => value ? value.replace('T', ' ').slice(0, 19) : '-'
const frequencyLabel = (frequency: string) => ({ day: '日线', week: '周线', month: '月线' }[frequency] || '行情')

const loadAccount = async () => {
  loading.value = true
  try {
    const response: any = await investmentAccount()
    account.value = response.data
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
  } catch {
    quote.value = null
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
  try {
    const response: any = await fundHistory({
      code: fundCode.value.trim(),
      startDate: historyStartDate.value,
      endDate: historyEndDate.value,
      frequency: historyFrequency.value
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
  try {
    const response: any = await offExchangeFundHistory({
      code: offExchangeFundCode.value.trim(),
      startDate: offExchangeHistoryStartDate.value,
      endDate: offExchangeHistoryEndDate.value
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
  if (props.pageType === 'overview') {
    loadAccount()
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
