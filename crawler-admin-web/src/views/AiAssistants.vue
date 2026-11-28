<template>
  <div class="assistant-page" v-loading="loading">
    <div class="card-header">
      <div>
        <h2>智能体管理</h2>
        <p>新建和维护可选的智能体，并配置知识库、提示词和状态。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增智能体</el-button>
    </div>

    <div class="assistant-list-panel">
      <el-form class="assistant-toolbar" :inline="true" @submit.prevent>
        <el-form-item>
          <el-input
            v-model="keyword"
            placeholder="搜索智能体名称、简介或知识库"
            clearable
            @keyup.enter="searchAssistants"
          />
        </el-form-item>
        <el-form-item>
          <el-select v-model="enabledFilter" placeholder="全部状态" clearable style="width: 130px">
            <el-option label="已启用" :value="true" />
            <el-option label="已停用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="searchAssistants">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-scroll-wrapper">
        <el-table :data="assistants" v-loading="loading" empty-text="暂无智能体" stripe resizable border>
          <el-table-column prop="name" label="智能体名称" min-width="180" fixed="left" show-overflow-tooltip resizable />
          <el-table-column prop="id" label="ID" min-width="80" resizable />
          <el-table-column label="角色" min-width="140" resizable>
            <template #default="{ row }">{{ row.roleName || '-' }}</template>
          </el-table-column>
          <el-table-column prop="description" label="简介" min-width="220" show-overflow-tooltip resizable />
          <el-table-column label="知识库" min-width="220" show-overflow-tooltip resizable>
            <template #default="{ row }">
              {{ row.knowledgeBaseNames?.length ? row.knowledgeBaseNames.join('、') : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" resizable>
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" effect="light">
                {{ row.enabled ? '已启用' : '已停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sort" label="排序" width="80" resizable />
          <el-table-column prop="createTime" label="创建时间" min-width="180" resizable>
            <template #default="{ row }">{{ formatDateTime(row.createTime) || '-' }}</template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" min-width="180" resizable>
            <template #default="{ row }">{{ formatDateTime(row.updateTime) || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right" align="center" resizable>
            <template #default="{ row }">
              <TableRowActions
                :items="rowActions(row)"
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
        @change="loadAssistantPage"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑智能体' : '新增智能体'" width="680px" top="8vh" class="assistant-edit-dialog" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="智能体名称" prop="name">
          <el-input v-model="form.name" maxlength="64" placeholder="例如：技术文档智能体" />
        </el-form-item>
        <el-form-item label="欢迎语">
          <el-input v-model="form.welcomeMessage" maxlength="256" placeholder="例如：你好，我是技术文档智能体，有什么可以帮你？" />
        </el-form-item>
        <el-form-item label="智能体简介" prop="description">
          <el-input v-model="form.description" maxlength="512" placeholder="简要描述智能体用途" />
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
        <el-form-item label="角色">
          <el-select
            v-model="form.roleId"
            placeholder="不选择则仅使用智能体自身的系统提示词"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="role in assistantRoles"
              :key="role.id"
              :label="role.name"
              :value="role.id"
              :disabled="!role.enabled"
            />
          </el-select>
          <div class="form-hint">角色启用且系统提示词不为空时，对话优先使用角色的系统提示词。</div>
        </el-form-item>
        <el-form-item label="系统提示词">
          <el-input v-model="form.systemPrompt" type="textarea" :rows="5" maxlength="4000" placeholder="定义智能体的角色、回答边界和语气" />
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
  </div>

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
import { ChatDotRound, Delete, Edit, Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createAssistant, deleteAssistant, getAssistantPage, getAssistantRoles, getKnowledgeBases, updateAssistant
} from '@/api'
import { formatDateTime } from '@/utils/dateTime'
import TableRowActions, { type TableRowAction } from '@/components/TableRowActions.vue'

interface Assistant {
  id: string
  name: string
  welcomeMessage: string
  description: string
  avatar: string
  systemPrompt: string
  roleId: string | null
  roleName: string | null
  enabled: boolean
  knowledgeBaseIds: string[]
  knowledgeBaseNames: string[]
  sort: number
  createTime?: string
  updateTime?: string
}

const assistants = ref<Assistant[]>([])
const knowledgeBases = ref<any[]>([])
const assistantRoles = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const enabledFilter = ref<boolean | undefined>()
const page = ref(1)
const pageSize = ref(15)
const total = ref(0)
const saving = ref(false)
const dialogVisible = ref(false)
const knowledgeBaseDialogVisible = ref(false)
const knowledgeBaseDraftIds = ref<string[]>([])
const editingId = ref<string | null>(null)
const router = useRouter()
const formRef = ref<FormInstance>()
const form = reactive({
  name: '', welcomeMessage: '', description: '', avatar: '', systemPrompt: '',
  roleId: '' as string, enabled: true, knowledgeBaseIds: [] as string[], sort: 0
})
const rules: FormRules = {
  name: [{ required: true, message: '请输入智能体名称', trigger: 'blur' }]
}

const selectedKnowledgeBaseNames = computed(() => {
  const selected = new Set(form.knowledgeBaseIds)
  return knowledgeBases.value
    .filter((base: any) => selected.has(base.id))
    .map((base: any) => base.name)
})
const rowActions = (assistant: Assistant): TableRowAction[] => [
  { command: 'chat', label: '开始对话', icon: ChatDotRound, disabled: !assistant.enabled },
  { command: 'edit', label: '编辑', icon: Edit },
  { command: 'delete', label: '删除', icon: Delete, danger: true }
]

function searchAssistants() {
  page.value = 1
  void loadAssistantPage()
}

function resetFilters() {
  keyword.value = ''
  enabledFilter.value = undefined
  page.value = 1
  void loadAssistantPage()
}

async function loadAll() {
  loading.value = true
  try {
    const [assistantRes, knowledgeBaseRes, roleRes] = await Promise.all([
      fetchAssistantPage(), getKnowledgeBases(), getAssistantRoles()
    ])
    knowledgeBases.value = knowledgeBaseRes.data || []
    assistantRoles.value = roleRes.data || []
  } finally {
    loading.value = false
  }
}

async function fetchAssistantPage() {
  const response: any = await getAssistantPage({
    page: page.value,
    pageSize: pageSize.value,
    keyword: keyword.value.trim() || undefined,
    enabled: enabledFilter.value
  })
  assistants.value = response.data.records || []
  total.value = response.data.total
  const lastPage = Math.max(1, Math.ceil(total.value / pageSize.value))
  if (page.value > lastPage) {
    page.value = lastPage
    await fetchAssistantPage()
  }
}

async function loadAssistantPage() {
  loading.value = true
  try {
    await fetchAssistantPage()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', welcomeMessage: '', description: '', avatar: '', systemPrompt: '',
    roleId: '', enabled: true, knowledgeBaseIds: [], sort: 0 })
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
    roleId: row.roleId || '',
    enabled: row.enabled,
    knowledgeBaseIds: row.knowledgeBaseIds || [],
    sort: row.sort
  })
  dialogVisible.value = true
}

function startConversation(row: Assistant) {
  void router.push(`/ai-chat?assistantId=${encodeURIComponent(row.id)}&new=1`)
}

function handleRowAction(command: string, row: Assistant) {
  if (command === 'chat' && row.enabled) startConversation(row)
  else if (command === 'edit') openEdit(row)
  else if (command === 'delete') void removeAssistant(row)
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
    const payload = { ...form, roleId: form.roleId || null }
    if (editingId.value) await updateAssistant(editingId.value, payload)
    else await createAssistant(payload)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadAll()
  } finally {
    saving.value = false
  }
}

