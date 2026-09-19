<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>爬虫管理</span>
        <el-button type="primary" @click="showCreate" :icon="Plus">新建爬虫</el-button>
      </div>
    </template>

    <el-form :inline="true" @submit.prevent>
      <el-form-item>
        <el-input v-model="keyword" placeholder="搜索爬虫名称" clearable @clear="loadData" @keyup.enter="loadData" />
      </el-form-item>
      <el-form-item>
        <el-select v-model="groupFilter" placeholder="全部分组" clearable style="width: 160px" @change="loadData">
          <el-option v-for="g in groupOptions" :key="g" :label="g" :value="g" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData" :icon="Search">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table ref="tableRef" :data="list" v-loading="loading" stripe :row-class-name="tableRowClassName">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" width="160" />
      <el-table-column prop="group" label="分组" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.group" size="small">{{ row.group }}</el-tag>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }">
          <el-tag>{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startUrls" label="起始URL" show-overflow-tooltip />
      <el-table-column prop="schedule" label="调度" width="160" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column prop="updateTime" label="更新时间" width="180" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '运行中' : '停止' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="320">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="handleRun(row)" :loading="row._running">执行</el-button>
          <el-button size="small" @click="row.status === 1 ? handleStop(row) : handleStart(row)">
            {{ row.status === 1 ? '停止' : '启动' }}
          </el-button>
          <el-button size="small" @click="showEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top:16px; justify-content:flex-end"
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      layout="total, prev, pager, next"
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
        <el-form-item label="内容选择器">
          <el-input v-model="form.contentSelector" placeholder="CSS选择器，如 .article-content 或 #content" />
          <div class="form-tip">优先取该选择器命中的文本作为 ES 内容；无内容时回退到标题</div>
        </el-form-item>
        <el-form-item label="图片选择器">
          <el-input v-model="form.imageSelector" placeholder="CSS选择器，如 .article img 或 #content" />
        </el-form-item>
        <el-form-item label="覆盖HTML">
          <el-switch v-model="form.overwriteHtml" :active-value="1" :inactive-value="0" active-text="覆盖" inactive-text="跳过" />
          <div class="form-tip">开启后重新爬取会覆盖 ES 中的内容；关闭则内容未变化时跳过</div>
        </el-form-item>
        <el-form-item label="覆盖图片">
          <el-switch v-model="form.overwriteImage" :active-value="1" :inactive-value="0" active-text="覆盖" inactive-text="跳过" />
          <div class="form-tip">开启后重新爬取会重新下载并覆盖已存在的图片；关闭则已存在图片不重复下载</div>
        </el-form-item>
        <el-form-item label="最大深度">
          <el-input-number v-model="form.maxDepth" :min="0" :max="5" />
        </el-form-item>
        <el-form-item label="遵循 robots.txt">
          <el-switch v-model="form.followRobots" :active-value="1" :inactive-value="0" active-text="是" inactive-text="否" />
          <div class="form-tip">开启后，遵循目标站点 robots.txt 中的 Disallow 规则</div>
        </el-form-item>
        <el-form-item label="调度表达式">
          <el-input v-model="form.schedule" placeholder="如: 0 */10 * * * ?" />
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
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { spiderPage, spiderCreate, spiderDetail, spiderUpdate, spiderStart, spiderStop, spiderDelete, spiderRun, dictTree } from '@/api'
import { Plus, Search } from '@element-plus/icons-vue'

const route = useRoute()
const tableRef = ref<any>(null)

const list = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const keyword = ref('')
const groupFilter = ref('')
const highlightSpiderId = ref<number | null>(null)
const groupOptions = ref<string[]>([])
const createVisible = ref(false)
const editingId = ref<number | null>(null)
const startUrlsStr = ref('')
const form = ref({
  name: '', description: '', type: 'http', group: '',
  contentSelector: '', imageSelector: '', overwriteHtml: 0, overwriteImage: 0, schedule: '', maxDepth: 2, timeout: 15000, followRobots: 0
})

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
    const res: any = await spiderPage({ current: 1, size: effectiveSize, keyword: keyword.value, group: groupFilter.value || undefined })
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

const showCreate = () => {
  editingId.value = null
  form.value = { name: '', description: '', type: 'http', group: '', contentSelector: '', imageSelector: '', overwriteHtml: 0, overwriteImage: 0, schedule: '', maxDepth: 2, timeout: 15000, followRobots: 0 }
  startUrlsStr.value = ''
  createVisible.value = true
}

const showEdit = async (row: any) => {
  const res: any = await spiderDetail(row.id)
  const d = res.data
  editingId.value = d.id
  form.value = {
    name: d.name, description: d.description || '', type: d.type, group: d.group || '',
    contentSelector: d.contentSelector || '', imageSelector: d.imageSelector || '', overwriteHtml: d.overwriteHtml ?? 0, overwriteImage: d.overwriteImage ?? 0,
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
  } catch {
    ElMessage.error('操作失败')
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
    await spiderRun(row.id)
    ElMessage.success('任务已派发')
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

.card-header { display: flex; justify-content: space-between; align-items: center; }
.form-tip { font-size: 12px; color: #999; line-height: 1.5; margin-top: 4px; }
.text-muted { color: #c0c4cc; }

:deep(.spider-dialog .el-dialog__body) {
  height: 70vh;
  overflow-y: auto;
  padding-top: 10px;
}
:deep(.spider-dialog .el-form-item__label) {
  white-space: nowrap;
}
</style>
