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
        <strong>{{ candidates.length }}</strong>
      </el-card>
      <el-card shadow="never">
        <span>可用</span>
        <strong class="available-count">{{ availableCandidates.length }}</strong>
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
          placeholder="搜索 IP 地址"
          :prefix-icon="Search"
          class="filter-keyword"
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
        <el-button :icon="Refresh" @click="resetFilters">重置筛选</el-button>
        <span class="filter-count">显示 {{ matchingCandidates.length }} / {{ candidates.length }}</span>
      </div>

      <div class="candidate-toolbar">
        <div class="toolbar-actions">
          <span class="selection-hint">已选 {{ selectedCandidates.length }} 个，单批最多测试 100 个</span>
          <el-button
            :icon="Connection"
            :disabled="selectedCandidates.length === 0 || selectedCandidates.length > 100 || probeBusy || rangeProbeBusy"
            :loading="probeBusy && !probeAllBusy"
            @click="probeSelected"
          >
            测试所选
          </el-button>
          <el-button
            type="primary"
            :icon="Refresh"
            :disabled="candidates.length === 0 || probeBusy || rangeProbeBusy"
            :loading="probeAllBusy"
            @click="probeAll"
          >
            {{ probeAllBusy ? `测试中 ${probeProgress}/${candidates.length}` : '测试全部' }}
          </el-button>
          <el-button
            :icon="CopyDocument"
            :disabled="availableCandidates.length === 0"
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
            :selectable="() => !probeBusy && !rangeProbeBusy"
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
                :disabled="probeBusy || rangeProbeBusy"
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
        v-if="matchingCandidates.length > pageSize"
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="matchingCandidates.length"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
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
      </div>
      <div class="range-actions-row">
        <el-button :icon="Refresh" :loading="rangeInitBusy" @click="initializeRanges">
          初始化官方网段
        </el-button>
        <el-button
          type="primary"
          :icon="Connection"
          :loading="rangeProbeBusy"
          :disabled="enabledRangeCount === 0 || rangeProbeBusy || probeBusy"
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
        <span class="filter-count">显示 {{ filteredRanges.length }} / {{ ranges.length }}</span>
      </div>
      <el-table :data="pagedRanges" v-loading="rangesLoading" stripe border max-height="min(65vh, 560px)">
        <el-table-column prop="cidr" label="CIDR 网段" min-width="190" />
        <el-table-column prop="ipVersion" label="版本" width="80" align="center">
          <template #default="{ row }">IPv{{ row.ipVersion }}</template>
        </el-table-column>
        <el-table-column label="优选启用" width="110" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :disabled="savingEnabledId === row.id || rangeProbeBusy || rangeInitBusy"
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
        v-if="filteredRanges.length > rangePageSize"
        v-model:current-page="rangePage"
        v-model:page-size="rangePageSize"
        :total="filteredRanges.length"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
      />
      <el-empty
        v-if="!rangesLoading && filteredRanges.length === 0"
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
  cfIpDelete,
  cfIpInitializeRanges,
  cfIpList,
  cfIpProbe,
  cfIpProbeRanges,
  cfIpRanges,
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

const candidates = ref<CfIpCandidate[]>([])
const ranges = ref<CfIpRange[]>([])
const activeTab = ref('candidates')
const candidateTable = ref()
const selectedCandidates = ref<CfIpCandidate[]>([])
const loading = ref(false)
const rangesLoading = ref(false)
const importBusy = ref(false)
const probeBusy = ref(false)
const probeAllBusy = ref(false)
const rangeInitBusy = ref(false)
const rangeProbeBusy = ref(false)
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
const keyword = ref('')
const ipVersionFilter = ref('')
const regionFilter = ref('')
const coloFilter = ref('')
const statusFilter = ref('ALL')
const maxTlsMs = ref<number | undefined>()
const minSpeedMbps = ref<number | undefined>()
const page = ref(1)
const pageSize = ref(10)

