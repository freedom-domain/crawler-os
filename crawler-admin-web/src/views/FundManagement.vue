<template>
  <section class="fund-management-page">
    <header class="page-heading">
      <div>
        <h1>基金管理</h1>
        <p>同步并浏览基金目录，查看基金详情与历史行情；关注清单可在弹窗中维护。</p>
      </div>
      <div class="heading-actions">
        <el-button :icon="Star" @click="openWatchlist">关注清单</el-button>
        <el-button type="primary" :icon="Refresh" :loading="syncing" @click="syncCatalog">同步基金目录</el-button>
      </div>
    </header>

    <el-card shadow="never" class="catalog-card">
      <el-form class="filter-form" inline @submit.prevent="searchCatalog">
        <el-form-item>
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索代码、名称或拼音"
            style="width: 250px"
            @keyup.enter="searchCatalog"
            @clear="searchCatalog"
          />
        </el-form-item>
        <el-form-item label="基金类型">
          <el-select v-model="fundType" style="width: 150px" @change="searchCatalog">
            <el-option label="全部基金" value="" />
            <el-option label="场内基金" value="ONSHORE" />
            <el-option label="场外基金" value="OFFSHORE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="searchCatalog">查询</el-button>
          <el-button :icon="Refresh" :disabled="loading" @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="catalog-summary">
        <span>基金目录 · 共 {{ total.toLocaleString() }} 只</span>
        <span v-if="lastSyncedAt">最近同步：{{ formatDateTime(lastSyncedAt) }}</span>
        <span v-else>尚未同步，请先同步基金目录</span>
      </div>
      <el-table :data="catalog" v-loading="loading" stripe border>
        <el-table-column label="类型" width="105">
          <template #default="{ row }">
            <el-tag :type="row.fundType === 'ONSHORE' ? 'primary' : 'warning'">
              {{ row.fundType === 'ONSHORE' ? '场内' : '场外' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="代码" width="105" />
        <el-table-column prop="name" label="基金名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="category" label="基金类型" min-width="155" show-overflow-tooltip />
        <el-table-column label="关注状态" width="105">
          <template #default="{ row }">
            <el-tag :type="row.watched ? 'success' : 'info'">{{ row.watched ? '已关注' : '未关注' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="60" fixed="right" align="center" resizable>
          <template #default="{ row }">
            <el-dropdown trigger="click" @command="(command: string) => handleCatalogCommand(command, row)">
              <el-button link size="small" class="action-more-btn"><el-icon><MoreFilled /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="simulate" :icon="Coin">模拟投资</el-dropdown-item>
                  <el-dropdown-item command="detail" :icon="View">详情</el-dropdown-item>
                  <el-dropdown-item v-if="!row.watched" command="watch" :icon="Star">加入关注</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="total ? '没有符合条件的基金' : '基金目录为空，请先同步基金目录'" />
        </template>
      </el-table>
      <el-pagination
        v-if="total > 0"
        class="pagination"
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @change="loadCatalog"
      />
    </el-card>

    <el-dialog v-model="watchlistVisible" title="关注清单" width="min(1100px, 94vw)" destroy-on-close>
      <p class="dialog-description">关注的基金会参与定时行情更新；此处也列出仅有历史缓存的基金。取消关注不会删除缓存。</p>
      <el-form class="watchlist-add-form" inline @submit.prevent="addManualWatch">
        <el-form-item label="类型">
          <el-select v-model="manualForm.fundType" style="width: 130px" @change="resetManualFrequency">
            <el-option label="场内基金" value="ONSHORE" />
            <el-option label="场外基金" value="OFFSHORE" />
          </el-select>
        </el-form-item>
        <el-form-item label="代码">
          <FundCodeSelect v-model="manualForm.code" :fund-type="manualForm.fundType" />
        </el-form-item>
        <el-form-item v-if="manualForm.fundType === 'ONSHORE'" label="周期">
          <el-select v-model="manualForm.frequency" style="width: 100px">
            <el-option label="日线" value="day" />
            <el-option label="周线" value="week" />
            <el-option label="月线" value="month" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="addingManual" @click="addManualWatch">加入关注</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="watchedFunds" v-loading="watchlistLoading" stripe border max-height="60vh">
        <el-table-column label="类型" width="85">
          <template #default="{ row }">{{ row.fundType === 'ONSHORE' ? '场内' : '场外' }}</template>
        </el-table-column>
        <el-table-column prop="code" label="代码" width="95" />
        <el-table-column prop="name" label="基金名称" min-width="175" show-overflow-tooltip />
        <el-table-column label="周期" width="85">
          <template #default="{ row }">{{ frequencyLabel(row.frequency) }}</template>
        </el-table-column>
        <el-table-column label="缓存点数" width="95" align="right">
          <template #default="{ row }">{{ row.cachedPoints || 0 }}</template>
        </el-table-column>
        <el-table-column prop="latestDate" label="最新行情日期" width="125">
          <template #default="{ row }">{{ row.latestDate || '暂无' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <el-tag :type="row.watched ? 'success' : 'info'">{{ row.watched ? '已关注' : '仅缓存' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="60" fixed="right" align="center" resizable>
          <template #default="{ row }">
            <el-dropdown trigger="click" @command="(command: string) => handleWatchlistCommand(command, row)">
              <el-button link size="small" class="action-more-btn"><el-icon><MoreFilled /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="refresh" :icon="Refresh" :disabled="busyKey === rowKey(row)">立即更新</el-dropdown-item>
                  <el-dropdown-item
                    v-if="row.watched"
                    command="remove"
                    :icon="Star"
                    :disabled="busyKey === rowKey(row)"
                  >取消关注</el-dropdown-item>
                  <el-dropdown-item
                    v-else
                    command="watch"
                    :icon="Star"
                    :disabled="busyKey === rowKey(row)"
                  >加入关注</el-dropdown-item>
                  <el-dropdown-item
                    command="clear"
                    :icon="Delete"
                    :disabled="!row.cachedPoints || busyKey === rowKey(row)"
                    divided
                    class="is-danger"
                  >清除缓存</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
        <template #empty><el-empty description="关注清单为空，可在基金目录中添加关注" /></template>
      </el-table>
      <template #footer>
        <el-button @click="watchlistVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="fundPageVisible"
      :title="fundPageType === 'onshore-fund' ? '场内基金模拟投资' : '场外基金模拟投资'"
      width="min(1400px, 96vw)"
      top="3vh"
      class="fund-page-dialog"
      destroy-on-close
    >
      <InvestmentView v-if="fundPageVisible" :page-type="fundPageType" :initial-code="fundPageCode" />
    </el-dialog>

    <el-dialog
      v-model="detailVisible"
      :title="detail?.name || '基金详情'"
      width="min(1000px, 94vw)"
      top="4vh"
      class="fund-detail-dialog"
      destroy-on-close
    >
      <div v-loading="detailLoading" class="fund-detail-content">
        <template v-if="detail">
          <el-tabs v-model="detailTab" class="fund-detail-tabs">
            <el-tab-pane label="基金资料" name="profile">
              <el-descriptions class="fund-detail-descriptions" :column="3" border>
            <el-descriptions-item label="基金名称">{{ detail.name }}</el-descriptions-item>
            <el-descriptions-item label="基金代码">{{ detail.code }}</el-descriptions-item>
            <el-descriptions-item label="基金类型">{{ detail.fundType === 'ONSHORE' ? '场内基金' : '场外基金' }}</el-descriptions-item>
            <el-descriptions-item label="基金全称">{{ detail.fundFullName || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="基金分类">{{ detail.category || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="拼音/助记码">{{ detail.pinyin || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="发行日期">{{ detail.issueDate || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="成立日期">{{ detail.establishedDate || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="成立规模">{{ detail.establishmentScale || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="目录同步时间">{{ formatDateTime(detail.syncedAt) }}</el-descriptions-item>
            <el-descriptions-item label="档案更新时间">{{ formatDateTime(detail.profileUpdatedAt) }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>

            <el-tab-pane label="规模与管理" name="management">
              <el-descriptions class="fund-detail-descriptions" :column="3" border>
            <el-descriptions-item label="净资产规模">
              {{ detail.netAssetScale || '暂无' }}
              <span v-if="detail.netAssetScaleAsOf" class="detail-as-of">（{{ detail.netAssetScaleAsOf }}）</span>
            </el-descriptions-item>
            <el-descriptions-item label="份额规模">
              {{ detail.shareScale || '暂无' }}
              <span v-if="detail.shareScaleAsOf" class="detail-as-of">（{{ detail.shareScaleAsOf }}）</span>
            </el-descriptions-item>
            <el-descriptions-item label="成立以来分红">{{ detail.distributionSinceLaunch || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="基金管理人">{{ detail.managementCompany || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="基金经理">{{ detail.managerNames || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="基金托管人">{{ detail.custodian || '暂无' }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>

            <el-tab-pane label="费率与投资基准" name="fees">
              <el-descriptions class="fund-detail-descriptions" :column="3" border>
            <el-descriptions-item label="管理费率">{{ detail.managementFeeRate || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="托管费率">{{ detail.custodianFeeRate || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="销售服务费率">{{ detail.serviceFeeRate || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="最高认购费率">{{ detail.maxSubscriptionFeeRate || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="最高申购费率">{{ detail.maxPurchaseFeeRate || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="最高赎回费率">{{ detail.maxRedemptionFeeRate || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="业绩比较基准" :span="2">{{ detail.benchmark || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="跟踪标的">{{ detail.trackingTarget || '暂无' }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>

            <el-tab-pane label="投资背景" name="investment">
              <el-descriptions class="fund-detail-descriptions fund-detail-narrative" :column="1" border>
            <el-descriptions-item label="投资目标">{{ detail.investmentObjective || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="投资理念">{{ detail.investmentPhilosophy || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="投资范围">{{ detail.investmentScope || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="投资策略">{{ detail.investmentStrategy || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="分红政策">{{ detail.dividendPolicy || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="风险收益特征">{{ detail.riskReturnFeature || '暂无' }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>

            <el-tab-pane label="关注与缓存" name="cache">
              <el-descriptions class="fund-detail-descriptions" :column="3" border>
            <el-descriptions-item label="关注状态">{{ detail.watched ? '已关注' : '未关注' }}</el-descriptions-item>
            <el-descriptions-item label="关注周期">
              {{ detail.watchlistFrequencies ? detail.watchlistFrequencies.split(',').map(frequencyLabel).join('、') : '未关注' }}
            </el-descriptions-item>
            <el-descriptions-item label="首次关注时间">{{ formatDateTime(detail.firstWatchedAt) }}</el-descriptions-item>
            <el-descriptions-item label="缓存点数">{{ detail.cachedPoints || 0 }}</el-descriptions-item>
            <el-descriptions-item label="日线缓存">{{ detail.dailyCachedPoints || 0 }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.fundType === 'ONSHORE'" label="周线缓存">{{ detail.weeklyCachedPoints || 0 }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.fundType === 'ONSHORE'" label="月线缓存">{{ detail.monthlyCachedPoints || 0 }}</el-descriptions-item>
            <el-descriptions-item label="缓存起始日期">{{ detail.cachedStartDate || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="最新缓存日期">{{ detail.latestMarketDate || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="缓存区间表现">{{ cachedPeriodReturn(detail) }}</el-descriptions-item>
            <el-descriptions-item label="区间最高价/净值">{{ formatPrice(detail.periodHigh) }}</el-descriptions-item>
            <el-descriptions-item label="区间最低价/净值">{{ formatPrice(detail.periodLow) }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>

            <el-tab-pane label="最新缓存行情" name="latest">
              <el-descriptions class="fund-detail-descriptions" :column="3" border>
            <template v-if="detail.fundType === 'ONSHORE'">
              <el-descriptions-item label="开盘价">{{ formatPrice(detail.latestOpen) }}</el-descriptions-item>
              <el-descriptions-item label="收盘价">{{ formatPrice(detail.latestClose) }}</el-descriptions-item>
              <el-descriptions-item label="涨跌幅">{{ formatPercent(detail.latestChangePercent) }}</el-descriptions-item>
              <el-descriptions-item label="最高价">{{ formatPrice(detail.latestHigh) }}</el-descriptions-item>
              <el-descriptions-item label="最低价">{{ formatPrice(detail.latestLow) }}</el-descriptions-item>
              <el-descriptions-item label="成交量">{{ detail.latestVolume == null ? '暂无' : detail.latestVolume }}</el-descriptions-item>
            </template>
            <template v-else>
              <el-descriptions-item label="单位净值">{{ formatPrice(detail.latestUnitNav) }}</el-descriptions-item>
              <el-descriptions-item label="累计净值">{{ formatPrice(detail.latestCumulativeNav) }}</el-descriptions-item>
              <el-descriptions-item label="日涨跌幅">{{ formatPercent(detail.latestChangePercent) }}</el-descriptions-item>
            </template>
              </el-descriptions>
            </el-tab-pane>
          </el-tabs>
        </template>
        <p class="detail-note">基金规模与档案背景来自东方财富基金档案，规模以页面标注的披露日期为准；行情摘要仅展示本系统已缓存的数据，不会在打开详情时请求历史行情接口。</p>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :icon="Refresh"
          :loading="profileUpdating"
          :disabled="detailLoading || !detail"
          @click="refreshFundProfile"
        >更新基金档案</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Coin, Delete, MoreFilled, Refresh, Search, Star, View } from '@element-plus/icons-vue'
import { confirm } from '@/utils/confirm'
import FundCodeSelect from '@/components/FundCodeSelect.vue'
import InvestmentView from '@/views/Investment.vue'
import {
  fundCatalogDetail,
  fundCatalogPage,
  fundCatalogProfileRefresh,
  fundCatalogSync,
  fundHistoryCacheClear,
  fundHistoryRefresh,
  fundManagementList,
  fundWatchlistAdd,
  fundWatchlistRemove
} from '@/api'

type FundType = 'ONSHORE' | 'OFFSHORE'

interface FundCatalogItem {
  fundType: FundType
  code: string
  name: string
  category: string
  pinyin: string
  watched: boolean
  syncedAt: string
  fundFullName?: string | null
  issueDate?: string | null
  establishedDate?: string | null
  establishmentScale?: string | null
  netAssetScale?: string | null
  netAssetScaleAsOf?: string | null
  shareScale?: string | null
  shareScaleAsOf?: string | null
  managementCompany?: string | null
  custodian?: string | null
  managerNames?: string | null
  distributionSinceLaunch?: string | null
  managementFeeRate?: string | null
  custodianFeeRate?: string | null
  serviceFeeRate?: string | null
  maxSubscriptionFeeRate?: string | null
  maxPurchaseFeeRate?: string | null
  maxRedemptionFeeRate?: string | null
  benchmark?: string | null
  trackingTarget?: string | null
  investmentObjective?: string | null
  investmentPhilosophy?: string | null
  investmentScope?: string | null
  investmentStrategy?: string | null
  dividendPolicy?: string | null
  riskReturnFeature?: string | null
  profileUpdatedAt?: string | null
  watchlistFrequencies?: string | null
  firstWatchedAt?: string | null
  cachedPoints?: number
  dailyCachedPoints?: number
  weeklyCachedPoints?: number
  monthlyCachedPoints?: number
  cachedStartDate?: string | null
  latestMarketDate?: string | null
  firstMarketValue?: number | null
  periodHigh?: number | null
  periodLow?: number | null
  latestOpen?: number | null
  latestClose?: number | null
  latestHigh?: number | null
  latestLow?: number | null
  latestVolume?: number | null
  latestChangePercent?: number | null
  latestUnitNav?: number | null
  latestCumulativeNav?: number | null
}

interface FundManagementItem extends FundCatalogItem {
  watchlistId: number | null
  frequency: string
  cachedPoints: number
  latestDate: string | null
  addedAt: string | null
}

const catalog = ref<FundCatalogItem[]>([])
const watchedFunds = ref<FundManagementItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const fundType = ref<FundType | ''>('')
const loading = ref(false)
const syncing = ref(false)
const watchlistVisible = ref(false)
const watchlistLoading = ref(false)
const fundPageVisible = ref(false)
const fundPageType = ref<'onshore-fund' | 'offshore-fund'>('onshore-fund')
const fundPageCode = ref('')
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailTab = ref('profile')
const profileUpdating = ref(false)
const busyKey = ref('')
const detail = ref<FundCatalogItem | null>(null)
const lastSyncedAt = ref('')
const addingManual = ref(false)
const manualForm = ref<{ fundType: FundType; code: string; frequency: string }>({
  fundType: 'ONSHORE',
  code: '',
  frequency: 'day'
})

const frequencyLabel = (frequency: string) => ({ day: '日线', week: '周线', month: '月线' }[frequency] || frequency)
const rowKey = (row: FundManagementItem) => `${row.fundType}:${row.code}:${row.frequency}`
const requestKey = (row: FundManagementItem) => ({
  fundType: row.fundType,
  code: row.code,
  frequency: row.frequency
})
const errorMessage = (error: unknown) => error instanceof Error ? error.message : '操作失败，请稍后重试'
const formatDateTime = (value: string | null | undefined) => value ? value.replace('T', ' ').slice(0, 16) : '暂无'
const formatPrice = (value: number | null | undefined) => value == null ? '暂无' : `¥${Number(value).toFixed(4)}`
const formatPercent = (value: number | null | undefined) => value == null ? '暂无' : `${Number(value).toFixed(2)}%`
const cachedPeriodReturn = (item: FundCatalogItem) => {
  const latestValue = item.fundType === 'ONSHORE' ? item.latestClose : item.latestUnitNav
  if (item.firstMarketValue == null || latestValue == null || item.firstMarketValue === 0) return '暂无'
  const change = (latestValue / item.firstMarketValue - 1) * 100
  return `${change >= 0 ? '+' : ''}${change.toFixed(2)}%`
}

const loadCatalog = async () => {
  loading.value = true
  try {
    const response: any = await fundCatalogPage({
      keyword: keyword.value.trim() || undefined,
      fundType: fundType.value || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    catalog.value = response.data?.items || []
    total.value = response.data?.total || 0
    lastSyncedAt.value = catalog.value.reduce((latest, row) => row.syncedAt > latest ? row.syncedAt : latest, '')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

const searchCatalog = () => {
  page.value = 1
  void loadCatalog()
}

const resetFilters = () => {
  keyword.value = ''
  fundType.value = ''
  searchCatalog()
}

const syncCatalog = async () => {
  syncing.value = true
  try {
    const response: any = await fundCatalogSync()
    const result = response.data
    ElMessage.success(`基金目录同步完成：共 ${result.total} 只（场内 ${result.onshore}，场外 ${result.offshore}）`)
    await loadCatalog()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    syncing.value = false
  }
}

const loadWatchlist = async () => {
  watchlistLoading.value = true
  try {
    const response: any = await fundManagementList()
    watchedFunds.value = response.data || []
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    watchlistLoading.value = false
  }
}

const openWatchlist = () => {
  watchlistVisible.value = true
  void loadWatchlist()
}

const openFundPage = (row: FundCatalogItem) => {
  fundPageType.value = row.fundType === 'ONSHORE' ? 'onshore-fund' : 'offshore-fund'
  fundPageCode.value = row.code
  fundPageVisible.value = true
}

const resetManualFrequency = () => {
  manualForm.value.frequency = 'day'
}

const addManualWatch = async () => {
  if (!manualForm.value.code) {
    ElMessage.warning('请选择基金代码')
    return
  }
  addingManual.value = true
  try {
    await fundWatchlistAdd({ ...manualForm.value, code: manualForm.value.code.trim() })
    ElMessage.success('已加入关注清单')
    manualForm.value.code = ''
    await Promise.all([loadWatchlist(), loadCatalog()])
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    addingManual.value = false
  }
}

const watchFund = async (row: FundCatalogItem) => {
  try {
    await fundWatchlistAdd({ fundType: row.fundType, code: row.code, frequency: 'day' })
    ElMessage.success(`${row.name} 已加入日线关注清单`)
    await Promise.all([loadCatalog(), watchlistVisible.value ? loadWatchlist() : Promise.resolve()])
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

const handleCatalogCommand = (command: string, row: FundCatalogItem) => {
  if (command === 'simulate') {
    openFundPage(row)
  } else if (command === 'detail') {
    void openDetail(row)
  } else if (command === 'watch') {
    void watchFund(row)
  }
}

const handleWatchlistCommand = (command: string, row: FundManagementItem) => {
  if (command === 'refresh') {
    void refreshFund(row)
  } else if (command === 'remove') {
    void removeFund(row)
  } else if (command === 'watch') {
    void watchFund(row)
  } else if (command === 'clear') {
    void clearCache(row)
  }
}

const removeFund = async (row: FundManagementItem) => {
  if (!await confirm(`取消关注 ${row.name || row.code}（${frequencyLabel(row.frequency)}）？已缓存的历史行情会保留。`, {
    title: '取消基金关注',
    confirmText: '取消关注'
  })) return
  busyKey.value = rowKey(row)
  try {
    await fundWatchlistRemove(requestKey(row))
    ElMessage.success('已从关注清单移除')
    await Promise.all([loadWatchlist(), loadCatalog()])
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    busyKey.value = ''
  }
}

const refreshFund = async (row: FundManagementItem) => {
  busyKey.value = rowKey(row)
  try {
    await fundHistoryRefresh(requestKey(row))
    ElMessage.success(`${row.name || row.code} 历史行情已更新`)
    await loadWatchlist()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    busyKey.value = ''
  }
}

const clearCache = async (row: FundManagementItem) => {
  if (!await confirm(`清除 ${row.name || row.code}（${frequencyLabel(row.frequency)}）的全部历史缓存？`, {
    title: '清除历史缓存',
    confirmText: '清除缓存',
    danger: true
  })) return
  busyKey.value = rowKey(row)
  try {
    await fundHistoryCacheClear(requestKey(row))
    ElMessage.success('历史缓存已清除')
    await loadWatchlist()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    busyKey.value = ''
  }
}

const openDetail = async (row: FundCatalogItem) => {
  detailVisible.value = true
  detailLoading.value = true
  detailTab.value = 'profile'
  detail.value = null
  try {
    const response: any = await fundCatalogDetail({ fundType: row.fundType, code: row.code })
    detail.value = response.data
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    detailLoading.value = false
  }
}

const refreshFundProfile = async () => {
  if (!detail.value) return
  profileUpdating.value = true
  try {
    const response: any = await fundCatalogProfileRefresh({
      fundType: detail.value.fundType,
      code: detail.value.code
    })
    detail.value = response.data
    ElMessage.success('基金档案已更新并保存')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    profileUpdating.value = false
  }
}

onMounted(loadCatalog)
</script>

<style scoped>
.fund-management-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.page-heading,
.heading-actions,
.catalog-summary,
.history-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.page-heading h1 {
  margin: 0;
  color: #243345;
  font-size: 22px;
}
.page-heading p,
.catalog-summary,
.dialog-description,
.history-summary {
  color: #8792a2;
  font-size: 13px;
}
.page-heading p {
  margin: 6px 0 0;
}
.catalog-card {
  border-radius: 10px;
}
.filter-form {
  margin-bottom: 2px;
}
.catalog-summary {
  margin: 4px 0 12px;
}
.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
.dialog-description {
  margin: 0 0 12px;
}
.action-more-btn {
  font-weight: 700;
  color: #0f9f9a;
}
.action-more-btn:hover {
  color: #0c8a85;
}
.watchlist-add-form {
  margin-bottom: 2px;
}
.detail-note {
  margin: 18px 0 0;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f4f7fa;
  color: #8792a2;
  font-size: 12px;
  line-height: 1.7;
}
.detail-as-of {
  color: #78889a;
  font-size: 12px;
}
@media (max-width: 650px) {
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .heading-actions {
    width: 100%;
    flex-wrap: wrap;
  }
  .catalog-summary {
    align-items: flex-start;
    flex-direction: column;
  }
  .history-toolbar :deep(.el-date-editor) {
    width: 100%;
  }
}
</style>

<style>
.fund-page-dialog .el-dialog__body {
  max-height: 82vh;
  overflow: auto;
}
.fund-detail-dialog .el-dialog__body {
  max-height: 76vh;
  overflow: auto;
  padding: 18px 24px 22px;
}
.fund-detail-dialog .el-dialog__header {
  padding-bottom: 14px;
  border-bottom: 1px solid #e8edf2;
}
.fund-detail-dialog .el-dialog__title {
  color: #203247;
  font-size: 19px;
  font-weight: 700;
}
.fund-detail-dialog .fund-detail-descriptions {
  margin-bottom: 4px;
}
.fund-detail-dialog .fund-detail-tabs .el-tabs__header {
  margin-bottom: 18px;
}
.fund-detail-dialog .fund-detail-descriptions .el-descriptions__label {
  min-width: 105px;
  background: #f1f5f8 !important;
  color: #526579 !important;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.01em;
}
.fund-detail-dialog .fund-detail-descriptions .el-descriptions__content {
  background: #fff !important;
  color: #26384b !important;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.65;
  overflow-wrap: anywhere;
}
.fund-detail-dialog .fund-detail-narrative .el-descriptions__label {
  width: 120px;
  white-space: nowrap;
}
.fund-detail-dialog .fund-detail-narrative .el-descriptions__content {
  color: #34495e !important;
  line-height: 1.85;
}
@media (max-width: 650px) {
  .fund-detail-dialog .el-dialog__body {
    padding: 14px 12px 18px;
  }
  .fund-detail-dialog .fund-detail-descriptions .el-descriptions__label {
    min-width: 82px;
  }
}
</style>
