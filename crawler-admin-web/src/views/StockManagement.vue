<template>
  <section class="stock-management-page">
    <header class="page-heading">
      <div>
        <h1>股票管理</h1>
        <p>同步 A 股目录，查看公司资料、历史行情与回测，并从股票列表直接进行模拟投资。</p>
      </div>
      <div class="heading-actions">
        <el-dropdown trigger="click" @command="handleManagementAction">
          <el-button type="primary">
            管理操作<el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="watchlist" :icon="Star">关注清单</el-dropdown-item>
              <el-dropdown-item command="history-sync">同步全部历史行情</el-dropdown-item>
              <el-dropdown-item command="sync-records">历史行情同步记录</el-dropdown-item>
              <el-dropdown-item command="concurrency">腾讯行情并发设置</el-dropdown-item>
              <el-dropdown-item command="overview" :icon="Coin">投资总览</el-dropdown-item>
              <el-dropdown-item command="catalog-sync" :icon="Refresh" :disabled="syncing">同步股票信息</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <HistorySyncTaskPanel ref="historySyncPanel" instrument-type="STOCK" />
      </div>
    </header>

    <el-card shadow="never" class="stock-catalog-card">
      <el-form class="filter-form" inline @submit.prevent="searchCatalog">
        <el-form-item>
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索代码、名称或行业"
            style="width: 260px"
            @keyup.enter="searchCatalog"
            @clear="searchCatalog"
          />
        </el-form-item>
        <el-form-item label="市场">
          <el-select v-model="market" clearable placeholder="全部市场" style="width: 140px" @change="searchCatalog">
            <el-option label="上海" value="上海" />
            <el-option label="深圳" value="深圳" />
            <el-option label="北京" value="北京" />
          </el-select>
        </el-form-item>
        <el-form-item label="关注状态">
          <el-select v-model="watched" clearable placeholder="全部状态" style="width: 140px" @change="searchCatalog">
            <el-option label="已关注" :value="true" />
            <el-option label="未关注" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="searchCatalog">查询</el-button>
          <el-button :icon="Refresh" :disabled="loading" @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="catalog-summary">
        <span>股票目录 · 共 {{ total.toLocaleString() }} 只</span>
        <span v-if="lastSyncedAt">最近同步：{{ formatDateTime(lastSyncedAt) }}</span>
        <span v-else>尚未同步，请先同步股票信息</span>
      </div>
      <el-table :data="stocks" v-loading="loading" stripe border>
        <el-table-column prop="code" label="股票代码" width="115" />
        <el-table-column prop="name" label="股票名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="market" label="市场" width="95" />
        <el-table-column prop="industry" label="所属行业" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.industry || '暂无' }}</template>
        </el-table-column>
        <el-table-column label="关注状态" width="125">
          <template #default="{ row }">
            <el-switch
              :model-value="row.watched"
              active-text="已关注"
              inactive-text="未关注"
              inline-prompt
              :loading="watchBusyCode === row.code"
              :disabled="watchBusyCode === row.code"
              @change="(value: string | number | boolean) => setStockWatchStatus(row, Boolean(value))"
            />
          </template>
        </el-table-column>
        <el-table-column label="目录同步时间" min-width="155">
          <template #default="{ row }">{{ formatDateTime(row.syncedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="60" fixed="right" align="center">
          <template #default="{ row }">
            <el-dropdown trigger="click" @command="(command: string) => handleCommand(command, row)">
              <el-button link size="small" class="action-more-btn"><el-icon><MoreFilled /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="market-data" :icon="TrendCharts">历史行情</el-dropdown-item>
                  <el-dropdown-item command="simulate" :icon="Coin">模拟投资</el-dropdown-item>
                  <el-dropdown-item command="detail" :icon="View">详情</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="total ? '没有符合条件的股票' : '股票目录为空，请先同步股票信息'" />
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

    <el-dialog v-model="watchlistVisible" title="股票关注清单" width="min(1100px, 96vw)" destroy-on-close>
      <div class="refresh-schedule-toolbar">
        <span>自动同步频率</span>
        <el-select v-model="refreshFrequency" class="refresh-schedule-select">
          <el-option label="每小时（整点）" value="HOURLY" />
          <el-option label="每个工作日（18:35）" value="DAILY" />
          <el-option label="每周一（18:35）" value="WEEKLY" />
        </el-select>
        <el-button type="primary" :loading="refreshScheduleSaving" @click="saveRefreshSchedule">保存</el-button>
      </div>
      <div class="watchlist-toolbar">
        <el-input
          v-model="watchlistKeyword"
          clearable
          placeholder="搜索股票代码或名称"
          :prefix-icon="Search"
          class="watchlist-search"
          @input="watchlistPage = 1"
          @clear="watchlistPage = 1"
        />
        <span class="watchlist-count">共 {{ filteredWatchlist.length.toLocaleString() }} 只</span>
      </div>
      <el-table :data="pagedWatchlist" v-loading="watchlistLoading" stripe border max-height="55vh">
        <el-table-column prop="code" label="股票代码" width="110" />
        <el-table-column prop="name" label="股票名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="market" label="市场" width="90" />
        <el-table-column prop="industry" label="所属行业" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.industry || '暂无' }}</template>
        </el-table-column>
        <el-table-column label="历史行情点数" width="125" align="right">
          <template #default="{ row }">{{ row.cachedPoints || 0 }}</template>
        </el-table-column>
        <el-table-column label="最新行情日期" width="125">
          <template #default="{ row }">{{ row.latestDate || '暂无' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="60" fixed="right" align="center">
          <template #default="{ row }">
            <el-dropdown trigger="click" @command="(command: string) => handleWatchlistCommand(command, row)">
              <el-button link size="small" class="action-more-btn"><el-icon><MoreFilled /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="refresh" :icon="Refresh" :disabled="watchBusyCode === row.code">立即更新</el-dropdown-item>
                  <el-dropdown-item command="remove" :icon="Star" :disabled="watchBusyCode === row.code">取消关注</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="watchlist.length ? '没有符合条件的股票' : '关注清单为空，可在股票目录中开启关注'" />
        </template>
      </el-table>
      <el-pagination
        v-if="filteredWatchlist.length > 0"
        class="watchlist-pagination"
        v-model:current-page="watchlistPage"
        v-model:page-size="watchlistPageSize"
        :total="filteredWatchlist.length"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="watchlistPage = 1"
      />
      <template #footer>
        <el-button @click="watchlistVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailVisible"
      :title="detail?.name ? `${detail.name}（${detail.code}）` : '股票详情'"
      width="min(1200px, 96vw)"
      top="4vh"
      class="stock-detail-dialog"
      destroy-on-close
    >
      <div v-loading="detailLoading" class="stock-detail-content">
        <template v-if="detail">
          <el-tabs v-model="detailTab" class="stock-detail-tabs">
            <el-tab-pane label="证券信息" name="security">
              <el-descriptions class="stock-detail-descriptions" :column="3" border>
            <el-descriptions-item label="股票名称">{{ detail.name }}</el-descriptions-item>
            <el-descriptions-item label="股票代码">{{ detail.code }}</el-descriptions-item>
            <el-descriptions-item label="上市市场">{{ detail.market }}</el-descriptions-item>
            <el-descriptions-item label="所属行业">{{ detail.industry || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="最新价格">{{ formatPrice(detail.currentPrice) }}</el-descriptions-item>
            <el-descriptions-item label="涨跌幅">{{ formatPercent(detail.changePercent) }}</el-descriptions-item>
            <el-descriptions-item label="昨收价格">{{ formatPrice(detail.previousClose) }}</el-descriptions-item>
            <el-descriptions-item label="行情时间">{{ detail.quoteTime || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="档案更新时间">{{ formatDateTime(detail.profileUpdatedAt) }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>

            <el-tab-pane label="公司基本资料" name="company">
              <el-descriptions class="stock-detail-descriptions" :column="3" border>
            <el-descriptions-item label="公司全称" :span="2">{{ detail.companyName || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="曾用名">{{ detail.formerNames || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="法定代表人">{{ detail.legalRepresentative || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="注册资本">{{ detail.registeredCapital || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="成立日期">{{ detail.establishedDate || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="所在省份">{{ detail.province || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="员工人数">{{ detail.employeeCount || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="公司地址" :span="3">{{ detail.registeredAddress || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="公司网站">{{ detail.website || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="电子邮箱">{{ detail.email || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ detail.phone || '暂无' }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>

            <el-tab-pane label="经营与公司背景" name="business">
              <el-descriptions class="stock-detail-descriptions stock-detail-narrative" :column="1" border>
            <el-descriptions-item label="公司简介">{{ detail.companyProfile || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="主营业务">{{ detail.mainBusiness || '暂无' }}</el-descriptions-item>
            <el-descriptions-item label="经营范围">{{ detail.businessScope || '暂无' }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>
          </el-tabs>
        </template>
        <p class="detail-note">公司资料来自东方财富公开档案，行情来自公开报价接口，可能存在延迟；内容仅供参考。</p>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :icon="Refresh"
          :loading="profileUpdating"
          :disabled="detailLoading || !detail"
          @click="refreshProfile"
        >更新公司档案</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="marketDataVisible"
      :title="`${marketDataStock?.name || '股票'}（${marketDataStock?.code || ''}）· 历史行情与回测`"
      width="min(1200px, 96vw)"
      top="4vh"
      class="stock-market-dialog"
      destroy-on-close
    >
      <el-tabs v-model="marketDataTab" class="stock-market-tabs">
        <el-tab-pane label="历史行情" name="history">
          <StockMarketPanel v-if="marketDataStock" :code="marketDataStock.code" mode="history" />
        </el-tab-pane>
        <el-tab-pane label="回测" name="backtest">
          <StockMarketPanel v-if="marketDataStock" :code="marketDataStock.code" mode="backtest" />
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <el-dialog
      v-model="investmentOverviewVisible"
      title="投资总览"
      width="min(1400px, 96vw)"
      top="3vh"
      class="investment-overview-dialog"
      destroy-on-close
    >
      <InvestmentView v-if="investmentOverviewVisible" page-type="overview" />
    </el-dialog>

    <el-dialog
      v-model="investmentVisible"
      :title="investmentStock ? `模拟投资 · ${investmentStock.name}（${investmentStock.code}）` : '模拟投资'"
      width="min(900px, 96vw)"
      top="4vh"
      class="stock-investment-dialog"
      destroy-on-close
    >
      <InvestmentView
        v-if="investmentVisible && investmentStock"
        page-type="overview"
        :initial-code="investmentStock.code"
        :trade-only="true"
      />
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, Coin, MoreFilled, Refresh, Search, Star, TrendCharts, View } from '@element-plus/icons-vue'
import { confirm } from '@/utils/confirm'
import StockMarketPanel from '@/components/StockMarketPanel.vue'
import HistorySyncTaskPanel from '@/components/HistorySyncTaskPanel.vue'
import InvestmentView from '@/views/Investment.vue'
import {
  stockCatalogDetail,
  stockCatalogPage,
  stockCatalogProfileRefresh,
  stockCatalogSync,
  stockWatchlistAdd,
  stockWatchlistList,
  stockWatchlistRefresh,
  stockWatchlistRemove,
  stockRefreshScheduleGet,
  stockRefreshScheduleUpdate
} from '@/api'

interface StockItem {
  code: string
  name: string
  market: string
  industry: string | null
  syncedAt: string
  watched: boolean
  companyName?: string | null
  formerNames?: string | null
  legalRepresentative?: string | null
  registeredCapital?: string | null
  establishedDate?: string | null
  province?: string | null
  employeeCount?: string | null
  mainBusiness?: string | null
  businessScope?: string | null
  companyProfile?: string | null
  registeredAddress?: string | null
  website?: string | null
  email?: string | null
  phone?: string | null
  profileUpdatedAt?: string | null
  currentPrice?: number | null
  previousClose?: number | null
  changePercent?: number | null
  quoteTime?: string | null
}

interface StockWatchlistRow {
  code: string
  name: string
  market: string | null
  industry: string | null
  cachedPoints: number
  latestDate: string | null
}

const stocks = ref<StockItem[]>([])
const keyword = ref('')
const market = ref<'' | '上海' | '深圳' | '北京'>('')
const watched = ref<boolean | undefined>(undefined)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const syncing = ref(false)
const historySyncPanel = ref<InstanceType<typeof HistorySyncTaskPanel> | null>(null)
const lastSyncedAt = ref('')
const investmentOverviewVisible = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailTab = ref('security')
const profileUpdating = ref(false)
const detail = ref<StockItem | null>(null)
const marketDataVisible = ref(false)
const marketDataTab = ref('history')
const marketDataStock = ref<StockItem | null>(null)
const investmentVisible = ref(false)
const investmentStock = ref<StockItem | null>(null)
const watchlistVisible = ref(false)
const watchlistLoading = ref(false)
const watchlist = ref<StockWatchlistRow[]>([])
const refreshFrequency = ref<'HOURLY' | 'DAILY' | 'WEEKLY'>('DAILY')
const refreshScheduleSaving = ref(false)
const watchlistKeyword = ref('')
const watchlistPage = ref(1)
const watchlistPageSize = ref(10)
const watchBusyCode = ref('')
const filteredWatchlist = computed(() => {
  const query = watchlistKeyword.value.trim().toLocaleLowerCase()
  if (!query) return watchlist.value
  return watchlist.value.filter(stock =>
    stock.code.toLocaleLowerCase().includes(query) || stock.name.toLocaleLowerCase().includes(query)
  )
})
const pagedWatchlist = computed(() => {
  const start = (watchlistPage.value - 1) * watchlistPageSize.value
  return filteredWatchlist.value.slice(start, start + watchlistPageSize.value)
})

const errorMessage = (error: unknown) => error instanceof Error ? error.message : '操作失败，请稍后重试'
const formatDateTime = (value: string | null | undefined) => value ? value.replace('T', ' ').slice(0, 16) : '暂无'
const formatPrice = (value: number | null | undefined) => value == null ? '暂无' : `¥${Number(value).toFixed(2)}`
const formatPercent = (value: number | null | undefined) => {
  if (value == null) return '暂无'
  const percent = Number(value)
  return `${percent > 0 ? '+' : ''}${percent.toFixed(2)}%`
}

const loadCatalog = async () => {
  loading.value = true
  try {
    const response: any = await stockCatalogPage({
      keyword: keyword.value.trim() || undefined,
      market: market.value || undefined,
      watched: watched.value,
      page: page.value,
      pageSize: pageSize.value
    })
    stocks.value = response.data?.items || []
    total.value = response.data?.total || 0
    lastSyncedAt.value = stocks.value.reduce((latest, stock) =>
      stock.syncedAt > latest ? stock.syncedAt : latest, '')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

const loadWatchlist = async () => {
  watchlistLoading.value = true
  try {
    const response: any = await stockWatchlistList()
    watchlist.value = response.data || []
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    watchlistLoading.value = false
  }
}

const loadRefreshSchedule = async () => {
  try {
    const response: any = await stockRefreshScheduleGet()
    refreshFrequency.value = response.data.frequency
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

const saveRefreshSchedule = async () => {
  refreshScheduleSaving.value = true
  try {
    const response: any = await stockRefreshScheduleUpdate(refreshFrequency.value)
    refreshFrequency.value = response.data.frequency
    ElMessage.success('股票自动同步频率已保存')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    refreshScheduleSaving.value = false
  }
}

watch(() => filteredWatchlist.value.length, count => {
  const lastPage = Math.max(1, Math.ceil(count / watchlistPageSize.value))
  if (watchlistPage.value > lastPage) watchlistPage.value = lastPage
})

const openWatchlist = () => {
  watchlistPage.value = 1
  watchlistVisible.value = true
  void Promise.all([loadWatchlist(), loadRefreshSchedule()])
}

const handleManagementAction = (command: string) => {
  if (command === 'watchlist') openWatchlist()
  else if (command === 'history-sync') void historySyncPanel.value?.startTask()
  else if (command === 'sync-records') void historySyncPanel.value?.openLatest()
  else if (command === 'concurrency') historySyncPanel.value?.openConcurrencySettings()
  else if (command === 'overview') openInvestmentOverview()
  else if (command === 'catalog-sync') void syncCatalog()
}

const setStockWatchStatus = async (stock: StockItem, watched: boolean) => {
  if (watched === stock.watched) return
  if (!watched && !await confirm(`取消关注 ${stock.name}（${stock.code}）？已缓存的历史行情会保留。`, {
    title: '取消股票关注',
    confirmText: '取消关注'
  })) return

  watchBusyCode.value = stock.code
  try {
    if (watched) {
      await stockWatchlistAdd(stock.code)
      ElMessage.success(`${stock.name} 已加入关注清单`)
    } else {
      await stockWatchlistRemove(stock.code)
      ElMessage.success(`${stock.name} 已取消关注`)
    }
    await Promise.all([loadCatalog(), watchlistVisible.value ? loadWatchlist() : Promise.resolve()])
  } catch (error) {
    ElMessage.error(errorMessage(error))
    await loadCatalog()
  } finally {
    watchBusyCode.value = ''
  }
}

const removeWatchedStock = async (stock: StockWatchlistRow) => {
  if (!await confirm(`取消关注 ${stock.name}（${stock.code}）？已缓存的历史行情会保留。`, {
    title: '取消股票关注',
    confirmText: '取消关注'
  })) return
  watchBusyCode.value = stock.code
  try {
    await stockWatchlistRemove(stock.code)
    ElMessage.success(`${stock.name} 已取消关注`)
    await Promise.all([loadWatchlist(), loadCatalog()])
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    watchBusyCode.value = ''
  }
}

const handleWatchlistCommand = (command: string, stock: StockWatchlistRow) => {
  if (command === 'refresh') {
    void refreshWatchedStock(stock)
  } else if (command === 'remove') {
    void removeWatchedStock(stock)
  }
}

const refreshWatchedStock = async (stock: StockWatchlistRow) => {
  watchBusyCode.value = stock.code
  try {
    await stockWatchlistRefresh(stock.code)
    ElMessage.success(`${stock.name} 历史行情已更新`)
    await loadWatchlist()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    watchBusyCode.value = ''
  }
}

const searchCatalog = () => {
  page.value = 1
  void loadCatalog()
}

const resetFilters = () => {
  keyword.value = ''
  market.value = ''
  watched.value = undefined
  searchCatalog()
}

const syncCatalog = async () => {
  syncing.value = true
  try {
    const response: any = await stockCatalogSync()
    ElMessage.success(`股票信息同步完成：共 ${response.data.total.toLocaleString()} 只`)
    page.value = 1
    await loadCatalog()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    syncing.value = false
  }
}

const openDetail = async (stock: StockItem) => {
  detailVisible.value = true
  detailLoading.value = true
  detailTab.value = 'security'
  detail.value = null
  try {
    const response: any = await stockCatalogDetail(stock.code)
    detail.value = response.data
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    detailLoading.value = false
  }
}

const refreshProfile = async () => {
  if (!detail.value) return
  profileUpdating.value = true
  try {
    const response: any = await stockCatalogProfileRefresh(detail.value.code)
    detail.value = response.data
    ElMessage.success('公司档案已更新并保存')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    profileUpdating.value = false
  }
}

const openInvestment = (stock: StockItem) => {
  investmentStock.value = stock
  investmentVisible.value = true
}

const openInvestmentOverview = () => {
  investmentOverviewVisible.value = true
}

const openMarketData = (stock: StockItem) => {
  marketDataStock.value = stock
  marketDataTab.value = 'history'
  marketDataVisible.value = true
}

const handleCommand = (command: string, stock: StockItem) => {
  if (command === 'detail') {
    void openDetail(stock)
  } else if (command === 'simulate') {
    openInvestment(stock)
  } else if (command === 'market-data') {
    openMarketData(stock)
  }
}

onMounted(loadCatalog)
</script>

<style scoped>
.stock-management-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.page-heading,
.heading-actions,
.catalog-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.heading-actions {
  justify-content: flex-end;
  flex-wrap: wrap;
}
.page-heading h1 {
  margin: 0;
  color: #243345;
  font-size: 22px;
}
.page-heading p,
.catalog-summary {
  color: #8792a2;
  font-size: 13px;
}
.page-heading p {
  margin: 6px 0 0;
}
.stock-catalog-card {
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
.action-more-btn {
  font-weight: 700;
  color: #0f9f9a;
}
.watchlist-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.watchlist-search {
  width: 280px;
}
.watchlist-count {
  margin-left: auto;
  color: #8792a2;
  font-size: 13px;
  white-space: nowrap;
}
.watchlist-pagination {
  justify-content: flex-end;
  margin-top: 14px;
}
.refresh-schedule-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  color: #526174;
  font-size: 13px;
}
.refresh-schedule-select {
  width: 210px;
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
@media (max-width: 650px) {
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .heading-actions {
    width: 100%;
    justify-content: flex-start;
  }
  .watchlist-toolbar {
    align-items: stretch;
    flex-wrap: wrap;
  }
  .refresh-schedule-toolbar {
    align-items: stretch;
    flex-wrap: wrap;
  }
  .refresh-schedule-select {
    width: 100%;
  }
  .watchlist-search {
    width: 100%;
  }
  .watchlist-count {
    margin-left: 0;
  }
}
</style>

<style>
.stock-detail-dialog .el-dialog__body,
.stock-investment-dialog .el-dialog__body,
.investment-overview-dialog .el-dialog__body {
  max-height: 76vh;
  overflow: auto;
  padding: 18px 24px 22px;
}
.stock-detail-dialog .el-dialog__header,
.stock-investment-dialog .el-dialog__header,
.investment-overview-dialog .el-dialog__header {
  padding-bottom: 14px;
  border-bottom: 1px solid #e8edf2;
}
.stock-detail-dialog .el-dialog__title,
.stock-investment-dialog .el-dialog__title,
.investment-overview-dialog .el-dialog__title {
  color: #203247;
  font-size: 19px;
  font-weight: 700;
}
.stock-detail-dialog .stock-detail-descriptions {
  margin-bottom: 4px;
}
.stock-detail-dialog .stock-detail-tabs .el-tabs__header {
  margin-bottom: 18px;
}
.stock-detail-dialog .stock-detail-descriptions .el-descriptions__label {
  min-width: 105px;
  background: #f1f5f8 !important;
  color: #526579 !important;
  font-size: 12px;
  font-weight: 700;
}
.stock-detail-dialog .stock-detail-descriptions .el-descriptions__content {
  background: #fff !important;
  color: #26384b !important;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.stock-detail-dialog .stock-detail-narrative .el-descriptions__label {
  width: 120px;
  white-space: nowrap;
}
.stock-detail-dialog .stock-detail-narrative .el-descriptions__content {
  line-height: 1.85;
}
@media (max-width: 650px) {
  .stock-detail-dialog .el-dialog__body,
  .stock-investment-dialog .el-dialog__body,
  .investment-overview-dialog .el-dialog__body {
    padding: 14px 12px 18px;
  }
  .stock-detail-dialog .stock-detail-descriptions .el-descriptions__label {
    min-width: 82px;
  }
}
</style>
