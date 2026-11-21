<template>
  <el-card class="assistant-page" v-loading="loading">
    <div class="card-header">
      <div>
        <h2>AI 助手管理</h2>
        <p>新建和维护可选的 AI 助手，并为助手配置知识库、提示词和状态。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增助手</el-button>
    </div>

    <el-table :data="assistants" empty-text="暂无 AI 助手" stripe>
      <el-table-column prop="name" label="助手名称" min-width="180" />
      <el-table-column prop="description" label="简介" min-width="220" show-overflow-tooltip />
      <el-table-column label="知识库" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.knowledgeBaseNames?.length ? row.knowledgeBaseNames.join('、') : '-' }}
        </template>
      </el-table-column>
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
          <el-button v-if="row.enabled" link type="success" @click="startConversation(row)">开始对话</el-button>
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
        <el-form-item label="欢迎语">
          <el-input v-model="form.welcomeMessage" maxlength="256" placeholder="例如：你好，我是技术文档助手，有什么可以帮你？" />
        </el-form-item>
        <el-form-item label="助手简介" prop="description">
          <el-input v-model="form.description" maxlength="512" placeholder="简要描述助手用途" />
        </el-form-item>
        <el-form-item label="知识库">
          <button type="button" class="knowledge-base-picker" @click="openKnowledgeBaseDialog">
            <span class="knowledge-base-picker-copy">
              <strong v-if="form.knowledgeBaseIds.length">
                {{ selectedKnowledgeBaseNames.join('、') }}
              </strong>
              <strong v-else class="knowledge-base-placeholder">请选择知识库</strong>
              <small>已选择 {{ form.knowledgeBaseIds.length }} 个知识库</small>
            </span>
            <span class="knowledge-base-picker-action">选择</span>
          </button>
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
    v-model="knowledgeBaseDialogVisible"
    title="选择知识库"
    width="560px"
    append-to-body
    destroy-on-close
  >
    <div class="knowledge-base-dialog-toolbar">
      <span>可同时选择多个知识库</span>
      <div>
        <el-button link type="primary" @click="selectAllKnowledgeBases">全选</el-button>
        <el-button link @click="knowledgeBaseDraftIds = []">清空</el-button>
      </div>
    </div>
    <el-checkbox-group v-model="knowledgeBaseDraftIds" class="knowledge-base-options">
      <el-checkbox
        v-for="base in knowledgeBases"
        :key="base.id"
        :label="base.id"
        :disabled="!base.enabled"
        class="knowledge-base-option"
      >
        <span class="knowledge-base-option-name">{{ base.name }}</span>
        <span class="knowledge-base-option-status">{{ base.enabled ? '已启用' : '已停用' }}</span>
      </el-checkbox>
    </el-checkbox-group>
    <template #footer>
      <el-button @click="knowledgeBaseDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="confirmKnowledgeBases">
        确定（{{ knowledgeBaseDraftIds.length }}）
      </el-button>
    </template>
  </el-dialog>

</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createAssistant, deleteAssistant, getAssistants, getKnowledgeBases, updateAssistant
} from '@/api'

interface Assistant {
  id: string
  name: string
  welcomeMessage: string
  description: string
  avatar: string
  systemPrompt: string
  enabled: boolean
  knowledgeBaseIds: string[]
  knowledgeBaseNames: string[]
  sort: number
}

const assistants = ref<Assistant[]>([])
const knowledgeBases = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const knowledgeBaseDialogVisible = ref(false)
const knowledgeBaseDraftIds = ref<string[]>([])
const editingId = ref<string | null>(null)
const router = useRouter()
const formRef = ref<FormInstance>()
const form = reactive({
  name: '', welcomeMessage: '', description: '', avatar: '', systemPrompt: '',
  enabled: true, knowledgeBaseIds: [] as string[], sort: 0
})
const rules: FormRules = {
  name: [{ required: true, message: '请输入助手名称', trigger: 'blur' }]
}

