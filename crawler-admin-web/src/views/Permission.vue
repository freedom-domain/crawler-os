<template>
  <el-card>
    <template #header><span>权限管理</span></template>

    <el-form :inline="true">
      <el-form-item>
        <el-button type="success" @click="openCreate(null)" :icon="Plus">新增权限</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="tree" v-loading="loading" row-key="id" default-expand-all :tree-props="{ children: 'children' }">
      <el-table-column prop="name" label="权限名称" width="200" />
      <el-table-column label="图标" width="90" align="center">
        <template #default="{ row }">
          <el-icon v-if="row.icon" class="permission-icon"><component :is="iconLibrary[row.icon]" /></el-icon>
          <span v-else class="icon-empty">自动</span>
        </template>
      </el-table-column>
      <el-table-column prop="code" label="权限编码" width="200" />
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="typeTag(row.type)">
            {{ typeLabel(row.type) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="path" label="路由路径" width="160" />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openCreate(row)">新增子项</el-button>
          <el-button type="warning" link @click="openEdit(row)">编辑</el-button>
          <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑权限' : '新增权限'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="权限名称">
          <el-input v-model="form.name" placeholder="请输入权限名称" />
        </el-form-item>
        <el-form-item label="权限编码">
          <el-input v-model="form.code" placeholder="如 user:list" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio :value="0">目录</el-radio>
            <el-radio :value="1">菜单</el-radio>
            <el-radio :value="2">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="路由路径" v-if="form.type === 1">
          <el-input v-model="form.path" placeholder="如 /user（仅菜单类型需要）" />
        </el-form-item>
        <el-form-item label="菜单图标" v-if="form.type !== 2">
          <el-select v-model="form.icon" placeholder="请选择菜单图标" clearable style="width: 100%">
            <el-option v-for="item in iconOptions" :key="item.value" :label="item.label" :value="item.value">
              <span class="icon-option"><el-icon><component :is="item.icon" /></el-icon>{{ item.label }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveForm">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { permissionTree, permissionCreate, permissionUpdate, permissionDelete } from '@/api'
import { Plus, User, Avatar, Lock, PriceTag, Connection, List, Search, Folder, Setting, Odometer, Monitor, DataAnalysis, Document, Collection } from '@element-plus/icons-vue'

const tree = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const formVisible = ref(false)
const form = ref<any>({ id: null, name: '', code: '', type: 1, path: '', icon: '', sort: 0, parentId: 0 })

const iconOptions = [
  { value: 'User', label: '用户', icon: User },
  { value: 'Avatar', label: '角色', icon: Avatar },
  { value: 'Lock', label: '权限', icon: Lock },
  { value: 'PriceTag', label: '标签', icon: PriceTag },
  { value: 'Connection', label: '连接', icon: Connection },
  { value: 'List', label: '列表', icon: List },
  { value: 'Search', label: '搜索', icon: Search },
  { value: 'Folder', label: '文件夹', icon: Folder },
  { value: 'Setting', label: '设置', icon: Setting },
  { value: 'Monitor', label: '监控', icon: Monitor },
  { value: 'DataAnalysis', label: '数据分析', icon: DataAnalysis },
  { value: 'Document', label: '文档', icon: Document },
  { value: 'Collection', label: '集合', icon: Collection },
  { value: 'Odometer', label: '仪表盘', icon: Odometer }
]
const iconLibrary = Object.fromEntries(iconOptions.map(item => [item.value, item.icon]))

// 类型展示
const typeLabel = (type: number) => (type === 0 ? '目录' : type === 1 ? '菜单' : '按钮')
const typeTag = (type: number) => (type === 0 ? 'warning' : type === 1 ? 'primary' : 'info')

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await permissionTree()
    tree.value = res.data || []
  } finally {
    loading.value = false
  }
}

const openCreate = (parent: any) => {
  form.value = {
    id: null,
    name: '',
    code: '',
    // 顶级默认目录，子级默认菜单
    type: parent ? 1 : 0,
    path: '',
    icon: '',
    sort: 0,
    parentId: parent ? parent.id : 0
  }
  formVisible.value = true
}

const openEdit = (row: any) => {
  form.value = {
    id: row.id,
    name: row.name,
    code: row.code,
    type: row.type,
    path: row.path,
    icon: row.icon || '',
    sort: row.sort,
    parentId: row.parentId
  }
  formVisible.value = true
}

const saveForm = async () => {
  if (!form.value.name || !form.value.code) {
    ElMessage.warning('权限名称和编码不能为空')
    return
  }
  saving.value = true
  try {
    const payload = { ...form.value }
    delete payload.id
    if (form.value.id) {
      await permissionUpdate(form.value.id, payload)
    } else {
      await permissionCreate(payload)
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确定删除权限「${row.name}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await permissionDelete(row.id)
      ElMessage.success('删除成功')
      loadData()
    }).catch(() => {})
}

onMounted(loadData)
</script>

<style scoped>
.permission-icon { color: #16a6a3; font-size: 18px; vertical-align: middle; }
.icon-empty { color: #9fb3c8; font-size: 12px; }
.icon-option { display: flex; align-items: center; gap: 8px; }
</style>
