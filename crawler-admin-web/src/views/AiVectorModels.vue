<template>
  <div class="vector-models-page">
    <component
      :is="embedded ? 'div' : 'el-card'"
      v-loading="loading"
      class="models-card"
      :class="{ 'models-card--embedded': embedded }"
    >
      <div class="card-header">
        <div>
          <h2 v-if="!embedded">向量模型管理</h2>
          <p>管理 Embedding 模型及其检索、分块参数，AI 配置可直接引用。</p>
        </div>
        <div class="header-actions">
          <router-link v-if="!embedded" to="/ai-config">返回 AI 配置</router-link>
          <el-button type="primary" @click="openCreate">新增向量模型</el-button>
        </div>
      </div>

      <el-table :data="models" empty-text="暂无向量模型">
        <el-table-column prop="name" label="配置名称" min-width="150" />
        <el-table-column label="提供方" width="130">
          <template #default="{ row }">
            {{ row.embeddingProvider === 'ollama' ? 'Ollama' : 'OpenAI 兼容' }}
          </template>
        </el-table-column>
        <el-table-column prop="embeddingModel" label="模型名称" min-width="220" show-overflow-tooltip />
        <el-table-column label="API Key" width="110">
          <template #default="{ row }">
            <el-tag :type="row.apiKeyConfigured ? 'success' : 'info'" effect="light">
              {{ row.apiKeyConfigured ? '已配置' : '未配置' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dimensions" label="维度" width="90" />
        <el-table-column prop="topK" label="Top K" width="90" />
        <el-table-column label="分块（长度 / 重叠）" width="170">
          <template #default="{ row }">
            {{ row.chunkSize }} / {{ row.chunkOverlap }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="removeModel(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
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
          <el-form-item label="分块长度（字符）" prop="chunkSize">
            <el-input-number v-model="form.chunkSize" :min="200" :max="20000" :step="100" controls-position="right" />
          </el-form-item>
          <el-form-item label="分块重叠（字符）" prop="chunkOverlap">
            <el-input-number v-model="form.chunkOverlap" :min="0" :max="19999" :step="50" controls-position="right" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveModel">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createVectorModel, deleteVectorModel, discoverVectorModels, getVectorModels, updateVectorModel
} from '@/api'

defineProps<{ embedded?: boolean }>()

interface VectorModel {
  id: number
  name: string
  embeddingProvider: string
  embeddingBaseUrl: string
  embeddingModel: string
  apiKeyConfigured: boolean
  dimensions: number
  topK: number
  chunkSize: number
  chunkOverlap: number
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
  chunkSize: 1500,
  chunkOverlap: 200
})

const models = ref<VectorModel[]>([])
const form = reactive(emptyModel())
const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const apiKeyConfigured = ref(false)
const discovering = ref(false)
const availableModels = ref<string[]>([])
const modelOptions = computed(() => {
  const options = [...availableModels.value]
  if (form.embeddingModel && !options.includes(form.embeddingModel)) options.unshift(form.embeddingModel)
  return options
})

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
  chunkSize: [{ required: true, type: 'number', min: 200, max: 20000, message: '分块长度范围为 200–20000', trigger: 'change' }],
  chunkOverlap: [{
    required: true,
    type: 'number',
    validator: (_rule, value, callback) => {
      if (value >= 0 && value < form.chunkSize) callback()
      else callback(new Error('重叠长度必须小于分块长度'))
    },
    trigger: 'change'
  }]
}

const loadModels = async () => {
  loading.value = true
  try {
    const response: any = await getVectorModels()
    if (response) models.value = response.data
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
    chunkSize: model.chunkSize,
    chunkOverlap: model.chunkOverlap
  })
  dialogVisible.value = true
}

const clearDiscoveredModels = () => {
  availableModels.value = []
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

onMounted(loadModels)
</script>

<style scoped>
.vector-models-page { max-width: 1180px; margin: 0 auto; }
.models-card { border-radius: 12px; }
.models-card--embedded { border: 0; border-radius: 0; }
.card-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.card-header h2 { margin: 0; color: var(--ink-900); font-size: 20px; }
.card-header p { margin: 6px 0 0; color: var(--ink-500); font-size: 13px; }
.header-actions { display: flex; align-items: center; gap: 16px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 24px; }
.form-grid :deep(.el-select), .form-grid :deep(.el-input-number) { width: 100%; }
.model-picker { display: flex; width: 100%; gap: 8px; }
.model-picker :deep(.el-select) { width: auto; min-width: 0; flex: 1; }
.form-grid :deep(.el-checkbox) { display: flex; margin-top: 8px; }
@media (max-width: 680px) {
  .card-header { flex-direction: column; }
  .form-grid { grid-template-columns: 1fr; }
}
</style>
