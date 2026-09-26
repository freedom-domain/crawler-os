<template>
  <el-card>
    <el-form class="spider-toolbar" :inline="true" @submit.prevent>
      <el-form-item>
        <el-input class="spider-name-filter" v-model="keyword" placeholder="搜索爬虫名称" clearable @clear="loadData" @keyup.enter="loadData" />
      </el-form-item>
      <el-form-item>
        <el-input
          class="spider-url-filter"
          v-model="urlFilter"
          placeholder="搜索起始 URL"
          clearable
          @clear="loadData"
          @keyup.enter="loadData"
        />
      </el-form-item>
      <el-form-item>
        <el-select v-model="groupFilter" placeholder="全部分组" clearable style="width: 160px" @change="loadData">
          <el-option v-for="g in groupOptions" :key="g" :label="g" :value="g" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData" :icon="Search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
      <el-form-item class="toolbar-actions">
        <div class="spider-actions">
          <el-button type="primary" :icon="Plus" @click="showCreate">新建爬虫</el-button>
          <el-button @click="handleExport">导出配置</el-button>
          <el-upload :show-file-list="false" :before-upload="handleImport" accept=".json">
            <el-button>导入配置</el-button>
          </el-upload>
        </div>
      </el-form-item>
    </el-form>

    <el-table ref="tableRef" :data="list" v-loading="loading" stripe :row-class-name="tableRowClassName" resizable border>
      <el-table-column prop="id" label="ID" min-width="60"  resizable />
      <el-table-column prop="name" label="名称" min-width="160" resizable>
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
          <el-tag :type="row.isPublic === 1 ? 'success' : 'info'" size="small">
            {{ row.isPublic === 1 ? '公开' : '私有' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="readCache" label="读取缓存" min-width="110" resizable>
        <template #default="{ row }">
          <el-tag :type="row.readCache === 1 ? 'success' : 'info'">
            {{ row.readCache === 1 ? '读取' : '联网' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="180"  resizable />
      <el-table-column prop="updateTime" label="更新时间" min-width="180"  resizable />
      <el-table-column prop="status" label="定时任务" min-width="100" resizable>
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '运行中' : '停止' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="schedule" label="调度" min-width="160" resizable />
      <el-table-column label="操作" width="120" fixed="right" resizable>
        <template #default="{ row }">
          <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, row)">
            <el-button size="small" type="primary">
              操作 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="run" :disabled="row._running">执行</el-dropdown-item>
                <el-dropdown-item command="search">查询内容</el-dropdown-item>
                <el-dropdown-item command="toggle">{{ row.status === 1 ? '停止' : '启动' }}</el-dropdown-item>
                <el-dropdown-item command="edit">编辑</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>

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
          <el-switch v-model="form.readCache" :active-value="1" :inactive-value="0" active-text="读取" inactive-text="联网" />
          <div class="form-tip">开启后优先读取已缓存的 HTML；命中时不覆盖已缓存的 HTML 和资源，并可补充缺少的资源，未命中时联网抓取并按原逻辑保存</div>
        </el-form-item>
        <el-form-item label="最大深度">
          <el-input-number v-model="form.maxDepth" :min="0" :max="5" />
        </el-form-item>
        <el-form-item label="遵循 robots.txt">
          <el-switch v-model="form.followRobots" :active-value="1" :inactive-value="0" active-text="是" inactive-text="否" />
          <div class="form-tip">开启后，遵循目标站点 robots.txt 中的 Disallow 规则</div>
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
          <el-input v-model="form.schedule" placeholder="生成的 Cron 表达式" class="schedule-result" />
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
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { spiderPage, spiderCreate, spiderDetail, spiderUpdate, spiderStart, spiderStop, spiderDelete, spiderRun, spiderExport, spiderImport, dictTree } from '@/api'
import { Plus, Search, ArrowDown } from '@element-plus/icons-vue'

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
const highlightSpiderId = ref<number | null>(null)
const groupOptions = ref<string[]>([])
const createVisible = ref(false)
const editingId = ref<number | null>(null)
const startUrlsStr = ref('')
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
const form = ref({
  name: '', description: '', type: 'http', group: '', isPublic: 0,
  contentSelector: '', imageSelector: '', vipSelector: '', vipSelectorContent: '', overwriteHtml: 0, overwriteImage: 0, readCache: 0, schedule: '', maxDepth: 2, timeout: 15000, followRobots: 0
})

// 调度表达式生成器
const scheduleType = ref('')
const scheduleInterval = ref(10)
const scheduleTime = ref('00:00')
const scheduleWeekDay = ref(1)
const scheduleDay = ref(1)

const generateSchedule = () => {
  const time = scheduleTime.value || '00:00'
  const [hour, minute] = time.split(':')
  switch (scheduleType.value) {
    case 'minute':
      form.value.schedule = `0 */${scheduleInterval.value} * * * ?`
      break
    case 'hour':
      form.value.schedule = `0 ${minute} */${scheduleInterval.value} * * ?`
      break
    case 'day':
      form.value.schedule = `0 ${minute} ${hour} * * ?`
      break
    case 'week':
      form.value.schedule = `0 ${minute} ${hour} ? * ${scheduleWeekDay.value}`
      break
    case 'month':
      form.value.schedule = `0 ${minute} ${hour} ${scheduleDay.value} * ?`
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

const tableRowClassName = ({ row }: { row: any }) => row.id === highlightSpiderId.value ? 'spider-highlight-row' : ''

const focusSpiderRow = () => {
  if (!highlightSpiderId.value) return
  nextTick(() => {
    const rows = tableRef.value?.$el?.querySelectorAll?.('.el-table__row') || []
    const targetIndex = list.value.findIndex((row) => row.id === highlightSpiderId.value)
    const targetRow = rows[targetIndex]
    if (targetRow) {
      targetRow.scrollIntoView({ behavior: 'smooth', block: 'center' })
      targetRow.classList.add('spider-focus-row')
    }
  })
}

const loadData = async () => {
  loading.value = true
  try {
    const effectiveSize = highlightSpiderId.value ? 1000 : size.value
    const res: any = await spiderPage({
      current: 1,
      size: effectiveSize,
      keyword: keyword.value,
      startUrl: urlFilter.value || undefined,
      group: groupFilter.value || undefined
    })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
    if (highlightSpiderId.value) {
      const found = list.value.some((row) => row.id === highlightSpiderId.value)
      if (!found) {
        highlightSpiderId.value = null
      }
    }
    focusSpiderRow()
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  keyword.value = ''
  urlFilter.value = ''
  groupFilter.value = ''
  page.value = 1
  loadData()
}

const showCreate = () => {
  editingId.value = null
  form.value = { name: '', description: '', type: 'http', group: '', isPublic: 0, contentSelector: '', imageSelector: '', vipSelector: '', vipSelectorContent: '', overwriteHtml: 0, overwriteImage: 0, readCache: 0, schedule: '', maxDepth: 2, timeout: 15000, followRobots: 0 }
  startUrlsStr.value = ''
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

const showEdit = async (row: any) => {
  const res: any = await spiderDetail(row.id)
  const d = res.data
  editingId.value = d.id
  form.value = {
    name: d.name, description: d.description || '', type: d.type, group: d.group || '', isPublic: d.isPublic ?? 0,
    contentSelector: d.contentSelector || '', imageSelector: d.imageSelector || '', vipSelector: d.vipSelector || '', vipSelectorContent: d.vipSelectorContent || '', overwriteHtml: d.overwriteHtml ?? 0, overwriteImage: d.overwriteImage ?? 0, readCache: d.readCache ?? 0,
    schedule: d.schedule || '', maxDepth: d.maxDepth ?? 2, timeout: d.timeout ?? 15000, followRobots: d.followRobots ?? 0
  }
  try {
    const urls = JSON.parse(d.startUrls || '[]')
    startUrlsStr.value = urls.join(', ')
  } catch {
    startUrlsStr.value = d.startUrls || ''
  }
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
      await spiderUpdate(editingId.value, { ...form.value, startUrls })
      ElMessage.success('更新成功')
    } else {
      await spiderCreate({ ...form.value, startUrls })
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

const handleStart = async (row: any) => {
  try {
    await spiderStart(row.id)
    ElMessage.success('已启动')
  } catch {
    ElMessage.error('启动失败')
  }
  loadData()
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
  row._running = true
  try {
    const res: any = await spiderRun(row.id)
    ElMessage.success(res.data?.status === 'PENDING' ? '任务已加入等待队列' : '任务已派发')
  } finally {
    row._running = false
  }
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定删除该爬虫?', '警告', { type: 'warning' })
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

const handleCommand = (cmd: string, row: any) => {
  switch (cmd) {
    case 'run': handleRun(row); break
    case 'search':
      router.push({ name: 'Search', query: { spiderId: String(row.id) } })
      break
    case 'toggle': row.status === 1 ? handleStop(row) : handleStart(row); break
    case 'edit': showEdit(row); break
    case 'delete': handleDelete(row); break
  }
}

watch(
  () => route.query.spiderId,
  (val) => {
    const id = Number(val)
    highlightSpiderId.value = Number.isFinite(id) && id > 0 ? id : null
    if (highlightSpiderId.value) {
      loadData()
    }
  },
  { immediate: true }
)

onMounted(() => {
  loadGroupOptions()
  loadData()
})
</script>

<style scoped>
:deep(.spider-highlight-row) {
  background: #fff7e6 !important;
}

:deep(.spider-focus-row) {
  box-shadow: inset 0 0 0 2px #f59e0b;
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
</style>
