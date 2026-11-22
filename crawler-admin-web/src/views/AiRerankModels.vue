<template>
  <div class="rerank-models-page" :class="{ 'rerank-models-page--embedded': embedded }">
    <component
      :is="embedded ? 'div' : 'el-card'"
      v-loading="loading"
      class="models-card"
      :class="{ 'models-card--embedded': embedded }"
    >
      <div class="model-management-panel">
        <div class="card-header">
          <div>
            <h2 v-if="!embedded">重排模型管理</h2>
            <p>管理 Reranker 服务地址、模型配置和 API Key。</p>
          </div>
          <div class="header-actions">
            <router-link v-if="!embedded" to="/ai-models">返回模型管理</router-link>
            <el-button type="primary" @click="openCreate">新增重排模型</el-button>
          </div>
        </div>

        <el-form class="model-filters" @submit.prevent>
          <el-input
            v-model="searchKeyword"
            clearable
            placeholder="搜索配置名称、模型名称或服务地址"
            @keyup.enter="searchModels"
          />
          <el-input v-model="providerFilter" clearable placeholder="提供方" @keyup.enter="searchModels" />
          <el-select v-model="apiKeyFilter" clearable placeholder="全部 API Key 状态">
            <el-option label="已配置" value="configured" />
            <el-option label="未配置" value="unconfigured" />
          </el-select>
          <el-button type="primary" @click="searchModels">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form>

        <div class="model-table-panel">
          <div class="table-scroll-wrapper model-table-scroll">
            <el-table :data="models" empty-text="暂无重排模型" stripe resizable border>
              <el-table-column prop="name" label="配置名称" min-width="200" show-overflow-tooltip />
              <el-table-column prop="rerankProvider" label="提供方" min-width="120" show-overflow-tooltip />
              <el-table-column prop="rerankModel" label="模型名称" min-width="180" show-overflow-tooltip />
              <el-table-column prop="rerankBaseUrl" label="API 地址" min-width="220" show-overflow-tooltip />
              <el-table-column label="API Key" min-width="110">
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
              <el-table-column label="操作" width="80" fixed="right" align="center">
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
      :title="editingId === null ? '新增重排模型' : '编辑重排模型'"
      width="640px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item label="配置名称" prop="name">
            <el-input v-model="form.name" maxlength="128" />
          </el-form-item>
          <el-form-item label="服务提供方" prop="rerankProvider">
            <el-select
              v-model="form.rerankProvider"
              filterable
              allow-create
              default-first-option
              placeholder="选择或输入服务提供方"
              @change="clearDiscoveredModels"
            >
              <el-option label="Jina" value="jina" />
              <el-option label="Cohere" value="cohere" />
              <el-option label="SiliconFlow" value="siliconflow" />
              <el-option label="DashScope" value="dashscope" />
              <el-option label="Voyage AI" value="voyage" />
            </el-select>
          </el-form-item>
          <el-form-item label="服务地址（根地址或接口地址）" prop="rerankBaseUrl">
            <el-input
              v-model="form.rerankBaseUrl"
              maxlength="512"
              placeholder="https://api.example.com"
              @change="clearDiscoveredModels"
            />
            <small class="field-hint">可填写服务根地址、版本地址（如 /v1）或完整 /rerank 接口地址，系统会自动补全接口路径。</small>
          </el-form-item>
          <el-form-item label="模型名称" prop="rerankModel">
            <div class="model-picker">
              <el-select
                v-model="form.rerankModel"
                filterable
                allow-create
                default-first-option
                placeholder="选择或输入模型名称"
              >
                <el-option v-for="name in modelOptions" :key="name" :label="name" :value="name" />
              </el-select>
              <el-button :loading="discovering" @click="discoverModels">拉取模型</el-button>
            </div>
          </el-form-item>
          <el-form-item label="API Key" class="full-width">
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
import TableRowActions, { type TableRowAction } from '@/components/TableRowActions.vue'
import {
  createRerankModel,
  deleteRerankModel,
  discoverRerankModels,
  getRerankModelApiKey,
  getRerankModelPage,
  updateRerankModel,
  updateRerankModelApiKey
} from '@/api'

defineProps<{ embedded?: boolean }>()

interface RerankModel {
  id: string
  name: string
  rerankProvider: string
  rerankBaseUrl: string
  rerankModel: string
  apiKeyConfigured: boolean
}

const emptyModel = () => ({
  name: '',
  rerankProvider: '',
  rerankBaseUrl: '',
  rerankModel: '',
  apiKey: '',
  clearApiKey: false
})

