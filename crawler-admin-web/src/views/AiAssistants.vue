<template>
  <el-card class="assistant-page" v-loading="loading">
    <div class="card-header">
      <div>
        <h2>AI 助手管理</h2>
        <p>新建和维护可选的 AI 助手，为每个助手配置聊天模型和知识检索模型。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增助手</el-button>
    </div>

    <el-table :data="assistants" empty-text="暂无 AI 助手" stripe>
      <el-table-column prop="name" label="助手名称" min-width="180" />
      <el-table-column prop="description" label="简介" min-width="220" show-overflow-tooltip />
      <el-table-column prop="chatModelName" label="聊天模型" min-width="180" show-overflow-tooltip />
      <el-table-column prop="vectorModelName" label="向量模型" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" effect="light">
            {{ row.enabled ? '已启用' : '已停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.enabled" link type="success" @click="openChat(row)">进入聊天</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="removeAssistant(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑 AI 助手' : '新增 AI 助手'" width="680px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="助手名称" prop="name">
          <el-input v-model="form.name" maxlength="64" placeholder="例如：技术文档助手" />
        </el-form-item>
        <el-form-item label="助手简介" prop="description">
          <el-input v-model="form.description" maxlength="512" placeholder="简要描述助手用途" />
        </el-form-item>
        <el-form-item label="聊天模型" prop="chatModelId">
          <el-select v-model="form.chatModelId" placeholder="请选择聊天模型" style="width:100%">
            <el-option v-for="model in chatModels" :key="model.id" :label="`${model.name}（${model.chatModel}）`" :value="model.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="向量模型" prop="vectorModelId">
          <el-select v-model="form.vectorModelId" placeholder="请选择向量模型" style="width:100%">
            <el-option v-for="model in vectorModels" :key="model.id" :label="`${model.name}（${model.embeddingModel}）`" :value="model.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="系统提示词">
          <el-input v-model="form.systemPrompt" type="textarea" :rows="5" maxlength="4000" placeholder="定义助手的角色、回答边界和语气" />
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
        <el-button type="primary" :loading="saving" @click="saveAssistant">保存</el-button>
      </template>
    </el-dialog>
  </el-card>

  <el-dialog
    v-model="chatDialogVisible"
    :title="`${chatAssistantName} - 聊天`"
    width="92vw"
    top="4vh"
    destroy-on-close
    class="assistant-chat-dialog"
  >
    <AiChat v-if="chatDialogVisible" :assistant-id="chatAssistantId" embedded />
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createAssistant, deleteAssistant, getAssistants, getChatModels, getVectorModels, updateAssistant
} from '@/api'
import AiChat from '@/views/AiChat.vue'

interface Assistant {
  id: string
  name: string
  description: string
  avatar: string
  systemPrompt: string
  enabled: boolean
  chatModelId: string
  vectorModelId: string
  chatModelName: string
  vectorModelName: string
  sort: number
}

const assistants = ref<Assistant[]>([])
const chatModels = ref<any[]>([])
const vectorModels = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const chatDialogVisible = ref(false)
const chatAssistantId = ref('')
const chatAssistantName = ref('')
const editingId = ref<string | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '', description: '', avatar: '', systemPrompt: '',
  enabled: true, chatModelId: '', vectorModelId: '', sort: 0
})
const rules: FormRules = {
  name: [{ required: true, message: '请输入助手名称', trigger: 'blur' }],
  chatModelId: [{ required: true, message: '请选择聊天模型', trigger: 'change' }],
  vectorModelId: [{ required: true, message: '请选择向量模型', trigger: 'change' }]
}

async function loadAll() {
  loading.value = true
  try {
    const [assistantRes, chatRes, vectorRes] = await Promise.all([
      getAssistants(), getChatModels(), getVectorModels()
    ])
    assistants.value = assistantRes.data || []
    chatModels.value = chatRes.data || []
    vectorModels.value = vectorRes.data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', description: '', avatar: '', systemPrompt: '',
    enabled: true, chatModelId: '', vectorModelId: '', sort: 0 })
  dialogVisible.value = true
}

function openEdit(row: Assistant) {
  editingId.value = row.id
  Object.assign(form, row)
  dialogVisible.value = true
}

function openChat(row: Assistant) {
  chatAssistantId.value = row.id
  chatAssistantName.value = row.name
  chatDialogVisible.value = true
}

async function saveAssistant() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editingId.value) await updateAssistant(editingId.value, { ...form })
    else await createAssistant({ ...form })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadAll()
  } finally {
    saving.value = false
  }
}

async function removeAssistant(row: Assistant) {
  await ElMessageBox.confirm(`确定删除 AI 助手“${row.name}”？`, '删除助手', { type: 'warning' })
  await deleteAssistant(row.id)
  ElMessage.success('删除成功')
  await loadAll()
}

function resetForm() {
  editingId.value = null
  formRef.value?.clearValidate()
}

onMounted(loadAll)
</script>

<style scoped>
.assistant-page { margin: 16px; }
.card-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:16px; }
.card-header h2 { margin:0 0 6px; }
.card-header p { margin:0; color:var(--el-text-color-secondary); }
.form-grid { display:grid; grid-template-columns:1fr 1fr; gap:16px; }
:deep(.assistant-chat-dialog .el-dialog__body) { height: calc(92vh - 54px); padding: 0; overflow: hidden; }
:deep(.assistant-chat-dialog .ai-chat-page) { height: 100%; min-height: 420px; margin: 0; border: 0; border-radius: 0; }
@media (max-width: 700px) { .card-header { align-items:flex-start; gap:12px; } .form-grid { grid-template-columns:1fr; } }
</style>
