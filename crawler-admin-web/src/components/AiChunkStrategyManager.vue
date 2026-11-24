<template>
  <div>
    <el-button v-if="showTrigger" @click="openManager">分块策略管理</el-button>

    <el-dialog v-model="managerVisible" title="文档分块策略" width="min(1100px, 94vw)">
      <div class="strategy-header">
        <span>进入分块流程后按字符数和文档类型匹配，命中多条时选择优先级较高者；未命中则使用默认策略。</span>
        <el-button type="primary" @click="openCreateStrategy">新增策略</el-button>
      </div>
      <div class="table-scroll-wrapper">
        <el-table :data="strategies" stripe resizable border>
          <el-table-column prop="name" label="策略名称" min-width="180" fixed="left" resizable />
          <el-table-column label="策略类型" min-width="120" resizable>
            <template #default="{ row }">{{ strategyLabel(row.strategyType) }}</template>
          </el-table-column>
          <el-table-column label="最大长度 / 重叠" min-width="150" resizable>
            <template #default="{ row }">
              {{ row.chunkSize }} / {{ row.strategyType === 'SEMANTIC' ? '-' : row.chunkOverlap }}
            </template>
          </el-table-column>
          <el-table-column label="语义阈值" min-width="100" resizable>
            <template #default="{ row }">
              {{ row.strategyType === 'SEMANTIC' ? row.similarityThreshold : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="priority" label="优先级" width="90" resizable />
          <el-table-column label="触发条件" min-width="220" resizable>
            <template #default="{ row }">
              <span v-if="row.defaultStrategy">未命中时兜底</span>
              <span v-else>
                {{ row.minDocumentLength }}–{{ row.maxDocumentLength ?? '不限' }} 字符 ·
                {{ row.documentTypes || '任意类型' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="默认" width="80" resizable>
            <template #default="{ row }">
              <el-tag v-if="row.defaultStrategy" type="success">默认</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right" resizable>
            <template #default="{ row }">
              <el-button link type="primary" @click="openEditStrategy(row)">编辑</el-button>
              <el-button link type="danger" :disabled="row.defaultStrategy" @click="removeStrategy(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <el-dialog
      v-model="strategyFormVisible"
      :title="editingStrategyId === null ? '新增分块策略' : '编辑分块策略'"
      width="560px"
      @closed="resetStrategyForm"
    >
      <el-form ref="strategyFormRef" :model="strategyForm" :rules="strategyRules" label-position="top">
        <el-form-item label="策略名称" prop="name">
          <el-input v-model="strategyForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="策略类型" prop="strategyType">
          <el-select v-model="strategyForm.strategyType" style="width:100%">
            <el-option label="固定字符" value="FIXED_CHARACTER" />
            <el-option label="按段落" value="PARAGRAPH" />
            <el-option label="递归分隔符" value="RECURSIVE" />
            <el-option label="语义分块" value="SEMANTIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="最大分块长度（字符）" prop="chunkSize">
          <el-input-number v-model="strategyForm.chunkSize" :min="200" :max="20000" :step="100" />
        </el-form-item>
        <el-form-item v-if="strategyForm.strategyType !== 'SEMANTIC'" label="分块重叠（字符）" prop="chunkOverlap">
          <el-input-number v-model="strategyForm.chunkOverlap" :min="0" :max="19999" :step="50" />
        </el-form-item>
        <el-form-item v-if="strategyForm.strategyType === 'SEMANTIC'" label="相似度阈值" prop="similarityThreshold">
          <el-input-number v-model="strategyForm.similarityThreshold" :min="0" :max="1" :step="0.05" />
          <small class="field-hint">相邻句子余弦相似度低于此值时开始新分块。</small>
        </el-form-item>
        <el-form-item v-if="!editingDefaultStrategy" label="触发文档最小字符数" prop="minDocumentLength">
          <el-input-number v-model="strategyForm.minDocumentLength" :min="0" :max="100000000" />
        </el-form-item>
        <el-form-item v-if="!editingDefaultStrategy" label="触发文档最大字符数" prop="maxDocumentLength">
          <el-input-number v-model="strategyForm.maxDocumentLength" :min="1" :max="100000000" clearable />
          <small class="field-hint">留空表示不限制；边界值包含在范围内。</small>
        </el-form-item>
        <el-form-item v-if="!editingDefaultStrategy" label="适用文档类型" prop="documentTypes">
          <div class="document-types-picker">
            <el-input
              :model-value="documentTypeSummary"
              readonly
              placeholder="不限文档类型"
              @click="openDocumentTypeSelector"
            >
              <template #append>
                <el-button @click="openDocumentTypeSelector">选择类型</el-button>
              </template>
            </el-input>
            <small class="field-hint">不选择类型表示适用于任意文档。</small>
          </div>
        </el-form-item>
        <el-form-item v-if="!editingDefaultStrategy" label="策略优先级" prop="priority">
          <el-input-number v-model="strategyForm.priority" :min="0" :max="10000" />
          <small class="field-hint">范围 0–10000，数值越大越先匹配。</small>
        </el-form-item>
        <el-alert
          v-if="editingDefaultStrategy"
          title="默认策略仅在没有其他策略命中时使用。"
          type="info"
          :closable="false"
        />
      </el-form>
      <template #footer>
        <el-button @click="strategyFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="strategySaving" @click="saveStrategy">保存策略</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="documentTypeSelectorVisible"
      title="选择适用文档类型"
      width="min(680px, 92vw)"
      append-to-body
    >
      <p class="document-type-hint">可多选；策略仅对所选扩展名的文档生效。</p>
      <el-checkbox-group v-model="documentTypeSelectionDraft" class="document-type-options">
        <el-checkbox
          v-for="type in documentTypeOptions"
          :key="type.value"
          :label="type.value"
          :value="type.value"
        >
          {{ type.label }}（.{{ type.value }}）
        </el-checkbox>
        <el-checkbox
          v-for="type in customDocumentTypeOptions"
          :key="type.value"
          :label="type.value"
          :value="type.value"
        >
          {{ type.value }}（已配置）
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="documentTypeSelectionDraft = []">清空</el-button>
        <el-button @click="documentTypeSelectorVisible = false">取消</el-button>
        <el-button type="primary" @click="applyDocumentTypeSelection">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createChunkStrategy, deleteChunkStrategy, getChunkStrategies, updateChunkStrategy
} from '@/api'

// showTrigger 为 false 时不渲染触发按钮，仅保留弹窗，由父组件通过 open() 打开
withDefaults(defineProps<{ showTrigger?: boolean }>(), { showTrigger: true })

interface ChunkStrategy {
  id: string
  name: string
  strategyType: string
  chunkSize: number
  chunkOverlap: number
  similarityThreshold: number
  priority: number
  minDocumentLength: number
  maxDocumentLength: number | null
  documentTypes: string
  defaultStrategy: boolean
}

const emptyStrategy = () => ({
  name: '',
  strategyType: 'FIXED_CHARACTER',
  chunkSize: 1500,
  chunkOverlap: 200,
  similarityThreshold: 0.7,
  priority: 0,
  minDocumentLength: 0,
  maxDocumentLength: null as number | null,
  documentTypes: ''
})

const strategies = ref<ChunkStrategy[]>([])
const managerVisible = ref(false)
const strategyFormVisible = ref(false)
const strategySaving = ref(false)
const editingStrategyId = ref<string | null>(null)
const editingDefaultStrategy = ref(false)
const strategyFormRef = ref<FormInstance>()
const strategyForm = reactive(emptyStrategy())
const documentTypeSelectorVisible = ref(false)
const documentTypeSelectionDraft = ref<string[]>([])
const documentTypeOptions = [
  { value: 'pdf', label: 'PDF' },
  { value: 'doc', label: 'Word 97–2003' },
  { value: 'docx', label: 'Word' },
  { value: 'odt', label: 'OpenDocument 文本' },
  { value: 'rtf', label: '富文本' },
  { value: 'xls', label: 'Excel 97–2003' },
  { value: 'xlsx', label: 'Excel' },
  { value: 'ods', label: 'OpenDocument 表格' },
  { value: 'csv', label: 'CSV 表格' },
  { value: 'ppt', label: 'PowerPoint 97–2003' },
  { value: 'pptx', label: 'PowerPoint' },
  { value: 'odp', label: 'OpenDocument 演示文稿' },
  { value: 'txt', label: '纯文本' },
  { value: 'md', label: 'Markdown' },
  { value: 'html', label: 'HTML' },
  { value: 'htm', label: 'HTM 网页' },
  { value: 'xml', label: 'XML' },
  { value: 'json', label: 'JSON' },
  { value: 'epub', label: '电子书' }
]
const selectedDocumentTypes = computed({
  get: () => strategyForm.documentTypes.split(',').filter(Boolean),
  set: (types: string[]) => {
    strategyForm.documentTypes = types.join(',')
  }
})
const documentTypeSummary = computed(() => {
  const labels = new Map(documentTypeOptions.map(type => [type.value, type.label]))
  return selectedDocumentTypes.value
    .map(type => labels.get(type) || type)
    .join('、')
})
const customDocumentTypeOptions = computed(() => {
  const knownTypes = new Set(documentTypeOptions.map(type => type.value))
  return selectedDocumentTypes.value
    .filter(type => !knownTypes.has(type))
    .map(value => ({ value }))
})

const strategyRules: FormRules = {
  name: [{ required: true, message: '请输入策略名称', trigger: 'blur' }],
  strategyType: [{ required: true, message: '请选择策略类型', trigger: 'change' }],
  chunkSize: [{ required: true, type: 'number', min: 200, max: 20000, message: '最大分块长度范围为 200–20000', trigger: 'change' }],
  chunkOverlap: [{
    required: true,
    type: 'number',
    validator: (_rule, value, callback) => {
      if (strategyForm.strategyType === 'SEMANTIC' || (value >= 0 && value < strategyForm.chunkSize)) callback()
      else callback(new Error('重叠长度必须小于最大分块长度'))
    },
    trigger: 'change'
  }],
  similarityThreshold: [{ required: true, type: 'number', min: 0, max: 1, message: '阈值范围为 0–1', trigger: 'change' }],
  priority: [{ required: true, type: 'number', min: 0, max: 10000, message: '优先级范围为 0–10000', trigger: 'change' }],
  minDocumentLength: [{ required: true, type: 'number', min: 0, message: '最小字符数不能小于 0', trigger: 'change' }],
  maxDocumentLength: [{
    validator: (_rule, value, callback) => {
      if (value == null || value >= strategyForm.minDocumentLength) callback()
      else callback(new Error('最大字符数不能小于最小字符数'))
    },
    trigger: 'change'
  }]
}

const strategyLabel = (type: string) => ({
  FIXED_CHARACTER: '固定字符',
  PARAGRAPH: '按段落',
  RECURSIVE: '递归分隔符',
  SEMANTIC: '语义分块'
}[type] || type)

const loadStrategies = async () => {
  try {
    const response: any = await getChunkStrategies()
    if (response) strategies.value = response.data || []
  } catch {
    ElMessage.error('加载分块策略失败')
  }
}

const openManager = async () => {
  managerVisible.value = true
  await loadStrategies()
}

defineExpose({ open: openManager })

const openCreateStrategy = () => {
  editingStrategyId.value = null
  editingDefaultStrategy.value = false
  Object.assign(strategyForm, emptyStrategy())
  strategyFormVisible.value = true
}

const openEditStrategy = (strategy: ChunkStrategy) => {
  editingStrategyId.value = strategy.id
  editingDefaultStrategy.value = strategy.defaultStrategy
  Object.assign(strategyForm, {
    name: strategy.name,
    strategyType: strategy.strategyType,
    chunkSize: strategy.chunkSize,
    chunkOverlap: strategy.chunkOverlap,
    similarityThreshold: strategy.similarityThreshold,
    priority: strategy.priority,
    minDocumentLength: strategy.minDocumentLength,
    maxDocumentLength: strategy.maxDocumentLength,
    documentTypes: strategy.documentTypes
  })
  strategyFormVisible.value = true
}

const openDocumentTypeSelector = () => {
  documentTypeSelectionDraft.value = [...selectedDocumentTypes.value]
  documentTypeSelectorVisible.value = true
}

const applyDocumentTypeSelection = () => {
  selectedDocumentTypes.value = [...documentTypeSelectionDraft.value]
  documentTypeSelectorVisible.value = false
}

const resetStrategyForm = () => {
  strategyFormRef.value?.clearValidate()
  Object.assign(strategyForm, emptyStrategy())
  editingDefaultStrategy.value = false
}

const saveStrategy = async () => {
  if (!await strategyFormRef.value?.validate().catch(() => false)) return
  strategySaving.value = true
  try {
    const data = {
      ...strategyForm,
      chunkOverlap: strategyForm.strategyType === 'SEMANTIC' ? 0 : strategyForm.chunkOverlap
    }
    if (editingStrategyId.value === null) {
      await createChunkStrategy(data)
    } else {
      await updateChunkStrategy(editingStrategyId.value, data)
    }
    ElMessage.success('分块策略已保存')
    strategyFormVisible.value = false
    await loadStrategies()
  } catch {
    ElMessage.error('保存分块策略失败')
  } finally {
    strategySaving.value = false
  }
}

const removeStrategy = async (strategy: ChunkStrategy) => {
  try {
    await ElMessageBox.confirm(`确定删除分块策略“${strategy.name}”吗？`, '删除确认', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteChunkStrategy(strategy.id)
    ElMessage.success('分块策略已删除')
    await loadStrategies()
  } catch {
    ElMessage.error('删除分块策略失败；默认策略不能删除')
  }
}
</script>

<style scoped>
.strategy-header { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 16px; }
.field-hint { display: block; margin-top: 6px; color: var(--el-text-color-secondary); line-height: 1.5; }
.document-types-picker { width: 100%; }
.document-type-options { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 8px 16px; }
.document-type-hint { margin: 0 0 16px; color: var(--el-text-color-secondary); }
</style>