async function removeAssistant(row: Assistant) {
  await ElMessageBox.confirm(`确定删除智能体“${row.name}”？`, '删除智能体', { type: 'warning' })
  await deleteAssistant(row.id)
  ElMessage.success('删除成功')
  await loadAssistantPage()
}

function resetForm() {
  editingId.value = null
  formRef.value?.clearValidate()
}

onMounted(loadAll)
</script>

<style scoped>
.assistant-page { margin: 16px; }
.assistant-list-panel { padding: 12px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); }
.assistant-list-panel :deep(.el-pagination) { margin-top: 12px; }
.card-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:16px; }
.card-header h2 { margin:0 0 6px; }
.card-header p { margin:0; color:var(--el-text-color-secondary); }
.assistant-toolbar { margin-bottom: 4px; }
.form-hint { margin-top: 4px; color: var(--ink-500); font-size: 12px; line-height: 1.5; }
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
:deep(.assistant-edit-dialog) { max-height: 84vh; display: flex; flex-direction: column; }
:deep(.assistant-edit-dialog .el-dialog__body) { min-height: 0; overflow-y: auto; }
:deep(.assistant-chat-dialog .el-dialog__body) { height: calc(92vh - 54px); padding: 0; overflow: hidden; }
:deep(.assistant-chat-dialog .ai-chat-page) { height: 100%; min-height: 420px; margin: 0; border: 0; border-radius: 0; }
@media (max-width: 700px) { .card-header { align-items:flex-start; gap:12px; } .form-grid { grid-template-columns:1fr; } }
@media (max-width: 600px) {
  .knowledge-base-options { grid-template-columns: 1fr; }
  .knowledge-base-dialog-toolbar { align-items:flex-start; gap:8px; }
}
</style>
