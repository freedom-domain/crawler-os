<template>
  <div class="chat-models-page">
    <component
      :is="embedded ? 'div' : 'el-card'"
      v-loading="loading"
      class="models-card"
      :class="{ 'models-card--embedded': embedded }"
    >
      <div class="card-header">
        <div>
          <h2 v-if="!embedded">聊天模型管理</h2>
          <p>管理聊天模型服务配置和 API Key，AI 配置可直接引用。</p>
        </div>
        <div class="header-actions">
          <router-link v-if="!embedded" to="/ai-config">返回 AI 配置</router-link>
          <el-button type="primary" @click="openCreate">新增聊天模型</el-button>
        </div>
      </div>

      <el-table :data="models" empty-text="暂无聊天模型">
        <el-table-column prop="name" label="配置名称" min-width="150" />
        <el-table-column label="提供方" width="130">
          <template #default="{ row }">
            {{ providerLabel(row.chatProvider) }}
          </template>
        </el-table-column>
        <el-table-column prop="chatModel" label="模型名称" min-width="220" show-overflow-tooltip />
        <el-table-column label="上下文长度" width="130">
          <template #default="{ row }">{{ row.contextLength.toLocaleString() }} Token</template>
        </el-table-column>
        <el-table-column prop="chatBaseUrl" label="API 地址" min-width="240" show-overflow-tooltip />
        <el-table-column label="API Key" width="130">
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
      :title="editingId === null ? '新增聊天模型' : '编辑聊天模型'"
      width="640px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item label="配置名称" prop="name">
            <el-input v-model="form.name" maxlength="128" />
          </el-form-item>
          <el-form-item label="服务提供方" prop="chatProvider">
            <el-select v-model="form.chatProvider" @change="clearDiscoveredModels">
              <el-option label="OpenAI 兼容 API" value="openai" />
              <el-option label="vLLM（OpenAI 兼容 API）" value="vllm" />
              <el-option label="Ollama 原生 API" value="ollama" />
            </el-select>
          </el-form-item>
          <el-form-item label="服务地址" prop="chatBaseUrl">
            <el-input v-model="form.chatBaseUrl" placeholder="http://host:port/v1" @change="clearDiscoveredModels" />
          </el-form-item>
          <el-form-item label="模型名称" prop="chatModel">
            <div class="model-picker">
              <el-select v-model="form.chatModel" filterable placeholder="请先拉取模型列表">
                <el-option v-for="name in modelOptions" :key="name" :label="name" :value="name" />
              </el-select>
              <el-button :loading="discovering" @click="discoverModels">拉取模型</el-button>
            </div>
          </el-form-item>
          <el-form-item label="上下文长度（Token）" prop="contextLength">
            <el-input-number
              v-model="form.contextLength"
              :min="1024"
              :max="2000000"
              :step="1024"
              controls-position="right"
            />
            <small class="field-hint">Ollama 会用此值设置 num_ctx；其他提供方按此值估算历史消息预算。</small>
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
        <el-button @click="apiKeyDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="apiKeySaving" @click="saveApiKey">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createChatModel, deleteChatModel, discoverChatModels, getChatModelApiKey, getChatModels, updateChatModel,
  updateChatModelApiKey
} from '@/api'

defineProps<{ embedded?: boolean }>()

interface ChatModel {
  id: string
  name: string
  chatProvider: string
  chatBaseUrl: string
  chatModel: string
  contextLength: number
  apiKeyConfigured: boolean
}

const emptyModel = () => ({
  name: '',
  chatProvider: 'openai',
  chatBaseUrl: '',
  chatModel: '',
  contextLength: 8192,
  apiKey: '',
  clearApiKey: false
})

