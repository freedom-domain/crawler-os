<template>
  <div class="chat-models-page">
    <el-card v-loading="loading" class="models-card">
      <template #header>
        <div class="card-header">
          <div>
            <h2>聊天模型管理</h2>
            <p>管理聊天模型服务配置和 API Key，AI 配置可直接引用。</p>
          </div>
          <div class="header-actions">
            <router-link to="/ai-config">返回 AI 配置</router-link>
            <el-button type="primary" @click="openCreate">新增聊天模型</el-button>
          </div>
        </div>
      </template>

      <el-table :data="models" empty-text="暂无聊天模型">
        <el-table-column prop="name" label="配置名称" min-width="150" />
        <el-table-column label="提供方" width="130">
          <template #default="{ row }">
            {{ row.chatProvider === 'ollama' ? 'Ollama' : 'OpenAI 兼容' }}
          </template>
        </el-table-column>
        <el-table-column prop="chatModel" label="模型名称" min-width="150" />
        <el-table-column label="API Key" width="120">
          <template #default="{ row }">
            <el-tag :type="row.apiKeyConfigured ? 'success' : 'info'" effect="light">
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
    </el-card>

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
            <el-select v-model="form.chatProvider">
              <el-option label="OpenAI 兼容 API" value="openai" />
              <el-option label="Ollama 原生 API" value="ollama" />
            </el-select>
          </el-form-item>
          <el-form-item label="服务地址" prop="chatBaseUrl">
            <el-input v-model="form.chatBaseUrl" placeholder="http://host:port/v1" />
          </el-form-item>
          <el-form-item label="模型名称" prop="chatModel">
            <el-input v-model="form.chatModel" maxlength="128" />
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createChatModel, deleteChatModel, getChatModels, updateChatModel } from '@/api'

interface ChatModel {
  id: number
  name: string
  chatProvider: string
  chatBaseUrl: string
  chatModel: string
  apiKeyConfigured: boolean
}

const emptyModel = () => ({
  name: '',
  chatProvider: 'openai',
  chatBaseUrl: '',
  chatModel: '',
  apiKey: '',
  clearApiKey: false
})

const models = ref<ChatModel[]>([])
const form = reactive(emptyModel())
const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const apiKeyConfigured = ref(false)

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
  Object.assign(form, emptyModel())
  dialogVisible.value = true
}

const openEdit = (model: ChatModel) => {
  editingId.value = model.id
  apiKeyConfigured.value = model.apiKeyConfigured
  Object.assign(form, { ...model, apiKey: '', clearApiKey: false })
  dialogVisible.value = true
}

const resetForm = () => {
  formRef.value?.clearValidate()
  Object.assign(form, emptyModel())
  apiKeyConfigured.value = false
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

onMounted(loadModels)
</script>

<style scoped>
.chat-models-page { max-width: 1180px; margin: 0 auto; }
.models-card { border-radius: 12px; }
.card-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.card-header h2 { margin: 0; color: var(--ink-900); font-size: 20px; }
.card-header p { margin: 6px 0 0; color: var(--ink-500); font-size: 13px; }
.header-actions { display: flex; align-items: center; gap: 16px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 24px; }
.form-grid :deep(.el-select) { width: 100%; }
.form-grid :deep(.el-checkbox) { display: flex; margin-top: 8px; }
@media (max-width: 680px) {
  .card-header { display: block; }
  .header-actions { margin-top: 12px; }
  .form-grid { grid-template-columns: 1fr; }
}
</style>
