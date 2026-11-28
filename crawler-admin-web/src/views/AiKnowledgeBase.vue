<template>
  <section class="knowledge-page">
    <header class="page-heading">
      <div>
        <h1>知识库管理</h1>
        <p>新建和维护知识库、向量模型及可选的重排模型。</p>
      </div>
      <div class="heading-actions">
        <el-button type="primary" :icon="Plus" @click="openCreate">新建知识库</el-button>
      </div>
    </header>

    <el-card shadow="never">
      <el-form class="knowledge-filters" :inline="true" @submit.prevent>
        <el-form-item>
          <el-input
            v-model="searchKeyword"
            clearable
            placeholder="搜索知识库名称、简介、索引"
            @keyup.enter="searchKnowledgeBases"
          />
        </el-form-item>
        <el-form-item>
          <el-select v-model="sourceTypeFilter" clearable placeholder="全部来源类型">
            <el-option label="ES 索引" value="ES_INDEX" />
            <el-option label="直接上传" value="UPLOAD" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="enabledFilter" clearable placeholder="全部状态">
            <el-option label="已启用" :value="true" />
            <el-option label="已停用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="searchKnowledgeBases">查询</el-button>
        </el-form-item>
        <el-form-item>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="table-scroll-wrapper">
        <el-table :data="knowledgeBases" v-loading="loading" stripe resizable border>
          <template #empty>
            <el-empty v-if="!loading" description="暂无知识库" />
          </template>
          <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip resizable />
          <el-table-column label="来源类型" min-width="120" align="center" resizable>
            <template #default="{ row }">
              <el-tag size="small" effect="light">
                {{ row.sourceType === 'UPLOAD' ? '直接上传' : 'ES 索引' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="简介" min-width="220" show-overflow-tooltip resizable />
          <el-table-column prop="vectorModelName" label="向量模型" min-width="180" show-overflow-tooltip resizable />
          <el-table-column prop="rerankModelName" label="重排模型" min-width="180" show-overflow-tooltip resizable />
          <el-table-column label="来源爬虫" min-width="180" show-overflow-tooltip resizable>
            <template #default="{ row }">
              <span v-if="row.sourceType === 'UPLOAD'">—</span>
              <span v-else-if="row.spiderNames?.length">{{ row.spiderNames.join('、') }}</span>
              <el-tag v-else size="small" type="info" effect="plain">全部</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="100" align="center" resizable>
            <template #default="{ row }">
              <el-tag size="small" :type="row.enabled ? 'success' : 'info'">
                {{ row.enabled ? '已启用' : '已停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sort" label="排序" min-width="80" align="center" resizable />
          <el-table-column label="操作" width="80" fixed="right" align="center" resizable>
            <template #default="{ row }">
              <TableRowActions
                :items="rowActions"
                @command="command => handleRowAction(command, row)"
              />
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 15, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @change="loadKnowledgeBasePage"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑知识库' : '新建知识库'" width="680px" top="5vh">
      <el-form :model="form" label-position="top">
        <el-form-item label="知识库名称" required>
          <el-input v-model="form.name" maxlength="64" placeholder="例如：产品文档知识库" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.description" maxlength="512" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="文档来源类型" required>
          <el-select v-model="form.sourceType" style="width:100%">
            <el-option label="通过 ES 索引" value="ES_INDEX" />
            <el-option label="直接上传文档" value="UPLOAD" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.sourceType === 'ES_INDEX'" label="源索引">
          <el-input :model-value="fixedConfig.sourceIndex" disabled />
        </el-form-item>
        <el-form-item v-if="form.sourceType === 'ES_INDEX'" label="来源爬虫">
          <button type="button" class="spider-picker" @click="openSpiderDialog">
            <span class="spider-picker-copy">
              <strong v-if="form.spiderIds.length">
                {{ selectedSpiderNames.join('、') }}
              </strong>
              <strong v-else class="spider-picker-placeholder">全部爬虫</strong>
              <small>
                {{ form.spiderIds.length
                  ? `已选择 ${form.spiderIds.length} 个爬虫`
                  : '不选择则收录全部爬虫抓取的文档' }}
              </small>
            </span>
            <span class="spider-picker-action">选择</span>
          </button>
          <small class="field-hint">
            仅收录所选爬虫抓取的文档，保存后需对知识库执行批量重建使范围生效。
          </small>
        </el-form-item>
        <el-form-item v-if="form.sourceType === 'UPLOAD'" label="MinIO Bucket">
          <el-input :model-value="fixedConfig.uploadBucket" disabled />
        </el-form-item>
        <el-form-item label="向量模型" required>
          <el-select v-model="form.vectorModelId" style="width:100%" placeholder="请选择向量模型">
            <el-option v-for="model in vectorModels" :key="model.id" :label="model.name" :value="model.id" />
          </el-select>
          <el-descriptions
            v-if="selectedVectorModel"
            :column="2"
            border
            size="small"
            class="selected-model-config"
          >
            <el-descriptions-item label="服务提供方">
              {{ selectedVectorModel.embeddingProvider === 'ollama' ? 'Ollama' : 'OpenAI 兼容' }}
            </el-descriptions-item>
            <el-descriptions-item label="模型名称">
              {{ selectedVectorModel.embeddingModel }}
            </el-descriptions-item>
            <el-descriptions-item label="服务地址">
              {{ selectedVectorModel.embeddingBaseUrl }}
            </el-descriptions-item>
            <el-descriptions-item label="API Key">
              {{ selectedVectorModel.apiKeyConfigured ? '已配置' : '未配置' }}
            </el-descriptions-item>
            <el-descriptions-item label="向量维度">
              {{ selectedVectorModel.dimensions }}
            </el-descriptions-item>
            <el-descriptions-item label="Top K">
              {{ selectedVectorModel.topK }}
            </el-descriptions-item>
          </el-descriptions>
        </el-form-item>
        <small class="field-hint">向量索引根据该知识库选定的向量模型自动生成；文档分块策略独立匹配，不随模型变化。</small>
        <el-form-item label="重排模型（可选）">
          <el-select
            v-model="form.rerankModelId"
            clearable
            style="width:100%"
            placeholder="不选择则不启用重排"
          >
            <el-option
              v-for="model in rerankModels"
              :key="model.id"
              :label="`${model.name}（${model.rerankProvider} / ${model.rerankModel}）`"
              :value="model.id"
            />
          </el-select>
          <el-descriptions
            v-if="selectedRerankModel"
            :column="2"
            border
            size="small"
            class="selected-model-config"
          >
            <el-descriptions-item label="服务提供方">
              {{ selectedRerankModel.rerankProvider }}
            </el-descriptions-item>
            <el-descriptions-item label="模型名称">
              {{ selectedRerankModel.rerankModel }}
            </el-descriptions-item>
            <el-descriptions-item label="服务地址">
              {{ selectedRerankModel.rerankBaseUrl }}
            </el-descriptions-item>
            <el-descriptions-item label="API Key">
              {{ selectedRerankModel.apiKeyConfigured ? '已配置' : '未配置' }}
            </el-descriptions-item>
          </el-descriptions>
          <small class="field-hint">配置后，检索会先召回候选文档，再由该模型重新排序。</small>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="排序">
            <el-input-number v-model="form.sort" :min="0" :max="999" controls-position="right" />
          </el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="spiderDialogVisible"
      title="选择来源爬虫"
      width="760px"
      top="8vh"
      append-to-body
    >
      <div class="spider-dialog-toolbar">
        <div class="spider-dialog-toolbar-filters">
          <el-select
            v-model="spiderGroup"
            clearable
            placeholder="全部分组"
            style="width: 150px"
            @change="loadSpiderOptions"
          >
            <el-option v-for="g in spiderGroupOptions" :key="g" :label="g" :value="g" />
          </el-select>
          <el-input
            v-model="spiderKeyword"
            clearable
            placeholder="搜索爬虫名称"
            style="width: 200px"
            @input="onSpiderKeywordInput"
          />
        </div>
        <div class="spider-dialog-toolbar-actions">
          <span>可同时选择多个爬虫，取消全部选择则收录所有爬虫的文档</span>
          <el-button link type="primary" @click="selectAllSpiders">全选</el-button>
          <el-button link @click="clearSpiderSelection">清空</el-button>
        </div>
      </div>
      <el-table
        ref="spiderTableRef"
        v-loading="spiderLoading"
        :data="pagedSpiders"
        row-key="id"
        size="small"
        height="340"
        @select="onSpiderSelect"
        @select-all="onSpiderSelectAll"
      >
        <el-table-column type="selection" width="42" reserve-selection />
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="group" label="分组" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.group || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="公开" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.isPublic === 1 ? 'success' : 'info'">
              {{ row.isPublic === 1 ? '公开' : '私有' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="spiderListPage"
        v-model:page-size="spiderPageSize"
        :total="spiderOptions.length"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        class="spider-dialog-pagination"
      />
      <template #footer>
        <el-button @click="spiderDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSpiderSelection">
          确定（{{ spiderDraftIds.length }}）
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, FolderOpened, Plus } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import {
  createKnowledgeBase, deleteKnowledgeBase, getKnowledgeBasePage, getRagConfig,
  dictTree, getRerankModels, getVectorModels, spiderPage, updateKnowledgeBase
} from '@/api'
import TableRowActions, { type TableRowAction } from '@/components/TableRowActions.vue'

interface VectorModel {
  id: string
  name: string
  embeddingProvider: string
  embeddingBaseUrl: string
  embeddingModel: string
  apiKeyConfigured: boolean
  dimensions: number
  topK: number
}

interface RerankModel {
  id: string
  name: string
  rerankProvider: string
  rerankBaseUrl: string
  rerankModel: string
  apiKeyConfigured: boolean
}

interface FixedConfig {
  sourceIndex: string
  vectorField: string
  uploadBucket: string
}

const knowledgeBases = ref<any[]>([])
const router = useRouter()
const vectorModels = ref<VectorModel[]>([])
const rerankModels = ref<RerankModel[]>([])
const fixedConfig = ref<FixedConfig>({
  sourceIndex: '',
  vectorField: '',
  uploadBucket: ''
})
const page = ref(1)
const pageSize = ref(15)
const total = ref(0)
const searchKeyword = ref('')
const sourceTypeFilter = ref('')
const enabledFilter = ref<boolean | null>(null)
const selectedVectorModel = computed(() =>
  vectorModels.value.find(model => model.id === form.value.vectorModelId)
)
const selectedRerankModel = computed(() =>
  rerankModels.value.find(model => model.id === form.value.rerankModelId)
)
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref('')
const form = ref({
  name: '',
  description: '',
  enabled: true,
  vectorModelId: '',
  rerankModelId: '',
  sourceIndex: '',
  vectorField: '',
  sourceType: 'ES_INDEX',
  uploadBucket: '',
  spiderIds: [] as string[],
  sort: 0
})

interface SpiderOption {
  id: string
  name: string
  group?: string
  isPublic?: number
}
const spiderDialogVisible = ref(false)
const spiderLoading = ref(false)
const spiderKeyword = ref('')
const spiderGroup = ref('')
const spiderGroupOptions = ref<string[]>([])
const spiderOptions = ref<SpiderOption[]>([])
const spiderListPage = ref(1)
const spiderPageSize = ref(20)
const spiderTableRef = ref()
// 弹窗内的草稿选择，点“确定”后才写回表单，“取消”即丢弃
const spiderDraftIds = ref<string[]>([])
// 爬虫 ID → 名称映射（编辑时来自后端，打开选择弹窗后补充最新名称）
const spiderNameMap = ref<Record<string, string>>({})
const pagedSpiders = computed(() => {
  const start = (spiderListPage.value - 1) * spiderPageSize.value
  return spiderOptions.value.slice(start, start + spiderPageSize.value)
})
const selectedSpiderNames = computed(() =>
  form.value.spiderIds.map(id => spiderNameMap.value[id] || `爬虫${id}`)
)

let spiderSearchTimer: ReturnType<typeof setTimeout> | null = null
function onSpiderKeywordInput() {
  if (spiderSearchTimer) clearTimeout(spiderSearchTimer)
  spiderSearchTimer = setTimeout(() => void loadSpiderOptions(), 300)
}

// 分组选项与爬虫管理页同源：字典树中 spider-group 分类下的子项
async function loadSpiderGroupOptions() {
  try {
    const res: any = await dictTree()
    const tree = res.data || []
    const cat = tree.find((c: any) => c.value === 'spider-group' || c.label === 'spider-group')
    spiderGroupOptions.value = cat
      ? (cat.children || []).map((ch: any) => ch.value || ch.label)
      : []
  } catch {
    spiderGroupOptions.value = []
  }
}

async function loadSpiderOptions() {
  spiderLoading.value = true
  try {
    const res: any = await spiderPage({
      current: 1,
      size: 1000,
      keyword: spiderKeyword.value.trim() || undefined,
      group: spiderGroup.value || undefined
    })
    const records = res.data?.records || []
    spiderOptions.value = records.map((row: any) => ({
      id: String(row.id),
      name: row.name,
      group: row.group,
      isPublic: row.isPublic
    }))
    records.forEach((row: any) => {
      spiderNameMap.value[String(row.id)] = row.name
    })
    if (spiderListPage.value > Math.max(1, Math.ceil(spiderOptions.value.length / spiderPageSize.value))) {
      spiderListPage.value = 1
    }
    await nextTick()
    // 按草稿预选已勾选爬虫（reserve-selection 通过 row-key 跨页保持勾选）
    spiderTableRef.value?.clearSelection()
    spiderDraftIds.value.forEach(id => {
      const row = spiderOptions.value.find(option => option.id === id)
      spiderTableRef.value?.toggleRowSelection(row || { id }, true)
    })
  } finally {
    spiderLoading.value = false
  }
}

function onSpiderSelect(selection: any[], row: any) {
  const draft = new Set(spiderDraftIds.value)
  const id = String(row.id)
  if (selection.some(item => String(item.id) === id)) draft.add(id)
  else draft.delete(id)
  spiderDraftIds.value = [...draft]
}

function onSpiderSelectAll(selection: any[]) {
  const draft = new Set(spiderDraftIds.value)
  const pageIds = new Set(pagedSpiders.value.map(option => option.id))
  const selectedPageIds = new Set(selection.map(item => String(item.id)))
  pageIds.forEach(id => {
    if (selectedPageIds.has(id)) draft.add(id)
    else draft.delete(id)
  })
  spiderDraftIds.value = [...draft]
}

function selectAllSpiders() {
  spiderDraftIds.value = spiderOptions.value.map(option => option.id)
  spiderOptions.value.forEach(row => spiderTableRef.value?.toggleRowSelection(row, true))
}

function clearSpiderSelection() {
  spiderDraftIds.value = []
  spiderTableRef.value?.clearSelection()
}

function openSpiderDialog() {
  spiderDraftIds.value = [...form.value.spiderIds]
  spiderKeyword.value = ''
  spiderGroup.value = ''
  spiderListPage.value = 1
  spiderDialogVisible.value = true
  void loadSpiderGroupOptions()
  void loadSpiderOptions()
}

function confirmSpiderSelection() {
  form.value.spiderIds = [...spiderDraftIds.value]
  spiderDialogVisible.value = false
}

async function loadAll() {
  loading.value = true
  try {
    const [_, vectorResponse, rerankResponse, configResponse] = await Promise.all([
      fetchKnowledgeBasePage(),
      getVectorModels(),
      getRerankModels(),
      getRagConfig()
    ])
    vectorModels.value = vectorResponse.data || []
    rerankModels.value = rerankResponse.data || []
    fixedConfig.value = {
      sourceIndex: configResponse.data.sourceIndex || '',
      vectorField: configResponse.data.vectorField || '',
      uploadBucket: configResponse.data.uploadBucket || ''
    }
  } finally {
    loading.value = false
  }
}

async function fetchKnowledgeBasePage() {
  const response: any = await getKnowledgeBasePage({
    page: page.value,
    pageSize: pageSize.value,
    keyword: searchKeyword.value.trim() || undefined,
    sourceType: sourceTypeFilter.value || undefined,
    enabled: enabledFilter.value ?? undefined
  })
  knowledgeBases.value = response.data.records || []
  total.value = response.data.total
  const lastPage = Math.max(1, Math.ceil(total.value / pageSize.value))
  if (page.value > lastPage) {
    page.value = lastPage
    await fetchKnowledgeBasePage()
  }
}

async function loadKnowledgeBasePage() {
  loading.value = true
  try {
    await fetchKnowledgeBasePage()
  } finally {
    loading.value = false
  }
}

function openDocumentManagement() {
  router.push('/ai-knowledge-documents')
}

function openKnowledgeDocuments(row: any) {
  router.push({
    name: 'AiKnowledgeDocumentsById',
    params: { knowledgeBaseId: String(row.id) }
  })
}

const rowActions: TableRowAction[] = [
  { command: 'documents', label: '文档管理', icon: FolderOpened },
  { command: 'edit', label: '编辑', icon: Edit },
  { command: 'delete', label: '删除', icon: Delete, divided: true, danger: true }
]

function handleRowAction(command: string, row: any) {
  if (command === 'documents') openKnowledgeDocuments(row)
  else if (command === 'edit') openEdit(row)
  else if (command === 'delete') void removeKnowledgeBase(row)
}

function resetFilters() {
  searchKeyword.value = ''
  sourceTypeFilter.value = ''
  enabledFilter.value = null
  page.value = 1
  void loadKnowledgeBasePage()
}

function searchKnowledgeBases() {
  page.value = 1
  void loadKnowledgeBasePage()
}

function openCreate() {
  editingId.value = ''
  spiderNameMap.value = {}
  form.value = {
    name: '', description: '', enabled: true, vectorModelId: '',
    rerankModelId: '',
    sourceIndex: fixedConfig.value.sourceIndex,
    vectorField: fixedConfig.value.vectorField,
    sourceType: 'ES_INDEX',
    uploadBucket: fixedConfig.value.uploadBucket,
    spiderIds: [], sort: 0
  }
  dialogVisible.value = true
}

function openEdit(row: any) {
  editingId.value = row.id
  const spiderIds: string[] = (row.spiderIds || []).map(String)
  const nameMap: Record<string, string> = {}
  spiderIds.forEach((id, index) => {
    const name = row.spiderNames?.[index]
    if (name) nameMap[id] = name
  })
  spiderNameMap.value = nameMap
  form.value = {
    name: row.name,
    description: row.description || '',
    enabled: row.enabled,
    vectorModelId: row.vectorModelId,
    rerankModelId: row.rerankModelId || '',
    sourceIndex: fixedConfig.value.sourceIndex,
    vectorField: fixedConfig.value.vectorField,
    sourceType: row.sourceType || 'ES_INDEX',
    uploadBucket: fixedConfig.value.uploadBucket,
    spiderIds: row.sourceType === 'ES_INDEX' ? spiderIds : [],
    sort: row.sort
  }
  dialogVisible.value = true
}

async function save() {
  if (!form.value.name.trim()) return ElMessage.warning('请输入知识库名称')
  if (!form.value.vectorModelId) {
    return ElMessage.warning('请完整填写向量模型和索引配置')
  }
  saving.value = true
  try {
    const payload = {
      ...form.value,
      rerankModelId: form.value.rerankModelId || null,
      spiderIds: form.value.sourceType === 'ES_INDEX' && form.value.spiderIds.length
        ? form.value.spiderIds
        : null
    }
    if (editingId.value) await updateKnowledgeBase(editingId.value, payload)
    else await createKnowledgeBase(payload)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadAll()
  } finally {
    saving.value = false
  }
}

async function removeKnowledgeBase(row: any) {
  await ElMessageBox.confirm(`确定删除知识库“${row.name}”？`, '删除知识库', { type: 'warning' })
  await deleteKnowledgeBase(row.id)
  ElMessage.success('删除成功')
  await loadAll()
}

onMounted(loadAll)
</script>

<style scoped>
.knowledge-page { width: 100%; max-width: none; margin: 0; box-sizing: border-box; }
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 16px; }
.page-heading h1 { margin: 0 0 6px; font-size: 22px; }
.page-heading p { margin: 0; color: var(--el-text-color-secondary); }
.heading-actions { display: flex; align-items: center; gap: 8px; }
.knowledge-filters { display: flex; flex-wrap: wrap; gap: 0 12px; }
.knowledge-filters :deep(.el-input) { width: min(320px, 70vw); }
.knowledge-filters :deep(.el-select) { width: 160px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.selected-model-config { width: 100%; margin-top: 12px; }
.field-hint { display: block; margin-top: 6px; color: var(--el-text-color-secondary); line-height: 1.5; }
.spider-picker {
  display: flex;
  width: 100%;
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px 8px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface-soft);
  color: var(--ink-900);
  text-align: left;
  cursor: pointer;
}
.spider-picker:hover {
  border-color: var(--line-strong);
  background: var(--surface);
}
.spider-picker-copy {
  display: grid;
  min-width: 0;
  gap: 3px;
}
.spider-picker-copy strong {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.spider-picker-placeholder { color: var(--ink-500); font-weight: 400; }
.spider-picker-copy small { color: var(--ink-500); font-size: 11px; }
.spider-picker-action {
  flex: 0 0 auto;
  padding: 6px 9px;
  border-radius: 6px;
  background: var(--chrome-hover);
  color: var(--teal-ink);
  font-size: 12px;
  font-weight: 600;
}
.spider-dialog-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  color: var(--ink-500);
  font-size: 12px;
}
.spider-dialog-toolbar-filters {
  display: flex;
  align-items: center;
  gap: 10px;
}
.spider-dialog-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.spider-dialog-pagination {
  margin-top: 10px;
  justify-content: flex-end;
}
@media (max-width: 680px) {
  .page-heading { flex-direction: column; align-items: stretch; }
  .heading-actions { flex-wrap: wrap; }
  .knowledge-filters { display: grid; grid-template-columns: 1fr; }
  .knowledge-filters :deep(.el-input), .knowledge-filters :deep(.el-select) { width: 100%; }
}
</style>