const matchingCandidates = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return candidates.value.filter(candidate => {
    if (query && !candidate.ipAddress.toLowerCase().includes(query)) return false
    if (ipVersionFilter.value && (candidate.ipAddress.includes(':') ? '6' : '4') !== ipVersionFilter.value) return false
    if (regionFilter.value && candidate.region !== regionFilter.value) return false
    if (coloFilter.value && candidate.colo !== coloFilter.value) return false
    if (statusFilter.value !== 'ALL' && candidate.status !== statusFilter.value) return false
    if (maxTlsMs.value !== undefined && (candidate.tlsMs === null || candidate.tlsMs > maxTlsMs.value)) return false
    if (minSpeedMbps.value !== undefined
        && (candidate.speedMbps === null || candidate.speedMbps < minSpeedMbps.value)) return false
    return true
  })
})
const filteredRanges = computed(() => {
  const cidrQuery = rangeKeyword.value.trim().toLowerCase()
  const regionQuery = rangeRegionFilter.value.trim().toLowerCase()
  return ranges.value.filter(range => {
    if (cidrQuery && !range.cidr.toLowerCase().includes(cidrQuery)) return false
    if (rangeIpVersionFilter.value && String(range.ipVersion) !== rangeIpVersionFilter.value) return false
    if (regionQuery && !(rangeRegions.value[range.id] || '').toLowerCase().includes(regionQuery)) return false
    if (rangeColoFilter.value && range.colo !== rangeColoFilter.value) return false
    return true
  })
})
const pagedRanges = computed(() => {
  const start = (rangePage.value - 1) * rangePageSize.value
  return filteredRanges.value.slice(start, start + rangePageSize.value)
})
const availableRegions = computed(() =>
  Array.from(new Set(candidates.value.map(candidate => candidate.region).filter(Boolean))).sort((a, b) => a.localeCompare(b))
)
const availableColos = computed(() =>
  Array.from(new Set(candidates.value.map(candidate => candidate.colo).filter((colo): colo is string => Boolean(colo))))
    .sort((a, b) => a.localeCompare(b))
)
const availableRangeColos = computed(() =>
  Array.from(new Set(ranges.value.map(range => range.colo).filter((colo): colo is string => Boolean(colo))))
    .sort((a, b) => a.localeCompare(b))
)
const enabledRangeCount = computed(() => ranges.value.filter(range => range.enabled).length)
const hasActiveFilters = computed(() =>
  Boolean(keyword.value.trim() || ipVersionFilter.value || regionFilter.value || coloFilter.value
    || statusFilter.value !== 'ALL'
    || maxTlsMs.value !== undefined || minSpeedMbps.value !== undefined)
)
const pagedCandidates = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return matchingCandidates.value.slice(start, start + pageSize.value)
})
const availableCandidates = computed(() =>
  candidates.value.filter(candidate => candidate.status === 'AVAILABLE' && candidate.latencyMs !== null)
)
const v2raynCandidates = computed(() => {
  const serverName = tlsServerName.value.trim().toLowerCase()
  return serverName
    ? availableCandidates.value.filter(candidate => candidate.tlsServerName?.toLowerCase() === serverName)
    : []
})
const fastestLatency = computed(() => {
  const timings = availableCandidates.value
    .map(candidate => candidate.tlsMs)
    .filter((timing): timing is number => timing !== null)
  return timings.length ? Math.min(...timings) : null
})

watch([keyword, ipVersionFilter, regionFilter, coloFilter, statusFilter, maxTlsMs, minSpeedMbps], async () => {
  page.value = 1
  selectedCandidates.value = []
  await nextTick()
  candidateTable.value?.clearSelection()
})

watch([matchingCandidates, pageSize], () => {
  const lastPage = Math.max(1, Math.ceil(matchingCandidates.value.length / pageSize.value))
  if (page.value > lastPage) page.value = lastPage
})

