<template>
  <section class="cf-ip-page">
    <header class="page-heading">
      <div>
        <h1>Cloudflare IP 优选</h1>
        <p>同步 Cloudflare 官方全球任播网段并测试连接；机房代码按测速响应识别，代表 IP 服务主机本次到达的节点。</p>
      </div>
      <div v-if="activeTab === 'candidates'" class="heading-actions">
        <el-button :icon="Download" :disabled="v2raynCandidates.length === 0" @click="exportV2rayn">
          导出 v2rayN IP/SNI 清单
        </el-button>
        <el-button type="primary" :icon="Plus" @click="importDialogVisible = true">添加候选 IP</el-button>
      </div>
    </header>

    <div class="summary-row">
      <el-card shadow="never">
        <span>候选 IP</span>
        <strong>{{ candidateSummary.total }}</strong>
      </el-card>
      <el-card shadow="never">
        <span>可用</span>
        <strong class="available-count">{{ candidateSummary.availableCount }}</strong>
        <strong class="available-count">{{ candidateSummary.availableAddressCount }}</strong>
      </el-card>
      <el-card shadow="never">
        <span>最快 TLS</span>
        <strong>{{ fastestLatency === null ? '-' : `${Number(fastestLatency).toFixed(3)} ms` }}</strong>
      </el-card>
    </div>

    <el-tabs v-model="activeTab" class="management-tabs">
      <el-tab-pane label="IP 管理" name="candidates" />
      <el-tab-pane label="Cloudflare 官方 IP 网段" name="ranges" />
    </el-tabs>

    <el-card v-if="activeTab === 'candidates'" shadow="never" class="candidate-card">
      <div class="filter-toolbar">
        <el-input
          v-model="keyword"
          clearable
          placeholder="按 IP 前缀搜索"
          :prefix-icon="Search"
          class="filter-keyword"
          @keyup.enter="applyFilters"
        />
        <el-select v-model="ipVersionFilter" clearable placeholder="IP 版本" class="filter-select">
          <el-option label="IPv4" value="4" />
          <el-option label="IPv6" value="6" />
        </el-select>
        <el-select v-model="regionFilter" clearable filterable placeholder="全部地区" class="filter-select">
          <el-option v-for="region in availableRegions" :key="region" :label="region" :value="region" />
        </el-select>
        <el-select v-model="coloFilter" clearable filterable placeholder="全部数据中心 colo" class="filter-select">
          <el-option v-for="colo in availableColos" :key="colo" :label="colo" :value="colo" />
        </el-select>
        <el-select v-model="statusFilter" placeholder="全部连接状态" class="filter-select">
          <el-option label="全部连接状态" value="ALL" />
          <el-option label="未测试" value="PENDING" />
          <el-option label="可连接" value="AVAILABLE" />
          <el-option label="连接失败" value="FAILED" />
        </el-select>
        <el-input-number
          v-model="maxTlsMs"
          :min="0"
          :precision="0"
          :step="10"
          :controls="false"
          clearable
          placeholder="TLS 上限 ms"
          class="filter-number"
        />
        <el-input-number
          v-model="minSpeedMbps"
          :min="0"
          :precision="0"
          :step="10"
          :controls="false"
          clearable
          placeholder="速度下限 Mbps"
          class="filter-number"
        />
        <el-button type="primary" :icon="Search" @click="applyFilters">查询</el-button>
        <el-button :icon="Refresh" @click="resetFilters">重置筛选</el-button>
        <span class="filter-count">匹配 {{ totalCandidates }} 个候选 IP</span>
      </div>

      <div class="candidate-toolbar">
        <div class="toolbar-actions">
          <span class="selection-hint">已选 {{ selectedCandidates.length }} 个，单批最多测试 100 个</span>
          <el-button
            :icon="Connection"
            :disabled="selectedCandidates.length === 0 || selectedCandidates.length > 100 || probeBusy || rangeProbeBusy"
            :loading="probeBusy && !probeAllBusy && !probeRetryStatus"
            @click="probeSelected"
          >
            测试所选
          </el-button>
          <el-button
            type="danger"
            :icon="Delete"
            :disabled="selectedCandidates.length === 0 || probeBusy || rangeProbeBusy || deleteSelectedBusy"
            :loading="deleteSelectedBusy"
            @click="deleteSelected"
          >
            删除所选
          </el-button>
          <el-button
            type="primary"
            :icon="Refresh"
            :disabled="candidateSummary.total === 0 || probeBusy || rangeProbeBusy"
            :loading="probeAllBusy"
            @click="probeAll"
          >
            {{ probeAllBusy ? `测试中 ${probeProgress}/${probeRetryTotal}` : '测试全部' }}
          </el-button>
          <el-button
            :icon="Refresh"
            :disabled="retryAvailableCount === 0 || probeBusy || rangeProbeBusy"
            :loading="probeRetryStatus === 'AVAILABLE'"
            @click="retryByStatus('AVAILABLE')"
          >
            {{ probeRetryStatus === 'AVAILABLE'
              ? `重试中 ${probeProgress}/${probeRetryTotal}`
              : `重试可连接 (${retryAvailableCount})` }}
          </el-button>
          <el-button
            :icon="Refresh"
            :disabled="retryFailedCount === 0 || probeBusy || rangeProbeBusy"
            :loading="probeRetryStatus === 'FAILED'"
            @click="retryByStatus('FAILED')"
          >
            {{ probeRetryStatus === 'FAILED'
              ? `重试中 ${probeProgress}/${probeRetryTotal}`
              : `重试连接失败 (${retryFailedCount})` }}
          </el-button>
          <el-button
            :icon="CopyDocument"
            :disabled="candidateSummary.availableAddressCount === 0"
            @click="copyAvailable"
          >
            复制可用 IP
          </el-button>
        </div>
      </div>

      <div class="table-scroll-wrapper">
        <el-table
          ref="candidateTable"
          :data="pagedCandidates"
          v-loading="loading"
          stripe
          border
          resizable
          max-height="calc(100vh - 390px)"
          @selection-change="selectedCandidates = $event"
        >
          <el-table-column
            type="selection"
            width="48"
            :selectable="() => !probeBusy && !rangeProbeBusy && !deleteSelectedBusy"
          />
          <el-table-column type="index" label="#" width="64" align="center" />
          <el-table-column prop="ipAddress" label="IP 地址" min-width="190" />
          <el-table-column prop="region" label="地区" width="130" />
          <el-table-column prop="colo" label="数据中心 colo" width="135" align="center">
            <template #default="{ row }">{{ row.colo || '-' }}</template>
          </el-table-column>
          <el-table-column label="TCP 443 (ms)" width="125" align="right">
            <template #default="{ row }">{{ formatMetric(row.pingMs) }}</template>
          </el-table-column>
          <el-table-column label="TLS (ms)" width="125" align="right">
            <template #default="{ row }">{{ formatMetric(row.tlsMs) }}</template>
          </el-table-column>
          <el-table-column label="测速 (Mbps)" width="135" align="right">
            <template #default="{ row }">{{ formatMetric(row.speedMbps) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="errorMessage" label="测试结果" min-width="150">
            <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
          </el-table-column>
          <el-table-column label="最近测试时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.lastTestedAt) || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right" align="center">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                :icon="CopyDocument"
                :aria-label="`复制 IP ${row.ipAddress}`"
                @click="copyCandidateIp(row)"
              />
              <el-button
                link
                type="primary"
                :icon="Connection"
                :loading="probingCandidateId === row.id"
                :disabled="probeBusy || rangeProbeBusy"
                @click="probeCandidate(row)"
              >
                测试
              </el-button>
              <el-button
                link
                type="danger"
                :icon="Delete"
                :disabled="probeBusy || rangeProbeBusy || deleteSelectedBusy"
                aria-label="删除 IP"
                @click="deleteCandidate(row)"
              />
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :description="hasActiveFilters ? '没有符合筛选条件的候选 IP' : '暂无候选 IP，请先添加或粘贴 IP 地址'" />
          </template>
        </el-table>
      </div>
      <el-pagination
        v-if="totalCandidates > pageSize"
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="totalCandidates"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadCandidates"
        @size-change="loadCandidates"
      />
    </el-card>

    <el-card v-if="activeTab === 'ranges'" shadow="never" class="candidate-card range-card">
      <div class="section-heading range-section-heading">
        <div>
          <h2>Cloudflare 官方 IP 网段</h2>
          <p>网段为全球任播，不代表固定地理位置；可按需维护地区标签，并停用不参与抽样优选的网段。</p>
        </div>
      </div>
      <div class="range-settings-row">
        <label class="sample-count-control">
          <span>定时抽样</span>
          <el-switch v-model="rangeProbeScheduleEnabled" active-text="启用" inactive-text="关闭" />
        </label>
        <label class="sample-count-control">
          <span>执行频率</span>
          <el-select v-model="rangeProbeFrequency" class="schedule-frequency-select">
            <el-option label="每小时" value="HOURLY" />
            <el-option label="每天 03:00" value="DAILY" />
            <el-option label="每周一 03:00" value="WEEKLY" />
          </el-select>
        </label>
        <label class="range-sni-control">
          <span>TLS serverName/SNI</span>
          <el-input
            v-model="tlsServerName"
            clearable
            maxlength="253"
            placeholder="v2rayN TLS serverName/SNI"
          />
        </label>
        <label class="sample-count-control">
          <span>每段抽样</span>
          <el-input-number v-model="samplesPerRange" :min="1" :max="5" :step="1" />
        </label>
        <el-button type="primary" :loading="rangeScheduleSaving" @click="saveRangeProbeSchedule">
          保存计划
        </el-button>
      </div>
      <div class="probe-note">定时抽样按上海时区执行；每小时在整点运行，每天或每周一于 03:00 运行。</div>
      <div class="range-actions-row">
        <el-button
          :icon="Refresh"
          :loading="rangeInitBusy"
          :disabled="rangeImportBusy || rangeProbeBusy"
          @click="initializeRanges"
        >
          初始化官方网段
        </el-button>
        <el-button
          :icon="Plus"
          :loading="rangeImportBusy"
          :disabled="enabledIpv4RangeCount === 0 || rangeInitBusy || rangeImportBusy || rangeProbeBusy"
          @click="importEnabledIpv4Ranges"
        >
          导入启用 IPv4 全部地址
        </el-button>
        <el-button
          type="primary"
          :icon="Connection"
          :loading="rangeProbeBusy"
          :disabled="enabledRangeCount === 0 || rangeProbeBusy || rangeImportBusy || probeBusy"
          @click="probeRanges"
        >
          测试启用网段样本
        </el-button>
        <span class="filter-count">已启用 {{ enabledRangeCount }} / {{ ranges.length }}</span>
      </div>
      <div class="probe-note">
        每段最多抽样 5 个地址，本次最多测试 200 个样本；Ping 指标为从 IP 服务主机 TCP 连接候选 IP:443 的耗时，不依赖 ICMP。
        TLS 使用上方填写的 serverName/SNI 与证书校验，必须与 v2rayN 节点配置一致；测速单独使用 speed.cloudflare.com，单次下载约 30 MB。
        CIDR 数据中心 colo 按已探测候选 IP 的 colo 多数票自动归类，平票时取最近一次观测。
      </div>
      <div class="range-filter-toolbar">
        <el-input v-model="rangeKeyword" clearable placeholder="搜索 CIDR 网段" class="range-filter-input" />
        <el-select v-model="rangeIpVersionFilter" clearable placeholder="全部版本" class="range-filter-select">
          <el-option label="IPv4" value="4" />
          <el-option label="IPv6" value="6" />
        </el-select>
        <el-input v-model="rangeRegionFilter" clearable placeholder="筛选地区标签" class="range-filter-input" />
        <el-select v-model="rangeColoFilter" clearable filterable placeholder="全部数据中心 colo" class="range-filter-select">
          <el-option v-for="colo in availableRangeColos" :key="colo" :label="colo" :value="colo" />
        </el-select>
        <el-button :icon="Refresh" @click="resetRangeFilters">重置筛选</el-button>
        <span class="filter-count">匹配 {{ totalFilteredRanges }} 个网段</span>
      </div>
      <el-table :data="rangePageRows" v-loading="rangesLoading" stripe border max-height="min(65vh, 560px)">
        <el-table-column prop="cidr" label="CIDR 网段" min-width="190" />
        <el-table-column prop="ipVersion" label="版本" width="80" align="center">
          <template #default="{ row }">IPv{{ row.ipVersion }}</template>
        </el-table-column>
        <el-table-column label="优选启用" width="110" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :disabled="savingEnabledId === row.id || rangeProbeBusy || rangeInitBusy || rangeImportBusy"
              @change="saveRangeEnabled(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="colo" label="数据中心 colo" width="135" align="center">
          <template #default="{ row }">{{ row.colo || '-' }}</template>
        </el-table-column>
        <el-table-column label="地区标签" min-width="230">
          <template #default="{ row }">
            <div class="region-editor">
              <el-input v-model="rangeRegions[row.id]" maxlength="64" />
              <el-button
                size="small"
                :loading="savingRegionId === row.id"
                @click="saveRangeRegion(row)"
              >
                保存
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="totalFilteredRanges > rangePageSize"
        v-model:current-page="rangePage"
        v-model:page-size="rangePageSize"
        :total="totalFilteredRanges"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadRangePage"
        @size-change="changeRangePageSize"
      />
      <el-empty
        v-if="!rangesLoading && totalFilteredRanges === 0"
        :description="ranges.length === 0 ? '尚未初始化官方网段' : '没有符合筛选条件的网段'"
      />
    </el-card>

    <el-dialog v-model="importDialogVisible" title="添加候选 IP" width="min(620px, calc(100vw - 32px))">
      <el-input
        v-model="importText"
        type="textarea"
        :rows="8"
        maxlength="100000"
        show-word-limit
        placeholder="每行一个，也可使用空格、逗号或分号分隔"
      />
      <p class="dialog-tip">请输入 Cloudflare IP 候选地址。重复 IP 会自动跳过，单次最多导入 1,000 个。</p>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importBusy" :disabled="!importText.trim()" @click="submitImport">
          保存候选
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cfIpAdd,
  cfIpAvailableAddresses,
  cfIpCandidateIds,
  cfIpDelete,
  cfIpDeleteBatch,
  cfIpFilters,
  cfIpInitializeRanges,
  cfIpImportEnabledIpv4,
  cfIpPage,
  cfIpProbe,
  cfIpProbeRanges,
  cfIpRangeProbeSchedule,
  cfIpRangePage,
  cfIpRanges,
  cfIpSummary,
  cfIpUpdateRangeProbeSchedule,
  cfIpUpdateRangeEnabled,
  cfIpUpdateRangeRegion
} from '@/api'
import { Connection, CopyDocument, Delete, Download, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { formatDateTime } from '@/utils/dateTime'

interface CfIpCandidate {
  id: number
  ipAddress: string
  region: string
  colo: string | null
  tlsServerName: string | null
  latencyMs: number | null
  pingMs: number | null
  tlsMs: number | null
  speedMbps: number | null
  status: 'PENDING' | 'AVAILABLE' | 'FAILED'
  errorMessage: string | null
  lastTestedAt: string | null
}

interface CfIpRange {
  id: number
  cidr: string
  ipVersion: number
  region: string
  colo: string | null
  active: boolean
  enabled: boolean
}

interface CfIpCandidateSummary {
  total: number
  availableCount: number
  availableAddressCount: number
  pendingCount: number
  failedCount: number
  fastestTlsMs: number | null
}

const candidates = ref<CfIpCandidate[]>([])
const probedCandidates = ref<CfIpCandidate[]>([])
const ranges = ref<CfIpRange[]>([])
const rangePageRows = ref<CfIpRange[]>([])
const totalFilteredRanges = ref(0)
const candidateSummary = ref<CfIpCandidateSummary>({
  total: 0,
  availableCount: 0,
  availableAddressCount: 0,
  pendingCount: 0,
  failedCount: 0,
  fastestTlsMs: null
})
const totalCandidates = ref(0)
const availableRegions = ref<string[]>([])
const availableColos = ref<string[]>([])
const activeTab = ref('candidates')
const candidateTable = ref()
const selectedCandidates = ref<CfIpCandidate[]>([])
const loading = ref(false)
const rangesLoading = ref(false)
const importBusy = ref(false)
const probeBusy = ref(false)
const probeAllBusy = ref(false)
const probeRetryStatus = ref<'AVAILABLE' | 'FAILED' | null>(null)
const probeRetryTotal = ref(0)
const deleteSelectedBusy = ref(false)
const rangeInitBusy = ref(false)
const rangeProbeBusy = ref(false)
const rangeScheduleSaving = ref(false)
const rangeImportBusy = ref(false)
const savingRegionId = ref<number | null>(null)
const savingEnabledId = ref<number | null>(null)
const probingCandidateId = ref<number | null>(null)
const probeProgress = ref(0)
const importDialogVisible = ref(false)
const importText = ref('')
const rangeRegions = ref<Record<number, string>>({})
const rangeKeyword = ref('')
const rangeIpVersionFilter = ref('')
const rangeRegionFilter = ref('')
const rangeColoFilter = ref('')
const rangePage = ref(1)
const rangePageSize = ref(10)
const samplesPerRange = ref(1)
const tlsServerName = ref('www.cloudflare.com')
const rangeProbeScheduleEnabled = ref(false)
const rangeProbeFrequency = ref<'HOURLY' | 'DAILY' | 'WEEKLY'>('DAILY')
const keyword = ref('')
const ipVersionFilter = ref('')
const regionFilter = ref('')
const coloFilter = ref('')
const statusFilter = ref('ALL')
const maxTlsMs = ref<number | undefined>()
const minSpeedMbps = ref<number | undefined>()
const appliedFilters = ref({
  keyword: '',
  ipVersion: '',
  region: '',
  colo: '',
  status: 'ALL',
  maxTlsMs: undefined as number | undefined,
  minSpeedMbps: undefined as number | undefined
})
const page = ref(1)
const pageSize = ref(10)

const availableRangeColos = computed(() =>
  Array.from(new Set(ranges.value.map(range => range.colo).filter((colo): colo is string => Boolean(colo))))
    .sort((a, b) => a.localeCompare(b))
)
const enabledRangeCount = computed(() => ranges.value.filter(range => range.enabled).length)
const enabledIpv4RangeCount = computed(() =>
  ranges.value.filter(range => range.enabled && range.ipVersion === 4).length
)
const hasActiveFilters = computed(() =>
  Boolean(appliedFilters.value.keyword.trim() || appliedFilters.value.ipVersion || appliedFilters.value.region
    || appliedFilters.value.colo || appliedFilters.value.status !== 'ALL'
    || appliedFilters.value.maxTlsMs !== undefined || appliedFilters.value.minSpeedMbps !== undefined)
)
const pagedCandidates = computed(() => candidates.value)
const retryAvailableCount = computed(() => candidateSummary.value.availableCount)
const retryFailedCount = computed(() => candidateSummary.value.failedCount)
const availableCandidates = computed(() =>
  probedCandidates.value.filter(candidate => candidate.status === 'AVAILABLE' && candidate.latencyMs !== null)
)
const v2raynCandidates = computed(() => {
  const serverName = tlsServerName.value.trim().toLowerCase()
  return serverName
    ? availableCandidates.value.filter(candidate => candidate.tlsServerName?.toLowerCase() === serverName)
    : []
})
const fastestLatency = computed(() => candidateSummary.value.fastestTlsMs)

watch(appliedFilters, async () => {
  page.value = 1
  selectedCandidates.value = []
  await nextTick()
  candidateTable.value?.clearSelection()
  await loadCandidates()
})

let rangeFilterTimer: ReturnType<typeof setTimeout> | null = null
watch([rangeKeyword, rangeIpVersionFilter, rangeRegionFilter, rangeColoFilter], () => {
  rangePage.value = 1
  if (rangeFilterTimer) clearTimeout(rangeFilterTimer)
  rangeFilterTimer = setTimeout(() => {
    rangeFilterTimer = null
    void loadRangePage().catch(() => ElMessage.error('筛选 Cloudflare IP 网段失败'))
  }, 250)
})
const resetFilters = () => {
  keyword.value = ''
  ipVersionFilter.value = ''
  regionFilter.value = ''
  coloFilter.value = ''
  statusFilter.value = 'ALL'
  maxTlsMs.value = undefined
  minSpeedMbps.value = undefined
  applyFilters()
}

const applyFilters = () => {
  appliedFilters.value = {
    keyword: keyword.value,
    ipVersion: ipVersionFilter.value,
    region: regionFilter.value,
    colo: coloFilter.value,
    status: statusFilter.value,
    maxTlsMs: maxTlsMs.value,
    minSpeedMbps: minSpeedMbps.value
  }
}

const resetRangeFilters = () => {
  rangeKeyword.value = ''
  rangeIpVersionFilter.value = ''
  rangeRegionFilter.value = ''
  rangeColoFilter.value = ''
  rangePage.value = 1
  if (rangeFilterTimer) {
    clearTimeout(rangeFilterTimer)
    rangeFilterTimer = null
  }
  void loadRangePage().catch(() => ElMessage.error('重置网段筛选失败'))
}

const changeRangePageSize = () => {
  rangePage.value = 1
  void loadRangePage().catch(() => ElMessage.error('加载 Cloudflare IP 网段失败'))
}

const loadCandidates = async () => {
  loading.value = true
  try {
    const filters = appliedFilters.value
    const response = await cfIpPage({
      current: page.value,
      size: pageSize.value,
      keyword: filters.keyword.trim() || undefined,
      ipVersion: filters.ipVersion || undefined,
      region: filters.region || undefined,
      colo: filters.colo || undefined,
      status: filters.status === 'ALL' ? undefined : filters.status,
      maxTlsMs: filters.maxTlsMs,
      minSpeedMbps: filters.minSpeedMbps
    }) as { data: { records: CfIpCandidate[]; total: number } }
    candidates.value = response.data.records || []
    totalCandidates.value = response.data.total || 0
    const lastPage = Math.max(1, Math.ceil(totalCandidates.value / pageSize.value))
    if (page.value > lastPage) {
      page.value = lastPage
      await loadCandidates()
    }
  } finally {
    loading.value = false
  }
}

const loadSummary = async () => {
  const response = await cfIpSummary() as { data: CfIpCandidateSummary }
  candidateSummary.value = response.data
}

const loadCandidateFilters = async () => {
  const response = await cfIpFilters() as { data: { regions: string[]; colos: string[] } }
  availableRegions.value = response.data.regions || []
  availableColos.value = response.data.colos || []
}

const refreshCandidateData = async () => {
  await Promise.all([loadCandidates(), loadSummary(), loadCandidateFilters()])
}

const loadRanges = async () => {
  const response = await cfIpRanges() as { data: CfIpRange[] }
  ranges.value = response.data || []
  rangeRegions.value = Object.fromEntries(ranges.value.map(range => [range.id, range.region]))
}

const loadRangeProbeSchedule = async () => {
  const response = await cfIpRangeProbeSchedule() as {
    data: {
      enabled: boolean
      frequency: 'HOURLY' | 'DAILY' | 'WEEKLY'
      samplesPerRange: number
      tlsServerName: string
    }
  }
  rangeProbeScheduleEnabled.value = response.data.enabled
  rangeProbeFrequency.value = response.data.frequency
  samplesPerRange.value = response.data.samplesPerRange
  tlsServerName.value = response.data.tlsServerName
}

const saveRangeProbeSchedule = async () => {
  rangeScheduleSaving.value = true
  try {
    await cfIpUpdateRangeProbeSchedule({
      enabled: rangeProbeScheduleEnabled.value,
      frequency: rangeProbeFrequency.value,
      samplesPerRange: samplesPerRange.value,
      tlsServerName: tlsServerName.value.trim()
    })
    ElMessage.success('定时抽样计划已保存')
  } catch {
    ElMessage.error('保存定时抽样计划失败，请检查配置并重试')
  } finally {
    rangeScheduleSaving.value = false
  }
}

const loadRangePage = async () => {
  rangesLoading.value = true
  try {
    const response = await cfIpRangePage({
      current: rangePage.value,
      size: rangePageSize.value,
      cidr: rangeKeyword.value.trim() || undefined,
      ipVersion: rangeIpVersionFilter.value || undefined,
      region: rangeRegionFilter.value.trim() || undefined,
      colo: rangeColoFilter.value || undefined
    }) as { data: { records: CfIpRange[]; total: number } }
    rangePageRows.value = response.data.records || []
    totalFilteredRanges.value = response.data.total || 0
  } finally {
    rangesLoading.value = false
  }
}

watch(activeTab, tab => {
  if (tab === 'ranges') {
    Promise.all([loadRanges(), loadRangePage(), loadRangeProbeSchedule()])
      .catch(() => ElMessage.error('加载 Cloudflare 官方 IP 网段失败'))
  }
})

const initializeRanges = async () => {
  rangeInitBusy.value = true
  try {
    const response = await cfIpInitializeRanges() as { data: number }
    await Promise.all([loadRanges(), loadRangePage()])
    ElMessage.success(`已同步 ${response.data || 0} 个 Cloudflare 官方网段`)
  } catch {
    ElMessage.error('初始化官方网段失败，请检查 IP 服务网络和日志')
  } finally {
    rangeInitBusy.value = false
  }
}

const importEnabledIpv4Ranges = async () => {
  if (!enabledIpv4RangeCount.value) return
  try {
    await ElMessageBox.confirm(
      `将导入 ${enabledIpv4RangeCount.value} 个启用 IPv4 网段的全部可用地址，跳过网络地址、广播地址及数据库中已有的 IP。IPv6 网段不会导入；导入数量可能较大。`,
      '确认导入启用网段',
      { type: 'warning', confirmButtonText: '开始导入', cancelButtonText: '取消', closeOnClickModal: false }
    )
  } catch {
    return
  }

  rangeImportBusy.value = true
  try {
    const response = await cfIpImportEnabledIpv4() as { data: number }
    ElMessage.success(`导入完成，新增 ${response.data || 0} 个 IP，重复地址已跳过；候选列表未自动刷新`)
  } catch {
    ElMessage.error('导入启用 IPv4 网段失败，请检查 IP 服务日志')
  } finally {
    rangeImportBusy.value = false
  }
}

const saveRangeRegion = async (range: CfIpRange) => {
  savingRegionId.value = range.id
  try {
    await cfIpUpdateRangeRegion(range.id, rangeRegions.value[range.id] || '')
    ElMessage.success(`已更新 ${range.cidr} 的地区标签`)
    await Promise.all([loadRanges(), loadRangePage(), refreshCandidateData()])
  } catch {
    ElMessage.error(`保存 ${range.cidr} 的地区标签失败`)
  } finally {
    savingRegionId.value = null
  }
}

const saveRangeEnabled = async (range: CfIpRange) => {
  savingEnabledId.value = range.id
  try {
    await cfIpUpdateRangeEnabled(range.id, range.enabled)
    await Promise.all([loadRanges(), loadRangePage()])
    ElMessage.success(`${range.cidr} 已${range.enabled ? '启用' : '停用'}优选`)
  } catch {
    ElMessage.error(`更新 ${range.cidr} 的优选状态失败`)
    try {
      await loadRanges()
    } catch {
      ElMessage.error('刷新网段状态失败，请手动刷新页面确认开关状态')
    }
  } finally {
    savingEnabledId.value = null
  }
}

const probeRanges = async () => {
  const serverName = validatedTlsServerName()
  if (!serverName) return
  rangeProbeBusy.value = true
  try {
    const response = await cfIpProbeRanges(samplesPerRange.value, serverName) as { data: CfIpCandidate[] }
    mergeProbeResults(response.data || [])
    await Promise.all([refreshCandidateData(), loadRanges(), loadRangePage()])
    ElMessage.success(`已完成 ${response.data?.length || 0} 个网段样本的 TCP、TLS 和测速测试`)
  } catch {
    ElMessage.error('网段抽样测试失败，请检查 IP 服务日志')
  } finally {
    rangeProbeBusy.value = false
  }
}

const formatMetric = (value: number | null) =>
  value === null || value === undefined ? '-' : Number(value).toFixed(3)

const mergeProbeResults = (results: CfIpCandidate[]) => {
  const candidatesById = new Map(candidates.value.map(candidate => [candidate.id, candidate]))
  const probedById = new Map(probedCandidates.value.map(candidate => [candidate.id, candidate]))
  results.forEach(candidate => {
    const existing = candidatesById.get(candidate.id)
    if (existing) {
      candidatesById.set(candidate.id, {
        ...existing,
        ...candidate,
        tlsServerName: candidate.tlsServerName || existing.tlsServerName
      })
    }
    const probed = probedById.get(candidate.id)
    probedById.set(candidate.id, probed
      ? { ...probed, ...candidate, tlsServerName: candidate.tlsServerName || probed.tlsServerName }
      : candidate)
  })
  candidates.value = Array.from(candidatesById.values())
  probedCandidates.value = Array.from(probedById.values())
}

const validatedTlsServerName = () => {
  const serverName = tlsServerName.value.trim()
  if (!serverName || serverName.length > 253
      || !/^([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/i.test(serverName)) {
    ElMessage.error('请输入与 v2rayN 节点一致的 TLS serverName/SNI 域名')
    return null
  }
  return serverName.toLowerCase()
}

const statusLabel = (status: CfIpCandidate['status']) => ({
  AVAILABLE: '可连接',
  FAILED: '连接失败',
  PENDING: '未测试'
}[status])

const statusType = (status: CfIpCandidate['status']) => ({
  AVAILABLE: 'success',
  FAILED: 'danger',
  PENDING: 'info'
}[status])

const submitImport = async () => {
  importBusy.value = true
  try {
    const response = await cfIpAdd(importText.value) as { data: number }
    ElMessage.success(`已添加 ${response.data || 0} 个新候选 IP`)
    importText.value = ''
    importDialogVisible.value = false
    page.value = 1
    await refreshCandidateData()
  } catch {
    ElMessage.error('添加候选 IP 失败')
  } finally {
    importBusy.value = false
  }
}

const probeBatch = async (ids: number[]) => {
  const serverName = validatedTlsServerName()
  if (!serverName) throw new Error('Invalid TLS serverName')
  const response = await cfIpProbe(ids, serverName) as { data: CfIpCandidate[] }
  mergeProbeResults(response.data || [])
  probeProgress.value += ids.length
}

const probeCandidate = async (candidate: CfIpCandidate) => {
  const serverName = validatedTlsServerName()
  if (!serverName) return
  probingCandidateId.value = candidate.id
  probeBusy.value = true
  try {
    const response = await cfIpProbe([candidate.id], serverName) as { data: CfIpCandidate[] }
    mergeProbeResults(response.data || [])
    await Promise.all([loadCandidates(), loadSummary(), loadRanges()])
    ElMessage.success(`已完成 ${candidate.ipAddress} 的测试`)
  } catch {
    ElMessage.error(`${candidate.ipAddress} 测试失败，请检查 IP 服务日志`)
  } finally {
    probingCandidateId.value = null
    probeBusy.value = false
  }
}

const probeSelected = async () => {
  const ids = selectedCandidates.value.map(candidate => candidate.id)
  if (!ids.length || ids.length > 100) return
  probeBusy.value = true
  probeProgress.value = 0
  try {
    await probeBatch(ids)
    await refreshCandidateData()
    ElMessage.success(`已完成 ${ids.length} 个 IP 的 TCP、TLS 和测速测试`)
    selectedCandidates.value = []
  } catch {
    ElMessage.error('IP 测试失败')
  } finally {
    probeBusy.value = false
  }
}

const probeAll = async () => {
  if (!validatedTlsServerName()) return
  probeBusy.value = true
  probeAllBusy.value = true
  probeProgress.value = 0
  try {
    const response = await cfIpCandidateIds() as { data: number[] }
    const ids = response.data || []
    if (!ids.length) return
    probeRetryTotal.value = ids.length
    for (let start = 0; start < ids.length; start += 100) {
      await probeBatch(ids.slice(start, start + 100))
    }
    await refreshCandidateData()
    ElMessage.success(`已完成 ${ids.length} 个 IP 的 TCP、TLS 和测速测试`)
    selectedCandidates.value = []
  } catch {
    ElMessage.error(`测试在 ${probeProgress.value} 个 IP 后中断`)
  } finally {
    probeBusy.value = false
    probeAllBusy.value = false
    probeRetryTotal.value = 0
  }
}

const retryByStatus = async (status: 'AVAILABLE' | 'FAILED') => {
  if (probeBusy.value || rangeProbeBusy.value) return
  if (!validatedTlsServerName()) return
  probeBusy.value = true
  probeRetryStatus.value = status
  probeProgress.value = 0
  try {
    const response = await cfIpCandidateIds(status) as { data: number[] }
    const ids = response.data || []
    if (!ids.length) return
    probeRetryTotal.value = ids.length
    for (let start = 0; start < ids.length; start += 100) {
      await probeBatch(ids.slice(start, start + 100))
    }
    await refreshCandidateData()
    ElMessage.success(`已重试 ${ids.length} 个${status === 'AVAILABLE' ? '可连接' : '连接失败'} IP`)
    selectedCandidates.value = []
  } catch {
    ElMessage.error(`重试在 ${probeProgress.value} 个 IP 后中断`)
  } finally {
    probeBusy.value = false
    probeRetryStatus.value = null
    probeRetryTotal.value = 0
  }
}

const deleteCandidate = async (candidate: CfIpCandidate) => {
  try {
    await ElMessageBox.confirm(`确定删除候选 IP ${candidate.ipAddress}？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await cfIpDelete(candidate.id)
    ElMessage.success('已删除候选 IP')
    await refreshCandidateData()
  } catch {
    ElMessage.error('删除候选 IP 失败')
  }
}

const deleteSelected = async () => {
  const selected = [...selectedCandidates.value]
  if (!selected.length) return
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selected.length} 个候选 IP？此操作不可撤销。`,
      '批量删除确认',
      {
        type: 'warning',
        confirmButtonText: '删除所选',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }

  deleteSelectedBusy.value = true
  let deletedCount: number
  try {
    const response = await cfIpDeleteBatch(selected.map(candidate => candidate.id)) as { data: number }
    deletedCount = response.data
  } catch {
    ElMessage.error('批量删除候选 IP 失败，列表未刷新；请稍后重试')
    deleteSelectedBusy.value = false
    return
  }
  selectedCandidates.value = []
  await nextTick()
  candidateTable.value?.clearSelection()
  deleteSelectedBusy.value = false
  ElMessage.success(`已删除 ${deletedCount} 个候选 IP`)
  try {
    await refreshCandidateData()
  } catch {
    ElMessage.error('删除成功，但列表刷新失败，请手动刷新确认结果')
  }
}

const downloadText = (filename: string, content: string) => {
  const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

const exportV2rayn = () => {
  const serverName = validatedTlsServerName()
  if (!serverName) return
  if (!v2raynCandidates.value.length) {
    ElMessage.warning('没有使用此 SNI 测试通过的 IP；请先使用相同的 serverName/SNI 测试')
    return
  }
  const escapeCsv = (value: string | number | null) =>
    `"${String(value ?? '').replace(/"/g, '""')}"`
  const rows = [
    ['address', 'port', 'tlsServerName/SNI', 'tcpConnectMs', 'tlsMs', 'speedMbps'],
    ...v2raynCandidates.value.map(candidate => [
      candidate.ipAddress,
      443,
      serverName,
      candidate.pingMs,
      candidate.tlsMs,
      candidate.speedMbps
    ])
  ]
  downloadText('v2rayn-cf-ip.csv', `\uFEFF${rows.map(row => row.map(escapeCsv).join(',')).join('\r\n')}`)
  ElMessage.success(`已导出 ${v2raynCandidates.value.length} 个使用 ${serverName} 测试通过的 IP`)
}

const copyAvailable = async () => {
  try {
    const response = await cfIpAvailableAddresses() as { data: string[] }
    const ips = response.data || []
    await navigator.clipboard.writeText(ips.join('\n'))
    ElMessage.success(`已复制 ${ips.length} 个可用 IP`)
  } catch {
    ElMessage.error('复制失败，请检查浏览器剪贴板权限')
  }
}

const copyCandidateIp = async (candidate: CfIpCandidate) => {
  try {
    await navigator.clipboard.writeText(candidate.ipAddress)
    ElMessage.success(`已复制 ${candidate.ipAddress}`)
  } catch {
    ElMessage.error(`复制 ${candidate.ipAddress} 失败，请检查浏览器剪贴板权限`)
  }
}

onMounted(() => {
  Promise.all([loadCandidates(), loadSummary(), loadCandidateFilters(), loadRanges()])
    .catch(() => ElMessage.error('加载 Cloudflare IP 数据失败'))
})
</script>

<style scoped>
.cf-ip-page { display: flex; flex-direction: column; gap: 18px; min-height: 100%; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.page-heading h1 { color: var(--ink-950); font-size: 23px; }
.page-heading p { margin-top: 7px; color: var(--ink-700); font-size: 13px; line-height: 1.6; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 12px; }
.section-heading h2 { color: var(--ink-950); font-size: 16px; }
.section-heading p { margin-top: 5px; color: var(--ink-700); font-size: 12px; }
.heading-actions, .toolbar-actions { display: flex; align-items: center; gap: 10px; }
.range-section-heading { margin-bottom: 16px; }
.range-settings-row { display: flex; align-items: flex-end; flex-wrap: wrap; gap: 16px; padding-bottom: 14px; border-bottom: 1px solid var(--line); }
.range-sni-control { display: flex; flex: 0 1 360px; flex-direction: column; gap: 6px; color: var(--ink-700); font-size: 12px; }
.range-actions-row { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; padding: 14px 0; border-bottom: 1px solid var(--line); }
.region-editor { display: flex; align-items: center; gap: 8px; }
.sample-count-control { display: flex; flex: 0 0 auto; align-items: center; gap: 8px; white-space: nowrap; }
.sample-count-control { color: var(--ink-700); font-size: 12px; }
.sample-count-control :deep(.el-input-number) { width: 112px !important; flex: 0 0 112px; }
.region-editor :deep(.el-input) { max-width: 180px; }
.summary-row { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.summary-row :deep(.el-card__body) { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px; }
.summary-row span { color: var(--ink-700); font-size: 13px; }
.summary-row strong { color: var(--ink-950); font-size: 21px; }
.summary-row .available-count { color: #16835f; }
.candidate-card { min-height: 300px; }
.management-tabs { margin-bottom: 0; }
.filter-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 14px; }
.range-filter-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin: 14px 0; padding: 12px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface-soft); }
.range-filter-input { width: 190px; }
.range-filter-select { width: 175px; }
.filter-keyword { width: 210px; }
.filter-select { width: 160px; }
.filter-number { width: 135px; }
.filter-count { margin-left: auto; color: var(--ink-700); font-size: 12px; white-space: nowrap; }
.candidate-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 12px; }
.selection-hint, .muted, .probe-note, .dialog-tip { color: var(--ink-700); font-size: 12px; }
.probe-note { margin: 12px 0 0; line-height: 1.6; }
.latency { font-variant-numeric: tabular-nums; }
.dialog-tip { margin-top: 10px; line-height: 1.6; }

@media (max-width: 900px) {
  .page-heading, .candidate-toolbar, .section-heading { align-items: stretch; flex-direction: column; }
  .heading-actions, .toolbar-actions { flex-wrap: wrap; }
  .range-settings-row { align-items: stretch; }
  .range-sni-control { flex: 1 1 100%; }
  .range-actions-row .el-button { flex: 1 1 180px; }
  .filter-toolbar > * { flex: 1 1 145px; }
  .range-filter-toolbar > * { flex: 1 1 145px; }
  .filter-count { flex: 1 1 100%; margin-left: 0; }
}

@media (max-width: 600px) {
  .summary-row { grid-template-columns: 1fr; gap: 8px; }
  .heading-actions .el-button { flex: 1; }
  .selection-hint { width: 100%; }
}
</style>
