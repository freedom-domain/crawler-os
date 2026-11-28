<template>
  <div class="role-page" v-loading="loading">
    <div class="card-header">
      <div>
        <h2>智能体角色</h2>
        <p>维护可被智能体选择的角色，角色的系统提示词选中后优先生效。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增角色</el-button>
    </div>

    <div class="role-list-panel">
      <el-form class="role-toolbar" :inline="true" @submit.prevent>
        <el-form-item>
          <el-input
            v-model="keyword"
            placeholder="搜索角色名称或简介"
            clearable
            @keyup.enter="searchRoles"
          />
        </el-form-item>
        <el-form-item>
          <el-select v-model="enabledFilter" placeholder="全部状态" clearable style="width: 130px">
            <el-option label="已启用" :value="true" />
            <el-option label="已停用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="searchRoles">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-scroll-wrapper">
        <el-table :data="roles" v-loading="loading" empty-text="暂无角色" stripe resizable border>
          <el-table-column prop="name" label="角色名称" min-width="160" fixed="left" show-overflow-tooltip resizable />
          <el-table-column prop="id" label="ID" min-width="80" resizable />
          <el-table-column prop="description" label="简介" min-width="200" show-overflow-tooltip resizable />
          <el-table-column prop="systemPrompt" label="系统提示词" min-width="240" show-overflow-tooltip resizable>
            <template #default="{ row }">{{ row.systemPrompt || '-' }}</template>
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
          <el-table-column label="操作" width="160" fixed="right" align="center" resizable>
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
        @change="loadRolePage"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑角色' : '新增角色'" width="640px" top="8vh" class="role-edit-dialog" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" maxlength="64" placeholder="例如：数据分析助手" />
        </el-form-item>
        <el-form-item label="角色简介" prop="description">
          <el-input v-model="form.description" maxlength="512" placeholder="简要描述角色的适用场景" />
        </el-form-item>
        <el-form-item label="系统提示词">
          <el-input
            v-model="form.systemPrompt"
            type="textarea"
            :rows="8"
            maxlength="4000"
            show-word-limit
            placeholder="智能体选择该角色后，此提示词将优先于智能体自身提示词生效"
          />
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
        <el-button type="primary" :loading="saving" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createAssistantRole, deleteAssistantRole, getAssistantRolePage, updateAssistantRole } from '@/api'
import { formatDateTime } from '@/utils/dateTime'
import TableRowActions, { type TableRowAction } from '@/components/TableRowActions.vue'

interface AssistantRole {
  id: string
  name: string
  description: string
  systemPrompt: string
  enabled: boolean
  sort: number
  createTime?: string
  updateTime?: string
}

const roles = ref<AssistantRole[]>([])
const loading = ref(false)
const keyword = ref('')
const enabledFilter = ref<boolean | undefined>()
const page = ref(1)
const pageSize = ref(15)
const total = ref(0)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<string | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '', description: '', systemPrompt: '', enabled: true, sort: 0
})
const rules: FormRules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

const rowActions: TableRowAction[] = [
  { command: 'edit', label: '编辑', icon: Edit },
  { command: 'delete', label: '删除', icon: Delete, danger: true }
]

function searchRoles() {
  page.value = 1
  void loadRolePage()
}

function resetFilters() {
  keyword.value = ''
  enabledFilter.value = undefined
  page.value = 1
  void loadRolePage()
}

async function fetchRolePage() {
  const response: any = await getAssistantRolePage({
    page: page.value,
    pageSize: pageSize.value,
    keyword: keyword.value.trim() || undefined,
    enabled: enabledFilter.value
  })
  roles.value = response.data.records || []
  total.value = response.data.total
  const lastPage = Math.max(1, Math.ceil(total.value / pageSize.value))
  if (page.value > lastPage) {
    page.value = lastPage
    await fetchRolePage()
  }
}

async function loadRolePage() {
  loading.value = true
  try {
    await fetchRolePage()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', description: '', systemPrompt: '', enabled: true, sort: 0 })
  dialogVisible.value = true
}

function openEdit(row: AssistantRole) {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    description: row.description,
    systemPrompt: row.systemPrompt || '',
    enabled: row.enabled,
    sort: row.sort
  })
  dialogVisible.value = true
}

function handleRowAction(command: string, row: AssistantRole) {
  if (command === 'edit') openEdit(row)
  else if (command === 'delete') void removeRole(row)
}

async function saveRole() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editingId.value) await updateAssistantRole(editingId.value, { ...form })
    else await createAssistantRole({ ...form })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadRolePage()
  } finally {
    saving.value = false
  }
}

async function removeRole(row: AssistantRole) {
  await ElMessageBox.confirm(`确定删除角色“${row.name}”？`, '删除角色', { type: 'warning' })
  await deleteAssistantRole(row.id)
  ElMessage.success('删除成功')
  await loadRolePage()
}

function resetForm() {
  editingId.value = null
  formRef.value?.clearValidate()
}

onMounted(loadRolePage)
</script>

<style scoped>
.role-page { margin: 16px; }
.role-list-panel { padding: 12px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); }
.role-list-panel :deep(.el-pagination) { margin-top: 12px; }
.card-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:16px; }
.card-header h2 { margin:0 0 6px; }
.card-header p { margin:0; color:var(--el-text-color-secondary); }
.role-toolbar { margin-bottom: 4px; }
.form-grid { display:grid; grid-template-columns:1fr 1fr; gap:16px; }
:deep(.role-edit-dialog) { max-height: 84vh; display: flex; flex-direction: column; }
:deep(.role-edit-dialog .el-dialog__body) { min-height: 0; overflow-y: auto; }
@media (max-width: 700px) { .card-header { align-items:flex-start; gap:12px; } .form-grid { grid-template-columns:1fr; } }
</style>