const models = ref<ChatModel[]>([])
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
const editingApiKeyModel = ref<ChatModel | null>(null)
const discovering = ref(false)
const availableModels = ref<string[]>([])
const modelOptions = computed(() => {
  const options = [...availableModels.value]
  if (form.chatModel && !options.includes(form.chatModel)) options.unshift(form.chatModel)
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
  chatProvider: [{ required: true, message: '请选择服务提供方', trigger: 'change' }],
  chatBaseUrl: [{ required: true, validator: validUrl, trigger: 'blur' }],
  chatModel: [{ required: true, message: '请输入模型名称', trigger: 'blur' }]
}

const providerLabel = (provider: string) => {
  if (provider === 'ollama') return 'Ollama'
  if (provider === 'vllm') return 'vLLM'
  return 'OpenAI 兼容'
}

const loadModels = async () => {
  loading.value = true
  try {
    const response: any = await getChatModels()
    if (response) models.value = response.data
  } catch {
    ElMessage.error('加载聊天模型失败')
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

const openEdit = (model: ChatModel) => {
  editingId.value = model.id
  apiKeyConfigured.value = model.apiKeyConfigured
  availableModels.value = []
  Object.assign(form, { ...model, apiKey: '', clearApiKey: false })
  dialogVisible.value = true
}

const clearDiscoveredModels = () => {
  availableModels.value = []
}

const discoverModels = async () => {
  if (!await formRef.value?.validateField(['chatProvider', 'chatBaseUrl']).then(() => true).catch(() => false)) return
  discovering.value = true
  try {
    const response: any = await discoverChatModels({
      modelId: editingId.value,
      provider: form.chatProvider,
      baseUrl: form.chatBaseUrl,
      apiKey: form.apiKey
    })
    if (!response) return
    availableModels.value = response.data || []
    if (!availableModels.value.includes(form.chatModel)) form.chatModel = ''
    if (availableModels.value.length) {
      ElMessage.success(`已拉取 ${availableModels.value.length} 个聊天模型`)
    } else {
      ElMessage.info('服务未返回可用的聊天模型')
    }
  } catch {
    ElMessage.error('拉取聊天模型失败，请检查服务地址和 API Key')
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
      ? await createChatModel(form)
      : await updateChatModel(editingId.value, form)
    if (!response) return
    ElMessage.success('聊天模型已保存')
    dialogVisible.value = false
    await loadModels()
  } catch {
    ElMessage.error('保存聊天模型失败')
  } finally {
    saving.value = false
  }
}

const removeModel = async (model: ChatModel) => {
  try {
    await ElMessageBox.confirm(`确定删除聊天模型“${model.name}”吗？`, '删除确认', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteChatModel(model.id)
    ElMessage.success('聊天模型已删除')
    await loadModels()
  } catch {
    ElMessage.error('删除失败；被 AI 配置引用的模型不能删除')
  }
}

const editApiKey = async (model: ChatModel) => {
  editingApiKeyModel.value = model
  apiKeyValue.value = ''
  apiKeyDialogVisible.value = true
  try {
    const response: any = await getChatModelApiKey(model.id)
    if (response) apiKeyValue.value = response.data || ''
  } catch {
    apiKeyDialogVisible.value = false
    ElMessage.error('读取 API Key 失败')
  }
}

const saveApiKey = async () => {
  const model = editingApiKeyModel.value
  if (!model) return
  apiKeySaving.value = true
  try {
    await updateChatModelApiKey(model.id, apiKeyValue.value)
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
.chat-models-page { max-width: 1180px; margin: 0 auto; }
.models-card { border-radius: 12px; }
.models-card--embedded { border: 0; border-radius: 0; }
.card-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.card-header h2 { margin: 0; color: var(--ink-900); font-size: 20px; }
.card-header p { margin: 6px 0 0; color: var(--ink-500); font-size: 13px; }
.header-actions { display: flex; align-items: center; gap: 16px; }
.is-clickable { cursor: pointer; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 24px; }
.model-picker { display: flex; width: 100%; gap: 8px; }
.model-picker :deep(.el-select) { width: auto; min-width: 0; flex: 1; }
.form-grid :deep(.el-select) { width: 100%; }
.form-grid :deep(.el-input-number) { width: 100%; }
.field-hint { display: block; margin-top: 5px; color: var(--ink-500); font-size: 12px; line-height: 1.5; }
.form-grid :deep(.el-checkbox) { display: flex; margin-top: 8px; }
@media (max-width: 680px) {
  .card-header { display: block; }
  .header-actions { margin-top: 12px; }
  .form-grid { grid-template-columns: 1fr; }
}
</style>