const selectedKnowledgeBaseNames = computed(() => {
  const selected = new Set(form.knowledgeBaseIds)
  return knowledgeBases.value
    .filter((base: any) => selected.has(base.id))
    .map((base: any) => base.name)
})

async function loadAll() {
  loading.value = true
  try {
    const [assistantRes, knowledgeBaseRes] = await Promise.all([
      getAssistants(), getKnowledgeBases()
    ])
    assistants.value = assistantRes.data || []
    knowledgeBases.value = knowledgeBaseRes.data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', welcomeMessage: '', description: '', avatar: '', systemPrompt: '',
    enabled: true, knowledgeBaseIds: [], sort: 0 })
  dialogVisible.value = true
}

function openEdit(row: Assistant) {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    welcomeMessage: row.welcomeMessage || '',
    description: row.description,
    avatar: row.avatar,
    systemPrompt: row.systemPrompt,
    enabled: row.enabled,
    knowledgeBaseIds: row.knowledgeBaseIds || [],
    sort: row.sort
  })
  dialogVisible.value = true
}

function startConversation(row: Assistant) {
  void router.push(`/ai-chat?assistantId=${encodeURIComponent(row.id)}&new=1`)
}

function openKnowledgeBaseDialog() {
  knowledgeBaseDraftIds.value = [...form.knowledgeBaseIds]
  knowledgeBaseDialogVisible.value = true
}

function selectAllKnowledgeBases() {
  knowledgeBaseDraftIds.value = knowledgeBases.value
    .filter((base: any) => base.enabled)
    .map((base: any) => base.id)
}

function confirmKnowledgeBases() {
  form.knowledgeBaseIds = [...knowledgeBaseDraftIds.value]
  knowledgeBaseDialogVisible.value = false
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
.knowledge-base-picker {
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
.knowledge-base-picker:hover {
  border-color: var(--line-strong);
  background: var(--surface);
}
.knowledge-base-picker-copy {
  display: grid;
  min-width: 0;
  gap: 3px;
}
.knowledge-base-picker-copy strong {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.knowledge-base-placeholder { color: var(--ink-500); font-weight: 400; }
.knowledge-base-picker-copy small { color: var(--ink-500); font-size: 11px; }
.knowledge-base-picker-action {
  flex: 0 0 auto;
  padding: 6px 9px;
  border-radius: 6px;
  background: var(--chrome-hover);
  color: var(--teal-ink);
  font-size: 12px;
  font-weight: 600;
}
.knowledge-base-dialog-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: var(--ink-500);
  font-size: 12px;
}
.knowledge-base-options {
  display: grid;
  max-height: 360px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  overflow: auto;
  padding: 2px;
}
.knowledge-base-option {
  height: 52px;
  margin: 0 !important;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface-soft);
}
.knowledge-base-option.is-checked {
  border-color: var(--teal);
  background: var(--tint);
}
.knowledge-base-option :deep(.el-checkbox__label) {
  display: grid;
  min-width: 0;
  gap: 2px;
  color: var(--ink-900);
}
.knowledge-base-option-name {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.knowledge-base-option-status { color: var(--ink-500); font-size: 10px; }
.form-grid { display:grid; grid-template-columns:1fr 1fr; gap:16px; }
:deep(.assistant-chat-dialog .el-dialog__body) { height: calc(92vh - 54px); padding: 0; overflow: hidden; }
:deep(.assistant-chat-dialog .ai-chat-page) { height: 100%; min-height: 420px; margin: 0; border: 0; border-radius: 0; }
@media (max-width: 700px) { .card-header { align-items:flex-start; gap:12px; } .form-grid { grid-template-columns:1fr; } }
@media (max-width: 600px) {
  .knowledge-base-options { grid-template-columns: 1fr; }
  .knowledge-base-dialog-toolbar { align-items:flex-start; gap:8px; }
}
</style>