const models = ref<RerankModel[]>([])
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
const editingApiKeyModel = ref<RerankModel | null>(null)
const discovering = ref(false)
const availableModels = ref<string[]>([])
const modelOptions = computed(() => {
  const options = [...availableModels.value]
  if (form.rerankModel && !options.includes(form.rerankModel)) options.unshift(form.rerankModel)
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
    // Let the validation error below report malformed URLs.
  }
  callback(new Error('请输入有效的 HTTP 或 HTTPS 地址'))
}

const rules: FormRules = {
  name: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  rerankProvider: [{ required: true, message: '请输入服务提供方', trigger: 'blur' }],
  rerankBaseUrl: [{ required: true, validator: validUrl, trigger: 'blur' }],
  rerankModel: [{ required: true, message: '请输入模型名称', trigger: 'blur' }]
}

const loadModels = async () => {
  loading.value = true
  try {
    const response: any = await getRerankModelPage({
      page: page.value,
      pageSize: pageSize.value,
      keyword: searchKeyword.value.trim() || undefined,
      provider: providerFilter.value.trim() || undefined,
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
    ElMessage.error('加载重排模型失败')
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

const openEdit = (model: RerankModel) => {
  editingId.value = model.id
  apiKeyConfigured.value = model.apiKeyConfigured
  Object.assign(form, {
    name: model.name,
    rerankProvider: model.rerankProvider,
    rerankBaseUrl: model.rerankBaseUrl,
    rerankModel: model.rerankModel,
    apiKey: '',
    clearApiKey: false
  })
  availableModels.value = []
  dialogVisible.value = true
}

const clearDiscoveredModels = () => {
  availableModels.value = []
}

const discoverModels = async () => {
  if (!await formRef.value?.validateField(['rerankProvider', 'rerankBaseUrl'])
    .then(() => true).catch(() => false)) return
  discovering.value = true
  try {
    const response: any = await discoverRerankModels({
      modelId: editingId.value,
      provider: form.rerankProvider,
      baseUrl: form.rerankBaseUrl,
      apiKey: form.apiKey
    })
    if (!response) return
    availableModels.value = response.data || []
    if (!availableModels.value.includes(form.rerankModel)) form.rerankModel = ''
    if (availableModels.value.length) {
      ElMessage.success(`已拉取 ${availableModels.value.length} 个重排模型`)
    } else {
      ElMessage.info('服务未返回可用的重排模型')
    }
  } catch {
    ElMessage.error('拉取重排模型失败；请检查服务地址、API Key，以及该服务是否提供模型列表接口')
  } finally {
    discovering.value = false
  }
}

const searchModels = () => {
  page.value = 1
  void loadModels()
}

const resetFilters = () => {
  searchKeyword.value = ''
  providerFilter.value = ''
  apiKeyFilter.value = ''
  page.value = 1
  void loadModels()
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
      ? await createRerankModel(form)
      : await updateRerankModel(editingId.value, form)
    if (!response) return
    ElMessage.success('重排模型已保存')
    dialogVisible.value = false
    await loadModels()
  } catch {
    ElMessage.error('保存重排模型失败')
  } finally {
    saving.value = false
  }
}

const removeModel = async (model: RerankModel) => {
  try {
    await ElMessageBox.confirm(`确定删除重排模型“${model.name}”吗？`, '删除确认', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteRerankModel(model.id)
    ElMessage.success('重排模型已删除')
    await loadModels()
  } catch {
    ElMessage.error('删除重排模型失败')
  }
}

const handleRowAction = (command: string, model: RerankModel) => {
  if (command === 'edit') openEdit(model)
  else if (command === 'delete') void removeModel(model)
}

const editApiKey = async (model: RerankModel) => {
  editingApiKeyModel.value = model
  apiKeyValue.value = ''
  apiKeyDialogVisible.value = true
  try {
    const response: any = await getRerankModelApiKey(model.id)
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
    await updateRerankModelApiKey(model.id, apiKeyValue.value)
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
.rerank-models-page { max-width: 1180px; margin: 0 auto; }
.rerank-models-page--embedded { width: 100%; max-width: none; margin: 0; box-sizing: border-box; }
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
.model-table-panel :deep(.el-pagination) { margin-top: 12px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 24px; }
.form-grid :deep(.el-input), .form-grid :deep(.el-select) { width: 100%; }
.model-picker { display: flex; width: 100%; gap: 8px; }
.model-picker :deep(.el-select) { min-width: 0; flex: 1; }
.full-width { grid-column: 1 / -1; }
.full-width :deep(.el-checkbox) { display: flex; margin-top: 8px; }
.field-hint { display: block; margin-top: 6px; color: var(--ink-500); line-height: 1.5; }
@media (max-width: 680px) {
  .card-header { flex-direction: column; }
  .form-grid, .model-filters { grid-template-columns: 1fr; }
}
</style>
