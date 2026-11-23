<template>
  <div class="vector-models-page" :class="{ 'vector-models-page--embedded': embedded }">
    <component
      :is="embedded ? 'div' : 'el-card'"
      v-loading="loading"
      class="models-card"
      :class="{ 'models-card--embedded': embedded }"
    >
      <div class="model-management-panel">
        <div class="card-header">
          <div>
            <h2 v-if="!embedded">向量模型管理</h2>
            <p>管理供知识库使用的 Embedding 模型及检索参数。</p>
          </div>
          <div class="header-actions">
            <router-link v-if="!embedded" to="/ai-config">返回 AI 配置</router-link>
            <el-button type="primary" @click="openCreate">新增向量模型</el-button>
          </div>
        </div>

        <el-form class="model-filters" @submit.prevent>
          <el-input
            v-model="searchKeyword"
            clearable
            placeholder="搜索配置名称、模型名称或服务地址"
            @keyup.enter="searchModels"
          />
          <el-select v-model="providerFilter" clearable placeholder="全部提供方">
            <el-option label="Ollama" value="ollama" />
            <el-option label="OpenAI 兼容" value="openai" />
          </el-select>
          <el-select v-model="apiKeyFilter" clearable placeholder="全部 API Key 状态">
            <el-option label="已配置" value="configured" />
            <el-option label="未配置" value="unconfigured" />
          </el-select>
          <el-button type="primary" @click="searchModels">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form>

        <div class="model-table-panel">
          <div class="table-scroll-wrapper model-table-scroll">
            <el-table :data="models" empty-text="暂无向量模型" stripe resizable border>
              <el-table-column prop="name" label="配置名称" min-width="240" show-overflow-tooltip resizable />
              <el-table-column label="提供方" min-width="110" resizable>
                <template #default="{ row }">
                  {{ row.embeddingProvider === 'ollama' ? 'Ollama' : 'OpenAI 兼容' }}
                </template>
              </el-table-column>
              <el-table-column prop="embeddingModel" label="模型名称" min-width="180" show-overflow-tooltip resizable />
              <el-table-column label="API Key" min-width="100" resizable>
                <template #default="{ row }">
                  <el-tag
                    :type="row.apiKeyConfigured ? 'success' : 'info'"
                    class="is-clickable"
                    effect="light"
                    @click="editApiKey(row)"
                  >
                    {{ row.apiKeyConfigured ? '已配置' : '未配置' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="dimensions" label="维度" min-width="70" resizable />
              <el-table-column prop="topK" label="Top K" min-width="75" resizable />
              <el-table-column label="最大 Token" min-width="100" resizable>
                <template #default="{ row }">
                  {{ row.maxTokens ?? '未设置' }}
                </template>
              </el-table-column>
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
            @change="loadModels"
          />
        </div>
      </div>
    </component>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新增向量模型' : '编辑向量模型'"
      width="640px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item label="配置名称" prop="name">
            <el-input v-model="form.name" maxlength="128" />
          </el-form-item>
          <el-form-item label="服务提供方" prop="embeddingProvider">
            <el-select v-model="form.embeddingProvider" @change="clearDiscoveredModels">
              <el-option label="Ollama 原生 API" value="ollama" />
              <el-option label="OpenAI 兼容 API" value="openai" />
            </el-select>
          </el-form-item>
          <el-form-item label="服务地址" prop="embeddingBaseUrl">
            <el-input
              v-model="form.embeddingBaseUrl"
              :placeholder="form.embeddingProvider === 'ollama' ? 'http://host:11434' : 'http://host:port/v1'"
              @change="clearDiscoveredModels"
            />
          </el-form-item>
          <el-form-item label="模型名称" prop="embeddingModel">
            <div class="model-picker">
              <el-select v-model="form.embeddingModel" filterable placeholder="请先拉取模型列表">
                <el-option v-for="name in modelOptions" :key="name" :label="name" :value="name" />
              </el-select>
              <el-button :loading="discovering" @click="discoverModels">拉取模型</el-button>
            </div>
          </el-form-item>
          <el-form-item label="API Key">
            <el-input
              v-model="form.apiKey"
              type="password"
              show-password
              autocomplete="new-password"
              :placeholder="editingId !== null && apiKeyConfigured ? '已保存密钥；留空保持不变' : '可选；无密钥时留空'"
              :disabled="form.clearApiKey"
            />
            <el-checkbox v-model="form.clearApiKey" :disabled="!apiKeyConfigured">
              清除已保存的 API Key
            </el-checkbox>
          </el-form-item>
          <el-form-item label="向量维度" prop="dimensions">
            <el-input-number v-model="form.dimensions" :min="1" :max="65536" controls-position="right" />
          </el-form-item>
          <el-form-item label="Top K" prop="topK">
            <el-select v-model="form.topK">
              <el-option v-for="value in 20" :key="value" :label="String(value)" :value="value" />
            </el-select>
          </el-form-item>
          <el-form-item label="最大 Token" prop="maxTokens">
            <el-input-number
              v-model="form.maxTokens"
              :min="1"
              :max="1048576"
              :step="512"
              controls-position="right"
              placeholder="可选"
            />
            <div class="form-tip">模型最大输入 Token 数；分块长度超过该值时会自动截断，留空表示不限制</div>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveModel">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="apiKeyDialogVisible"
      title="编辑 API Key"
      width="520px"
      @closed="apiKeyValue = ''"
    >
      <el-input
        v-model="apiKeyValue"
        type="password"
        show-password
        autocomplete="off"
        placeholder="请输入新的 API Key；留空并保存可清除"
      />
      <template #footer>
        <el-button :disabled="!apiKeyValue" @click="copyApiKey">复制 API Key</el-button>
        <el-button @click="apiKeyDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="apiKeySaving" @click="saveApiKey">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Edit } from '@element-plus/icons-vue'
import {
  createVectorModel, deleteVectorModel, discoverVectorModels, getVectorModelApiKey,
  getVectorModelPage, updateVectorModel, updateVectorModelApiKey
} from '@/api'
import TableRowActions, { type TableRowAction } from '@/components/TableRowActions.vue'

defineProps<{ embedded?: boolean }>()

interface VectorModel {
  id: string
  name: string
  embeddingProvider: string
  embeddingBaseUrl: string
  embeddingModel: string
  apiKeyConfigured: boolean
  dimensions: number
  topK: number
  maxTokens?: number
}

const emptyModel = () => ({
  name: '',
  embeddingProvider: 'ollama',
  embeddingBaseUrl: '',
  embeddingModel: '',
  apiKey: '',
  clearApiKey: false,
  dimensions: 1024,
  topK: 5,
  maxTokens: undefined
})

const models = ref<VectorModel[]>([])
const page = ref(1)
const pageSize = ref(15)
const total = ref(0)
const searchKeyword = ref('')
const providerFilter = ref('')
const apiKeyFilter = ref('')
const form = reactive(emptyModel())
const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<string | null>(null)
const apiKeyConfigured = ref(false)
const apiKeyDialogVisible = ref(false)
const apiKeyValue = ref('')
const apiKeySaving = ref(false)
const editingApiKeyModel = ref<VectorModel | null>(null)
const discovering = ref(false)
const availableModels = ref<string[]>([])
const modelOptions = computed(() => {
  const options = [...availableModels.value]
  if (form.embeddingModel && !options.includes(form.embeddingModel)) options.unshift(form.embeddingModel)
  return options
})
const rowActions: TableRowAction[] = [
  { command: 'edit', label: '编辑', icon: Edit },
  { command: 'delete', label: '删除', icon: Delete, danger: true }
]
const validUrl = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  try {
    const url = new URL(value)
    if (url.protocol === 'http:' || url.protocol === 'https:') return callback()
  } catch {
    // Validation below reports malformed URLs.
  }
  callback(new Error('请输入有效的 HTTP 或 HTTPS 地址'))
}

const rules: FormRules = {
  name: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  embeddingProvider: [{ required: true, message: '请选择服务提供方', trigger: 'change' }],
  embeddingBaseUrl: [{ required: true, validator: validUrl, trigger: 'blur' }],
  embeddingModel: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  dimensions: [{ required: true, type: 'number', min: 1, message: '向量维度必须大于 0', trigger: 'change' }],
  topK: [{ required: true, type: 'number', min: 1, max: 20, message: 'Top K 范围为 1–20', trigger: 'change' }],
}

const loadModels = async () => {
  loading.value = true
  try {
    const response: any = await getVectorModelPage({
      page: page.value,
      pageSize: pageSize.value,
      keyword: searchKeyword.value.trim() || undefined,
      provider: providerFilter.value || undefined,
      apiKeyStatus: apiKeyFilter.value || undefined
    })
    if (response) {
      models.value = response.data.records || []
      total.value = response.data.total
      const lastPage = Math.max(1, Math.ceil(total.value / pageSize.value))
      if (page.value > lastPage) {
        page.value = lastPage
        await loadModels()
      }
    }
  } catch {
    ElMessage.error('加载向量模型失败')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editingId.value = null
  apiKeyConfigured.value = false
  availableModels.value = []
  Object.assign(form, emptyModel())
  dialogVisible.value = true
}

const openEdit = (model: VectorModel) => {
  editingId.value = model.id
  apiKeyConfigured.value = model.apiKeyConfigured
  availableModels.value = []
  Object.assign(form, {
    name: model.name,
    embeddingProvider: model.embeddingProvider,
    embeddingBaseUrl: model.embeddingBaseUrl,
    embeddingModel: model.embeddingModel,
    apiKey: '',
    clearApiKey: false,
    dimensions: model.dimensions,
    topK: model.topK,
    maxTokens: model.maxTokens ?? undefined
  })
  dialogVisible.value = true
}

const clearDiscoveredModels = () => {
  availableModels.value = []
}

const resetFilters = () => {
  searchKeyword.value = ''
  providerFilter.value = ''
  apiKeyFilter.value = ''
  page.value = 1
  void loadModels()
}

const searchModels = () => {
  page.value = 1
  void loadModels()
}

const discoverModels = async () => {
  if (!await formRef.value?.validateField(['embeddingProvider', 'embeddingBaseUrl']).then(() => true).catch(() => false)) return
  discovering.value = true
  try {
    const response: any = await discoverVectorModels({
      modelId: editingId.value,
      provider: form.embeddingProvider,
      baseUrl: form.embeddingBaseUrl,
      apiKey: form.apiKey
    })
    if (!response) return
    availableModels.value = response.data || []
    if (!availableModels.value.includes(form.embeddingModel)) form.embeddingModel = ''
    if (availableModels.value.length) {
      ElMessage.success(`已拉取 ${availableModels.value.length} 个向量模型`)
    } else {
      ElMessage.info('服务未返回可用的向量模型')
    }
  } catch {
    ElMessage.error('拉取向量模型失败，请检查服务地址和 API Key')
  } finally {
    discovering.value = false
  }
}

const resetForm = () => {
  formRef.value?.clearValidate()
  Object.assign(form, emptyModel())
  apiKeyConfigured.value = false
  availableModels.value = []
}

const saveModel = async () => {
  if (!await formRef.value?.validate().catch(() => false)) return
  saving.value = true
  try {
    const response: any = editingId.value === null
      ? await createVectorModel(form)
      : await updateVectorModel(editingId.value, form)
    if (!response) return
    ElMessage.success('向量模型已保存')
    dialogVisible.value = false
    await loadModels()
  } catch {
    ElMessage.error('保存向量模型失败')
  } finally {
    saving.value = false
  }
}

const removeModel = async (model: VectorModel) => {
  try {
    await ElMessageBox.confirm(`确定删除向量模型“${model.name}”吗？`, '删除确认', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteVectorModel(model.id)
    ElMessage.success('向量模型已删除')
    await loadModels()
  } catch {
    ElMessage.error('删除失败；被 AI 配置引用的模型不能删除')
  }
}

const handleRowAction = (command: string, model: VectorModel) => {
  if (command === 'edit') openEdit(model)
  else if (command === 'delete') void removeModel(model)
}

const editApiKey = async (model: VectorModel) => {
  editingApiKeyModel.value = model
  apiKeyValue.value = ''
  apiKeyDialogVisible.value = true
  try {
    const response: any = await getVectorModelApiKey(model.id)
    if (response) apiKeyValue.value = response.data || ''
  } catch {
    apiKeyDialogVisible.value = false
    ElMessage.error('读取 API Key 失败')
  }
}

const copyApiKey = async () => {
  if (!apiKeyValue.value) return
  try {
    await navigator.clipboard.writeText(apiKeyValue.value)
    ElMessage.success('API Key 已复制')
  } catch {
    ElMessage.error('复制 API Key 失败，请检查浏览器剪贴板权限')
  }
}

const saveApiKey = async () => {
  const model = editingApiKeyModel.value
  if (!model) return
  apiKeySaving.value = true
  try {
    await updateVectorModelApiKey(model.id, apiKeyValue.value)
    model.apiKeyConfigured = Boolean(apiKeyValue.value.trim())
    ElMessage.success('API Key 已更新')
    apiKeyDialogVisible.value = false
  } catch {
    ElMessage.error('更新 API Key 失败')
  } finally {
    apiKeySaving.value = false
  }
}

onMounted(loadModels)
</script>

<style scoped>
.vector-models-page { max-width: 1180px; margin: 0 auto; }
.vector-models-page--embedded { width: 100%; max-width: none; margin: 0; box-sizing: border-box; }
.models-card { border-radius: 12px; }
.models-card--embedded { border: 0; border-radius: 0; }
.card-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.card-header h2 { margin: 0; color: var(--ink-900); font-size: 20px; }
.card-header p { margin: 6px 0 0; color: var(--ink-500); font-size: 13px; }
.header-actions { display: flex; align-items: center; gap: 16px; }
.model-filters { display: grid; grid-template-columns: minmax(220px, 1fr) repeat(2, minmax(150px, 200px)) auto auto; gap: 12px; margin: 16px 0; }
.is-clickable { cursor: pointer; }
.model-table-scroll :deep(.el-table) { width: 100%; }
.model-management-panel { padding: 12px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); }
.model-table-panel { padding: 0; }
.model-table-panel :deep(.el-pagination) { margin-top: 12px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 24px; }
.form-grid :deep(.el-select), .form-grid :deep(.el-input-number) { width: 100%; }
.model-picker { display: flex; width: 100%; gap: 8px; }
.model-picker :deep(.el-select) { width: auto; min-width: 0; flex: 1; }
.form-grid :deep(.el-checkbox) { display: flex; margin-top: 8px; }
.form-tip { margin-top: 6px; color: var(--ink-500); font-size: 12px; line-height: 1.5; }
@media (max-width: 680px) {
  .card-header { flex-direction: column; }
  .form-grid { grid-template-columns: 1fr; }
  .model-filters { grid-template-columns: 1fr; }
}
</style>
