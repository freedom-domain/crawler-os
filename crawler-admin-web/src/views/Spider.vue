<template>
  <el-card>
    <el-form class="spider-toolbar" :inline="true" @submit.prevent>
      <el-form-item>
        <el-input
          class="spider-name-filter"
          v-model="keyword"
          placeholder="搜索爬虫名称"
          clearable
          @input="scheduleFilterSearch"
          @clear="searchImmediately"
          @keyup.enter="searchImmediately"
        />
      </el-form-item>
      <el-form-item>
        <el-input
          class="spider-url-filter"
          v-model="urlFilter"
          placeholder="搜索起始 URL"
          clearable
          @input="scheduleFilterSearch"
          @clear="searchImmediately"
          @keyup.enter="searchImmediately"
        />
      </el-form-item>
      <el-form-item>
        <el-select v-model="groupFilter" placeholder="全部分组" clearable style="width: 160px" @change="handleGroupChange">
          <el-option v-for="g in groupOptions" :key="g" :label="g" :value="g" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-select v-model="publicFilter" placeholder="是否公开" clearable style="width: 120px" @change="searchImmediately">
          <el-option label="公开" :value="1" />
          <el-option label="私有" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="searchImmediately" :icon="Search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
      <el-form-item class="toolbar-actions">
        <div class="spider-actions">
          <el-button type="primary" :icon="Plus" @click="showCreate">新建爬虫</el-button>
          <el-dropdown trigger="click" @command="handleConfigCommand">
            <el-button>
              配置管理
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="export" :icon="Download">导出配置</el-dropdown-item>
                <el-dropdown-item command="import" :icon="Upload">导入配置</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <input
            ref="importInputRef"
            type="file"
            accept=".json"
            style="display: none"
            @change="onImportFileChange"
          />
        </div>
      </el-form-item>
    </el-form>

    <div class="table-scroll-wrapper">
    <el-table ref="tableRef" :data="list" v-loading="loading" stripe :row-class-name="tableRowClassName" resizable border>
      <el-table-column prop="id" label="ID" min-width="60" fixed="left" resizable />
      <el-table-column prop="name" label="名称" min-width="160" fixed="left" resizable>
        <template #default="{ row }">
          <el-link type="primary" @click="router.push({ name: 'Search', query: { spiderId: String(row.id) } })">
            {{ row.name }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column label="起始URL" min-width="320" show-overflow-tooltip resizable>
        <template #default="{ row }">
          <template v-for="(url, index) in parseStartUrls(row.startUrls)" :key="`${url}-${index}`">
            <a v-if="isHttpUrl(url)" :href="url" target="_blank" rel="noopener noreferrer">{{ url }}</a>
            <span v-else>{{ url }}</span>
            <span v-if="index < parseStartUrls(row.startUrls).length - 1">, </span>
          </template>
        </template>
      </el-table-column>
      <el-table-column prop="group" label="分组" min-width="120" resizable>
        <template #default="{ row }">
          <el-tag v-if="row.group" size="small">{{ row.group }}</el-tag>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="isPublic" label="公开" width="90" align="center" resizable>
        <template #default="{ row }">
          <el-switch
            v-model="row.isPublic"
            :active-value="1"
            :inactive-value="0"
            size="small"
            :loading="row._savingPublic"
            :disabled="row._savingPublic"
            @change="handleTogglePublic(row)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="readCache" label="读取缓存" min-width="130" align="center" resizable>
        <template #default="{ row }">
          <el-switch
            v-model="row.readCache"
            :active-value="1"
            :inactive-value="0"
            active-text="缓存"
            inactive-text="联网"
            size="small"
            :loading="row._savingCache"
            :disabled="row._savingCache"
            @change="handleToggleReadCache(row)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="readCacheMissOnline" label="未命中联网" min-width="140" align="center" resizable>
        <template #default="{ row }">
          <el-switch
            v-model="row.readCacheMissOnline"
            :active-value="1"
            :inactive-value="0"
            active-text="联网"
            inactive-text="跳过"
            size="small"
            :loading="row._savingCacheMiss"
            :disabled="row._savingCacheMiss || row.readCache !== 1"
            @change="handleToggleReadCacheMissOnline(row)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="overwriteHtml" label="覆盖HTML" min-width="130" align="center" resizable>
        <template #default="{ row }">
          <el-switch
            v-model="row.overwriteHtml"
            :active-value="1"
            :inactive-value="0"
            active-text="覆盖"
            inactive-text="跳过"
            size="small"
            :loading="row._savingHtml"
            :disabled="row._savingHtml"
            @change="handleToggleOverwriteHtml(row)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="定时任务" min-width="150" align="center" resizable>
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
            :active-value="1"
            :inactive-value="0"
            active-text="运行中"
            inactive-text="停止"
            size="small"
            :loading="row._savingStatus"
            :disabled="row._savingStatus || (row.status !== 1 && !row.schedule?.trim())"
            @change="handleToggleStatus(row)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="schedule" label="调度" min-width="180" resizable>
        <template #default="{ row }">
          <span
            class="schedule-editable"
            title="点击编辑调度表达式"
            @click="handleEditSchedule(row)"
          >{{ row.schedule?.trim() ? row.schedule : '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="180" resizable>
        <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" min-width="180" resizable>
        <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right" align="center" resizable>
        <template #default="{ row }">
          <div class="row-actions">
            <el-button
              type="primary"
              size="small"
              :icon="VideoPlay"
              :loading="row._running"
              :disabled="row._running"
              @click="handleRun(row)"
            />
            <TableRowActions :items="getRowActions(row)" @command="command => handleCommand(command, row)" />
          </div>
        </template>
      </el-table-column>
    </el-table>
    </div>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 15, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      @change="loadData"
    />

    <el-dialog v-model="createVisible" :title="editingId ? '编辑爬虫' : '新建爬虫'" width="700px" top="5vh" class="spider-dialog">
      <el-form :model="form" label-width="110px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.type">
            <el-option label="HTTP" value="http" />
            <el-option label="JS渲染" value="playwright" />
          </el-select>
        </el-form-item>
        <el-form-item label="起始URL" required>
          <el-input v-model="startUrlsStr" placeholder="多个URL用逗号分隔" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" />
        </el-form-item>
        <el-form-item label="分组">
          <el-select v-model="form.group" placeholder="选择分组" clearable style="width: 100%">
            <el-option v-for="g in groupOptions" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="公开搜索">
          <el-switch v-model="form.isPublic" :active-value="1" :inactive-value="0" active-text="公开" inactive-text="私有" />
          <div class="form-tip">公开后，未登录用户也可以在公共搜索页查询该爬虫的内容</div>
        </el-form-item>
        <el-form-item label="内容选择器">
          <el-input v-model="form.contentSelector" placeholder="CSS选择器，如 .article-content 或 #content" />
          <div class="form-tip">优先取该选择器命中的文本作为 ES 内容；无内容时回退到标题</div>
        </el-form-item>
        <el-form-item label="图片选择器">
          <el-input v-model="form.imageSelector" placeholder="CSS选择器，如 .article img 或 #content" />
        </el-form-item>
        <el-form-item label="图片 XPath">
          <el-input
            v-model="form.imageXpath"
            type="textarea"
            :rows="2"
            placeholder="可填写多个 XPath，并用分号分隔，如 //article//img/@src; //div[@class='gallery']//img/@data-src"
          />
          <div class="form-tip">多个 XPath 用分号分隔，命中结果会合并去重；填写后优先使用 XPath 定位图片，留空时使用上面的 CSS 选择器</div>
        </el-form-item>
        <el-form-item label="VIP选择器">
          <el-input v-model="form.vipSelector" placeholder="CSS选择器，如 .vip 或 .member-only" />
          <div class="form-tip">命中选择器且元素文本包含下方内容时，跳过图片下载</div>
        </el-form-item>
        <el-form-item label="VIP包含内容">
          <el-input v-model="form.vipSelectorContent" placeholder="选择器命中文本需包含的内容" />
        </el-form-item>
        <el-form-item label="覆盖HTML">
          <el-switch v-model="form.overwriteHtml" :active-value="1" :inactive-value="0" active-text="覆盖" inactive-text="跳过" />
          <div class="form-tip">开启后重新爬取会覆盖 ES 中的内容；关闭则内容未变化时跳过</div>
        </el-form-item>
        <el-form-item label="覆盖图片">
          <el-switch v-model="form.overwriteImage" :active-value="1" :inactive-value="0" active-text="覆盖" inactive-text="跳过" />
          <div class="form-tip">开启后重新爬取会重新下载并覆盖已存在的图片；关闭则已存在图片不重复下载</div>
        </el-form-item>
        <el-form-item label="读取缓存">
          <el-switch v-model="form.readCache" :active-value="1" :inactive-value="0" active-text="缓存" inactive-text="联网" />
          <div class="form-tip">开启后页面内容优先从 MinIO 缓存获取；起始URL 也会从缓存中读取：与起始URL 同域名的已缓存页面自动加入本次爬取范围，并合并回起始URL 配置</div>
        </el-form-item>
        <el-form-item v-if="form.readCache === 1" label="未命中联网">
          <el-switch v-model="form.readCacheMissOnline" :active-value="1" :inactive-value="0" active-text="联网" inactive-text="跳过" />
          <div class="form-tip">读取缓存时未命中缓存的页面如何处理：开启则联网抓取并按原逻辑保存，关闭则直接跳过（全程不联网，不扩展新页面）</div>
        </el-form-item>
        <el-form-item label="最大深度">
          <el-input-number v-model="form.maxDepth" :min="0" :max="5" />
        </el-form-item>
        <el-form-item label="自定义Header">
          <div class="header-editor">
            <div v-if="headerRows.length === 0" class="header-editor-empty">未配置 Header，点击下方按钮添加</div>
            <div v-for="(row, index) in headerRows" :key="index" class="header-editor-row">
              <el-input v-model="row.name" placeholder="名称，如 X-Api-Key" class="header-editor-name" />
              <el-input v-model="row.value" placeholder="值" class="header-editor-value" />
              <el-button type="danger" :icon="Delete" circle @click="removeHeaderRow(index)" />
            </div>
            <el-button type="primary" plain :icon="Plus" @click="addHeaderRow">添加 Header</el-button>
          </div>
          <div class="form-tip">配置后，该爬虫发起的每次 HTTP 请求都会带上这些 Header；同名 Header 会覆盖内置默认值</div>
        </el-form-item>
        <el-form-item label="排除URL">
          <el-select
            v-model="form.excludedUrls"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入URL或路径前缀后回车，如 /tags/ 或 https://example.com/page"
            style="width: 100%"
          />
          <div class="form-tip">命中规则的URL不会被抓取；支持精确URL或路径前缀（如 /tags/ 排除该目录下所有页面）</div>
        </el-form-item>
        <el-form-item label="遵循 robots.txt">
          <el-switch v-model="form.followRobots" :active-value="1" :inactive-value="0" active-text="是" inactive-text="否" />
          <div class="form-tip">开启后，遵循目标站点 robots.txt 中的 Disallow 规则</div>
        </el-form-item>
        <el-form-item label="跳过 TLS 校验">
          <el-switch v-model="form.skipTlsVerify" :active-value="1" :inactive-value="0" active-text="跳过" inactive-text="校验" />
          <div class="form-tip">仅在 HTTPS 证书异常且确认目标可信时开启；开启后将跳过证书链和域名校验</div>
        </el-form-item>
        <el-form-item label="调度表达式">
          <div class="schedule-builder">
            <el-select v-model="scheduleType" placeholder="选择频率" style="width: 120px" @change="generateSchedule">
              <el-option label="每分钟" value="minute" />
              <el-option label="每小时" value="hour" />
              <el-option label="每天" value="day" />
              <el-option label="每周" value="week" />
              <el-option label="每月" value="month" />
              <el-option label="自定义" value="custom" />
            </el-select>
            <el-input-number v-if="scheduleType === 'minute' || scheduleType === 'hour'" v-model="scheduleInterval" :min="1" :max="scheduleType === 'minute' ? 59 : 23" style="width: 100px" @change="generateSchedule" />
            <span v-if="scheduleType === 'minute'">分钟</span>
            <span v-if="scheduleType === 'hour'">小时</span>
            <el-time-picker v-if="scheduleType === 'day' || scheduleType === 'week' || scheduleType === 'month'" v-model="scheduleTime" format="HH:mm" value-format="HH:mm" style="width: 120px" @change="generateSchedule" />
            <el-select v-if="scheduleType === 'week'" v-model="scheduleWeekDay" placeholder="星期" style="width: 80px" @change="generateSchedule">
              <el-option label="一" :value="1" />
              <el-option label="二" :value="2" />
              <el-option label="三" :value="3" />
              <el-option label="四" :value="4" />
              <el-option label="五" :value="5" />
              <el-option label="六" :value="6" />
              <el-option label="日" :value="0" />
            </el-select>
            <el-input-number v-if="scheduleType === 'month'" v-model="scheduleDay" :min="1" :max="31" style="width: 80px" @change="generateSchedule" />
            <span v-if="scheduleType === 'month'">日</span>
          </div>
          <el-input v-model="scheduleTarget" placeholder="生成的 Cron 表达式" class="schedule-result" />
        </el-form-item>
        <el-form-item label="超时(ms)">
          <el-input-number v-model="form.timeout" :min="1000" :max="60000" :step="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="scheduleDialogVisible" title="编辑调度表达式" width="560px" top="20vh">
      <el-form label-width="110px">
        <el-form-item label="调度表达式">
          <div class="schedule-builder">
            <el-select v-model="scheduleType" placeholder="选择频率" style="width: 120px" @change="generateSchedule">
              <el-option label="每分钟" value="minute" />
              <el-option label="每小时" value="hour" />
              <el-option label="每天" value="day" />
              <el-option label="每周" value="week" />
              <el-option label="每月" value="month" />
              <el-option label="自定义" value="custom" />
            </el-select>
            <el-input-number v-if="scheduleType === 'minute' || scheduleType === 'hour'" v-model="scheduleInterval" :min="1" :max="scheduleType === 'minute' ? 59 : 23" style="width: 100px" @change="generateSchedule" />
            <span v-if="scheduleType === 'minute'">分钟</span>
            <span v-if="scheduleType === 'hour'">小时</span>
            <el-time-picker v-if="scheduleType === 'day' || scheduleType === 'week' || scheduleType === 'month'" v-model="scheduleTime" format="HH:mm" value-format="HH:mm" style="width: 120px" @change="generateSchedule" />
            <el-select v-if="scheduleType === 'week'" v-model="scheduleWeekDay" placeholder="星期" style="width: 80px" @change="generateSchedule">
              <el-option label="一" :value="1" />
              <el-option label="二" :value="2" />
              <el-option label="三" :value="3" />
              <el-option label="四" :value="4" />
              <el-option label="五" :value="5" />
              <el-option label="六" :value="6" />
              <el-option label="日" :value="0" />
            </el-select>
            <el-input-number v-if="scheduleType === 'month'" v-model="scheduleDay" :min="1" :max="31" style="width: 80px" @change="generateSchedule" />
            <span v-if="scheduleType === 'month'">日</span>
          </div>
          <el-input v-model="scheduleTarget" placeholder="生成的 Cron 表达式" class="schedule-result" />
          <div class="form-tip">留空表示不启用定时任务</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scheduleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleScheduleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { confirm } from '@/utils/confirm'
import { spiderPage, spiderCreate, spiderDetail, spiderUpdate, spiderStart, spiderStop, spiderDelete, spiderClearContent, spiderRun, spiderExport, spiderImport, dictTree } from '@/api'
import { Plus, Search, VideoPlay, EditPen, Delete, FolderOpened, ArrowDown, Download, Upload, List } from '@element-plus/icons-vue'
import { formatDateTime } from '@/utils/dateTime'
import TableRowActions, { type TableRowAction } from '@/components/TableRowActions.vue'

const route = useRoute()
const router = useRouter()
const tableRef = ref<any>(null)

const list = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(15)
const total = ref(0)
const keyword = ref('')
const urlFilter = ref('')
const groupFilter = ref('')
const publicFilter = ref<number | null>(null)
const highlightSpiderId = ref<number | null>(null)
const groupOptions = ref<string[]>([])
const createVisible = ref(false)
const editingId = ref<number | null>(null)
const startUrlsStr = ref('')
let filterSearchTimer: ReturnType<typeof setTimeout> | null = null
const parseStartUrls = (value: unknown): string[] => {
  if (Array.isArray(value)) {
    return value.filter((url): url is string => typeof url === 'string' && Boolean(url.trim()))
  }
  if (typeof value !== 'string' || !value.trim()) return []
  try {
    const parsed: unknown = JSON.parse(value)
    if (Array.isArray(parsed)) {
      return parsed.filter((url): url is string => typeof url === 'string' && Boolean(url.trim()))
    }
  } catch {
    // Older records may store URLs as a comma-separated string.
  }
  return value.split(',').map(url => url.trim()).filter(Boolean)
}

const isHttpUrl = (value: string): boolean => {
  try {
    const protocol = new URL(value).protocol
    return protocol === 'http:' || protocol === 'https:'
  } catch {
    return false
  }
}

// 自定义 Header 编辑器：UI 用 [{name, value}] 行，提交时序列化成 "名称: 值\n" 文本存 form.headers
const headerRows = ref<{ name: string; value: string }[]>([])
const parseHeadersToRows = (text: string) => {
  if (!text || !text.trim()) return []
  return text.split('\n').map(line => line.split('#', 2)[0].trim()).filter(Boolean).map(line => {
    const idx = line.indexOf(':')
    if (idx <= 0) return null
    return { name: line.substring(0, idx).trim(), value: line.substring(idx + 1).trim() }
  }).filter((row): row is { name: string; value: string } => row != null && Boolean(row.name))
}
const serializeHeaderRows = () =>
  headerRows.value.filter(r => Boolean(r.name.trim()) && Boolean(r.value.trim()))
    .map(r => `${r.name.trim()}: ${r.value.trim()}`)
    .join('\n')
const addHeaderRow = () => headerRows.value.push({ name: '', value: '' })
const removeHeaderRow = (index: number) => headerRows.value.splice(index, 1)
const syncHeadersToForm = () => { form.value.headers = serializeHeaderRows() }
watch(headerRows, syncHeadersToForm, { deep: true })

// 排除URL：后端存 JSON 数组字符串，前端用数组（el-select 动态标签）
const parseExcludedUrls = (text: string): string[] => {
  if (!text || !text.trim()) return []
  try {
    const parsed: unknown = JSON.parse(text)
    if (Array.isArray(parsed)) {
      return parsed.filter((u): u is string => typeof u === 'string' && Boolean(u.trim()))
    }
  } catch {
    // 兼容旧数据：逗号分隔
  }
  return String(text).split(',').map(s => s.trim()).filter(Boolean)
}
const form = ref({
  name: '', description: '', type: 'http', group: '', isPublic: 0,
  contentSelector: '', imageSelector: '', imageXpath: '', vipSelector: '', vipSelectorContent: '', overwriteHtml: 0, overwriteImage: 0, readCache: 0, readCacheMissOnline: 0, schedule: '', maxDepth: 2, timeout: 15000, followRobots: 0, skipTlsVerify: 0, headers: '', excludedUrls: [] as string[]
})

// 调度表达式生成器
const scheduleType = ref('')
const scheduleInterval = ref(10)
const scheduleTime = ref('00:00')
const scheduleWeekDay = ref(1)
const scheduleDay = ref(1)
const scheduleTarget = ref('')
const scheduleDialogVisible = ref(false)

const generateSchedule = () => {
  const time = scheduleTime.value || '00:00'
  const [hour, minute] = time.split(':')
  switch (scheduleType.value) {
    case 'minute':
      scheduleTarget.value = `0 */${scheduleInterval.value} * * * ?`
      break
    case 'hour':
      scheduleTarget.value = `0 ${minute} */${scheduleInterval.value} * * ?`
      break
    case 'day':
      scheduleTarget.value = `0 ${minute} ${hour} * * ?`
      break
    case 'week':
      scheduleTarget.value = `0 ${minute} ${hour} ? * ${scheduleWeekDay.value}`
      break
    case 'month':
      scheduleTarget.value = `0 ${minute} ${hour} ${scheduleDay.value} * ?`
      break
  }
}

const loadGroupOptions = async () => {
  try {
    const res: any = await dictTree()
    const tree = res.data || []
    const cat = tree.find((c: any) => c.value === 'spider-group' || c.label === 'spider-group')
    groupOptions.value = cat
      ? (cat.children || []).map((ch: any) => ch.value || ch.label)
      : []
  } catch {
    groupOptions.value = []
  }
}

const tableRowClassName = ({ row }: { row: any }) =>
  Number(row.id) === highlightSpiderId.value ? 'spider-highlight-row' : ''

const focusSpiderRow = () => {
  if (!highlightSpiderId.value) return
  nextTick(() => {
    const rows = tableRef.value?.$el?.querySelectorAll?.('.el-table__body-wrapper tbody tr') || []
    const targetIndex = list.value.findIndex((row) => Number(row.id) === highlightSpiderId.value)
    const targetRow = rows[targetIndex]
    if (targetRow) {
      targetRow.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
  })
}

const routeQueryValue = (value: unknown) => {
  if (Array.isArray(value)) return typeof value[0] === 'string' ? value[0] : ''
  return typeof value === 'string' ? value : ''
}

const trimmedFilterValue = (value: unknown) =>
  typeof value === 'string' ? value.trim() : ''

let routeInitialized = false

const syncQueryToRoute = () => {
  const filters = {
    keyword: trimmedFilterValue(keyword.value),
    startUrl: trimmedFilterValue(urlFilter.value),
    group: trimmedFilterValue(groupFilter.value),
    isPublic: publicFilter.value != null ? String(publicFilter.value) : '',
    spiderId: highlightSpiderId.value ? String(highlightSpiderId.value) : ''
  }
  const routeFilters = {
    keyword: routeQueryValue(route.query.keyword ?? route.query.spiderName),
    startUrl: routeQueryValue(route.query.startUrl),
    group: routeQueryValue(route.query.group),
    isPublic: routeQueryValue(route.query.isPublic),
    spiderId: routeQueryValue(route.query.spiderId)
  }
  if (Object.keys(filters).every((key) => filters[key as keyof typeof filters] === routeFilters[key as keyof typeof routeFilters])
      && route.query.spiderName === undefined) {
    return
  }

  const query = { ...route.query }
  delete query.keyword
  delete query.startUrl
  delete query.group
  delete query.isPublic
  delete query.spiderId
  if (filters.keyword) query.keyword = filters.keyword
  if (filters.startUrl) query.startUrl = filters.startUrl
  if (filters.group) query.group = filters.group
  if (filters.isPublic) query.isPublic = filters.isPublic
  if (filters.spiderId) query.spiderId = filters.spiderId

  void router.replace({ query })
}

const loadData = async () => {
  const requestedHighlightId = highlightSpiderId.value
  loading.value = true
  try {
    const res: any = await spiderPage({
      current: 1,
      size: requestedHighlightId ? 100 : size.value,
      keyword: keyword.value,
      startUrl: urlFilter.value || undefined,
      group: groupFilter.value || undefined,
      isPublic: publicFilter.value ?? undefined
    })
    if (requestedHighlightId !== highlightSpiderId.value) return
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
    if (requestedHighlightId) {
      const found = list.value.some((row) => Number(row.id) === requestedHighlightId)
      if (!found) {
        const detail: any = await spiderDetail(requestedHighlightId)
        if (requestedHighlightId !== highlightSpiderId.value) return
        if (detail.data && Number(detail.data.id) === requestedHighlightId) {
          list.value.unshift(detail.data)
        }
      }
    }
    focusSpiderRow()
  } finally {
    loading.value = false
  }
}

const cancelScheduledFilterSearch = () => {
  if (filterSearchTimer) {
    clearTimeout(filterSearchTimer)
    filterSearchTimer = null
  }
}

const scheduleFilterSearch = () => {
  cancelScheduledFilterSearch()
  filterSearchTimer = setTimeout(() => {
    filterSearchTimer = null
    page.value = 1
    loadData()
  }, 300)
}

const searchImmediately = () => {
  cancelScheduledFilterSearch()
  page.value = 1
  loadData()
}

const handleGroupChange = () => {
  cancelScheduledFilterSearch()
  syncQueryToRoute()
  loadData()
}

const resetFilters = () => {
  cancelScheduledFilterSearch()
  keyword.value = ''
  urlFilter.value = ''
  groupFilter.value = ''
  publicFilter.value = null
  highlightSpiderId.value = null
  page.value = 1
  loadData()
}

const showCreate = () => {
  editingId.value = null
  form.value = { name: '', description: '', type: 'http', group: '', isPublic: 0, contentSelector: '', imageSelector: '', imageXpath: '', vipSelector: '', vipSelectorContent: '', overwriteHtml: 0, overwriteImage: 0, readCache: 0, readCacheMissOnline: 0, schedule: '', maxDepth: 2, timeout: 15000, followRobots: 0, skipTlsVerify: 0, headers: '', excludedUrls: [] }
  headerRows.value = []
  startUrlsStr.value = ''
  scheduleTarget.value = ''
  scheduleType.value = ''
  createVisible.value = true
}

const handleExport = async () => {
  try {
    const blob: Blob = await spiderExport() as any
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'spiders.json'
    link.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('导出失败')
  }
}

const handleImport = async (file: File) => {
  try {
    const res: any = await spiderImport(file)
    const result = res.data || {}
    ElMessage.success(`导入完成：成功 ${result.imported} 个，跳过 ${result.skipped} 个，失败 ${result.failed} 个`)
    loadData()
  } catch {
    ElMessage.error('导入失败')
  }
  return false
}

const importInputRef = ref<HTMLInputElement | null>(null)

const handleConfigCommand = (cmd: string) => {
  if (cmd === 'export') handleExport()
  if (cmd === 'import') importInputRef.value?.click()
}

const onImportFileChange = (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) handleImport(file)
  input.value = ''
}

const showEdit = async (row: any) => {
  const res: any = await spiderDetail(row.id)
  const d = res.data
  editingId.value = d.id
  form.value = {
    name: d.name, description: d.description || '', type: d.type, group: d.group || '', isPublic: d.isPublic ?? 0,
    contentSelector: d.contentSelector || '', imageSelector: d.imageSelector || '', imageXpath: d.imageXpath || '', vipSelector: d.vipSelector || '', vipSelectorContent: d.vipSelectorContent || '', overwriteHtml: d.overwriteHtml ?? 0, overwriteImage: d.overwriteImage ?? 0, readCache: d.readCache ?? 0, readCacheMissOnline: d.readCacheMissOnline ?? 0,
    schedule: d.schedule || '', maxDepth: d.maxDepth ?? 2, timeout: d.timeout ?? 15000, followRobots: d.followRobots ?? 0, skipTlsVerify: d.skipTlsVerify ?? 0, headers: d.headers || '', excludedUrls: parseExcludedUrls(d.excludedUrls)
  }
  headerRows.value = parseHeadersToRows(d.headers || '')
  try {
    const urls = JSON.parse(d.startUrls || '[]')
    startUrlsStr.value = urls.join(', ')
  } catch {
    startUrlsStr.value = d.startUrls || ''
  }
  scheduleTarget.value = d.schedule || ''
  scheduleType.value = 'custom'
  createVisible.value = true
}

const handleSubmit = async () => {
  const startUrls = startUrlsStr.value.split(',').map(s => s.trim()).filter(Boolean)
  if (!form.value.name || startUrls.length === 0) {
    ElMessage.warning('请填写名称和起始URL')
    return
  }
  try {
    if (editingId.value) {
      await spiderUpdate(editingId.value, { ...form.value, startUrls, schedule: scheduleTarget.value || form.value.schedule })
      ElMessage.success('更新成功')
    } else {
      await spiderCreate({ ...form.value, startUrls, schedule: scheduleTarget.value || form.value.schedule })
      ElMessage.success('创建成功')
    }
  } catch (error) {
    if (error instanceof Error && error.message === '爬虫名称已存在') return
    ElMessage.error('操作失败')
    return
  }
  createVisible.value = false
  loadData()
}

const handleEditSchedule = (row: any) => {
  editingId.value = row.id
  scheduleTarget.value = row.schedule || ''
  scheduleType.value = 'custom'
  createVisible.value = false
  scheduleDialogVisible.value = true
}

const handleScheduleSubmit = async () => {
  const row = list.value.find((r) => Number(r.id) === Number(editingId.value))
  if (!row) {
    scheduleDialogVisible.value = false
    return
  }
  try {
    const detail: any = await spiderDetail(row.id)
    const payload = buildUpdatePayload(detail.data)
    payload.schedule = scheduleTarget.value.trim()
    await spiderUpdate(row.id, payload)
    row.schedule = payload.schedule
    ElMessage.success('调度表达式已更新')
  } catch {
    ElMessage.error('调度表达式更新失败')
    return
  }
  scheduleDialogVisible.value = false
}

const handleStart = async (row: any) => {
  try {
    await spiderStart(row.id)
    ElMessage.success('已启动')
  } catch {
    ElMessage.error('启动失败')
  }
  loadData()
}

// 列表内直接切换「公开 / 读取缓存」开关
const buildUpdatePayload = (detail: any) => {
  let startUrls: string[] = []
  try {
    const parsed = JSON.parse(detail.startUrls || '[]')
    startUrls = Array.isArray(parsed) ? parsed.filter((u: any) => typeof u === 'string' && u.trim()) : []
  } catch {
    startUrls = String(detail.startUrls || '').split(',').map(s => s.trim()).filter(Boolean)
  }
  return {
    name: detail.name,
    description: detail.description || '',
    type: detail.type,
    startUrls,
    contentSelector: detail.contentSelector || '',
    imageSelector: detail.imageSelector || '',
    imageXpath: detail.imageXpath || '',
    vipSelector: detail.vipSelector || '',
    vipSelectorContent: detail.vipSelectorContent || '',
    overwriteHtml: detail.overwriteHtml ?? 0,
    overwriteImage: detail.overwriteImage ?? 0,
    readCache: detail.readCache ?? 0,
    readCacheMissOnline: detail.readCacheMissOnline ?? 0,
    isPublic: detail.isPublic ?? 0,
    group: detail.group || '',
    schedule: detail.schedule || '',
    maxDepth: detail.maxDepth ?? 2,
    timeout: detail.timeout ?? 15000,
    followRobots: detail.followRobots ?? 0,
    skipTlsVerify: detail.skipTlsVerify ?? 0,
    headers: detail.headers || '',
    excludedUrls: parseExcludedUrls(detail.excludedUrls)
  }
}

const applyToggle = async (row: any, field: 'isPublic' | 'readCache' | 'readCacheMissOnline' | 'overwriteHtml', savingKey: string) => {
  const oldValue = row[field]
  row[savingKey] = true
  try {
    const detail: any = await spiderDetail(row.id)
    const payload = buildUpdatePayload(detail.data)
    payload[field] = oldValue
    await spiderUpdate(row.id, payload)
    const label = field === 'isPublic' ? '公开状态' : field === 'readCache' ? '读取缓存' : field === 'readCacheMissOnline' ? '未命中联网' : '覆盖HTML'
    ElMessage.success(`${label}已更新`)
  } catch {
    row[field] = oldValue
    const label = field === 'isPublic' ? '公开状态' : field === 'readCache' ? '读取缓存' : field === 'readCacheMissOnline' ? '未命中联网' : '覆盖HTML'
    ElMessage.error(`${label}切换失败`)
  } finally {
    row[savingKey] = false
  }
}

const handleTogglePublic = (row: any) => applyToggle(row, 'isPublic', '_savingPublic')
const handleToggleReadCache = (row: any) => applyToggle(row, 'readCache', '_savingCache')
const handleToggleReadCacheMissOnline = (row: any) => applyToggle(row, 'readCacheMissOnline', '_savingCacheMiss')
const handleToggleOverwriteHtml = (row: any) => applyToggle(row, 'overwriteHtml', '_savingHtml')

const handleToggleStatus = async (row: any) => {
  // el-switch @change 触发时 v-model 已变为目标值（row.status = 新值）
  row._savingStatus = true
  try {
    if (row.status === 1) {
      // 目标状态：运行中 → 启动定时任务
      await spiderStart(row.id)
      ElMessage.success('定时任务已启动')
    } else {
      // 目标状态：停止 → 停止定时任务
      await spiderStop(row.id)
      ElMessage.success('定时任务已停止')
    }
  } catch {
    // 失败时回滚：v-model 已改变，需取反恢复
    row.status = row.status === 1 ? 0 : 1
    ElMessage.error('定时任务切换失败')
  } finally {
    row._savingStatus = false
  }
}

const handleStop = async (row: any) => {
  try {
    await spiderStop(row.id)
    ElMessage.success('已停止')
  } catch {
    ElMessage.error('停止失败')
  }
  loadData()
}

const handleRun = async (row: any) => {
  try {
    if (!await confirm(`确定执行爬虫「${row.name}」的爬取任务吗？`, { title: '执行任务', confirmText: '确认执行' })) return
  } catch {
    return
  }
  row._running = true
  try {
    const res: any = await spiderRun(row.id)
    const status = res.data?.status
    const isPending = status === 'PENDING'
    ElNotification({
      title: isPending ? '任务已加入等待队列' : '任务已派发',
      message: isPending ? '爬虫将按并发策略排队执行' : '爬虫已开始执行',
      type: isPending ? 'info' : 'success',
      duration: 5000
    })
  } catch {
    // The request interceptor already displays the backend error message.
  } finally {
    row._running = false
  }
}

const handleDelete = async (row: any) => {
  try {
    if (!await confirm('确定删除该爬虫？', { title: '删除确认', danger: true })) return
  } catch {
    return
  }
  try {
    await spiderDelete(row.id)
    ElMessage.success('删除成功')
  } catch {
    ElMessage.error('删除失败')
  }
  loadData()
}

const handleClearContent = async (row: any) => {
  try {
    if (!await confirm(
      `确定清空爬虫「${row.name}」的所有已采集内容吗？此操作将删除该爬虫在 ES 中的内容、MinIO 文件及文件元数据，且无法恢复。`,
      { title: '清空内容', confirmText: '清空内容', danger: true }
    )) return
  } catch {
    return
  }

  row._clearingContent = true
  try {
    const res: any = await spiderClearContent(row.id)
    const result = res.data || {}
    ElMessage.success(
      `清理完成：ES 内容 ${result.deletedDocuments || 0} 条，MinIO 文件 ${result.deletedFiles || 0} 个，文件元数据 ${result.deletedMetadata || 0} 条`
    )
  } catch {
    ElMessage.error('清空内容失败，请检查服务日志后重试')
  } finally {
    row._clearingContent = false
  }
}

const goToTaskPage = (row?: any) => {
  const query: Record<string, string> = {}
  if (row?.id) query.spiderId = String(row.id)
  router.push({ name: 'Task', query })
}

const getRowActions = (row: any): TableRowAction[] => [
  { command: 'tasks', label: '查看任务', icon: List },
  { command: 'files', label: '查看文件', icon: FolderOpened },
  { command: 'edit', label: '编辑', icon: EditPen },
  { command: 'clear-content', label: '清空内容', icon: Delete, divided: true, disabled: row._clearingContent },
  { command: 'delete', label: '删除', icon: Delete, divided: true, danger: true }
]

const handleCommand = (cmd: string, row: any) => {
  switch (cmd) {
    case 'tasks': goToTaskPage(row); break
    case 'files':
      router.push({ name: 'File', query: { spiderId: String(row.id) } })
      break
    case 'edit': showEdit(row); break
    case 'clear-content': handleClearContent(row); break
    case 'delete': handleDelete(row); break
  }
}

watch(
  [keyword, urlFilter, groupFilter, publicFilter],
  () => syncQueryToRoute()
)

watch(
  () => [route.query.spiderId, route.query.keyword, route.query.spiderName, route.query.startUrl, route.query.group, route.query.isPublic],
  ([spiderIdValue, keywordValue, spiderNameValue, startUrlValue, groupValue, isPublicValue]) => {
    const id = Number(routeQueryValue(spiderIdValue))
    const routeKeyword = keywordValue ?? spiderNameValue
    const nextKeyword = routeQueryValue(routeKeyword)
    const nextStartUrl = routeQueryValue(startUrlValue)
    const nextGroup = routeQueryValue(groupValue)
    const nextIsPublic = routeQueryValue(isPublicValue)
    const nextPublicFilter = nextIsPublic === '0' || nextIsPublic === '1' ? Number(nextIsPublic) : null
    const nextHighlightId = Number.isFinite(id) && id > 0 ? id : null
    const routeMatchesFilters = route.query.spiderName === undefined
        && trimmedFilterValue(keyword.value) === nextKeyword
        && trimmedFilterValue(urlFilter.value) === nextStartUrl
        && trimmedFilterValue(groupFilter.value) === nextGroup
        && (publicFilter.value ?? null) === nextPublicFilter
        && highlightSpiderId.value === nextHighlightId
    if (routeInitialized && routeMatchesFilters) {
      return
    }
    routeInitialized = true

    highlightSpiderId.value = nextHighlightId
    keyword.value = nextKeyword
    urlFilter.value = nextStartUrl
    groupFilter.value = nextGroup
    publicFilter.value = nextPublicFilter
    loadData()
  },
  { immediate: true }
)

onMounted(() => {
  loadGroupOptions()
})

onUnmounted(cancelScheduledFilterSearch)
</script>

<style scoped>
:deep(.el-table__body tr.spider-highlight-row > td.el-table__cell) {
  background-color: #fff1cc !important;
}

.spider-toolbar :deep(.spider-name-filter) { width: 240px; }
.spider-toolbar :deep(.spider-url-filter) { width: 280px; }
.spider-toolbar :deep(.el-select) { width: 160px; }
.spider-toolbar :deep(.el-upload) { display: inline-flex; }
.spider-toolbar :deep(.toolbar-actions) { flex: 1 0 auto; justify-content: flex-end; }
.spider-toolbar :deep(.toolbar-actions > .el-form-item__content) { width: 100%; justify-content: flex-end; }
.spider-actions { display: flex; width: 100%; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 8px; }
.form-tip { font-size: 12px; color: #999; line-height: 1.5; margin-top: 4px; margin-left: 0; width: 100%; }
.text-muted { color: #c0c4cc; }

.row-actions { display: inline-flex; align-items: center; gap: 8px; }
.schedule-editable { cursor: pointer; color: var(--el-color-primary); border-bottom: 1px dashed var(--el-color-primary); padding: 2px 0; }
.schedule-editable:hover { opacity: .8; }
:deep(.el-table .el-switch__label) { white-space: nowrap; }

:deep(.spider-dialog .el-dialog__body) {
  height: 70vh;
  overflow-y: auto;
  padding-top: 10px;
}
:deep(.spider-dialog .el-form-item__label) {
  white-space: nowrap;
}

@media (max-width: 767px) {
  .spider-toolbar :deep(.el-form-item) { width: 100%; }
  .spider-toolbar :deep(.el-input),
  .spider-toolbar :deep(.el-select) { width: 100% !important; }
  .spider-toolbar :deep(.toolbar-actions .el-form-item__content) { width: 100%; }
  .spider-actions { width: 100%; }
}
.schedule-builder {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
.schedule-result {
  width: 100%;
}
.header-editor {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.header-editor-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-editor-name {
  width: 38%;
  flex: 0 0 auto;
}
.header-editor-value {
  flex: 1 1 auto;
}
.header-editor-empty {
  font-size: 12px;
  color: #c0c4cc;
  padding: 4px 0;
}
</style>