watch([rangeKeyword, rangeIpVersionFilter, rangeRegionFilter, rangeColoFilter], () => {
  rangePage.value = 1
})
watch([filteredRanges, rangePageSize], () => {
  const lastPage = Math.max(1, Math.ceil(filteredRanges.value.length / rangePageSize.value))
  if (rangePage.value > lastPage) rangePage.value = lastPage
})

const resetFilters = () => {
  keyword.value = ''
  ipVersionFilter.value = ''
  regionFilter.value = ''
  coloFilter.value = ''
  statusFilter.value = 'ALL'
  maxTlsMs.value = undefined
  minSpeedMbps.value = undefined
}

const resetRangeFilters = () => {
  rangeKeyword.value = ''
  rangeIpVersionFilter.value = ''
  rangeRegionFilter.value = ''
  rangeColoFilter.value = ''
  rangePage.value = 1
}

const loadCandidates = async () => {
  loading.value = true
  try {
    const response = await cfIpList() as { data: CfIpCandidate[] }
    candidates.value = response.data || []
  } finally {
    loading.value = false
  }
}

const loadRanges = async () => {
  rangesLoading.value = true
  try {
    const response = await cfIpRanges() as { data: CfIpRange[] }
    ranges.value = response.data || []
    rangeRegions.value = Object.fromEntries(ranges.value.map(range => [range.id, range.region]))
  } finally {
    rangesLoading.value = false
  }
}

watch(activeTab, tab => {
  if (tab === 'ranges') {
    loadRanges().catch(() => ElMessage.error('加载 Cloudflare 官方 IP 网段失败'))
  }
})

const initializeRanges = async () => {
  rangeInitBusy.value = true
  try {
    const response = await cfIpInitializeRanges() as { data: number }
    await loadRanges()
    ElMessage.success(`已同步 ${response.data || 0} 个 Cloudflare 官方网段`)
  } catch {
    ElMessage.error('初始化官方网段失败，请检查 IP 服务网络和日志')
  } finally {
    rangeInitBusy.value = false
  }
}

const saveRangeRegion = async (range: CfIpRange) => {
  savingRegionId.value = range.id
  try {
    await cfIpUpdateRangeRegion(range.id, rangeRegions.value[range.id] || '')
    ElMessage.success(`已更新 ${range.cidr} 的地区标签`)
    await loadRanges()
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
    await loadRanges()
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
  results.forEach(candidate => {
    const existing = candidatesById.get(candidate.id)
    candidatesById.set(candidate.id, existing
      ? { ...existing, ...candidate, tlsServerName: candidate.tlsServerName || existing.tlsServerName }
      : candidate)
  })
  candidates.value = Array.from(candidatesById.values())
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
    await loadCandidates()
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
    await loadRanges()
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
    ElMessage.success(`已完成 ${ids.length} 个 IP 的 TCP、TLS 和测速测试`)
    selectedCandidates.value = []
  } catch {
    ElMessage.error('IP 测试失败')
  } finally {
    probeBusy.value = false
  }
}

const probeAll = async () => {
  const ids = candidates.value.map(candidate => candidate.id)
  if (!ids.length) return
  probeBusy.value = true
  probeAllBusy.value = true
  probeProgress.value = 0
  try {
    for (let start = 0; start < ids.length; start += 100) {
      await probeBatch(ids.slice(start, start + 100))
    }
    ElMessage.success(`已完成 ${ids.length} 个 IP 的 TCP、TLS 和测速测试`)
    selectedCandidates.value = []
  } catch {
    ElMessage.error(`测试在 ${probeProgress.value} 个 IP 后中断`)
  } finally {
    probeBusy.value = false
    probeAllBusy.value = false
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
    await loadCandidates()
  } catch {
    ElMessage.error('删除候选 IP 失败')
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
    await navigator.clipboard.writeText(availableCandidates.value.map(candidate => candidate.ipAddress).join('\n'))
    ElMessage.success(`已复制 ${availableCandidates.value.length} 个可用 IP`)
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
  Promise.all([loadCandidates(), loadRanges()])
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
