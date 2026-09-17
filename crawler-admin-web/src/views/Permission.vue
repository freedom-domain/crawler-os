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
      <el-table-column prop="code" label="权限编码" width="200" />
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.type === 1 ? 'primary' : 'info'">
            {{ row.type === 1 ? '菜单' : '按钮' }}
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
            <el-radio :value="1">菜单</el-radio>
            <el-radio :value="2">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="路由路径">
          <el-input v-model="form.path" placeholder="如 /user（菜单类型）" />
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
import { Plus } from '@element-plus/icons-vue'

const tree = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const formVisible = ref(false)
const form = ref<any>({ id: null, name: '', code: '', type: 1, path: '', sort: 0, parentId: 0 })

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
    type: parent ? 2 : 1,
    path: '',
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
